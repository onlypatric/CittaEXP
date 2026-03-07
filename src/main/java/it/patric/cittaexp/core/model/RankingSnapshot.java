package it.patric.cittaexp.core.model;

import java.util.Objects;
import java.util.UUID;

public record RankingSnapshot(
        UUID cityId,
        int rank,
        long score,
        String sourceVersion,
        long fetchedAtEpochMilli
) {

    public RankingSnapshot {
        cityId = Objects.requireNonNull(cityId, "cityId");
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be >= 1");
        }
        sourceVersion = Objects.requireNonNull(sourceVersion, "sourceVersion").trim();
        if (sourceVersion.isEmpty()) {
            throw new IllegalArgumentException("sourceVersion must not be blank");
        }
        if (fetchedAtEpochMilli < 0L) {
            throw new IllegalArgumentException("fetchedAtEpochMilli must be >= 0");
        }
    }
}
