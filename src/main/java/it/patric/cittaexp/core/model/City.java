package it.patric.cittaexp.core.model;

import java.util.Objects;
import java.util.UUID;

public record City(
        UUID cityId,
        String name,
        String tag,
        UUID leaderUuid,
        CityTier tier,
        CityStatus status,
        boolean capital,
        long treasuryBalance,
        int memberCount,
        int maxMembers,
        int revision,
        long createdAtEpochMilli,
        long updatedAtEpochMilli
) {

    public City {
        cityId = Objects.requireNonNull(cityId, "cityId");
        name = requireText(name, "name");
        tag = requireText(tag, "tag");
        leaderUuid = Objects.requireNonNull(leaderUuid, "leaderUuid");
        tier = Objects.requireNonNull(tier, "tier");
        status = Objects.requireNonNull(status, "status");
        if (memberCount < 0 || maxMembers < 1 || revision < 0) {
            throw new IllegalArgumentException("invalid numeric values");
        }
        if (createdAtEpochMilli < 0L || updatedAtEpochMilli < 0L) {
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
