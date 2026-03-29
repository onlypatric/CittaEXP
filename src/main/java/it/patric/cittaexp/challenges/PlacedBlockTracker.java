package it.patric.cittaexp.challenges;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PlacedBlockTracker {

    private final ChallengeStore repository;
    private final ConcurrentHashMap<String, Instant> placedBlocks = new ConcurrentHashMap<>();

    public PlacedBlockTracker(ChallengeStore repository) {
        this.repository = repository;
    }

    public void loadFromRepository() {
        placedBlocks.clear();
        placedBlocks.putAll(repository.loadPlacedBlocks());
    }

    public void track(String blockKey, Instant placedAt) {
        if (blockKey == null || blockKey.isBlank() || placedAt == null) {
            return;
        }
        placedBlocks.put(blockKey, placedAt);
        repository.upsertPlacedBlock(blockKey, placedAt);
    }

    public boolean isTrackedActive(String blockKey, Instant now, int ttlHours) {
        if (blockKey == null || blockKey.isBlank() || now == null) {
            return false;
        }
        Instant placedAt = placedBlocks.get(blockKey);
        if (placedAt == null) {
            return false;
        }
        long ttlSeconds = Math.max(1, ttlHours) * 3600L;
        if (now.getEpochSecond() - placedAt.getEpochSecond() <= ttlSeconds) {
            return true;
        }
        placedBlocks.remove(blockKey);
        repository.deletePlacedBlock(blockKey);
        return false;
    }

    public int prune(Instant now, int ttlHours, int maxEntries) {
        if (now == null) {
            return 0;
        }
        List<String> removed = new ArrayList<>();
        long ttlSeconds = Math.max(1, ttlHours) * 3600L;
        for (Map.Entry<String, Instant> entry : placedBlocks.entrySet()) {
            Instant placedAt = entry.getValue();
            if (placedAt == null || now.getEpochSecond() - placedAt.getEpochSecond() > ttlSeconds) {
                removed.add(entry.getKey());
            }
        }

        int safeMax = Math.max(1, maxEntries);
        int overflow = Math.max(0, placedBlocks.size() - removed.size() - safeMax);
        if (overflow > 0) {
            HashSet<String> alreadyRemoved = new HashSet<>(removed);
            List<Map.Entry<String, Instant>> oldest = placedBlocks.entrySet().stream()
                    .filter(entry -> !alreadyRemoved.contains(entry.getKey()))
                    .sorted(Comparator.comparing(Map.Entry::getValue))
                    .limit(overflow)
                    .toList();
            for (Map.Entry<String, Instant> entry : oldest) {
                removed.add(entry.getKey());
            }
        }

        if (removed.isEmpty()) {
            return 0;
        }
        for (String key : removed) {
            placedBlocks.remove(key);
        }
        repository.deletePlacedBlocks(removed);
        return removed.size();
    }

    public int size() {
        return placedBlocks.size();
    }
}
