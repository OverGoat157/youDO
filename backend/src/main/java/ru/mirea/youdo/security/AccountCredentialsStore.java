package ru.mirea.youdo.security;

import java.util.Optional;
import java.util.UUID;

/** Auth/account boundary for credential snapshots and locked writes. */
public interface AccountCredentialsStore {
    Optional<AccountCredentials> findByLogin(String normalizedLogin);
    Optional<AccountCredentials> findById(UUID id);
    AccountCredentials lock(UUID id);
    void changePassword(UUID id, String encodedPassword);
    void bootstrap(String login, String name, String encodedPassword);
}
