package it.patric.cittaexp.levels;

import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.utils.PluginConfigUtils;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.william278.husktowns.claim.TownClaim;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;

public final class TownSpawnerCapListener implements Listener {

    private final HuskTownsApiHook huskTownsApiHook;
    private final CityLevelService cityLevelService;
    private final TownClaimTileScanner tileScanner;
    private final PluginConfigUtils configUtils;

    public TownSpawnerCapListener(Plugin plugin, HuskTownsApiHook huskTownsApiHook, CityLevelService cityLevelService) {
        this.huskTownsApiHook = huskTownsApiHook;
        this.cityLevelService = cityLevelService;
        this.tileScanner = new TownClaimTileScanner(huskTownsApiHook);
        this.configUtils = new PluginConfigUtils(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SPAWNER) {
            return;
        }

        Optional<TownClaim> townClaim = huskTownsApiHook.getClaimAt(event.getBlockPlaced().getLocation());
        if (townClaim.isEmpty()) {
            return;
        }

        int townId = townClaim.get().town().getId();
        CityLevelService.LevelStatus status = cityLevelService.statusForTown(townId, false);
        int spawnerCap = status.spawnerCap();
        long existingSpawnerCount = tileScanner.countMatchingTileEntities(
                townId,
                state -> state.getType() == Material.SPAWNER
        );
        if (existingSpawnerCount < spawnerCap) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        player.sendMessage(configUtils.msg(
                "city.level.errors.spawner_cap_reached",
                "<red>Limite spawner raggiunto per lo stage {stage}: {current}/{cap}</red>",
                Placeholder.unparsed("stage", status.stage().displayName()),
                Placeholder.unparsed("current", Long.toString(existingSpawnerCount)),
                Placeholder.unparsed("cap", Integer.toString(spawnerCap))
        ));
    }
}
