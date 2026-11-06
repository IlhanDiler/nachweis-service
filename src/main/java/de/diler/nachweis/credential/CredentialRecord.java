package de.diler.nachweis.credential;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadaten eines ausgestellten Nachweises. Personenbezogene Inhalte (Name, Geburtsdatum,
 * Versichertennummer) werden bewusst nicht gespeichert – sie stehen nur im signierten JWT.
 */
@Entity
@Table(name = "credential")
public class CredentialRecord {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String type;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CredentialStatus status;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    // Optimistic Locking: Hibernate erhoeht die Version bei jedem Update und bricht ab,
    // wenn ein anderer Vorgang den Datensatz inzwischen geaendert hat.
    @Version
    @Column(nullable = false)
    private long version;

    protected CredentialRecord() {
        // fuer JPA
    }

    public CredentialRecord(UUID id, String type, Instant issuedAt, Instant expiresAt) {
        this.id = id;
        this.type = type;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.status = CredentialStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public CredentialStatus getStatus() {
        return status;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public boolean isRevoked() {
        return status == CredentialStatus.REVOKED;
    }

    public void revoke(Instant when) {
        if (isRevoked()) {
            throw new IllegalStateException("Nachweis ist bereits widerrufen");
        }
        this.status = CredentialStatus.REVOKED;
        this.revokedAt = when;
    }
}
