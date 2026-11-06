package de.diler.nachweis.statistics;

import de.diler.nachweis.audit.AuditAction;
import de.diler.nachweis.credential.CredentialStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Auswertungen ueber JPQL. Hibernate uebersetzt die Abfragen in den SQL-Dialekt der
 * jeweiligen Datenbank – dieselbe Abfrage laeuft so auf PostgreSQL und Oracle.
 */
@Service
@Transactional(readOnly = true)
public class StatisticsService {

    private final EntityManager entityManager;

    public StatisticsService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public StatisticsResponse collect() {
        return new StatisticsResponse(
                credentialsByStatus(),
                issuedPerMonth(),
                auditByAction(),
                verificationsByCredentialStatus());
    }

    private Map<CredentialStatus, Long> credentialsByStatus() {
        List<Object[]> rows = entityManager.createQuery("""
                        select c.status, count(c)
                        from CredentialRecord c
                        group by c.status""", Object[].class)
                .getResultList();
        return toEnumMap(rows, CredentialStatus.class);
    }

    private List<StatisticsResponse.MonthCount> issuedPerMonth() {
        return entityManager.createQuery("""
                        select extract(year from c.issuedAt), extract(month from c.issuedAt), count(c)
                        from CredentialRecord c
                        group by extract(year from c.issuedAt), extract(month from c.issuedAt)
                        order by extract(year from c.issuedAt), extract(month from c.issuedAt)""", Object[].class)
                .getResultList()
                .stream()
                .map(row -> new StatisticsResponse.MonthCount(
                        ((Number) row[0]).intValue(),
                        ((Number) row[1]).intValue(),
                        ((Number) row[2]).longValue()))
                .toList();
    }

    private Map<AuditAction, Long> auditByAction() {
        List<Object[]> rows = entityManager.createQuery("""
                        select a.action, count(a)
                        from AuditEntry a
                        group by a.action""", Object[].class)
                .getResultList();
        return toEnumMap(rows, AuditAction.class);
    }

    // JOIN zwischen Audit-Log und Nachweisen: Wie oft wurden Nachweise geprueft, gruppiert nach
    // ihrem aktuellen Status? (Viele Pruefungen widerrufener Nachweise koennen auf Missbrauch hindeuten.)
    private Map<CredentialStatus, Long> verificationsByCredentialStatus() {
        List<Object[]> rows = entityManager.createQuery("""
                        select c.status, count(a)
                        from AuditEntry a
                        join CredentialRecord c on c.id = a.credentialId
                        where a.action = :action
                        group by c.status""", Object[].class)
                .setParameter("action", AuditAction.VERIFIED)
                .getResultList();
        return toEnumMap(rows, CredentialStatus.class);
    }

    private static <E extends Enum<E>> Map<E, Long> toEnumMap(List<Object[]> rows, Class<E> type) {
        Map<E, Long> result = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) {
            result.put(value, 0L);
        }
        for (Object[] row : rows) {
            result.put(type.cast(row[0]), ((Number) row[1]).longValue());
        }
        return result;
    }
}
