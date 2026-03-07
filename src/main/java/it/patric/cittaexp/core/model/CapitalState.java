package it.patric.cittaexp.core.model;

import java.util.UUID;

public record CapitalState(
        UUID cityId,
        long assignedAtEpochMilli,
        long updatedAtEpochMilli,
        String sourceRankingVersion
) {
}
