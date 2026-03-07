package it.patric.cittaexp.persistence.domain;

import java.util.Objects;
import java.util.UUID;

public record ClaimBindingRecord(
        UUID cityId,
        String worldName,
        int minX,
        int minZ,
        int maxX,
        int maxZ,
        int area,
        long createdAtEpochMilli,
        long updatedAtEpochMilli
) {

    public ClaimBindingRecord {
        cityId = Objects.requireNonNull(cityId, "cityId");
        worldName = requireText(worldName, "worldName");
        if (maxX < minX || maxZ < minZ) {
            throw new IllegalArgumentException("invalid bounds");
        }
        if (area <= 0) {
            throw new IllegalArgumentException("area must be > 0");
        }
        if (createdAtEpochMilli < 0L || updatedAtEpochMilli < 0L) {
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
