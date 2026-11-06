CREATE TABLE audit_log (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    action        VARCHAR(20)  NOT NULL,
    credential_id UUID         REFERENCES credential (id),
    actor         VARCHAR(100) NOT NULL,
    occurred_at   TIMESTAMPTZ  NOT NULL,
    details       VARCHAR(500),
    CONSTRAINT chk_audit_action CHECK (action IN ('ISSUED', 'VERIFIED', 'REVOKED'))
);

CREATE INDEX idx_audit_credential ON audit_log (credential_id);
CREATE INDEX idx_audit_occurred_at ON audit_log (occurred_at);
