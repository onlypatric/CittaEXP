package it.patric.cittaexp.persistence.domain;

import java.util.Objects;
import java.util.UUID;

public record RankingSnapshotRecord(
        UUID cityId,
        int rank,
        long score,
        String sourceVersion,
        long fetchedAtEpochMilli
) {

    public RankingSnapshotRecord {
        cityId = Objects.requireNonNull(cityId, "cityId");
        sourceVersion = requireText(sourceVersion, "sourceVersion");
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be >= 1");
        }
        if (fetchedAtEpochMilli < 0L) {
            throw new IllegalArgumentException("fetchedAtEpochMilli must be >= 0");
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
