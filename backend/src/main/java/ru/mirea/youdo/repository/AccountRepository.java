package ru.mirea.youdo.repository;

import ru.mirea.youdo.domain.UserAccount;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface AccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByLogin(String login);
    boolean existsByLogin(String login);
    boolean existsByLoginAndIdNot(String login, UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from UserAccount a where a.id = :id")
    Optional<UserAccount> findLockedById(@Param("id") UUID id);
}
