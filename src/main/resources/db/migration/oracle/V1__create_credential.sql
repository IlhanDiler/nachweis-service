CREATE TABLE credential (
    id         RAW(16)                  PRIMARY KEY,
    type       VARCHAR2(100)            NOT NULL,
    issued_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status     VARCHAR2(20)             NOT NULL
);
