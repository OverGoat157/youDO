package ru.mirea.youdo.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.youdo.config.YoudoProperties;
import ru.mirea.youdo.domain.RefreshSession;
import ru.mirea.youdo.dto.AuthDtos.*;
import ru.mirea.youdo.error.ApiException;
import ru.mirea.youdo.repository.RefreshSessionRepository;
import ru.mirea.youdo.security.*;

@Service
public class AuthService {
    private final AccountCredentialsStore accounts;
    private final PasswordEncoder encoder;
    private final NewPasswordPolicy passwords;
    private final TokenService tokens;
    private final RefreshSessionRepository sessions;
    private final SessionRevoker revoker;
    private final LoginRateLimiter limiter;
    private final AuditWriter audit;
    private final Clock clock;
    private final YoudoProperties properties;
    private final String dummyHash;
    public AuthService(AccountCredentialsStore accounts, PasswordEncoder encoder, NewPasswordPolicy passwords,
            TokenService tokens, RefreshSessionRepository sessions, SessionRevoker revoker,
            LoginRateLimiter limiter, AuditWriter audit, Clock clock, YoudoProperties properties) {
        this.accounts=accounts; this.encoder=encoder; this.passwords=passwords; this.tokens=tokens;
        this.sessions=sessions; this.revoker=revoker; this.limiter=limiter; this.audit=audit;
        this.clock=clock; this.properties=properties;
        this.dummyHash=encoder.encode(tokens.refresh());
    }
    @Transactional(noRollbackFor=ApiException.class)
    public IssuedTokens login(LoginRequest request, String ip) {
        String login=request.login().strip().toLowerCase(Locale.ROOT);
        String key=limiter.check(login,ip);
        var found=accounts.findByLogin(login);
        var account=found.map(a -> accounts.lock(a.id())).orElse(null);
        boolean matches=matches(request.password(), account == null ? dummyHash : account.passwordHash());
        if (account == null || !account.active() || !matches) {
            limiter.failed(key); audit.failedLogin();
            throw new ApiException(401,"INVALID_CREDENTIALS","Неверный логин или пароль");
        }
        limiter.succeeded(key);
        String refresh=tokens.refresh();
        sessions.save(new RefreshSession(account.id(),account.credentialsVersion(),request.clientType(),
            TokenService.hash(refresh),clock.instant(),clock.instant().plus(properties.refreshDuration())));
        audit.recordAs(CurrentUser.from(account),"LOGIN","ACCOUNT",account.id(),"Успешный вход");
        return issued(account,refresh,request.clientType());
    }
    @Transactional
    public IssuedTokens refresh(String raw, ClientType clientType) {
        String hash=refreshHash(raw);
        // Always lock account before refresh row, matching account administration/password changes.
        var snapshot=sessions.findByTokenHash(hash).orElseThrow(ApiException::unauthorized);
        var account=accounts.lock(snapshot.accountId());
        var session=sessions.lockByTokenHash(hash).orElseThrow(ApiException::unauthorized);
        if (!session.validAt(clock.instant()) || session.clientType() != clientType || !account.active()
                || account.credentialsVersion() != session.credentialsVersion()) throw ApiException.unauthorized();
        if (account.mustChangePassword()) throw new ApiException(403,"PASSWORD_CHANGE_REQUIRED","Сначала смените временный пароль");
        String next=tokens.refresh();
        session.rotate(TokenService.hash(next));
        return issued(account,next,clientType);
    }
    @Transactional
    public void logout(String raw, ClientType clientType) {
        if (raw == null) return;
        var session=sessions.lockByTokenHash(refreshHash(raw));
        session.filter(s -> s.clientType() == clientType && s.validAt(clock.instant())).ifPresent(s -> {
            s.revoke(clock.instant());
            var actor=accounts.findById(s.accountId()).map(CurrentUser::from).orElse(null);
            audit.recordAs(actor,"LOGOUT","ACCOUNT",s.accountId(),"Выход из сессии");
        });
    }
    @Transactional
    public void changePassword(CurrentUser current, ChangePasswordRequest request) {
        var account=accounts.lock(current.id());
        if (!account.active() || account.credentialsVersion() != current.credentialsVersion()) throw ApiException.unauthorized();
        if (!matches(request.currentPassword(),account.passwordHash()))
            throw new ApiException(400,"CURRENT_PASSWORD_INVALID","Текущий пароль неверен");
        passwords.validate(request.newPassword());
        if (matches(request.newPassword(),account.passwordHash()))
            throw new ApiException(400,"PASSWORD_UNCHANGED","Новый пароль должен отличаться");
        accounts.changePassword(account.id(),encoder.encode(request.newPassword()));
        revoker.revokeAll(account.id());
        audit.recordAs(CurrentUser.from(account),"PASSWORD_CHANGED","ACCOUNT",account.id(),"Пароль изменён, сессии отозваны");
    }
    private boolean matches(String password, String hash) {
        return password.getBytes(StandardCharsets.UTF_8).length <= 72 && encoder.matches(password,hash);
    }
    private String refreshHash(String raw) {
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}")) throw ApiException.unauthorized();
        return TokenService.hash(raw);
    }
    private IssuedTokens issued(AccountCredentials account, String refresh, ClientType clientType) {
        return new IssuedTokens(new TokenResponse(tokens.access(account),"Bearer",properties.accessDuration().toSeconds(),
            UserResponse.from(CurrentUser.from(account)),clientType == ClientType.ANDROID ? refresh : null),refresh);
    }
}
