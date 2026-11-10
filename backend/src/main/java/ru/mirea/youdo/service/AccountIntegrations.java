package ru.mirea.youdo.service;

import java.util.UUID;

/** Required integration ports. No no-op implementations: missing adapters fail startup. */
public final class AccountIntegrations {
    private AccountIntegrations() {}
    @FunctionalInterface public interface SessionRevoker { void revokeAll(UUID accountId); }
    @FunctionalInterface public interface AuditWriter {
        void record(String action, String entityType, UUID entityId, String summary);
    }
}
