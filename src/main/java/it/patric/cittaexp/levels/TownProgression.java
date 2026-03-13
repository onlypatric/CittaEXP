package it.patric.cittaexp.levels;

import java.time.Instant;
import java.io.Serializable;

public record TownProgression(
        int townId,
        long xpScaled,
        int cityLevel,
        TownStage stage,
        Instant updatedAt
) implements Serializable {
}
