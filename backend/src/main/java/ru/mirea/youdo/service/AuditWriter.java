package ru.mirea.youdo.service;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import ru.mirea.youdo.domain.AuditEvent;
import ru.mirea.youdo.repository.AuditEventRepository;
import ru.mirea.youdo.security.*;

@Service
public class AuditWriter {
    private final AuditEventRepository events;
    private final Clock clock;
    public AuditWriter(AuditEventRepository events, Clock clock) { this.events=events; this.clock=clock; }
    /** Must join the transaction that changes the business data. Summary must be a safe, short description. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(String action, String entityType, UUID entityId, String summary) {
        recordAs(CurrentUser.optional().orElse(null), action, entityType, entityId, summary);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void recordAs(CurrentUser actor, String action, String entityType, UUID entityId, String summary) {
        events.save(new AuditEvent(actor == null ? null : actor.id(), actor == null ? null : actor.login(),
            action, entityType, entityId, clock.instant(), summary, CorrelationIdFilter.current()));
    }
    /** Failed logins survive rollback of the operation which was rejected. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void failedLogin() {
        recordAs(null, "LOGIN_FAILED", "ACCOUNT", null, "Вход отклонён");
    }
}
