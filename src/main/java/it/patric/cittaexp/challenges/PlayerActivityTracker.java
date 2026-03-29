package it.patric.cittaexp.challenges;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerActivityTracker {

    private final ConcurrentHashMap<UUID, Instant> lastActiveByPlayer = new ConcurrentHashMap<>();

    public void markActive(UUID playerId, Instant at) {
        if (playerId == null || at == null) {
            return;
        }
        lastActiveByPlayer.put(playerId, at);
    }

    public void markInactive(UUID playerId) {
        if (playerId == null) {
            return;
        }
        lastActiveByPlayer.remove(playerId);
    }

    public boolean isAfk(UUID playerId, Instant now, int idleSeconds) {
        if (playerId == null || now == null || idleSeconds <= 0) {
            return false;
        }
        Instant last = lastActiveByPlayer.putIfAbsent(playerId, now);
        if (last == null) {
            return false;
        }
        return now.getEpochSecond() - last.getEpochSecond() >= idleSeconds;
    }
}
