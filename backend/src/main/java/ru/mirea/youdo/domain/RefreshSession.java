package ru.mirea.youdo.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import ru.mirea.youdo.dto.AuthDtos.ClientType;

@Entity @Table(name = "refresh_sessions")
public class RefreshSession {
    @Id private UUID id;
    @Column(nullable=false) private UUID accountId;
    @Column(nullable=false, length=64, unique=true) private String tokenHash;
    @Column(nullable=false) private long credentialsVersion;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=10) private ClientType clientType;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant expiresAt;
    private Instant revokedAt;
    @Version private long version;
    protected RefreshSession() {}
    public RefreshSession(UUID accountId, long credentialsVersion, ClientType clientType,
                          String tokenHash, Instant now, Instant expiresAt) {
        this.id=UUID.randomUUID(); this.accountId=accountId; this.credentialsVersion=credentialsVersion;
        this.clientType=clientType; this.tokenHash=tokenHash; this.createdAt=now; this.expiresAt=expiresAt;
    }
    public UUID accountId() { return accountId; }
    public long credentialsVersion() { return credentialsVersion; }
    public ClientType clientType() { return clientType; }
    public boolean validAt(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
    public void rotate(String hash) { tokenHash=hash; }
    public void revoke(Instant now) { revokedAt=now; }
}
