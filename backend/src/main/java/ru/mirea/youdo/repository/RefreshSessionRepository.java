package ru.mirea.youdo.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.mirea.youdo.domain.RefreshSession;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
    Optional<RefreshSession> findByTokenHash(String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSession s where s.tokenHash = :hash")
    Optional<RefreshSession> lockByTokenHash(@Param("hash") String hash);
    @Modifying(clearAutomatically=true, flushAutomatically=true)
    @Query("update RefreshSession s set s.revokedAt = :now, s.version = s.version + 1 where s.accountId = :id and s.revokedAt is null")
    int revokeAll(@Param("id") UUID accountId, @Param("now") Instant now);
}
