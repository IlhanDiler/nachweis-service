package de.diler.nachweis.credential;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import de.diler.nachweis.audit.AuditAction;
import de.diler.nachweis.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class CredentialService {

    static final String ISSUER = "https://nachweis.example.de";
    static final Duration VALIDITY = Duration.ofDays(365);
    static final String TYPE = "Versicherungsbescheinigung";

    private final ECKey signingKey;
    private final CredentialRepository credentialRepository;
    private final AuditService auditService;

    public CredentialService(ECKey signingKey, CredentialRepository credentialRepository,
                             AuditService auditService) {
        this.signingKey = signingKey;
        this.credentialRepository = credentialRepository;
        this.auditService = auditService;
    }

    @Transactional
    public String issue(CredentialRequest request, String actor) throws JOSEException {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(VALIDITY);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .jwtID(id.toString())
                .claim("name", request.name())
                .claim("geburtsdatum", request.geburtsdatum().toString())
                .claim("versichertennummer", request.versichertennummer())
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(signingKey.getKeyID())
                .build();

        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new ECDSASigner(signingKey));

        credentialRepository.save(new CredentialRecord(id, TYPE, now, expiresAt));
        auditService.log(AuditAction.ISSUED, id, actor, TYPE);
        return jwt.serialize();
    }

    @Transactional
    public CredentialRecord revoke(UUID id, String actor) {
        CredentialRecord record = credentialRepository.findById(id)
                .orElseThrow(() -> new CredentialNotFoundException(id));
        if (record.isRevoked()) {
            throw new CredentialAlreadyRevokedException(id);
        }
        record.revoke(Instant.now().truncatedTo(ChronoUnit.MICROS));
        // saveAndFlush, damit ein Versionskonflikt (Optimistic Locking) noch in dieser
        // Methode auffaellt und nicht erst beim Commit.
        CredentialRecord saved = credentialRepository.saveAndFlush(record);
        auditService.log(AuditAction.REVOKED, id, actor, null);
        return saved;
    }
}
