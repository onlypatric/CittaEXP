package it.patric.cittaexp.manageclaim;

import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.william278.husktowns.claim.TownClaim;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;

public final class ClaimBorderOverlayService implements Listener {

    public enum Source {
        SECTION_MAP,
        INSPECTOR_TOOL,
        CLAIM_TOOL
    }

    public enum ClaimToolPreview {
        CLAIMABLE,
        OWN_CLAIM,
        OTHER_CLAIM,
        BLOCKED
    }

    private static final float CITY_BORDER_HEIGHT = 1.6F;
    private static final float TARGET_BORDER_HEIGHT = 2.2F;
    private static final float BORDER_THICKNESS = 0.24F;
    private static final float TARGET_THICKNESS = 0.34F;
    private static final float CORNER_PILLAR_HEIGHT = 2.8F;
    private static final int SIDE_SEGMENT_LENGTH = 4;
    private static final long TOOL_HELD_CHECK_PERIOD_TICKS = 5L;

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final Map<UUID, Map<Source, List<UUID>>> displayIdsByViewer = new ConcurrentHashMap<>();
    private final Set<UUID> inspectorViewers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> claimToolViewers = ConcurrentHashMap.newKeySet();
    private BukkitTask toolHeldTask;

    public ClaimBorderOverlayService(Plugin plugin, HuskTownsApiHook huskTownsApiHook) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
    }

    public void start() {
        stop();
        toolHeldTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::tickToolHeldState,
                TOOL_HELD_CHECK_PERIOD_TICKS,
                TOOL_HELD_CHECK_PERIOD_TICKS
        );
    }

    public void stop() {
        if (toolHeldTask != null) {
            toolHeldTask.cancel();
            toolHeldTask = null;
        }
        clearAll();
    }

    public void showSectionMapBorders(Player player, String worldName, int townId, int centerChunkX, int centerChunkZ) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            clearSource(player.getUniqueId(), Source.SECTION_MAP);
            return;
        }
        List<TownClaim> allTownClaims = huskTownsApiHook.getTownClaims(world, townId);
        if (allTownClaims.isEmpty()) {
            clearSource(player.getUniqueId(), Source.SECTION_MAP);
            return;
        }

        Set<Long> ownTownChunks = chunkKeys(allTownClaims);
        List<TownClaim> visibleClaims = allTownClaims.stream()
                .filter(claim -> {
                    int x = claim.claim().getChunk().getX();
                    int z = claim.claim().getChunk().getZ();
                    return Math.abs(x - centerChunkX) <= 4 && Math.abs(z - centerChunkZ) <= 2;
                })
                .toList();

        List<UUID> displayIds = new ArrayList<>();
        for (TownClaim claim : visibleClaims) {
            int chunkX = claim.claim().getChunk().getX();
            int chunkZ = claim.claim().getChunk().getZ();
            if (!ownTownChunks.contains(chunkKey(chunkX, chunkZ - 1))) {
                spawnHorizontalBorder(player, world, chunkX, chunkZ, true, displayIds, Material.YELLOW_STAINED_GLASS, Color.YELLOW, CITY_BORDER_HEIGHT, BORDER_THICKNESS);
            }
            if (!ownTownChunks.contains(chunkKey(chunkX, chunkZ + 1))) {
                spawnHorizontalBorder(player, world, chunkX, chunkZ, false, displayIds, Material.YELLOW_STAINED_GLASS, Color.YELLOW, CITY_BORDER_HEIGHT, BORDER_THICKNESS);
            }
            if (!ownTownChunks.contains(chunkKey(chunkX - 1, chunkZ))) {
                spawnVerticalBorder(player, world, chunkX, chunkZ, true, displayIds, Material.YELLOW_STAINED_GLASS, Color.YELLOW, CITY_BORDER_HEIGHT, BORDER_THICKNESS);
            }
            if (!ownTownChunks.contains(chunkKey(chunkX + 1, chunkZ))) {
                spawnVerticalBorder(player, world, chunkX, chunkZ, false, displayIds, Material.YELLOW_STAINED_GLASS, Color.YELLOW, CITY_BORDER_HEIGHT, BORDER_THICKNESS);
            }
        }
        replaceSource(player, Source.SECTION_MAP, displayIds);
    }

    public void showInspectorBorders(Player player, org.bukkit.Chunk targetChunk) {
        World world = targetChunk.getWorld();
        Optional<TownClaim> claim = huskTownsApiHook.getClaimAt(targetChunk);
        if (claim.isEmpty() || claim.get().isAdminClaim(huskTownsApiHook.plugin())) {
            clearSource(player.getUniqueId(), Source.INSPECTOR_TOOL);
            return;
        }
        List<UUID> displayIds = new ArrayList<>();
        spawnFullChunkOutline(player, world, targetChunk.getX(), targetChunk.getZ(), displayIds, Material.BLUE_STAINED_GLASS, Color.BLUE, TARGET_BORDER_HEIGHT, TARGET_THICKNESS);
        int townId = claim.get().town().getId();
        List<TownClaim> allTownClaims = huskTownsApiHook.getTownClaims(world, townId);
        Set<Long> ownTownChunks = chunkKeys(allTownClaims);
        for (TownClaim townClaim : allTownClaims) {
            int chunkX = townClaim.claim().getChunk().getX();
            int chunkZ = townClaim.claim().getChunk().getZ();
            if (!ownTownChunks.contains(chunkKey(chunkX, chunkZ - 1))) {
                spawnHorizontalBorder(player, world, chunkX, chunkZ, true, displayIds, Material.LIGHT_BLUE_STAINED_GLASS, Color.AQUA, CITY_BORDER_HEIGHT, BORDER_THICKNESS);
            }
            if (!ownTownChunks.contains(chunkKey(chunkX, chunkZ + 1))) {
                spawnHorizontalBorder(player, world, chunkX, chunkZ, false, displayIds, Material.LIGHT_BLUE_STAINED_GLASS, Color.AQUA, CITY_BORDER_HEIGHT, BORDER_THICKNESS);
            }
            if (!ownTownChunks.contains(chunkKey(chunkX - 1, chunkZ))) {
                spawnVerticalBorder(player, world, chunkX, chunkZ, true, displayIds, Material.LIGHT_BLUE_STAINED_GLASS, Color.AQUA, CITY_BORDER_HEIGHT, BORDER_THICKNESS);
            }
            if (!ownTownChunks.contains(chunkKey(chunkX + 1, chunkZ))) {
                spawnVerticalBorder(player, world, chunkX, chunkZ, false, displayIds, Material.LIGHT_BLUE_STAINED_GLASS, Color.AQUA, CITY_BORDER_HEIGHT, BORDER_THICKNESS);
            }
        }

        replaceSource(player, Source.INSPECTOR_TOOL, displayIds);
        inspectorViewers.add(player.getUniqueId());
    }

    public void showClaimToolBorders(Player player, org.bukkit.Chunk targetChunk, ClaimToolPreview preview) {
        World world = targetChunk.getWorld();
        Optional<TownClaim> claim = huskTownsApiHook.getClaimAt(targetChunk);
        List<UUID> displayIds = new ArrayList<>();

        Material targetMaterial = switch (preview) {
            case CLAIMABLE -> Material.LIME_STAINED_GLASS;
            case OWN_CLAIM -> Material.RED_STAINED_GLASS;
            case OTHER_CLAIM -> Material.ORANGE_STAINED_GLASS;
            case BLOCKED -> Material.GRAY_STAINED_GLASS;
        };
        Color targetGlow = switch (preview) {
            case CLAIMABLE -> Color.LIME;
            case OWN_CLAIM -> Color.RED;
            case OTHER_CLAIM -> Color.ORANGE;
            case BLOCKED -> Color.GRAY;
        };
        spawnFullChunkOutline(player, world, targetChunk.getX(), targetChunk.getZ(), displayIds, targetMaterial, targetGlow, TARGET_BORDER_HEIGHT, TARGET_THICKNESS);

        if (claim.isPresent() && !claim.get().isAdminClaim(huskTownsApiHook.plugin())) {
            int townId = claim.get().town().getId();
            List<TownClaim> allTownClaims = huskTownsApiHook.getTownClaims(world, townId);
            Set<Long> ownTownChunks = chunkKeys(allTownClaims);
            Material cityMaterial = preview == ClaimToolPreview.OWN_CLAIM
                    ? Material.RED_STAINED_GLASS
                    : Material.LIGHT_BLUE_STAINED_GLASS;
            Color cityGlow = preview == ClaimToolPreview.OWN_CLAIM ? Color.RED : Color.AQUA;
            for (TownClaim townClaim : allTownClaims) {
                int chunkX = townClaim.claim().getChunk().getX();
                int chunkZ = townClaim.claim().getChunk().getZ();
                if (!ownTownChunks.contains(chunkKey(chunkX, chunkZ - 1))) {
                    spawnHorizontalBorder(player, world, chunkX, chunkZ, true, displayIds, cityMaterial, cityGlow, CITY_BORDER_HEIGHT, BORDER_THICKNESS);
                }
                if (!ownTownChunks.contains(chunkKey(chunkX, chunkZ + 1))) {
                    spawnHorizontalBorder(player, world, chunkX, chunkZ, false, displayIds, cityMaterial, cityGlow, CITY_BORDER_HEIGHT, BORDER_THICKNESS);
                }
                if (!ownTownChunks.contains(chunkKey(chunkX - 1, chunkZ))) {
                    spawnVerticalBorder(player, world, chunkX, chunkZ, true, displayIds, cityMaterial, cityGlow, CITY_BORDER_HEIGHT, BORDER_THICKNESS);
                }
                if (!ownTownChunks.contains(chunkKey(chunkX + 1, chunkZ))) {
                    spawnVerticalBorder(player, world, chunkX, chunkZ, false, displayIds, cityMaterial, cityGlow, CITY_BORDER_HEIGHT, BORDER_THICKNESS);
                }
            }
        }

        replaceSource(player, Source.CLAIM_TOOL, displayIds);
        claimToolViewers.add(player.getUniqueId());
    }

    public void clearSource(UUID viewerId, Source source) {
        Map<Source, List<UUID>> bySource = displayIdsByViewer.get(viewerId);
        if (bySource == null) {
            inspectorViewers.remove(viewerId);
            return;
        }
        List<UUID> ids = bySource.remove(source);
        removeDisplays(ids);
        if (bySource.isEmpty()) {
            displayIdsByViewer.remove(viewerId);
        }
        if (source == Source.INSPECTOR_TOOL) {
            inspectorViewers.remove(viewerId);
        }
        if (source == Source.CLAIM_TOOL) {
            claimToolViewers.remove(viewerId);
        }
    }

    public void clearViewer(UUID viewerId) {
        Map<Source, List<UUID>> bySource = displayIdsByViewer.remove(viewerId);
        if (bySource != null) {
            bySource.values().forEach(this::removeDisplays);
        }
        inspectorViewers.remove(viewerId);
        claimToolViewers.remove(viewerId);
    }

    public void clearAll() {
        new HashSet<>(displayIdsByViewer.keySet()).forEach(this::clearViewer);
    }

    private void replaceSource(Player player, Source source, List<UUID> displayIds) {
        clearSource(player.getUniqueId(), source);
        if (displayIds.isEmpty()) {
            return;
        }
        displayIdsByViewer.computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(Source.class))
                .put(source, displayIds);
    }

    private void spawnFullChunkOutline(
            Player player,
            World world,
            int chunkX,
            int chunkZ,
            List<UUID> collector,
            Material material,
            Color glowColor,
            float heightScale,
            float thickness
    ) {
        spawnHorizontalBorder(player, world, chunkX, chunkZ, true, collector, material, glowColor, heightScale, thickness);
        spawnHorizontalBorder(player, world, chunkX, chunkZ, false, collector, material, glowColor, heightScale, thickness);
        spawnVerticalBorder(player, world, chunkX, chunkZ, true, collector, material, glowColor, heightScale, thickness);
        spawnVerticalBorder(player, world, chunkX, chunkZ, false, collector, material, glowColor, heightScale, thickness);
        spawnCornerPillars(player, world, chunkX, chunkZ, collector, material, glowColor);
    }

    private void spawnHorizontalBorder(
            Player viewer,
            World world,
            int chunkX,
            int chunkZ,
            boolean northEdge,
            List<UUID> collector,
            Material material,
            Color glowColor,
            float heightScale,
            float thickness
    ) {
        int minX = chunkX << 4;
        int blockZ = northEdge ? ((chunkZ << 4) - 1) : ((chunkZ << 4) + 15);
        for (int startOffset = 0; startOffset < 16; startOffset += SIDE_SEGMENT_LENGTH) {
            int segmentLength = Math.min(SIDE_SEGMENT_LENGTH, 16 - startOffset);
            int anchorY = averageHorizontalSegmentHeight(world, minX, blockZ, startOffset, segmentLength);
            double centerX = minX + startOffset + (segmentLength / 2.0D);
            double centerZ = blockZ + 0.5D;
            spawnBeam(viewer, world, centerX, anchorY, centerZ, segmentLength, heightScale, thickness, true, Bukkit.createBlockData(material), glowColor, collector);
        }
    }

    private void spawnVerticalBorder(
            Player viewer,
            World world,
            int chunkX,
            int chunkZ,
            boolean westEdge,
            List<UUID> collector,
            Material material,
            Color glowColor,
            float heightScale,
            float thickness
    ) {
        int minZ = chunkZ << 4;
        int blockX = westEdge ? ((chunkX << 4) - 1) : ((chunkX << 4) + 15);
        for (int startOffset = 0; startOffset < 16; startOffset += SIDE_SEGMENT_LENGTH) {
            int segmentLength = Math.min(SIDE_SEGMENT_LENGTH, 16 - startOffset);
            int anchorY = averageVerticalSegmentHeight(world, blockX, minZ, startOffset, segmentLength);
            double centerX = blockX + 0.5D;
            double centerZ = minZ + startOffset + (segmentLength / 2.0D);
            spawnBeam(viewer, world, centerX, anchorY, centerZ, segmentLength, heightScale, thickness, false, Bukkit.createBlockData(material), glowColor, collector);
        }
    }

    private void spawnCornerPillars(
            Player viewer,
            World world,
            int chunkX,
            int chunkZ,
            List<UUID> collector,
            Material material,
            Color glowColor
    ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        spawnPillar(viewer, world, minX - 1, minZ - 1, material, glowColor, collector);
        spawnPillar(viewer, world, minX + 15, minZ - 1, material, glowColor, collector);
        spawnPillar(viewer, world, minX - 1, minZ + 15, material, glowColor, collector);
        spawnPillar(viewer, world, minX + 15, minZ + 15, material, glowColor, collector);
    }

    private void spawnPillar(
            Player viewer,
            World world,
            int blockX,
            int blockZ,
            Material material,
            Color glowColor,
            List<UUID> collector
    ) {
        int anchorY = sampleHeight(world, blockX, blockZ);
        spawnBeam(
                viewer,
                world,
                blockX + 0.5D,
                anchorY,
                blockZ + 0.5D,
                0.7F,
                CORNER_PILLAR_HEIGHT,
                0.7F,
                true,
                Bukkit.createBlockData(material),
                glowColor,
                collector
        );
    }

    private void spawnBeam(
            Player viewer,
            World world,
            double centerX,
            int anchorY,
            double centerZ,
            double primaryScale,
            float heightScale,
            float thickness,
            boolean alongX,
            BlockData blockData,
            Color glowColor,
            List<UUID> collector
    ) {
        double centerY = anchorY + (heightScale / 2.0D);
        Location at = new Location(world, centerX, centerY, centerZ);
        Entity spawned = world.spawnEntity(at, EntityType.BLOCK_DISPLAY);
        if (!(spawned instanceof BlockDisplay display)) {
            spawned.remove();
            return;
        }
        display.setPersistent(false);
        display.setVisibleByDefault(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setBlock(blockData);
        display.setGlowing(true);
        display.setGlowColorOverride(glowColor);
        display.setTransformation(new Transformation(
                new Vector3f(0.0F, 0.0F, 0.0F),
                new Quaternionf(),
                alongX
                        ? new Vector3f((float) primaryScale, heightScale, thickness)
                        : new Vector3f(thickness, heightScale, (float) primaryScale),
                new Quaternionf()
        ));
        display.setViewRange(64.0F);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setDisplayHeight(Math.max(heightScale, (float) primaryScale));
        display.setDisplayWidth(Math.max(thickness, (float) primaryScale));
        display.setInterpolationDuration(2);
        display.setInterpolationDelay(0);
        display.setShadowRadius(0.0F);
        viewer.showEntity(plugin, display);
        collector.add(display.getUniqueId());
    }

    private int averageHorizontalSegmentHeight(World world, int minX, int borderZ, int startOffset, int segmentLength) {
        long total = 0L;
        for (int offset = 0; offset < segmentLength; offset++) {
            total += sampleHeight(world, minX + startOffset + offset, borderZ);
        }
        return (int) Math.round((double) total / (double) Math.max(1, segmentLength));
    }

    private int averageVerticalSegmentHeight(World world, int borderX, int minZ, int startOffset, int segmentLength) {
        long total = 0L;
        for (int offset = 0; offset < segmentLength; offset++) {
            total += sampleHeight(world, borderX, minZ + startOffset + offset);
        }
        return (int) Math.round((double) total / (double) Math.max(1, segmentLength));
    }

    private int sampleHeight(World world, int blockX, int blockZ) {
        return world.getHighestBlockYAt(blockX, blockZ, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
    }

    private Set<Long> chunkKeys(List<TownClaim> claims) {
        Set<Long> keys = new HashSet<>();
        for (TownClaim claim : claims) {
            keys.add(chunkKey(claim.claim().getChunk().getX(), claim.claim().getChunk().getZ()));
        }
        return keys;
    }

    private long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    private void removeDisplays(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (UUID entityId : ids) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
    }

    private void tickToolHeldState() {
        for (UUID viewerId : new HashSet<>(inspectorViewers)) {
            Player player = Bukkit.getPlayer(viewerId);
            if (player == null || !player.isOnline() || !isHoldingInspectorTool(player)) {
                clearSource(viewerId, Source.INSPECTOR_TOOL);
            }
        }
        for (UUID viewerId : new HashSet<>(claimToolViewers)) {
            Player player = Bukkit.getPlayer(viewerId);
            if (player == null || !player.isOnline() || !isHoldingClaimTool(player)) {
                clearSource(viewerId, Source.CLAIM_TOOL);
            }
        }
    }

    private boolean isHoldingInspectorTool(Player player) {
        ItemStack stack = player.getInventory().getItemInMainHand();
        return stack.getType() == Material.BLAZE_ROD;
    }

    private boolean isHoldingClaimTool(Player player) {
        ItemStack stack = player.getInventory().getItemInMainHand();
        return stack.getType() == Material.WOODEN_SHOVEL;
    }

    private void scheduleInspectorHeldCheck(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!isHoldingInspectorTool(player)) {
                clearSource(player.getUniqueId(), Source.INSPECTOR_TOOL);
            }
            if (!isHoldingClaimTool(player)) {
                clearSource(player.getUniqueId(), Source.CLAIM_TOOL);
            }
        });
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (inspectorViewers.contains(event.getPlayer().getUniqueId())) {
            scheduleInspectorHeldCheck(event.getPlayer());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && inspectorViewers.contains(player.getUniqueId())) {
            scheduleInspectorHeldCheck(player);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (inspectorViewers.contains(event.getPlayer().getUniqueId())) {
            scheduleInspectorHeldCheck(event.getPlayer());
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (inspectorViewers.contains(event.getPlayer().getUniqueId())) {
            scheduleInspectorHeldCheck(event.getPlayer());
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        clearViewer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        clearViewer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        clearViewer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearViewer(event.getPlayer().getUniqueId());
    }
}
