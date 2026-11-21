package ru.mirea.youdo.repository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.mirea.youdo.domain.CsvImportPreview;
public interface CsvImportPreviewRepository extends JpaRepository<CsvImportPreview,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CsvImportPreview p where p.id=:id")
    Optional<CsvImportPreview> lockById(@Param("id") UUID id);
    @Modifying @Query("delete from CsvImportPreview p where p.ownerId=:owner or p.expiresAt<=:now")
    void discardOld(@Param("owner") UUID owner,@Param("now") Instant now);
}
