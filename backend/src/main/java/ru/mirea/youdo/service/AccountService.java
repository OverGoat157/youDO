package ru.mirea.youdo.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.data.domain.*;
import ru.mirea.youdo.domain.*;
import ru.mirea.youdo.dto.AccountDtos.*;
import ru.mirea.youdo.repository.AccountRepository;
import ru.mirea.youdo.service.AccountIntegrations.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

@Service
@Transactional(readOnly = true)
public class AccountService {
    private final AccountRepository accounts;
    private final PasswordPolicy policy;
    private final SessionRevoker sessions;
    private final AuditWriter audit;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    public AccountService(AccountRepository accounts, PasswordPolicy policy, SessionRevoker sessions, AuditWriter audit) {
        this.accounts = accounts; this.policy = policy; this.sessions = sessions; this.audit = audit;
    }
    public static String normalizeLogin(String login) {
        if (login == null) throw AccountProblem.invalid("login", "Укажите email для входа");
        return login.strip().toLowerCase(Locale.ROOT);
    }
    /** Internal auth lookup only. Never expose this entity from a REST controller. */
    public Optional<UserAccount> findByLogin(String login) { return accounts.findByLogin(normalizeLogin(login)); }
    public UserAccount findById(UUID id) { return accounts.findById(id).orElseThrow(() -> AccountProblem.missing("Аккаунт")); }
    public boolean matchesPassword(UserAccount account, String password) {
        return account.isActive() && password != null && StandardCharsets.UTF_8.newEncoder().canEncode(password)
            && password.getBytes(StandardCharsets.UTF_8).length <= 72 && password.indexOf('\0') < 0
            && encoder.matches(password, account.getPasswordHash());
    }
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<Response> list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw AccountProblem.invalid("page", "page ≥ 0, size от 1 до 100");
        Page<UserAccount> result = accounts.findAll(PageRequest.of(page, size, Sort.by("login", "id")));
        return new PageResponse<>(result.map(Response::of).getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    public Response create(Create input) {
        return Response.of(createAccount(input.name(), input.login(), input.roles(), input.temporaryPassword()));
    }
    /** Called by the infrastructure bootstrap runner, not an HTTP endpoint. Existing accounts are never reset. */
    @Transactional
    public UserAccount createBootstrapAdmin(String name, String login, String password) {
        return accounts.findByLogin(normalizeLogin(login)).orElseGet(() -> createAccount(name, login, Set.of(AccessRole.ADMIN), password));
    }
    private UserAccount createAccount(String name, String login, Set<AccessRole> roles, String password) {
        String normalized = normalizeLogin(login);
        validateIdentity(name, normalized, roles);
        policy.validate(password, "temporaryPassword");
        if (accounts.existsByLogin(normalized)) throw AccountProblem.duplicate("login");
        UserAccount account = accounts.saveAndFlush(new UserAccount(name.strip(), normalized, encoder.encode(password), roles));
        audit.record("ACCOUNT_CREATED", "ACCOUNT", account.getId(), "Создан аккаунт");
        return account;
    }
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    public Response update(UUID id, Update input) {
        String login = normalizeLogin(input.login());
        validateIdentity(input.name(), login, input.roles());
        if (input.active() == null) throw AccountProblem.invalid("active", "Укажите активность");
        UserAccount account = locked(id);
        if (accounts.existsByLoginAndIdNot(login, id)) throw AccountProblem.duplicate("login");
        boolean revoke = !account.getRoles().equals(input.roles()) || (account.isActive() && !input.active())
            || !account.getLogin().equals(login);
        account.edit(input.name().strip(), login, input.roles(), input.active());
        if (revoke) invalidate(account);
        accounts.flush();
        audit.record("ACCOUNT_UPDATED", "ACCOUNT", id, "Изменены данные аккаунта");
        return Response.of(account);
    }
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    public Response resetPassword(UUID id, String temporaryPassword) {
        policy.validate(temporaryPassword, "temporaryPassword");
        UserAccount account = locked(id);
        account.replacePassword(encoder.encode(temporaryPassword), true);
        invalidate(account);
        accounts.flush();
        audit.record("ACCOUNT_PASSWORD_RESET", "ACCOUNT", id, "Сброшен пароль");
        return Response.of(account);
    }
    /** AuthService supplies the authenticated UUID, never an ID accepted from the request body. */
    @Transactional
    public void changePassword(UUID authenticatedId, String currentPassword, String newPassword) {
        policy.validate(newPassword);
        UserAccount account = locked(authenticatedId);
        if (!matchesPassword(account, currentPassword)) throw AccountProblem.invalid("currentPassword", "Текущий пароль неверен");
        if (encoder.matches(newPassword, account.getPasswordHash())) throw AccountProblem.invalid("newPassword", "Новый пароль должен отличаться от текущего");
        account.replacePassword(encoder.encode(newPassword), false);
        invalidate(account);
        accounts.flush();
        audit.record("ACCOUNT_PASSWORD_CHANGED", "ACCOUNT", account.getId(), "Изменён пароль");
    }
    private UserAccount locked(UUID id) { return accounts.findLockedById(id).orElseThrow(() -> AccountProblem.missing("Аккаунт")); }
    private void invalidate(UserAccount account) { account.invalidateCredentials(); sessions.revokeAll(account.getId()); }
    private void validateIdentity(String name, String login, Set<AccessRole> roles) {
        if (name == null || name.isBlank() || name.strip().length() > 100) throw AccountProblem.invalid("name", "Имя обязательно, до 100 символов");
        if (login.length() > 150 || !login.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw AccountProblem.invalid("login", "Укажите корректный email");
        if (roles == null || roles.isEmpty() || roles.stream().anyMatch(Objects::isNull)) throw AccountProblem.invalid("roles", "Выберите хотя бы одну роль");
    }
}
