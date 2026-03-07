package it.patric.cittaexp.persistence.domain;

import java.util.Objects;
import java.util.UUID;

public record CityViceRecord(
        UUID cityId,
        UUID viceUuid,
        long updatedAtEpochMilli
) {

    public CityViceRecord {
        cityId = Objects.requireNonNull(cityId, "cityId");
        if (updatedAtEpochMilli < 0L) {
            throw new IllegalArgumentException("updatedAtEpochMilli must be >= 0");
        }
    }
}
