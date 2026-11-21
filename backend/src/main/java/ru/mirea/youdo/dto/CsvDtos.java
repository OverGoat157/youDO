package ru.mirea.youdo.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** CSV-импорт заказов: проверка файла и применение проверенного снимка. */
public final class CsvDtos {
    private CsvDtos() {}

    /** CREATE_ONLY — только новые коды; UPSERT_BY_CODE — существующий код обновляет заказ. */
    public enum ImportMode { CREATE_ONLY, UPSERT_BY_CODE }

    /**
     * Ошибка строки: line — номер записи с учётом заголовка (заголовок — 1). Совпадает с номером строки файла,
     * пока в значениях нет переносов строк внутри кавычек.
     */
    public record RowIssue(long line, String field, String message) {}

    public record ImportPreview(UUID previewId, ImportMode mode, int totalRows, int validRows, int invalidRows,
                                int toCreate, int toUpdate, List<RowIssue> issues, Instant expiresAt) {}

    public record ApplyRequest(@NotNull UUID previewId) {}

    public record ImportResult(int created, int updated, int skipped) {}
}
