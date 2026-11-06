package de.diler.nachweis.statistics;

import de.diler.nachweis.audit.AuditAction;
import de.diler.nachweis.credential.CredentialStatus;

import java.util.List;
import java.util.Map;

public record StatisticsResponse(
        Map<CredentialStatus, Long> credentialsByStatus,
        List<MonthCount> issuedPerMonth,
        Map<AuditAction, Long> auditByAction,
        Map<CredentialStatus, Long> verificationsByCredentialStatus
) {

    public record MonthCount(int year, int month, long count) {
    }
}
