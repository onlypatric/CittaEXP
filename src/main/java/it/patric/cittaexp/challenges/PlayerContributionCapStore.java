package it.patric.cittaexp.challenges;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerContributionCapStore {

    private final ChallengeStore repository;
    private final ConcurrentHashMap<String, Integer> values = new ConcurrentHashMap<>();

    public PlayerContributionCapStore(ChallengeStore repository) {
        this.repository = repository;
    }

    public void loadFromRepository() {
        values.clear();
        values.putAll(repository.loadPlayerContributions());
    }

    public int get(String instanceId, int townId, UUID playerId) {
        if (instanceId == null || instanceId.isBlank() || playerId == null || townId <= 0) {
            return 0;
        }
        return values.getOrDefault(key(instanceId, townId, playerId), 0);
    }

    public void increment(String instanceId, int townId, UUID playerId, int delta) {
        if (instanceId == null || instanceId.isBlank() || playerId == null || townId <= 0 || delta <= 0) {
            return;
        }
        String key = key(instanceId, townId, playerId);
        Integer current = values.get(key);
        int updated = Math.max(0, current == null ? 0 : current) + delta;
        values.put(key, updated);
        repository.upsertPlayerContribution(instanceId, townId, playerId, updated, Instant.now());
    }

    public Map<String, Integer> snapshot() {
        return Map.copyOf(values);
    }

    public Map<UUID, Integer> contributionsForTownInstance(String instanceId, int townId) {
        if (instanceId == null || instanceId.isBlank() || townId <= 0) {
            return Map.of();
        }
        Map<UUID, Integer> result = new HashMap<>();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            String[] split = entry.getKey().split("\\|", 3);
            if (split.length != 3 || !instanceId.equals(split[0])) {
                continue;
            }
            int keyTownId;
            try {
                keyTownId = Integer.parseInt(split[1]);
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (keyTownId != townId) {
                continue;
            }
            try {
                UUID playerId = UUID.fromString(split[2]);
                result.put(playerId, Math.max(0, entry.getValue()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Map.copyOf(result);
    }

    public int contributorsCount(String instanceId, int townId) {
        if (instanceId == null || instanceId.isBlank() || townId <= 0) {
            return 0;
        }
        int count = 0;
        String prefix = instanceId + '|' + townId + '|';
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            if (entry.getValue() != null && entry.getValue() > 0) {
                count++;
            }
        }
        return count;
    }

    public void clearRuntime() {
        values.clear();
    }

    private static String key(String instanceId, int townId, UUID playerId) {
        return instanceId + '|' + townId + '|' + playerId;
    }
}
