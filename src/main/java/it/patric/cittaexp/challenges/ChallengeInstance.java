package it.patric.cittaexp.challenges;

import java.time.Instant;
import java.util.UUID;

public record ChallengeInstance(
        String instanceId,
        Integer townId,
        String cycleKey,
        ChallengeCycleType cycleType,
        String windowKey,
        String challengeId,
        String challengeName,
        ChallengeObjectiveType objectiveType,
        int category,
        int baseTarget,
        int target,
        int excellenceTarget,
        double fairnessMultiplier,
        int activeContributors7d,
        Integer sizeSnapshot,
        Integer onlineSnapshot,
        String targetFormulaVersion,
        ChallengeVariantType variantType,
        ChallengeFocusType focusType,
        String focusKey,
        String focusLabel,
        String biomeKey,
        String dimensionKey,
        String signatureKey,
        ChallengeMode mode,
        ChallengeInstanceStatus status,
        Integer winnerTownId,
        UUID winnerPlayerId,
        Instant cycleStartAt,
        Instant cycleEndAt,
        Instant startedAt,
        Instant endedAt
) {
    public boolean active() {
        return status == ChallengeInstanceStatus.ACTIVE;
    }

    public boolean globalInstance() {
        return townId == null;
    }

    public boolean specificVariant() {
        return variantType == ChallengeVariantType.SPECIFIC;
    }
}
