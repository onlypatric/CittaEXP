package it.patric.cittaexp.levels;

import it.patric.cittaexp.utils.PluginConfigUtils;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;

public final class GlobalHopperChunkCapListener implements Listener {

    public static final String LIMIT_PATH = "city.performance.hopper_chunk_limit.max_per_chunk";
    public static final String MESSAGE_PATH = "city.performance.hopper_chunk_limit.message";
    public static final int DEFAULT_LIMIT = 130;

    private final PluginConfigUtils configUtils;

    public GlobalHopperChunkCapListener(Plugin plugin) {
        this.configUtils = new PluginConfigUtils(plugin);
    }

    public static int configuredLimit(Plugin plugin) {
        return new PluginConfigUtils(plugin).cfgInt(LIMIT_PATH, DEFAULT_LIMIT);
    }

    public static long countHoppersInChunk(Chunk chunk) {
        return countHoppersInChunk(chunk, null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.HOPPER) {
            return;
        }

        int hopperLimit = configUtils.cfgInt(LIMIT_PATH, DEFAULT_LIMIT);
        if (hopperLimit <= 0) {
            return;
        }

        Chunk chunk = event.getBlockPlaced().getChunk();
        Location placedLocation = event.getBlockPlaced().getLocation();
        long existingHoppers = countHoppersInChunk(chunk, placedLocation);
        if (existingHoppers < hopperLimit) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        player.sendMessage(configUtils.msg(
                MESSAGE_PATH,
                "<red>Limite hopper raggiunto in questo chunk:</red> <white>{current}/{cap}</white> <gray>({chunk_x},{chunk_z})</gray>",
                Placeholder.unparsed("current", Long.toString(existingHoppers)),
                Placeholder.unparsed("cap", Integer.toString(hopperLimit)),
                Placeholder.unparsed("chunk_x", Integer.toString(chunk.getX())),
                Placeholder.unparsed("chunk_z", Integer.toString(chunk.getZ()))
        ));
    }

    private static long countHoppersInChunk(Chunk chunk, Location excludedLocation) {
        long count = 0;
        for (BlockState state : chunk.getTileEntities()) {
            if (state.getType() != Material.HOPPER) {
                continue;
            }
            if (sameBlock(state.getLocation(), excludedLocation)) {
                continue;
            }
            count++;
        }
        return count;
    }

    private static boolean sameBlock(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) {
            return false;
        }
        return first.getWorld().equals(second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }
}
