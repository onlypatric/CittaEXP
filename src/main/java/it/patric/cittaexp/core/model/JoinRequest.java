package it.patric.cittaexp.core.model;

import java.util.Objects;
import java.util.UUID;

public record JoinRequest(
        UUID requestId,
        UUID cityId,
        UUID playerUuid,
        JoinRequestStatus status,
        String message,
        UUID reviewedByUuid,
        long requestedAtEpochMilli,
        long reviewedAtEpochMilli
) {

    public JoinRequest {
        requestId = Objects.requireNonNull(requestId, "requestId");
        cityId = Objects.requireNonNull(cityId, "cityId");
        playerUuid = Objects.requireNonNull(playerUuid, "playerUuid");
        status = Objects.requireNonNull(status, "status");
        message = Objects.requireNonNullElse(message, "");
        if (requestedAtEpochMilli < 0L || reviewedAtEpochMilli < 0L) {
            throw new IllegalArgumentException("timestamps must be >= 0");
        }
    }
}
