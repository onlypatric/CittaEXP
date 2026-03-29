package it.patric.cittaexp.challenges;

public record ChallengeSnapshot(
        String instanceId,
        String challengeId,
        String challengeName,
        String cycleKey,
        ChallengeCycleType cycleType,
        String windowKey,
        ChallengeObjectiveType objectiveType,
        int category,
        int baseTarget,
        int target,
        int excellenceTarget,
        double fairnessMultiplier,
        int activeContributors7d,
        ChallengeVariantType variantType,
        ChallengeFocusType focusType,
        String focusKey,
        String focusLabel,
        String biomeKey,
        String dimensionKey,
        int townProgress,
        boolean townCompleted,
        boolean townExcellenceCompleted,
        int playerContribution,
        boolean personalRewardEligible,
        ChallengeMode mode,
        ChallengeInstanceStatus status,
        Integer winnerTownId,
        String winnerTownName,
        long secondsRemaining
) {
}
