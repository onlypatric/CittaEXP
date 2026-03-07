package it.patric.cittaexp.core.model;

import java.util.Objects;
import java.util.UUID;

public record StaffApprovalTicket(
        UUID ticketId,
        UUID cityId,
        TicketType type,
        TicketStatus status,
        UUID requesterUuid,
        UUID reviewerUuid,
        String reason,
        String payloadJson,
        long requestedAtEpochMilli,
        long reviewedAtEpochMilli
) {

    public StaffApprovalTicket {
        ticketId = Objects.requireNonNull(ticketId, "ticketId");
        cityId = Objects.requireNonNull(cityId, "cityId");
        type = Objects.requireNonNull(type, "type");
        status = Objects.requireNonNull(status, "status");
        requesterUuid = Objects.requireNonNull(requesterUuid, "requesterUuid");
        reason = requireText(reason, "reason");
        payloadJson = Objects.requireNonNullElse(payloadJson, "{}");
        if (requestedAtEpochMilli < 0L || reviewedAtEpochMilli < 0L) {
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
