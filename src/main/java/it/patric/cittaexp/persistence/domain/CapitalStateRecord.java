package it.patric.cittaexp.persistence.domain;

import java.util.Objects;
import java.util.UUID;

public record CapitalStateRecord(
        String capitalSlot,
        UUID cityId,
        long assignedAtEpochMilli,
        long updatedAtEpochMilli,
        String sourceRankingVersion
) {

    public CapitalStateRecord {
        capitalSlot = requireText(capitalSlot, "capitalSlot");
        cityId = Objects.requireNonNull(cityId, "cityId");
        sourceRankingVersion = requireText(sourceRankingVersion, "sourceRankingVersion");
        if (assignedAtEpochMilli < 0L || updatedAtEpochMilli < 0L) {
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
