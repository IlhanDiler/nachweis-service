package de.diler.nachweis.credential;

import com.nimbusds.jwt.SignedJWT;
import de.diler.nachweis.audit.AuditAction;
import de.diler.nachweis.audit.AuditEntry;
import de.diler.nachweis.audit.AuditRepository;
import de.diler.nachweis.statistics.StatisticsResponse;
import de.diler.nachweis.statistics.StatisticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Gemeinsame Datenbanktests. Die Unterklassen fuehren sie gegen PostgreSQL und Oracle aus,
 * damit beide Migrationspfade und alle Abfragen nachweislich funktionieren.
 */
@SpringBootTest
abstract class CredentialDatabaseContractTest {

    private static final String ACTOR = "sachbearbeiter-1";

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private CredentialRepository credentialRepository;

    @Autowired
    private AuditRepository auditRepository;

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ausgestellterNachweisWirdAlsMetadatenGespeichert() throws Exception {
        String credential = issue();
        SignedJWT jwt = SignedJWT.parse(credential);

        CredentialRecord gespeichert = credentialRepository.findById(idOf(credential)).orElseThrow();

        assertThat(gespeichert.getType()).isEqualTo(CredentialService.TYPE);
        assertThat(gespeichert.getStatus()).isEqualTo(CredentialStatus.ACTIVE);
        assertThat(gespeichert.getRevokedAt()).isNull();
        assertThat(gespeichert.getIssuedAt()).isEqualTo(jwt.getJWTClaimsSet().getIssueTime().toInstant());
        assertThat(gespeichert.getExpiresAt()).isEqualTo(jwt.getJWTClaimsSet().getExpirationTime().toInstant());
    }

    @Test
    void widerrufenerNachweisWirdBeiPruefungErkanntUndProtokolliert() throws Exception {
        String credential = issue();
        UUID id = idOf(credential);

        assertThat(verificationService.verify(credential, "pruefstelle").valid()).isTrue();

        CredentialRecord widerrufen = credentialService.revoke(id, ACTOR);
        assertThat(widerrufen.getStatus()).isEqualTo(CredentialStatus.REVOKED);
        assertThat(widerrufen.getRevokedAt()).isNotNull();

        VerificationResult nachWiderruf = verificationService.verify(credential, "pruefstelle");
        assertThat(nachWiderruf.valid()).isFalse();
        assertThat(nachWiderruf.reasons()).containsExactly("Nachweis widerrufen");

        assertThat(auditRepository.findByCredentialIdOrderByOccurredAtAsc(id))
                .extracting(AuditEntry::getAction, AuditEntry::getActor)
                .containsExactly(
                        tuple(AuditAction.ISSUED, ACTOR),
                        tuple(AuditAction.VERIFIED, "pruefstelle"),
                        tuple(AuditAction.REVOKED, ACTOR),
                        tuple(AuditAction.VERIFIED, "pruefstelle"));
    }

    @Test
    void doppelterWiderrufWirdAbgelehnt() throws Exception {
        UUID id = idOf(issue());
        credentialService.revoke(id, ACTOR);

        assertThatThrownBy(() -> credentialService.revoke(id, ACTOR))
                .isInstanceOf(CredentialAlreadyRevokedException.class);
    }

    @Test
    void widerrufEinesUnbekanntenNachweisesWirdAbgelehnt() {
        assertThatThrownBy(() -> credentialService.revoke(UUID.randomUUID(), ACTOR))
                .isInstanceOf(CredentialNotFoundException.class);
    }

    @Test
    void gleichzeitigeAenderungWirdDurchOptimisticLockingErkannt() throws Exception {
        UUID id = idOf(issue());
        // Zwei Sachbearbeiter laden denselben Datensatz (ausserhalb einer Transaktion = zwei Kopien).
        CredentialRecord ersteKopie = credentialRepository.findById(id).orElseThrow();
        CredentialRecord zweiteKopie = credentialRepository.findById(id).orElseThrow();

        ersteKopie.revoke(Instant.now());
        credentialRepository.saveAndFlush(ersteKopie);

        zweiteKopie.revoke(Instant.now());
        assertThatThrownBy(() -> credentialRepository.saveAndFlush(zweiteKopie))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void datenbankLehntUngueltigenStatusAb() throws Exception {
        issue();

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE credential SET status = 'UNGUELTIG'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void datenbankLehntWiderrufOhneZeitpunktAb() throws Exception {
        issue();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE credential SET status = 'REVOKED' WHERE revoked_at IS NULL"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void statistikZaehltNachStatusMonatUndAktion() throws Exception {
        StatisticsResponse vorher = statisticsService.collect();

        String erster = issue();
        issue();
        credentialService.revoke(idOf(erster), ACTOR);
        verificationService.verify(erster, "pruefstelle");

        StatisticsResponse nachher = statisticsService.collect();

        assertThat(delta(vorher.credentialsByStatus().get(CredentialStatus.ACTIVE),
                nachher.credentialsByStatus().get(CredentialStatus.ACTIVE))).isEqualTo(1);
        assertThat(delta(vorher.credentialsByStatus().get(CredentialStatus.REVOKED),
                nachher.credentialsByStatus().get(CredentialStatus.REVOKED))).isEqualTo(1);
        assertThat(delta(vorher.auditByAction().get(AuditAction.ISSUED),
                nachher.auditByAction().get(AuditAction.ISSUED))).isEqualTo(2);
        assertThat(delta(vorher.auditByAction().get(AuditAction.REVOKED),
                nachher.auditByAction().get(AuditAction.REVOKED))).isEqualTo(1);
        assertThat(delta(vorher.verificationsByCredentialStatus().get(CredentialStatus.REVOKED),
                nachher.verificationsByCredentialStatus().get(CredentialStatus.REVOKED))).isEqualTo(1);
        assertThat(sumPerMonth(nachher) - sumPerMonth(vorher)).isEqualTo(2);
    }

    private String issue() throws Exception {
        return credentialService.issue(
                new CredentialRequest("Max Mustermann", LocalDate.of(1990, 5, 1), "A123456789"), ACTOR);
    }

    private static UUID idOf(String credential) throws Exception {
        return UUID.fromString(SignedJWT.parse(credential).getJWTClaimsSet().getJWTID());
    }

    private static long delta(Long vorher, Long nachher) {
        return nachher - vorher;
    }

    private static long sumPerMonth(StatisticsResponse statistics) {
        return statistics.issuedPerMonth().stream().mapToLong(StatisticsResponse.MonthCount::count).sum();
    }
}
