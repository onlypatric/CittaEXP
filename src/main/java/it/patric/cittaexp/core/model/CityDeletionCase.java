package it.patric.cittaexp.core.model;

import java.util.Objects;
import java.util.UUID;

public record CityDeletionCase(
        UUID caseId,
        UUID cityId,
        boolean requiresStaffApproval,
        TicketStatus status,
        UUID requesterUuid,
        UUID reviewerUuid,
        String reason,
        long requestedAtEpochMilli,
        long resolvedAtEpochMilli
) {

    public CityDeletionCase {
        caseId = Objects.requireNonNull(caseId, "caseId");
        cityId = Objects.requireNonNull(cityId, "cityId");
        status = Objects.requireNonNull(status, "status");
        requesterUuid = Objects.requireNonNull(requesterUuid, "requesterUuid");
        reason = requireText(reason, "reason");
        if (requestedAtEpochMilli < 0L || resolvedAtEpochMilli < 0L) {
            throw new IllegalArgumentException("timestamps must be >= 0");
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
