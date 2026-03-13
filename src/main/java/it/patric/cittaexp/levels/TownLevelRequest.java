package it.patric.cittaexp.levels;

import java.time.Instant;
import java.util.UUID;

public record TownLevelRequest(
        long id,
        int townId,
        TownStage targetStage,
        CityLevelRepository.RequestStatus status,
        UUID requestedBy,
        UUID reviewedBy,
        String reason,
        String note,
        Instant createdAt,
        Instant reviewedAt
) {
}
