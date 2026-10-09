CREATE TABLE iam_users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    reference_currency VARCHAR(3) NOT NULL DEFAULT 'PEN',
    CONSTRAINT ck_reference_currency CHECK (reference_currency = 'PEN')
);

CREATE TABLE iam_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES iam_users(id),
    refresh_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_iam_sessions_user ON iam_sessions(user_id);
