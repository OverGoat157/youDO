package ru.mirea.youdo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import java.util.Set;
import java.util.UUID;
import ru.mirea.youdo.security.CurrentUser;

public final class AuthDtos {
    private AuthDtos() {}
    public enum ClientType { WEB, ANDROID }
    public record LoginRequest(@NotBlank @Email @Size(max=150) String login,
            @NotBlank @Size(max=72) String password, @NotNull ClientType clientType) {
        @Override public String toString() { return "LoginRequest[redacted]"; }
    }
    public record SessionRequest(@NotNull ClientType clientType, @Size(min=43,max=43) String refreshToken) {
        @Override public String toString() { return "SessionRequest[redacted]"; }
    }
    public record ChangePasswordRequest(@NotBlank @Size(max=72) String currentPassword,
            @NotBlank @Size(max=72) String newPassword) {
        @Override public String toString() { return "ChangePasswordRequest[redacted]"; }
    }
    public record UserResponse(UUID id, String name, String login, Set<String> roles, boolean mustChangePassword) {
        public static UserResponse from(CurrentUser u) { return new UserResponse(u.id(),u.name(),u.login(),u.roles(),u.mustChangePassword()); }
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, UserResponse user, String refreshToken) {
        @Override public String toString() { return "TokenResponse[redacted]"; }
    }
    public record IssuedTokens(TokenResponse response, String refreshToken) {
        @Override public String toString() { return "IssuedTokens[redacted]"; }
    }
}
