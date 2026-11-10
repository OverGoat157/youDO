package ru.mirea.youdo.security;

import java.util.Set;
import java.util.UUID;

/** Internal snapshot supplied by the account module. Never serialize this record. */
public record AccountCredentials(UUID id, String name, String login, String passwordHash,
        Set<String> roles, boolean active, boolean mustChangePassword, long credentialsVersion) {
    public AccountCredentials { roles = Set.copyOf(roles); }
    @Override public String toString() { return "AccountCredentials[id=" + id + ",redacted]"; }
}
