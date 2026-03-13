package it.patric.cittaexp.levels;

import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.william278.husktowns.town.Town;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.Plugin;

public final class CityLevelRuntimeService {

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final CityLevelService cityLevelService;
    private final CityLevelSettings settings;
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    private BukkitTask scanTask;

    public CityLevelRuntimeService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityLevelService cityLevelService,
            CityLevelSettings settings
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.cityLevelService = cityLevelService;
        this.settings = settings;
    }

    public void start() {
        long ticks = Math.max(20L, settings.scanIntervalSeconds() * 20L);
        this.scanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanOneTownRoundRobin, ticks, ticks);
    }

    public void stop() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
    }

    private void scanOneTownRoundRobin() {
        try {
            List<Town> towns = huskTownsApiHook.getTowns();
            if (towns.isEmpty()) {
                return;
            }
            int index = Math.floorMod(roundRobinIndex.getAndIncrement(), towns.size());
            Town town = towns.get(index);
            cityLevelService.applyScanAndNotify(town.getId());
        } catch (Exception exception) {
            plugin.getLogger().warning("[levels] periodic scan failed reason=" + exception.getMessage());
        }
    }
}
