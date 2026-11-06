package de.diler.nachweis.credential;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CredentialRepository extends JpaRepository<CredentialRecord, UUID> {
}
