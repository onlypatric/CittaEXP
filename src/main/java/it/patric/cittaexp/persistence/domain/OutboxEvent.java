package it.patric.cittaexp.persistence.domain;

import java.util.Objects;
import java.util.UUID;

public record OutboxEvent(
        UUID eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payloadJson,
        long occurredAtEpochMilli,
        ReplayStatus status,
        int replayAttempts,
        String lastError
) {

    public OutboxEvent {
        eventId = Objects.requireNonNull(eventId, "eventId");
        aggregateType = requireText(aggregateType, "aggregateType");
        aggregateId = requireText(aggregateId, "aggregateId");
        eventType = requireText(eventType, "eventType");
        payloadJson = requireText(payloadJson, "payloadJson");
        if (occurredAtEpochMilli < 0L) {
            throw new IllegalArgumentException("occurredAtEpochMilli must be >= 0");
        }
        status = Objects.requireNonNull(status, "status");
        if (replayAttempts < 0) {
            throw new IllegalArgumentException("replayAttempts must be >= 0");
        }
        lastError = lastError == null ? "" : lastError;
    }

    public static OutboxEvent pending(
            UUID eventId,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payloadJson,
            long occurredAtEpochMilli
    ) {
        return new OutboxEvent(
                eventId,
                aggregateType,
                aggregateId,
                eventType,
                payloadJson,
                occurredAtEpochMilli,
                ReplayStatus.PENDING,
                0,
                ""
        );
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
