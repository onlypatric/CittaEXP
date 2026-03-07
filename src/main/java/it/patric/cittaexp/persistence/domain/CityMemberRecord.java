package it.patric.cittaexp.persistence.domain;

import java.util.Objects;
import java.util.UUID;

public record CityMemberRecord(
        UUID cityId,
        UUID playerUuid,
        String roleKey,
        long joinedAtEpochMilli,
        boolean active
) {

    public CityMemberRecord {
        cityId = Objects.requireNonNull(cityId, "cityId");
        playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
        roleKey = requireText(roleKey, "roleKey");
        if (joinedAtEpochMilli < 0L) {
            throw new IllegalArgumentException("joinedAtEpochMilli must be >= 0");
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
