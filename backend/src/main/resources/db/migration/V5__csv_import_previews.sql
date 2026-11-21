CREATE TABLE csv_import_previews (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE CASCADE,
    mode VARCHAR(30) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    invalid_rows INTEGER NOT NULL CHECK (invalid_rows >= 0),
    rows_json TEXT NOT NULL,
    used_at TIMESTAMPTZ
);
CREATE INDEX csv_import_previews_owner_idx ON csv_import_previews(owner_id);
CREATE INDEX csv_import_previews_expiry_idx ON csv_import_previews(expires_at);
