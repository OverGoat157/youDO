CREATE TABLE user_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    login VARCHAR(150) NOT NULL UNIQUE CHECK (login = lower(btrim(login))),
    password_hash VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
    credentials_version BIGINT NOT NULL DEFAULT 0 CHECK (credentials_version >= 0),
    participant_id BIGINT REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE account_roles (
    account_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('VIEWER', 'EDITOR', 'ADMIN')),
    PRIMARY KEY (account_id, role)
);
CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    credentials_version BIGINT NOT NULL,
    client_type VARCHAR(10) NOT NULL CHECK (client_type IN ('WEB', 'ANDROID')),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX refresh_sessions_account_idx ON refresh_sessions(account_id);
CREATE INDEX refresh_sessions_expiry_idx ON refresh_sessions(expires_at);
CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor_id UUID,
    actor_login VARCHAR(150),
    action VARCHAR(60) NOT NULL,
    entity_type VARCHAR(60) NOT NULL,
    entity_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    summary VARCHAR(500) NOT NULL,
    correlation_id VARCHAR(64)
);
CREATE INDEX audit_events_time_idx ON audit_events(occurred_at DESC, id);
CREATE INDEX audit_events_actor_idx ON audit_events(actor_id);
CREATE INDEX audit_events_action_idx ON audit_events(action, entity_type);
CREATE INDEX audit_events_correlation_idx ON audit_events(correlation_id);
CREATE TABLE auth_login_attempts (
    attempt_key VARCHAR(64) PRIMARY KEY,
    failures INTEGER NOT NULL CHECK (failures >= 0),
    blocked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE order_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    text VARCHAR(5000) NOT NULL CHECK (length(btrim(text)) > 0),
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX order_notes_order_idx ON order_notes(order_id, pinned DESC, created_at);
