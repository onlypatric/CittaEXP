package it.patric.cittaexp.challenges;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TownDailyCategoryCapStore {

    private final ChallengeStore repository;
    private final ConcurrentHashMap<String, Integer> values = new ConcurrentHashMap<>();

    public TownDailyCategoryCapStore(ChallengeStore repository) {
        this.repository = repository;
    }

    public void loadFromRepository() {
        values.clear();
        values.putAll(repository.loadDailyCategoryCaps());
    }

    public int get(String dayKey, int townId, String bucket) {
        if (dayKey == null || dayKey.isBlank() || townId <= 0 || bucket == null || bucket.isBlank()) {
            return 0;
        }
        return values.getOrDefault(key(dayKey, townId, bucket), 0);
    }

    public void increment(String dayKey, int townId, String bucket, int delta) {
        if (dayKey == null || dayKey.isBlank() || townId <= 0 || bucket == null || bucket.isBlank() || delta <= 0) {
            return;
        }
        String key = key(dayKey, townId, bucket);
        Integer current = values.get(key);
        int updated = Math.max(0, current == null ? 0 : current) + delta;
        values.put(key, updated);
        repository.upsertDailyCategoryCap(dayKey, townId, bucket, updated, Instant.now());
    }

    public Map<String, Integer> snapshot() {
        return Map.copyOf(values);
    }

    public void clearRuntime() {
        values.clear();
    }

    private static String key(String dayKey, int townId, String bucket) {
        return dayKey + '|' + townId + '|' + bucket;
    }
}
