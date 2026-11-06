package de.diler.nachweis.credential;

import com.nimbusds.jwt.SignedJWT;
import de.diler.nachweis.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CredentialPersistenceTest {

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private CredentialRepository credentialRepository;

    @Test
    void ausgestellterNachweisWirdAlsMetadatenGespeichert() throws Exception {
        String credential = credentialService.issue(
                new CredentialRequest("Max Mustermann", LocalDate.of(1990, 5, 1), "A123456789"));
        SignedJWT jwt = SignedJWT.parse(credential);
        UUID id = UUID.fromString(jwt.getJWTClaimsSet().getJWTID());

        CredentialRecord gespeichert = credentialRepository.findById(id).orElseThrow();

        assertThat(gespeichert.getType()).isEqualTo(CredentialService.TYPE);
        assertThat(gespeichert.getStatus()).isEqualTo(CredentialStatus.ACTIVE);
        assertThat(gespeichert.getIssuedAt()).isEqualTo(jwt.getJWTClaimsSet().getIssueTime().toInstant());
        assertThat(gespeichert.getExpiresAt()).isEqualTo(jwt.getJWTClaimsSet().getExpirationTime().toInstant());
    }
}
