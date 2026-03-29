package it.patric.cittaexp.levels;

import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.william278.husktowns.town.Town;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class CityXpScanService {

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final CityLevelStore store;
    private final CityLevelSettings settings;
    private final TownClaimTileScanner tileScanner;

    public CityXpScanService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityLevelStore store,
            CityLevelSettings settings
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.store = store;
        this.settings = settings;
        this.tileScanner = new TownClaimTileScanner(huskTownsApiHook);
    }

    public CityScanResult scanTown(int townId) {
        Optional<Town> town = huskTownsApiHook.getTownById(townId);
        if (town.isEmpty()) {
            return CityScanResult.error(townId, "town-not-found");
        }

        store.getOrCreateProgression(townId);
        if (settings.materialXpScaled().isEmpty()) {
            return store.applyScanAward(
                    townId,
                    0L,
                    Map.of(),
                    settings.xpPerLevel() * settings.xpScale(),
                    settings.levelCap(),
                    "materials-empty",
                    "container_scan",
                    null,
                    "materials-empty"
            );
        }

        try {
            Map<Material, Long> materialCounts = scanMaterialCounts(town.get().getId());
            Map<String, Long> currentHighwater = store.getHighwaterByMaterial(townId);
            Map<String, Long> updates = new HashMap<>();
            long awardedScaled = 0L;

            for (Map.Entry<Material, Long> entry : materialCounts.entrySet()) {
                String materialKey = entry.getKey().name();
                long current = entry.getValue();
                long previous = currentHighwater.getOrDefault(materialKey, 0L);
                if (current <= previous) {
                    continue;
                }
                long delta = current - previous;
                long unitScaled = settings.materialXpScaled().getOrDefault(entry.getKey(), 0L);
                if (unitScaled <= 0L) {
                    continue;
                }
                awardedScaled += delta * unitScaled;
                updates.put(materialKey, current);
            }

            return store.applyScanAward(
                    townId,
                    awardedScaled,
                    updates,
                    settings.xpPerLevel() * settings.xpScale(),
                    settings.levelCap(),
                    null,
                    "container_scan",
                    null,
                    "scan"
            );
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("[levels] scan failed town=" + townId + " reason=" + exception.getMessage());
            return store.applyScanAward(
                    townId,
                    0L,
                    Map.of(),
                    settings.xpPerLevel() * settings.xpScale(),
                    settings.levelCap(),
                    exception.getMessage() == null ? "scan-error" : exception.getMessage(),
                    "container_scan",
                    null,
                    "scan-error"
            );
        }
    }

    private Map<Material, Long> scanMaterialCounts(int townId) {
        Map<Material, Long> counts = new EnumMap<>(Material.class);
        tileScanner.forEachTileEntity(townId, state -> {
            if (!settings.allowedContainers().contains(state.getType())) {
                return;
            }
            if (!(state instanceof Container container)) {
                return;
            }
            Inventory inventory = container.getInventory();
            for (ItemStack item : inventory.getContents()) {
                if (item == null || item.getType() == Material.AIR) {
                    continue;
                }
                if (!settings.materialXpScaled().containsKey(item.getType())) {
                    continue;
                }
                Long current = counts.get(item.getType());
                long updated = Math.max(0L, current == null ? 0L : current) + (long) item.getAmount();
                counts.put(item.getType(), updated);
            }
        });

        for (Material material : settings.materialXpScaled().keySet()) {
            counts.putIfAbsent(material, 0L);
        }
        return counts;
    }
}
