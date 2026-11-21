package ru.mirea.youdo.dto;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** CSV-импорт заказов: проверка файла и применение проверенного снимка. */
public final class CsvDtos {
    private CsvDtos() {}

    /** NEW_ONLY — только новые коды (CREATE_ONLY — совместимый вариант); UPSERT_BY_CODE — существующий код обновляет заказ. */
    public enum ImportMode { NEW_ONLY, CREATE_ONLY, UPSERT_BY_CODE }

    /**
     * Ошибка строки: line — номер записи с учётом заголовка (заголовок — 1). Совпадает с номером строки файла,
     * пока в значениях нет переносов строк внутри кавычек.
     */
    public record RowIssue(long line, String field, String message) {
        @JsonProperty("row") public long row() { return line; }
        @JsonProperty("code") public String code() { return "VALIDATION_ERROR"; }
    }

    public record ImportPreview(UUID previewId, ImportMode mode, int totalRows, int validRows, int invalidRows,
                                int toCreate, int toUpdate, List<RowIssue> issues, Instant expiresAt) {
        @JsonProperty("createdCount") public int createdCount() { return toCreate; }
        @JsonProperty("updatedCount") public int updatedCount() { return toUpdate; }
    }

    public record ApplyRequest(@NotNull UUID previewId) {}

    public record ImportResult(int created, int updated, int skipped) {}
}
