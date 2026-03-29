package it.patric.cittaexp.challenges;

public record WeeklyStreakSnapshot(
        int streakCount,
        double currentBonusRatio,
        double nextBonusRatio
) {
    public static final WeeklyStreakSnapshot EMPTY = new WeeklyStreakSnapshot(0, 0.0D, 0.0D);
}
