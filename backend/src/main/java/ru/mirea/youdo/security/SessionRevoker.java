package ru.mirea.youdo.security;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.youdo.repository.RefreshSessionRepository;

@Service
public class SessionRevoker {
    private final RefreshSessionRepository sessions;
    private final Clock clock;
    public SessionRevoker(RefreshSessionRepository sessions, Clock clock) { this.sessions=sessions; this.clock=clock; }
    @Transactional public void revokeAll(UUID accountId) { sessions.revokeAll(accountId, clock.instant()); }
}
