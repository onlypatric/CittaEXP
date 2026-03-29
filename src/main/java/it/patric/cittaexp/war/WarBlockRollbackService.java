package it.patric.cittaexp.war;

import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.william278.husktowns.claim.TownClaim;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.plugin.Plugin;

public final class WarBlockRollbackService implements Listener {

    private record BlockKey(java.util.UUID worldId, int x, int y, int z) {
        static BlockKey from(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    private record BlockSnapshot(Material type, String blockData, boolean container) {
        static BlockSnapshot capture(Block block) {
            BlockState state = block.getState();
            return new BlockSnapshot(block.getType(), block.getBlockData().getAsString(), state instanceof Container);
        }
    }

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final CityWarSettings settings;
    private final Map<BlockKey, BlockSnapshot> snapshots;
    private volatile CityWarService.CityWarRuntime runtime;

    public WarBlockRollbackService(Plugin plugin, HuskTownsApiHook huskTownsApiHook, CityWarSettings settings) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.settings = settings;
        this.snapshots = new LinkedHashMap<>();
    }

    public void beginTracking(CityWarService.CityWarRuntime runtime) {
        this.runtime = runtime;
        this.snapshots.clear();
    }

    public void clearRuntime() {
        this.runtime = null;
        this.snapshots.clear();
    }

    public void restoreTrackedBlocks() {
        CityWarService.CityWarRuntime current = runtime;
        List<Map.Entry<BlockKey, BlockSnapshot>> entries = List.copyOf(snapshots.entrySet());
        if (current == null || entries.isEmpty()) {
            snapshots.clear();
            return;
        }
        Runnable restoreTask = () -> {
            for (Map.Entry<BlockKey, BlockSnapshot> entry : entries) {
                BlockKey key = entry.getKey();
                BlockSnapshot snapshot = entry.getValue();
                org.bukkit.World world = Bukkit.getWorld(key.worldId());
                if (world == null) {
                    continue;
                }
                Block block = world.getBlockAt(key.x(), key.y(), key.z());
                block.setType(snapshot.type(), false);
                try {
                    block.setBlockData(Bukkit.createBlockData(snapshot.blockData()), false);
                } catch (IllegalArgumentException ignored) {
                    block.setType(snapshot.type(), false);
                }
                if (snapshot.container()) {
                    BlockState state = block.getState();
                    if (state instanceof Container container) {
                        container.getInventory().clear();
                        container.update(true, false);
                    }
                }
            }
            snapshots.clear();
        };
        if (Bukkit.isPrimaryThread()) {
            restoreTask.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, restoreTask);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!shouldTrack(event.getBlock().getLocation())) {
            return;
        }
        snapshot(event.getBlock());
        if (settings.rollback().suppressBlockDrops()) {
            event.setDropItems(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!shouldTrack(event.getBlock().getLocation())) {
            return;
        }
        snapshot(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!shouldTrack(event.getBlock().getLocation())) {
            return;
        }
        snapshot(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!shouldTrack(event.getBlock().getLocation())) {
            return;
        }
        snapshot(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (runtime == null) {
            return;
        }
        boolean trackedAny = false;
        for (Block block : event.blockList()) {
            if (!shouldTrack(block.getLocation())) {
                continue;
            }
            snapshot(block);
            trackedAny = true;
        }
        if (trackedAny && settings.rollback().suppressBlockDrops()) {
            event.setYield(0.0F);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (runtime == null) {
            return;
        }
        boolean trackedAny = false;
        for (Block block : event.blockList()) {
            if (!shouldTrack(block.getLocation())) {
                continue;
            }
            snapshot(block);
            trackedAny = true;
        }
        if (trackedAny && settings.rollback().suppressBlockDrops()) {
            event.setYield(0.0F);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!settings.rollback().trackBurn()) {
            return;
        }
        if (!shouldTrack(event.getBlock().getLocation())) {
            return;
        }
        snapshot(event.getBlock());
    }

    private void snapshot(Block block) {
        snapshots.computeIfAbsent(BlockKey.from(block), ignored -> BlockSnapshot.capture(block));
    }

    private boolean shouldTrack(Location location) {
        CityWarService.CityWarRuntime current = runtime;
        if (current == null || !settings.rollback().enabled() || location == null || location.getWorld() == null) {
            return false;
        }
        Location center = current.defenderSpawn();
        if (center == null || center.getWorld() == null || !Objects.equals(center.getWorld().getUID(), location.getWorld().getUID())) {
            return false;
        }
        if (center.distanceSquared(location) > (double) current.warZoneRadius() * (double) current.warZoneRadius()) {
            return false;
        }
        java.util.Optional<TownClaim> claim = huskTownsApiHook.getClaimAt(location);
        return claim.isPresent() && claim.get().town().getId() == current.defenderTownId();
    }
}
