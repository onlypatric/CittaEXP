package it.patric.cittaexp.persistence.domain;

import java.util.Objects;
import java.util.UUID;

public record CityRecord(
        UUID cityId,
        String name,
        String tag,
        UUID leaderUuid,
        boolean capital,
        boolean frozen,
        long treasuryBalance,
        long createdAtEpochMilli,
        long updatedAtEpochMilli,
        int revision
) {

    public CityRecord {
        cityId = Objects.requireNonNull(cityId, "cityId");
        name = requireText(name, "name");
        tag = requireText(tag, "tag");
        leaderUuid = Objects.requireNonNull(leaderUuid, "leaderUuid");
        if (createdAtEpochMilli < 0L || updatedAtEpochMilli < 0L) {
            throw new IllegalArgumentException("timestamps must be >= 0");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be >= 0");
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
