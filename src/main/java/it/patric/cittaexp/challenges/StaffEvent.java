package it.patric.cittaexp.challenges;

import java.time.Instant;
import java.util.UUID;

public record StaffEvent(
        String eventId,
        StaffEventKind kind,
        StaffEventStatus status,
        String title,
        String subtitle,
        String description,
        Instant startAt,
        Instant endAt,
        UUID createdBy,
        UUID publishedBy,
        UUID closedBy,
        boolean visible,
        String linkedChallengeInstanceId,
        String payload,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        Instant completedAt
) {
}
