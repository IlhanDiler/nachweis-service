CREATE TABLE credential (
    id         UUID        PRIMARY KEY,
    type       VARCHAR(100) NOT NULL,
    issued_at  TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status     VARCHAR(20) NOT NULL
);
