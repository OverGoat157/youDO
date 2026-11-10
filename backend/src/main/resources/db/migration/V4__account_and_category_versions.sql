ALTER TABLE user_accounts
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);

ALTER TABLE categories
    ALTER COLUMN code TYPE VARCHAR(50),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
