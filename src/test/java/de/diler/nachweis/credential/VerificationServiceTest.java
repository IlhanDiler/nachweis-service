package de.diler.nachweis.credential;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import de.diler.nachweis.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerificationServiceTest {

    private static final String ACTOR = "test";

    private ECKey signingKey;
    private CredentialRepository credentialRepository;
    private CredentialService credentialService;
    private VerificationService verificationService;

    @BeforeEach
    void setUp() throws JOSEException {
        signingKey = new ECKeyGenerator(Curve.P_256).keyID("test-key").generate();
        credentialRepository = mock(CredentialRepository.class);
        AuditService auditService = mock(AuditService.class);
        credentialService = new CredentialService(signingKey, credentialRepository, auditService);
        verificationService = new VerificationService(signingKey, credentialRepository, auditService);
    }

    @Test
    void gueltigerNachweisWirdAkzeptiert() throws JOSEException {
        String credential = credentialService.issue(beispielAntrag(), ACTOR);

        VerificationResult result = verificationService.verify(credential, ACTOR);

        assertThat(result.valid()).isTrue();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void manipulierterNachweisWirdAbgelehnt() throws Exception {
        SignedJWT original = SignedJWT.parse(credentialService.issue(beispielAntrag(), ACTOR));
        JWTClaimsSet geaendert = new JWTClaimsSet.Builder(original.getJWTClaimsSet())
                .claim("name", "Erika Musterfrau")
                .build();
        String manipuliert = original.getHeader().toBase64URL() + "."
                + Base64URL.encode(geaendert.toString()) + "."
                + original.getSignature();

        VerificationResult result = verificationService.verify(manipuliert, ACTOR);

        assertThat(result.valid()).isFalse();
        assertThat(result.reasons()).containsExactly("Signatur ungueltig");
    }

    @Test
    void abgelaufenerNachweisWirdAbgelehnt() throws JOSEException {
        Instant vorZweiJahren = Instant.now().minus(Duration.ofDays(730));
        String credential = signiere(signingKey, CredentialService.ISSUER,
                vorZweiJahren, vorZweiJahren.plus(CredentialService.VALIDITY));

        VerificationResult result = verificationService.verify(credential, ACTOR);

        assertThat(result.valid()).isFalse();
        assertThat(result.reasons()).containsExactly("Nachweis abgelaufen");
    }

    @Test
    void falscherAusstellerWirdAbgelehnt() throws JOSEException {
        Instant jetzt = Instant.now();
        String credential = signiere(signingKey, "https://falscher-aussteller.example",
                jetzt, jetzt.plus(CredentialService.VALIDITY));

        VerificationResult result = verificationService.verify(credential, ACTOR);

        assertThat(result.valid()).isFalse();
        assertThat(result.reasons()).containsExactly("Unbekannter Aussteller");
    }

    @Test
    void fremderSchluesselWirdAbgelehnt() throws JOSEException {
        ECKey fremderSchluessel = new ECKeyGenerator(Curve.P_256).keyID("test-key").generate();
        Instant jetzt = Instant.now();
        String credential = signiere(fremderSchluessel, CredentialService.ISSUER,
                jetzt, jetzt.plus(CredentialService.VALIDITY));

        VerificationResult result = verificationService.verify(credential, ACTOR);

        assertThat(result.valid()).isFalse();
        assertThat(result.reasons()).containsExactly("Signatur ungueltig");
    }

    @Test
    void widerrufenerNachweisWirdAbgelehnt() throws Exception {
        String credential = credentialService.issue(beispielAntrag(), ACTOR);
        UUID id = UUID.fromString(SignedJWT.parse(credential).getJWTClaimsSet().getJWTID());
        Instant jetzt = Instant.now();
        CredentialRecord widerrufen = new CredentialRecord(id, CredentialService.TYPE, jetzt,
                jetzt.plus(CredentialService.VALIDITY));
        widerrufen.revoke(jetzt);
        when(credentialRepository.findById(id)).thenReturn(Optional.of(widerrufen));

        VerificationResult result = verificationService.verify(credential, ACTOR);

        assertThat(result.valid()).isFalse();
        assertThat(result.reasons()).containsExactly("Nachweis widerrufen");
    }

    @Test
    void keinJwtWirdAbgelehnt() {
        VerificationResult result = verificationService.verify("das-ist-kein-jwt", ACTOR);

        assertThat(result.valid()).isFalse();
        assertThat(result.reasons()).containsExactly("Format ungueltig: kein signiertes JWT");
    }

    private static CredentialRequest beispielAntrag() {
        return new CredentialRequest("Max Mustermann", LocalDate.of(1990, 5, 1), "A123456789");
    }

    private static String signiere(ECKey key, String issuer, Instant ausgestellt, Instant ablauf)
            throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .issueTime(Date.from(ausgestellt))
                .expirationTime(Date.from(ablauf))
                .jwtID(UUID.randomUUID().toString())
                .claim("name", "Max Mustermann")
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new ECDSASigner(key));
        return jwt.serialize();
    }
}
