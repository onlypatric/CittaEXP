package it.patric.cittaexp.persistence.domain;

import java.util.Objects;
import java.util.UUID;

public record MemberClaimPermissionRecord(
        UUID cityId,
        UUID playerUuid,
        boolean access,
        boolean container,
        boolean build,
        UUID updatedBy,
        long updatedAtEpochMilli
) {

    public MemberClaimPermissionRecord {
        cityId = Objects.requireNonNull(cityId, "cityId");
        playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
        if (updatedAtEpochMilli < 0L) {
            throw new IllegalArgumentException("updatedAtEpochMilli must be >= 0");
        }
    }
}
