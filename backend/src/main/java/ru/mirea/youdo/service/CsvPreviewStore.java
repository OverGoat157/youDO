package ru.mirea.youdo.service;
import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.youdo.domain.CsvImportPreview;
import ru.mirea.youdo.repository.CsvImportPreviewRepository;
/** Separate transaction: rejected preview rows never mark a surrounding write transaction rollback-only. */
@Service
public class CsvPreviewStore {
    private final CsvImportPreviewRepository previews;
    public CsvPreviewStore(CsvImportPreviewRepository previews) { this.previews=previews; }
    @Transactional
    public UUID save(UUID owner,String mode,Instant now,Instant expires,int invalidRows,String rowsJson) {
        previews.discardOld(owner,now);
        return previews.saveAndFlush(new CsvImportPreview(owner,mode,expires,invalidRows,rowsJson)).getId();
    }
}
