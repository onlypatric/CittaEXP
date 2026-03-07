package it.patric.cittaexp.core.model;

import java.util.Objects;
import java.util.UUID;

public record AuditEvent(
        UUID eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        UUID actorUuid,
        String payloadJson,
        long occurredAtEpochMilli
) {

    public AuditEvent {
        eventId = Objects.requireNonNull(eventId, "eventId");
        aggregateType = requireText(aggregateType, "aggregateType");
        aggregateId = requireText(aggregateId, "aggregateId");
        eventType = requireText(eventType, "eventType");
        payloadJson = Objects.requireNonNullElse(payloadJson, "{}");
        if (occurredAtEpochMilli < 0L) {
            throw new IllegalArgumentException("occurredAtEpochMilli must be >= 0");
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
