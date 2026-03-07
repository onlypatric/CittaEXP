package it.patric.cittaexp.core.model;

import java.util.Objects;
import java.util.UUID;

public record CityInvitation(
        UUID invitationId,
        UUID cityId,
        UUID invitedPlayerUuid,
        UUID invitedByUuid,
        InvitationStatus status,
        long createdAtEpochMilli,
        long expiresAtEpochMilli,
        long updatedAtEpochMilli
) {

    public CityInvitation {
        invitationId = Objects.requireNonNull(invitationId, "invitationId");
        cityId = Objects.requireNonNull(cityId, "cityId");
        invitedPlayerUuid = Objects.requireNonNull(invitedPlayerUuid, "invitedPlayerUuid");
        invitedByUuid = Objects.requireNonNull(invitedByUuid, "invitedByUuid");
        status = Objects.requireNonNull(status, "status");
        if (createdAtEpochMilli < 0L || expiresAtEpochMilli < 0L || updatedAtEpochMilli < 0L) {
            throw new IllegalArgumentException("timestamps must be >= 0");
        }
    }
}
