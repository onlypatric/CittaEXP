package it.patric.cittaexp.challenges;

public final class ChallengePersonalRewardService {

    private final CityChallengeSettings.PersonalRewardSettings settings;

    public ChallengePersonalRewardService(CityChallengeSettings settings) {
        this.settings = settings.personalRewards();
    }

    public int requiredContribution(int target) {
        int safeTarget = Math.max(1, target);
        int ratioThreshold = Math.max(1, (int) Math.ceil(safeTarget * Math.max(0.0D, settings.minContributionRatio())));
        int absoluteThreshold = Math.max(0, settings.thresholdContributionAbsolute());
        if (absoluteThreshold <= 0) {
            return ratioThreshold;
        }
        return Math.max(1, Math.min(ratioThreshold, absoluteThreshold));
    }

    public boolean eligible(int contribution, int target) {
        return Math.max(0, contribution) >= requiredContribution(target);
    }
}

