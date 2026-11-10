package ru.mirea.youdo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import ru.mirea.youdo.domain.*;
import java.util.*;

public final class AccountDtos {
    private AccountDtos() {}
    public record Create(@NotBlank @Size(max=100) String name,
        @NotBlank @Email @Size(max=150) String login,
        @NotEmpty Set<@NotNull AccessRole> roles,
        @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) @NotNull String temporaryPassword) {
        @Override public String toString() { return "Create[redacted]"; }
    }
    public record Update(@NotBlank @Size(max=100) String name,
        @NotBlank @Email @Size(max=150) String login, @NotEmpty Set<@NotNull AccessRole> roles,
        @NotNull Boolean active) {}
    public record Reset(@JsonProperty(access=JsonProperty.Access.WRITE_ONLY) @NotNull String temporaryPassword) {
        @Override public String toString() { return "Reset[redacted]"; }
    }
    public record Response(UUID id, String name, String login, Set<AccessRole> roles,
                           boolean active, boolean mustChangePassword, long credentialsVersion) {
        public static Response of(UserAccount a) {
            return new Response(a.getId(), a.getName(), a.getLogin(), a.getRoles(),
                a.isActive(), a.isMustChangePassword(), a.getCredentialsVersion());
        }
    }
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
}
