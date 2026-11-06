package de.diler.nachweis.credential;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import de.diler.nachweis.audit.AuditAction;
import de.diler.nachweis.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class VerificationService {

    private final ECKey signingKey;
    private final CredentialRepository credentialRepository;
    private final AuditService auditService;

    public VerificationService(ECKey signingKey, CredentialRepository credentialRepository,
                               AuditService auditService) {
        this.signingKey = signingKey;
        this.credentialRepository = credentialRepository;
        this.auditService = auditService;
    }

    @Transactional
    public VerificationResult verify(String credential, String actor) {
        SignedJWT jwt;
        JWTClaimsSet claims;
        try {
            jwt = SignedJWT.parse(credential);
            claims = jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            return audited(null, actor, List.of("Format ungueltig: kein signiertes JWT"));
        }

        List<String> reasons = new ArrayList<>();

        // Nur ES256 akzeptieren, damit kein anderer (schwaecherer) Algorithmus untergeschoben werden kann.
        boolean signatureValid = false;
        if (!JWSAlgorithm.ES256.equals(jwt.getHeader().getAlgorithm())) {
            reasons.add("Algorithmus nicht erlaubt");
        } else if (!isSignatureValid(jwt)) {
            reasons.add("Signatur ungueltig");
        } else {
            signatureValid = true;
        }

        Date expiration = claims.getExpirationTime();
        if (expiration == null || expiration.before(new Date())) {
            reasons.add("Nachweis abgelaufen");
        }

        if (!CredentialService.ISSUER.equals(claims.getIssuer())) {
            reasons.add("Unbekannter Aussteller");
        }

        // Der jti ist nur vertrauenswuerdig, wenn die Signatur stimmt. Sonst koennte ein
        // Angreifer eine fremde ID eintragen und so z. B. das Audit-Log verfaelschen.
        Optional<CredentialRecord> record = signatureValid
                ? parseId(claims.getJWTID()).flatMap(credentialRepository::findById)
                : Optional.empty();
        if (record.isPresent() && record.get().isRevoked()) {
            reasons.add("Nachweis widerrufen");
        }

        return audited(record.map(CredentialRecord::getId).orElse(null), actor, reasons);
    }

    private VerificationResult audited(UUID credentialId, String actor, List<String> reasons) {
        VerificationResult result = new VerificationResult(reasons.isEmpty(), reasons);
        String details = result.valid() ? "gueltig" : "ungueltig: " + String.join(", ", reasons);
        auditService.log(AuditAction.VERIFIED, credentialId, actor, details);
        return result;
    }

    private static Optional<UUID> parseId(String jti) {
        if (jti == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(jti));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private boolean isSignatureValid(SignedJWT jwt) {
        try {
            return jwt.verify(new ECDSAVerifier(signingKey.toPublicJWK()));
        } catch (JOSEException e) {
            return false;
        }
    }
}
