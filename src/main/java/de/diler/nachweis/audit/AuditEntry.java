package de.diler.nachweis.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Ein Eintrag im Audit-Log: wer hat wann welche Aktion ausgefuehrt.
 * Eintraege werden nur angelegt, nie geaendert oder geloescht.
 */
@Entity
@Table(name = "audit_log")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column(name = "credential_id")
    private UUID credentialId;

    @Column(nullable = false)
    private String actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    private String details;

    protected AuditEntry() {
        // fuer JPA
    }

    public AuditEntry(AuditAction action, UUID credentialId, String actor, Instant occurredAt, String details) {
        this.action = action;
        this.credentialId = credentialId;
        this.actor = actor;
        this.occurredAt = occurredAt;
        this.details = details;
    }

    public Long getId() {
        return id;
    }

    public AuditAction getAction() {
        return action;
    }

    public UUID getCredentialId() {
        return credentialId;
    }

    public String getActor() {
        return actor;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getDetails() {
        return details;
    }
}
