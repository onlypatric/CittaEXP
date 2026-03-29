package it.patric.cittaexp.challenges;

public final class ChallengeDifficultyResolver {

    private final CityChallengeSettings.DifficultyResolverSettings settings;

    public ChallengeDifficultyResolver(CityChallengeSettings settings) {
        this.settings = settings.difficultyResolver();
    }

    public ChallengeDifficulty resolve(ChallengeCycleType cycleType, int target) {
        if (cycleType == null) {
            return null;
        }
        CityChallengeSettings.DifficultyThreshold threshold = switch (cycleType) {
            case DAILY -> settings.daily();
            case WEEKLY -> settings.weekly();
            case MONTHLY, SEASONAL -> settings.monthly();
        };
        if (target >= threshold.hardTarget()) {
            return ChallengeDifficulty.HARD;
        }
        if (target >= threshold.normalTarget()) {
            return ChallengeDifficulty.NORMAL;
        }
        return ChallengeDifficulty.EASY;
    }

    public double multiplier(ChallengeDifficulty difficulty) {
        if (difficulty == null) {
            return 1.0D;
        }
        return switch (difficulty) {
            case EASY -> settings.easyMultiplier();
            case NORMAL -> settings.normalMultiplier();
            case HARD -> settings.hardMultiplier();
        };
    }
}
