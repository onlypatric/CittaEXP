package it.patric.cittaexp.activity;

import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public final class PlayerActivityService implements Listener {

    private final Plugin plugin;
    private final PlayerActivityRepository repository;
    private final ConcurrentMap<UUID, Long> lastSeenCache = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "cittaexp-player-activity");
        thread.setDaemon(true);
        return thread;
    });

    public PlayerActivityService(Plugin plugin, PlayerActivityRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public void start() {
        repository.initialize();
        long now = System.currentTimeMillis();
        for (Player online : Bukkit.getOnlinePlayers()) {
            markSeen(online.getUniqueId(), now);
        }
    }

    public void stop() {
        long now = System.currentTimeMillis();
        for (Player online : Bukkit.getOnlinePlayers()) {
            markSeenSync(online.getUniqueId(), now);
        }
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }

    public void markSeen(UUID playerId, long seenAt) {
        if (playerId == null || seenAt <= 0L) {
            return;
        }
        lastSeenCache.put(playerId, seenAt);
        writer.execute(() -> safeUpsert(playerId, seenAt));
    }

    public OptionalLong lastSeenMillis(UUID playerId) {
        if (playerId == null) {
            return OptionalLong.empty();
        }
        Long cached = lastSeenCache.get(playerId);
        if (cached != null) {
            return OptionalLong.of(cached);
        }
        try {
            OptionalLong loaded = repository.findLastSeen(playerId);
            loaded.ifPresent(value -> lastSeenCache.put(playerId, value));
            return loaded;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "[player-activity] lettura last_seen fallita per " + playerId, exception);
            return OptionalLong.empty();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        markSeen(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        markSeen(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    private void markSeenSync(UUID playerId, long seenAt) {
        if (playerId == null || seenAt <= 0L) {
            return;
        }
        lastSeenCache.put(playerId, seenAt);
        safeUpsert(playerId, seenAt);
    }

    private void safeUpsert(UUID playerId, long seenAt) {
        try {
            repository.upsertLastSeen(playerId, seenAt);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "[player-activity] scrittura last_seen fallita per " + playerId, exception);
        }
    }
}
