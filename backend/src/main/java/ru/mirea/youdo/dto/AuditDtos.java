package ru.mirea.youdo.dto;

import java.time.Instant;
import java.util.UUID;
import ru.mirea.youdo.domain.AuditEvent;

/** DTO журнала аудита. Сущность AuditEvent наружу не отдаётся. */
public final class AuditDtos {
    private AuditDtos() {}

    /** Условия поиска; null или пустая строка — условие не задано. */
    public record AuditQuery(String q, String action, String entityType, UUID entityId,
                             Instant from, Instant to) {}

    public record AuditResponse(UUID id, UUID actorId, String actorLogin, String action, String entityType,
                                UUID entityId, Instant occurredAt, String summary, String correlationId) {
        public static AuditResponse of(AuditEvent event) {
            return new AuditResponse(event.getId(), event.getActorId(), event.getActorLogin(), event.getAction(),
                event.getEntityType(), event.getEntityId(), event.getOccurredAt(), event.getSummary(),
                event.getCorrelationId());
        }
    }
}
