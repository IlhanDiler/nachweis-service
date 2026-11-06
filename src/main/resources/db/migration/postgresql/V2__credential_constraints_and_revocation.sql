-- Integritaet direkt in der Datenbank absichern, nicht nur im Java-Code.
ALTER TABLE credential ADD COLUMN revoked_at TIMESTAMPTZ;
ALTER TABLE credential ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE credential ADD CONSTRAINT chk_credential_status
    CHECK (status IN ('ACTIVE', 'REVOKED'));
ALTER TABLE credential ADD CONSTRAINT chk_credential_validity
    CHECK (expires_at > issued_at);
ALTER TABLE credential ADD CONSTRAINT chk_credential_revoked
    CHECK ((status = 'ACTIVE' AND revoked_at IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL));

CREATE INDEX idx_credential_status ON credential (status);
CREATE INDEX idx_credential_issued_at ON credential (issued_at);
