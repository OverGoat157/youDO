package ru.mirea.youdo.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.mirea.youdo.domain.*;
import ru.mirea.youdo.dto.CsvDtos.*;
import ru.mirea.youdo.dto.OrderDtos.*;
import ru.mirea.youdo.error.ApiException;
import ru.mirea.youdo.filter.OrderFilter;
import ru.mirea.youdo.repository.*;
import ru.mirea.youdo.security.CurrentUser;

/**
 * CSV заказов: экспорт текущей выборки, проверка файла и применение проверенного снимка.
 * Все записи идут через OrderService — те же проверки, версии и аудит, что у формы.
 */
@Service
public class CsvOrderService {
    static final String[] HEADERS = {"code", "title", "description", "categoryCode", "status", "budget",
        "deadline", "customerEmail", "freelancerEmail", "urgent", "tags"};
    static final int MAX_ROWS = 10000;
    static final Duration PREVIEW_TTL = Duration.ofMinutes(15);
    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT);

    private final OrderService orders;
    private final CategoryRepository categories;
    private final ParticipantRepository participants;
    private final AuditWriter audit;
    private final Clock clock;
    private final CsvImportPreviewRepository previews;
    private final CsvPreviewStore previewStore;
    private final ObjectMapper json;

    public CsvOrderService(OrderService orders, CategoryRepository categories, ParticipantRepository participants,
                           AuditWriter audit, Clock clock, CsvImportPreviewRepository previews, CsvPreviewStore previewStore, ObjectMapper json) {
        this.orders = orders; this.categories = categories; this.participants = participants;
        this.audit = audit; this.clock = clock; this.previews=previews; this.previewStore=previewStore; this.json=json;
    }

    public record Planned(long line, String code, boolean update, Write input) {}

    // ---------- экспорт ----------

    /** Все заказы под фильтром списка (без страниц), UTF-8 с BOM и «;» — Excel открывает без настройки. */
    @PreAuthorize("hasAnyRole('VIEWER','EDITOR','ADMIN')")
    public byte[] export(Map<String, String> params) {
        List<ExportRow> rows = orders.findForExport(OrderFilter.from(params), params.getOrDefault("sort", "createdAt,desc"));
        StringBuilder out = new StringBuilder("\uFEFF");
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader(HEADERS).get())) {
            for (ExportRow r : rows) {
                printer.printRecord(safe(r.code()), safe(r.title()), safe(r.description()), safe(r.categoryCode()),
                    r.status(), r.budget(), r.deadline(), safe(r.customerEmail()), safe(r.freelancerEmail()),
                    r.urgent(), safe(String.join("|", r.tags())));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Excel выполняет ячейку, которая начинается с = + - @, как формулу: экранируем апострофом. */
    static String safe(String value) {
        if (value == null) return "";
        return !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }

    /** Обратное к safe(): при повторном импорте выгруженного файла апостроф снимается. */
    static String unsafe(String value) {
        return value.length() > 1 && value.charAt(0) == '\'' && "=+-@\t\r".indexOf(value.charAt(1)) >= 0
            ? value.substring(1) : value;
    }

    // ---------- проверка (preview) ----------

    /**
     * Проверяет файл теми же правилами, что и запись, но ничего не пишет.
     * Специально без @Transactional: ошибка проверки одной строки не должна помечать общую транзакцию на откат.
     */
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public ImportPreview preview(MultipartFile file, ImportMode mode) {
        CurrentUser user = OrderService.requireEditor();
        if (mode == null || mode == ImportMode.CREATE_ONLY) mode = ImportMode.NEW_ONLY;
        List<RowIssue> issues = new ArrayList<>();
        List<Planned> planned = new ArrayList<>();
        Set<String> codesInFile = new HashSet<>();
        int total = 0;

        String text = readText(file);
        try (CSVParser csv = CSVParser.parse(text, format(text))) {
            checkHeaders(csv.getHeaderMap());
            for (CSVRecord record : csv) {
                long line = record.getRecordNumber() + 1;
                // Строки вида ;;;;; Excel оставляет после очистки ячеек — это не заказ, пропускаем.
                if (record.stream().allMatch(String::isBlank)) continue;
                if (++total > MAX_ROWS) throw AccountProblem.invalid("file", "В файле больше " + MAX_ROWS + " строк");
                if (!record.isConsistent()) {
                    issues.add(new RowIssue(line, "row", "Число значений не совпадает с заголовком"));
                    continue;
                }
                try {
                    Write input = toWrite(record);
                    String code = input.code();
                    if (code != null && !codesInFile.add(code)) {
                        issues.add(new RowIssue(line, "code", "Код повторяется в файле"));
                        continue;
                    }
                    Optional<Response> existing = code == null ? Optional.empty() : orders.findByCode(code);
                    if (existing.isPresent() && mode == ImportMode.NEW_ONLY) {
                        issues.add(new RowIssue(line, "code", "Заказ с таким кодом уже есть"));
                    } else if (existing.isPresent()) {
                        // Запоминаем версию на момент проверки: если заказ изменят до apply, будет 409, а не тихая перезапись.
                        Write update = withVersion(input, existing.get().version());
                        orders.validateUpdateByCode(code, update);
                        planned.add(new Planned(line, code, true, update));
                    } else {
                        orders.validateCreate(input);
                        planned.add(new Planned(line, code, false, input));
                    }
                } catch (AccountProblem e) {
                    issues.add(new RowIssue(line, e.field() == null ? "row" : e.field(), e.getMessage()));
                } catch (ApiException e) {
                    issues.add(new RowIssue(line, "row", e.getMessage()));
                }
            }
        } catch (IOException | UncheckedIOException | IllegalArgumentException | IllegalStateException e) {
            throw AccountProblem.invalid("file", "Не удалось разобрать CSV: проверьте заголовок и кавычки");
        }
        if (total == 0) throw AccountProblem.invalid("file", "В файле нет строк с заказами");

        Instant now = clock.instant();
        UUID id;
        try {
            id=previewStore.save(user.id(),mode.name(),now,now.plus(PREVIEW_TTL),issues.size(),json.writeValueAsString(planned));
        } catch(JsonProcessingException e) { throw new IllegalStateException("Не удалось сохранить проверку CSV",e); }
        int toUpdate = (int) planned.stream().filter(Planned::update).count();
        return new ImportPreview(id, mode, total, planned.size(), issues.size(), planned.size() - toUpdate, toUpdate,
            List.copyOf(issues), now.plus(PREVIEW_TTL));
    }

    /**
     * UTF-8 (с BOM или без). Русский Excel по умолчанию сохраняет CSV в windows-1251: если байты не являются
     * корректным UTF-8, читаем как windows-1251, а не превращаем кириллицу в «����».
     */
    private static String readText(MultipartFile file) {
        if (file != null && file.getSize() > 5L * 1024 * 1024) throw new ApiException(413,"FILE_TOO_LARGE","CSV не больше 5 MiB");
        if (file == null || file.isEmpty()) throw AccountProblem.invalid("file", "Выберите непустой CSV-файл");
        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (IOException e) { throw AccountProblem.invalid("file", "Не удалось прочитать файл"); }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            text = new String(bytes, Charset.forName("windows-1251"));
        }
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    /** Разделитель по строке заголовка: Excel в русской локали сохраняет «;», остальные программы — «,». */
    private static CSVFormat format(String text) {
        int end = text.indexOf('\n');
        String header = end < 0 ? text : text.substring(0, end);
        char delimiter = header.contains(";") ? ';' : ',';
        return CSVFormat.DEFAULT.builder().setDelimiter(delimiter).setHeader().setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true).setTrim(true).setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW).get();
    }

    private static void checkHeaders(Map<String, Integer> actual) {
        for (String header : HEADERS) {
            if (!actual.containsKey(header) && !(header.equals("categoryCode") && actual.containsKey("category"))) throw AccountProblem.invalid("file", "В заголовке нет колонки " + header);
        }
    }

    /** Строка CSV → тот же Write, что присылает форма. Длины, бюджет, статус и даты дальше проверяет OrderService. */
    private Write toWrite(CSVRecord r) {
        String categoryCode = text(r, r.isMapped("categoryCode") ? "categoryCode" : "category");
        UUID categoryId = categoryCode == null ? null : categories.findByCode(categoryCode.toUpperCase(Locale.ROOT))
            .orElseThrow(() -> AccountProblem.invalid("categoryCode", "Категория не найдена: " + categoryCode)).getId();
        String budget = text(r, "budget");
        return new Write(text(r, "code"), text(r, "title"), text(r, "description"), categoryId, status(text(r, "status")),
            budget == null ? null : budget.replace(" ", "").replace(" ", "").replace(',', '.'),
            date(text(r, "deadline")), participant(text(r, "customerEmail"), "customerEmail"),
            participant(text(r, "freelancerEmail"), "freelancerEmail"), bool(text(r, "urgent")), tags(text(r, "tags")), null);
    }

    private static String text(CSVRecord r, String column) {
        String value = r.get(column);
        return value == null || value.isBlank() ? null : unsafe(value.strip());
    }

    private Long participant(String email, String field) {
        if (email == null) return null;
        return participants.findByEmailIgnoreCase(email)
            .orElseThrow(() -> AccountProblem.invalid(field, "Участник с email не найден: " + email)).getId();
    }

    private static OrderStatus status(String value) {
        if (value == null) return null;
        try { return OrderStatus.valueOf(value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) {
            throw AccountProblem.invalid("status", "Неизвестный статус: " + value + " (OPEN, IN_PROGRESS, ON_REVIEW, COMPLETED, CANCELLED)");
        }
    }

    /** ГГГГ-ММ-ДД, как в выгрузке, или ДД.ММ.ГГГГ — так дату пересохраняет Excel. */
    private static LocalDate date(String value) {
        if (value == null) return null;
        try { return value.contains(".") ? LocalDate.parse(value, RU_DATE) : LocalDate.parse(value); }
        catch (DateTimeParseException e) { throw AccountProblem.invalid("deadline", "Дата в формате ГГГГ-ММ-ДД или ДД.ММ.ГГГГ"); }
    }

    private static Boolean bool(String value) {
        if (value == null) return false;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "1", "да" -> true;
            case "false", "0", "нет" -> false;
            default -> throw AccountProblem.invalid("urgent", "urgent: true/false или да/нет");
        };
    }

    private static List<String> tags(String value) {
        if (value == null) return List.of();
        return Arrays.stream(value.split("\\|")).map(String::strip).filter(t -> !t.isEmpty()).toList();
    }

    private static Write withVersion(Write w, long version) {
        return new Write(w.code(), w.title(), w.description(), w.categoryId(), w.status(), w.budget(), w.deadline(),
            w.customerId(), w.freelancerId(), w.urgent(), w.tags(), version);
    }

    // ---------- применение (apply) ----------

    /**
     * Применяет весь проверенный файл без ошибок одной транзакцией.
     * Ошибка любой строки (например, 409 — заказ изменили после проверки) откатывает весь файл вместе с аудитом.
     */
    @Transactional
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public ImportResult apply(UUID previewId) {
        CurrentUser user = OrderService.requireEditor();
        CsvImportPreview snapshot = previews.lockById(previewId).orElseThrow(CsvOrderService::missingPreview);
        if (!snapshot.getOwnerId().equals(user.id()) || snapshot.getUsedAt()!=null
                || !snapshot.getExpiresAt().isAfter(clock.instant())) throw missingPreview();
        if(snapshot.getInvalidRows()>0) throw AccountProblem.invalid("previewId","Исправьте все ошибки CSV и проверьте файл заново");
        List<Planned> rows;
        try { rows=json.readValue(snapshot.getRowsJson(),new TypeReference<List<Planned>>() {}); }
        catch(JsonProcessingException e) { throw new IllegalStateException("Не удалось прочитать проверку CSV",e); }
        if(rows.isEmpty()) throw AccountProblem.invalid("previewId","В файле нет корректных строк");

        int created = 0, updated = 0;
        for (Planned row : rows) {
            try {
                if (row.update()) { orders.updateByCode(row.code(), row.input()); updated++; }
                else { orders.create(row.input()); created++; }
            } catch (AccountProblem e) {
                throw new AccountProblem(e.status(), e.code(), "Строка " + row.line() + ": " + e.getMessage(), e.field());
            } catch (ApiException e) {
                throw new ApiException(e.status(), e.code(), "Строка " + row.line() + ": " + e.getMessage());
            }
        }
        String mode = snapshot.getMode().equals(ImportMode.NEW_ONLY.name()) ? "только новые" : "обновление по коду";
        snapshot.markUsed(clock.instant());
        previews.flush();
        audit.record("ORDER_CSV_IMPORTED", "ORDER", null, "Импорт CSV (" + mode + "): создано " + created
            + ", обновлено " + updated);
        return new ImportResult(created, updated, 0);
    }
    private static ApiException missingPreview() {
        return new ApiException(404,"PREVIEW_NOT_FOUND","Проверка устарела или уже применена. Проверьте файл ещё раз");
    }
}
