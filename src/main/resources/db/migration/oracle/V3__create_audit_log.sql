CREATE TABLE audit_log (
    id            NUMBER(19)               GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    action        VARCHAR2(20)             NOT NULL,
    credential_id RAW(16)                  REFERENCES credential (id),
    actor         VARCHAR2(100)            NOT NULL,
    occurred_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    details       VARCHAR2(500),
    CONSTRAINT chk_audit_action CHECK (action IN ('ISSUED', 'VERIFIED', 'REVOKED'))
);

CREATE INDEX idx_audit_credential ON audit_log (credential_id);
CREATE INDEX idx_audit_occurred_at ON audit_log (occurred_at);
