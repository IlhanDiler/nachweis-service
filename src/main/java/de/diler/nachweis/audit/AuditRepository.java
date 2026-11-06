package de.diler.nachweis.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditRepository extends JpaRepository<AuditEntry, Long> {

    List<AuditEntry> findByCredentialIdOrderByOccurredAtAsc(UUID credentialId);
}
