package ru.mirea.youdo.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;
import ru.mirea.youdo.domain.AuditEvent;

/**
 * Только чтение журнала для поиска. Запись идёт через AuditEventRepository инфраструктуры,
 * поэтому методов save/delete здесь намеренно нет.
 */
public interface AuditQueryRepository extends Repository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {}
