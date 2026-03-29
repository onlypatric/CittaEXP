package it.patric.cittaexp.challenges;

import java.time.Instant;
import java.util.UUID;

public record CycleRecapSnapshot(
        long recapId,
        int townId,
        ChallengeCycleType cycleType,
        String cycleKey,
        int completedChallenges,
        int totalChallenges,
        long xpScaled,
        long excellenceXpScaled,
        int streakDelta,
        int leaderboardPosition,
        int rewardsGranted,
        UUID topContributorId,
        int topContribution,
        Instant createdAt
) {
}
