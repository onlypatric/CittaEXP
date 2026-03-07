package it.patric.cittaexp.core.model;

import java.util.Objects;
import java.util.UUID;

public record ClaimBinding(
        UUID cityId,
        String world,
        int minX,
        int minZ,
        int maxX,
        int maxZ,
        int area,
        long createdAtEpochMilli,
        long updatedAtEpochMilli
) {

    public ClaimBinding {
        cityId = Objects.requireNonNull(cityId, "cityId");
        world = Objects.requireNonNull(world, "world").trim();
        if (world.isEmpty()) {
            throw new IllegalArgumentException("world must not be blank");
        }
        if (maxX < minX || maxZ < minZ) {
            throw new IllegalArgumentException("invalid claim bounds");
        }
        if (area <= 0) {
            throw new IllegalArgumentException("area must be > 0");
        }
        if (createdAtEpochMilli < 0L || updatedAtEpochMilli < 0L) {
            throw new IllegalArgumentException("timestamps must be >= 0");
        }
    }
}
