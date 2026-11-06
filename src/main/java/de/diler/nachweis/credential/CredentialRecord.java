package de.diler.nachweis.credential;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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
}
