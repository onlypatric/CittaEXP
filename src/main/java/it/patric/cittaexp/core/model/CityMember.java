package it.patric.cittaexp.core.model;

import java.util.Objects;
import java.util.UUID;

public record CityMember(
        UUID cityId,
        UUID playerUuid,
        String roleKey,
        MemberStatus status,
        long joinedAtEpochMilli,
        long updatedAtEpochMilli
) {

    public CityMember {
        cityId = Objects.requireNonNull(cityId, "cityId");
        playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
        roleKey = requireText(roleKey, "roleKey");
        status = Objects.requireNonNull(status, "status");
        if (joinedAtEpochMilli < 0L || updatedAtEpochMilli < 0L) {
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
