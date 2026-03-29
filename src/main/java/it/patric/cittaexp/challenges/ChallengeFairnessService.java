package it.patric.cittaexp.challenges;

public final class ChallengeFairnessService {

    public record Scaling(
            int activeContributors7d,
            double multiplier,
            boolean fallbackApplied
    ) {
    }

    private final ChallengeTargetScalingService delegate;

    public ChallengeFairnessService(CityChallengeSettings settings) {
        this.delegate = new ChallengeTargetScalingService(settings);
    }

    public Scaling resolve(int activeContributors7d, boolean fallbackApplied) {
        ChallengeTargetScalingService.CityScaling scaling = delegate.resolveForTownMembers(activeContributors7d, fallbackApplied);
        return new Scaling(scaling.townMembersTotal(), scaling.multiplier(), scaling.fallbackApplied());
    }

    public double multiplierFor(int activeContributors7d) {
        return delegate.multiplierForTownMembers(activeContributors7d);
    }

    public int scaleTarget(int baseTarget, int step, double multiplier) {
        return delegate.scaleTarget(baseTarget, step, multiplier);
    }

    public int excellenceTarget(int scaledTarget) {
        return delegate.excellenceTarget(scaledTarget);
    }
}
