package it.patric.cittaexp.challenges;

public record CycleProgressSnapshot(
        ChallengeCycleType cycleType,
        int activeChallenges,
        int completedChallenges,
        int totalProgress,
        int totalTarget,
        long secondsRemaining
) {
}
