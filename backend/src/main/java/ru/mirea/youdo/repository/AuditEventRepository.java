package ru.mirea.youdo.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.mirea.youdo.domain.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {}
