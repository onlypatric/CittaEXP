package it.patric.cittaexp.persistence.domain;

import it.patric.cittaexp.core.model.CityStatus;
import it.patric.cittaexp.core.model.CityTier;
import java.util.Objects;
import java.util.UUID;

public record CityRecord(
        UUID cityId,
        String name,
        String tag,
        UUID leaderUuid,
        CityTier tier,
        CityStatus status,
        boolean capital,
        boolean frozen,
        long treasuryBalance,
        int memberCount,
        int maxMembers,
        long createdAtEpochMilli,
        long updatedAtEpochMilli,
        int revision
) {

    public CityRecord {
        cityId = Objects.requireNonNull(cityId, "cityId");
        name = requireText(name, "name");
        tag = requireText(tag, "tag");
        leaderUuid = Objects.requireNonNull(leaderUuid, "leaderUuid");
        tier = Objects.requireNonNull(tier, "tier");
        status = Objects.requireNonNull(status, "status");
        if (createdAtEpochMilli < 0L || updatedAtEpochMilli < 0L) {
            throw new IllegalArgumentException("timestamps must be >= 0");
        }
        if (memberCount < 0 || maxMembers < 1) {
            throw new IllegalArgumentException("invalid member counts");
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
