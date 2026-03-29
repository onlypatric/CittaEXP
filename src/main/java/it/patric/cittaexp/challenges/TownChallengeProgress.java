package it.patric.cittaexp.challenges;

import java.time.Instant;

public record TownChallengeProgress(
        String instanceId,
        int townId,
        int progress,
        Instant completedAt,
        Instant excellenceCompletedAt,
        Instant updatedAt
) {
    public boolean completed(int target) {
        return baseCompleted(target);
    }

    public boolean baseCompleted(int target) {
        return progress >= target || completedAt != null;
    }

    public boolean excellenceCompleted(int excellenceTarget) {
        return progress >= excellenceTarget || excellenceCompletedAt != null;
    }
}
