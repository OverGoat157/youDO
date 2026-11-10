package ru.mirea.youdo.security;

import java.util.Optional;
import java.util.UUID;

/** Auth/account boundary. Yura's AccountService can implement this instead of the JDBC adapter. */
public interface AccountCredentialsStore {
    Optional<AccountCredentials> findByLogin(String normalizedLogin);
    Optional<AccountCredentials> findById(UUID id);
    AccountCredentials lock(UUID id);
    void changePassword(UUID id, String encodedPassword);
    void bootstrap(String login, String name, String encodedPassword);
}
