package it.patric.cittaexp.persistence.domain;

import it.patric.cittaexp.core.model.FreezeReason;
import java.util.Objects;
import java.util.UUID;

public record FreezeCaseRecord(
        UUID caseId,
        UUID cityId,
        FreezeReason reason,
        String details,
        boolean active,
        UUID openedBy,
        UUID closedBy,
        long openedAtEpochMilli,
        long closedAtEpochMilli
) {

    public FreezeCaseRecord {
        caseId = Objects.requireNonNull(caseId, "caseId");
        cityId = Objects.requireNonNull(cityId, "cityId");
        reason = Objects.requireNonNull(reason, "reason");
        details = Objects.requireNonNullElse(details, "");
        if (openedAtEpochMilli < 0L || closedAtEpochMilli < 0L) {
            throw new IllegalArgumentException("timestamps must be >= 0");
        }
    }
}
