package it.patric.cittaexp.challenges;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CityChallengeStreakService {

    public record State(
            int townId,
            int streakCount,
            String lastCompletedWeekKey,
            Instant updatedAt
    ) {
    }

    public record Diagnostics(
            int activeCities,
            long resets,
            int maxStreak
    ) {
    }

    private final ChallengeStore repository;
    private final CityChallengeSettings.StreakSettings settings;
    private final Map<Integer, State> stateByTown = new ConcurrentHashMap<>();
    private volatile long resets;

    public CityChallengeStreakService(
            ChallengeStore repository,
            CityChallengeSettings.StreakSettings settings
    ) {
        this.repository = repository;
        this.settings = settings;
    }

    public void loadFromRepository() {
        stateByTown.clear();
        stateByTown.putAll(repository.loadWeeklyStreakState());
    }

    public WeeklyStreakSnapshot snapshotForTown(int townId) {
        State state = stateByTown.get(townId);
        if (state == null || !settings.enabled()) {
            return WeeklyStreakSnapshot.EMPTY;
        }
        int current = Math.max(0, state.streakCount());
        return new WeeklyStreakSnapshot(
                current,
                settings.bonusForStreak(current),
                settings.bonusForStreak(current + 1)
        );
    }

    public Diagnostics diagnostics() {
        int max = stateByTown.values().stream().mapToInt(State::streakCount).max().orElse(0);
        return new Diagnostics(stateByTown.size(), resets, Math.max(0, max));
    }

    public double weeklyBonusRatioForTown(int townId) {
        if (!settings.enabled()) {
            return 0.0D;
        }
        State state = stateByTown.get(townId);
        if (state == null) {
            return settings.bonusForStreak(1);
        }
        return settings.bonusForStreak(state.streakCount());
    }

    public void markWeeklyCompleted(int townId, String currentWeekKey, String previousWeekKey) {
        if (!settings.enabled() || townId <= 0 || currentWeekKey == null || currentWeekKey.isBlank()) {
            return;
        }
        State current = stateByTown.get(townId);
        if (current != null && currentWeekKey.equals(current.lastCompletedWeekKey())) {
            return;
        }
        int nextStreak = 1;
        if (current != null && previousWeekKey != null && previousWeekKey.equals(current.lastCompletedWeekKey())) {
            nextStreak = Math.max(1, current.streakCount() + 1);
        }
        State updated = new State(townId, nextStreak, currentWeekKey, Instant.now());
        stateByTown.put(townId, updated);
        repository.upsertWeeklyStreakState(updated);
    }

    public void onWeeklyCycleRollover(String previousWeekKey) {
        if (!settings.enabled() || previousWeekKey == null || previousWeekKey.isBlank()) {
            return;
        }
        for (Map.Entry<Integer, State> entry : stateByTown.entrySet()) {
            State state = entry.getValue();
            if (state == null || state.streakCount() <= 0) {
                continue;
            }
            if (previousWeekKey.equals(state.lastCompletedWeekKey())) {
                continue;
            }
            State reset = new State(state.townId(), 0, state.lastCompletedWeekKey(), Instant.now());
            stateByTown.put(entry.getKey(), reset);
            repository.upsertWeeklyStreakState(reset);
            resets++;
        }
    }
}
