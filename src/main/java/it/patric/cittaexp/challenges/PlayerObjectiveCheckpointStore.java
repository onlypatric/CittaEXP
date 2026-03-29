package it.patric.cittaexp.challenges;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerObjectiveCheckpointStore {

    private final ChallengeStore repository;
    private final ConcurrentHashMap<String, Long> checkpoints;

    public PlayerObjectiveCheckpointStore(ChallengeStore repository) {
        this.repository = repository;
        this.checkpoints = new ConcurrentHashMap<>();
    }

    public void loadFromRepository() {
        checkpoints.clear();
        checkpoints.putAll(repository.loadObjectiveCheckpoints());
    }

    public Long get(UUID playerId, String objectiveKey) {
        if (playerId == null || objectiveKey == null || objectiveKey.isBlank()) {
            return null;
        }
        return checkpoints.get(composeKey(playerId, objectiveKey));
    }

    public boolean contains(UUID playerId, String objectiveKey) {
        return get(playerId, objectiveKey) != null;
    }

    public void put(UUID playerId, String objectiveKey, long value) {
        if (playerId == null || objectiveKey == null || objectiveKey.isBlank()) {
            return;
        }
        checkpoints.put(composeKey(playerId, objectiveKey), value);
        repository.upsertObjectiveCheckpoint(playerId, objectiveKey, value, Instant.now());
    }

    public Map<String, Long> snapshot() {
        return Map.copyOf(checkpoints);
    }

    private static String composeKey(UUID playerId, String objectiveKey) {
        return playerId + "|" + objectiveKey;
    }
}
