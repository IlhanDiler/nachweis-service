package de.diler.nachweis.audit;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditService {

    private static final int MAX_DETAILS_LENGTH = 500;

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /**
     * Schreibt einen Audit-Eintrag in der laufenden Transaktion. Scheitert die eigentliche
     * Aktion, wird so auch der Eintrag zurueckgerollt – das Log bleibt konsistent.
     */
    public void log(AuditAction action, UUID credentialId, String actor, String details) {
        auditRepository.save(new AuditEntry(action, credentialId, actor, Instant.now(), truncate(details)));
    }

    private static String truncate(String details) {
        if (details == null || details.length() <= MAX_DETAILS_LENGTH) {
            return details;
        }
        return details.substring(0, MAX_DETAILS_LENGTH);
    }
}
