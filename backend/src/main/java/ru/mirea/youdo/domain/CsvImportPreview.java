package ru.mirea.youdo.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="csv_import_previews")
public class CsvImportPreview {
    @Id private UUID id;
    @Column(nullable=false) private UUID ownerId;
    @Column(nullable=false,length=30) private String mode;
    @Column(nullable=false) private Instant expiresAt;
    @Column(nullable=false) private int invalidRows;
    @Column(nullable=false,columnDefinition="text") private String rowsJson;
    private Instant usedAt;
    protected CsvImportPreview() {}
    public CsvImportPreview(UUID ownerId,String mode,Instant expiresAt,int invalidRows,String rowsJson) {
        id=UUID.randomUUID();this.ownerId=ownerId;this.mode=mode;this.expiresAt=expiresAt;
        this.invalidRows=invalidRows;this.rowsJson=rowsJson;
    }
    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getMode() { return mode; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getInvalidRows() { return invalidRows; }
    public String getRowsJson() { return rowsJson; }
    public Instant getUsedAt() { return usedAt; }
    public void markUsed(Instant now) { usedAt=now; }
}
