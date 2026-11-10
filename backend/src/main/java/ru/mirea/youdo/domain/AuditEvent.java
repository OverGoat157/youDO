package ru.mirea.youdo.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="audit_events")
public class AuditEvent {
    @Id private UUID id;
    private UUID actorId;
    @Column(length=150) private String actorLogin;
    @Column(nullable=false, length=60) private String action;
    @Column(nullable=false, length=60) private String entityType;
    private UUID entityId;
    @Column(nullable=false) private Instant occurredAt;
    @Column(nullable=false, length=500) private String summary;
    @Column(length=64) private String correlationId;
    protected AuditEvent() {}
    public AuditEvent(UUID actorId, String actorLogin, String action, String entityType,
                      UUID entityId, Instant occurredAt, String summary, String correlationId) {
        this.id=UUID.randomUUID(); this.actorId=actorId; this.actorLogin=actorLogin; this.action=action;
        this.entityType=entityType; this.entityId=entityId; this.occurredAt=occurredAt;
        this.summary=summary; this.correlationId=correlationId;
    }
    public UUID getId() { return id; }
    public UUID getActorId() { return actorId; }
    public String getActorLogin() { return actorLogin; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getSummary() { return summary; }
    public String getCorrelationId() { return correlationId; }
}
