package it.patric.cittaexp.challenges;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SuspicionScoreStore {

    public record SuspicionState(
            int townId,
            UUID playerId,
            String cycleKey,
            int score,
            boolean reviewFlagged,
            Instant updatedAt
    ) {
    }

    private final ChallengeStore repository;
    private final ConcurrentHashMap<String, SuspicionState> values = new ConcurrentHashMap<>();

    public SuspicionScoreStore(ChallengeStore repository) {
        this.repository = repository;
    }

    public void loadFromRepository() {
        values.clear();
        values.putAll(repository.loadSuspicionScores());
    }

    public SuspicionState get(int townId, UUID playerId, String cycleKey) {
        if (townId <= 0 || playerId == null || cycleKey == null || cycleKey.isBlank()) {
            return new SuspicionState(townId, playerId, safeCycle(cycleKey), 0, false, Instant.EPOCH);
        }
        return values.getOrDefault(
                key(townId, playerId, cycleKey),
                new SuspicionState(townId, playerId, safeCycle(cycleKey), 0, false, Instant.EPOCH)
        );
    }

    public SuspicionState increment(
            int townId,
            UUID playerId,
            String cycleKey,
            int delta,
            Instant now,
            int reviewThreshold
    ) {
        if (townId <= 0 || playerId == null || cycleKey == null || cycleKey.isBlank() || delta <= 0) {
            return get(townId, playerId, cycleKey);
        }
        Instant safeNow = now == null ? Instant.now() : now;
        String key = key(townId, playerId, cycleKey);
        SuspicionState updated = values.compute(key, (ignored, current) -> {
            SuspicionState base = current == null
                    ? new SuspicionState(townId, playerId, safeCycle(cycleKey), 0, false, safeNow)
                    : current;
            int nextScore = Math.max(0, base.score() + delta);
            boolean review = base.reviewFlagged() || (reviewThreshold > 0 && nextScore >= reviewThreshold);
            return new SuspicionState(townId, playerId, safeCycle(cycleKey), nextScore, review, safeNow);
        });
        repository.upsertSuspicionScore(
                townId,
                playerId,
                safeCycle(cycleKey),
                updated.score(),
                updated.reviewFlagged(),
                safeNow
        );
        return updated;
    }

    public int decay(Instant now, int decayIntervalMinutes, int decayAmount) {
        if (decayIntervalMinutes <= 0 || decayAmount <= 0) {
            return 0;
        }
        Instant safeNow = now == null ? Instant.now() : now;
        int changed = 0;
        for (Map.Entry<String, SuspicionState> entry : values.entrySet()) {
            SuspicionState state = entry.getValue();
            if (state == null || state.score() <= 0 || state.updatedAt() == null) {
                continue;
            }
            long elapsedMinutes = Duration.between(state.updatedAt(), safeNow).toMinutes();
            if (elapsedMinutes < decayIntervalMinutes) {
                continue;
            }
            long steps = Math.max(1L, elapsedMinutes / Math.max(1, decayIntervalMinutes));
            int nextScore = Math.max(0, state.score() - (int) (steps * decayAmount));
            SuspicionState updated = new SuspicionState(
                    state.townId(),
                    state.playerId(),
                    state.cycleKey(),
                    nextScore,
                    state.reviewFlagged() && nextScore > 0,
                    safeNow
            );
            values.put(entry.getKey(), updated);
            repository.upsertSuspicionScore(
                    updated.townId(),
                    updated.playerId(),
                    updated.cycleKey(),
                    updated.score(),
                    updated.reviewFlagged(),
                    safeNow
            );
            changed++;
        }
        return changed;
    }

    public Map<String, SuspicionState> snapshot() {
        return Map.copyOf(values);
    }

    public Map<String, SuspicionState> snapshotByCycle(String cycleKey) {
        if (cycleKey == null || cycleKey.isBlank()) {
            return Map.of();
        }
        Map<String, SuspicionState> result = new HashMap<>();
        String expected = safeCycle(cycleKey);
        for (Map.Entry<String, SuspicionState> entry : values.entrySet()) {
            SuspicionState state = entry.getValue();
            if (state == null || !expected.equals(state.cycleKey())) {
                continue;
            }
            result.put(entry.getKey(), state);
        }
        return Map.copyOf(result);
    }

    public void clearRuntime() {
        values.clear();
    }

    private static String key(int townId, UUID playerId, String cycleKey) {
        return townId + "|" + playerId + "|" + safeCycle(cycleKey);
    }

    private static String safeCycle(String cycleKey) {
        return cycleKey == null || cycleKey.isBlank() ? "unknown" : cycleKey.trim();
    }
}

