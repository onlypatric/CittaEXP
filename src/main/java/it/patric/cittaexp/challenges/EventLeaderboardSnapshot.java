package it.patric.cittaexp.challenges;

public record EventLeaderboardSnapshot(
        boolean active,
        String windowKey,
        String challengeName,
        int position,
        int participants,
        int townProgress,
        int leaderProgress,
        int thresholdTarget,
        boolean thresholdReached,
        long secondsRemaining
) {
    public static final EventLeaderboardSnapshot EMPTY =
            new EventLeaderboardSnapshot(false, "-", "-", 0, 0, 0, 0, 0, false, 0L);
}
