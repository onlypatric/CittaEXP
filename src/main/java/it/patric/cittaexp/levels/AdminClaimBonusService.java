package it.patric.cittaexp.levels;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.william278.husktowns.events.TownDisbandEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public final class AdminClaimBonusService implements Listener {

    private final AdminClaimBonusRepository repository;
    private final ConcurrentMap<Integer, Integer> cache;

    public AdminClaimBonusService(Plugin plugin) {
        this.repository = new AdminClaimBonusRepository(plugin);
        this.cache = new ConcurrentHashMap<>();
    }

    public void start() {
        repository.initialize();
        cache.clear();
        cache.putAll(repository.loadAll());
    }

    public void stop() {
        cache.clear();
    }

    public int getBonus(int townId) {
        return Math.max(0, cache.getOrDefault(townId, 0));
    }

    public int setBonus(int townId, int amount) {
        int normalized = Math.max(0, amount);
        if (normalized == 0) {
            cache.remove(townId);
        } else {
            cache.put(townId, normalized);
        }
        repository.setBonus(townId, normalized);
        return normalized;
    }

    public int addBonus(int townId, int delta) {
        return setBonus(townId, getBonus(townId) + Math.max(0, delta));
    }

    public int takeBonus(int townId, int delta) {
        return setBonus(townId, Math.max(0, getBonus(townId) - Math.max(0, delta)));
    }

    public Map<Integer, Integer> snapshot() {
        return Map.copyOf(cache);
    }

    public void deleteTown(int townId) {
        cache.remove(townId);
        repository.deleteTown(townId);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTownDisband(TownDisbandEvent event) {
        deleteTown(event.getTown().getId());
    }
}
