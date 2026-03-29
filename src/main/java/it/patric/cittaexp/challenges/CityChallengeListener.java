package it.patric.cittaexp.challenges;

import com.destroystokyo.paper.loottable.LootableInventoryReplenishEvent;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Vault;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.raid.RaidFinishEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

public final class CityChallengeListener implements Listener {

    private static final Set<EntityType> RARE_MOBS = Set.of(
            EntityType.WARDEN,
            EntityType.ELDER_GUARDIAN,
            EntityType.BREEZE,
            EntityType.PIGLIN_BRUTE,
            EntityType.SHULKER
    );

    private static final Set<EntityType> BOSSES = Set.of(
            EntityType.WITHER,
            EntityType.ENDER_DRAGON,
            EntityType.WARDEN,
            EntityType.ELDER_GUARDIAN
    );

    private static final Set<Material> REDSTONE_COMPONENTS = Set.of(
            Material.REDSTONE,
            Material.REDSTONE_TORCH,
            Material.REPEATER,
            Material.COMPARATOR,
            Material.PISTON,
            Material.STICKY_PISTON,
            Material.OBSERVER,
            Material.DROPPER,
            Material.DISPENSER,
            Material.HOPPER
    );
    private static final Map<Material, String> MINED_BLOCK_ALIASES = Map.ofEntries(
            Map.entry(Material.DEEPSLATE_COAL_ORE, "DEEPSLATE_COAL_ORE|COAL_ORE"),
            Map.entry(Material.DEEPSLATE_IRON_ORE, "DEEPSLATE_IRON_ORE|IRON_ORE"),
            Map.entry(Material.DEEPSLATE_COPPER_ORE, "DEEPSLATE_COPPER_ORE|COPPER_ORE"),
            Map.entry(Material.DEEPSLATE_GOLD_ORE, "DEEPSLATE_GOLD_ORE|GOLD_ORE"),
            Map.entry(Material.NETHER_GOLD_ORE, "NETHER_GOLD_ORE|GOLD_ORE"),
            Map.entry(Material.DEEPSLATE_REDSTONE_ORE, "DEEPSLATE_REDSTONE_ORE|REDSTONE_ORE"),
            Map.entry(Material.DEEPSLATE_LAPIS_ORE, "DEEPSLATE_LAPIS_ORE|LAPIS_ORE"),
            Map.entry(Material.DEEPSLATE_DIAMOND_ORE, "DEEPSLATE_DIAMOND_ORE|DIAMOND_ORE"),
            Map.entry(Material.DEEPSLATE_EMERALD_ORE, "DEEPSLATE_EMERALD_ORE|EMERALD_ORE")
    );

    private static final Set<Material> TRACKED_FOOD_OUTPUTS = Set.of(
            Material.COOKED_BEEF,
            Material.COOKED_CHICKEN,
            Material.COOKED_MUTTON,
            Material.COOKED_PORKCHOP,
            Material.COOKED_RABBIT,
            Material.COOKED_COD,
            Material.COOKED_SALMON,
            Material.BREAD,
            Material.GOLDEN_CARROT,
            Material.BAKED_POTATO,
            Material.DRIED_KELP,
            Material.COOKIE,
            Material.CAKE,
            Material.PUMPKIN_PIE,
            Material.MUSHROOM_STEW,
            Material.BEETROOT_SOUP,
            Material.RABBIT_STEW,
            Material.SUSPICIOUS_STEW
    );
    private static final Set<Material> TRACKED_ADVANCED_CRAFT_OUTPUTS = Set.of(
            Material.BEACON,
            Material.DIAMOND_HELMET,
            Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_BOOTS,
            Material.NETHERITE_HELMET,
            Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS,
            Material.NETHERITE_BOOTS,
            Material.PISTON,
            Material.STICKY_PISTON,
            Material.REPEATER,
            Material.COMPARATOR,
            Material.OBSERVER,
            Material.HOPPER
    );
    private static final Set<Material> OCEAN_ACTIVITY_BLOCKS = Set.of(
            Material.PRISMARINE,
            Material.PRISMARINE_BRICKS,
            Material.DARK_PRISMARINE,
            Material.SEA_LANTERN
    );
    private static final Set<String> NON_SCORING_POTION_TYPES = Set.of("WATER", "AWKWARD", "MUNDANE", "THICK");
    private static final long BREW_ACTOR_TTL_MILLIS = 15L * 60L * 1000L;
    private static final long DIMENSION_TRAVEL_COOLDOWN_MILLIS = 60_000L;

    private final CityChallengeService challengeService;
    private final HuskTownsApiHook huskTownsApiHook;
    private final ChallengeObjectiveHandlerRegistry objectiveHandlers;
    private final ChallengeContextResolver contextResolver;
    private final Map<String, BrewActorSnapshot> brewingStandActors;
    private final ConcurrentMap<UUID, Long> dimensionTravelCooldowns;

    private record BrewActorSnapshot(UUID playerId, long touchedAt) {
    }

    public CityChallengeListener(CityChallengeService challengeService, HuskTownsApiHook huskTownsApiHook) {
        this.challengeService = challengeService;
        this.huskTownsApiHook = huskTownsApiHook;
        this.objectiveHandlers = new ChallengeObjectiveHandlerRegistry(challengeService);
        this.contextResolver = new ChallengeContextResolver();
        this.brewingStandActors = new HashMap<>();
        this.dimensionTravelCooldowns = new ConcurrentHashMap<>();
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        if (event.getEntityType() == EntityType.PLAYER
                && event.getEntity() instanceof Player victim
                && sameTown(killer, victim)) {
            return;
        }
        String spawnReason = resolveSpawnReason(event);
        ChallengeObjectiveContext context = ChallengeObjectiveContext.mobKill(
                spawnReason,
                event.getEntityType().name(),
                contextResolver.biomeKey(killer),
                contextResolver.dimensionKey(killer)
        );
        objectiveHandlers.record(killer, ChallengeObjectiveType.MOB_KILL, 1, context);
        if (RARE_MOBS.contains(event.getEntityType())) {
            objectiveHandlers.record(killer, ChallengeObjectiveType.RARE_MOB_KILL, 1, context);
        }
        if (BOSSES.contains(event.getEntityType())) {
            objectiveHandlers.record(killer, ChallengeObjectiveType.BOSS_KILL, 1, context);
        }
        if (event.getEntityType() == EntityType.GUARDIAN || event.getEntityType() == EntityType.ELDER_GUARDIAN) {
            objectiveHandlers.record(
                    killer,
                    ChallengeObjectiveType.OCEAN_ACTIVITY,
                    1,
                    ChallengeObjectiveContext.entityAction(
                            event.getEntityType().name(),
                            contextResolver.biomeKey(killer),
                            contextResolver.dimensionKey(killer)
                    )
            );
        }
        if (isNetherDimension(contextResolver.dimensionKey(killer)) && event.getEntityType() == EntityType.PIGLIN_BRUTE) {
            objectiveHandlers.record(
                    killer,
                    ChallengeObjectiveType.NETHER_ACTIVITY,
                    1,
                    ChallengeObjectiveContext.entityAction(
                            event.getEntityType().name(),
                            contextResolver.biomeKey(killer),
                            contextResolver.dimensionKey(killer)
                    )
            );
            challengeService.recordSecretQuestTrigger(
                    killer,
                    CityChallengeService.SECRET_TRIGGER_KILL_MOB,
                    killer.getLocation()
            );
        }
    }

    private boolean sameTown(Player first, Player second) {
        if (first == null || second == null) {
            return false;
        }
        return huskTownsApiHook.getUserTown(first)
                .flatMap(firstMember -> huskTownsApiHook.getUserTown(second)
                        .map(secondMember -> firstMember.town().getId() == secondMember.town().getId()))
                .orElse(false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        objectiveHandlers.record(
                player,
                ChallengeObjectiveType.BLOCK_MINE,
                1,
                ChallengeObjectiveContext.blockBreak(
                        CityChallengeService.blockKey(event.getBlock().getLocation()),
                        minedMaterialKey(event.getBlock()),
                        contextResolver.biomeKey(event.getBlock().getLocation()),
                        contextResolver.dimensionKey(event.getBlock().getLocation())
                )
        );
        if (isMatureCrop(event.getBlock())) {
            ChallengeObjectiveContext cropContext = ChallengeObjectiveContext.materialAction(
                    event.getBlock().getType().name(),
                    contextResolver.biomeKey(event.getBlock().getLocation()),
                    contextResolver.dimensionKey(event.getBlock().getLocation())
            );
            objectiveHandlers.record(player, ChallengeObjectiveType.CROP_HARVEST, 1, cropContext);
            objectiveHandlers.record(player, ChallengeObjectiveType.FARMING_ECOSYSTEM, 1, cropContext);
        }
        if (event.getBlock().getType() == Material.ANCIENT_DEBRIS
                && isNetherDimension(contextResolver.dimensionKey(event.getBlock().getLocation()))) {
            objectiveHandlers.record(
                    player,
                    ChallengeObjectiveType.NETHER_ACTIVITY,
                    1,
                    ChallengeObjectiveContext.materialAction(
                            event.getBlock().getType().name(),
                            contextResolver.biomeKey(event.getBlock().getLocation()),
                            contextResolver.dimensionKey(event.getBlock().getLocation())
                    )
            );
            challengeService.recordSecretQuestTrigger(
                    player,
                    CityChallengeService.SECRET_TRIGGER_MINE_BLOCK,
                    event.getBlock().getLocation()
            );
        }
        if (OCEAN_ACTIVITY_BLOCKS.contains(event.getBlock().getType())) {
            objectiveHandlers.record(
                    player,
                    ChallengeObjectiveType.OCEAN_ACTIVITY,
                    1,
                    ChallengeObjectiveContext.materialAction(
                            event.getBlock().getType().name(),
                            contextResolver.biomeKey(event.getBlock().getLocation()),
                            contextResolver.dimensionKey(event.getBlock().getLocation())
                    )
            );
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        challengeService.trackPlacedBlock(CityChallengeService.blockKey(event.getBlockPlaced().getLocation()));
        String materialKey = event.getBlockPlaced().getType().name();
        ChallengeObjectiveContext context = ChallengeObjectiveContext.materialAction(
                materialKey,
                contextResolver.biomeKey(event.getBlockPlaced().getLocation()),
                contextResolver.dimensionKey(event.getBlockPlaced().getLocation())
        );
        objectiveHandlers.record(player, ChallengeObjectiveType.CONSTRUCTION, 1, context);
        objectiveHandlers.record(player, ChallengeObjectiveType.PLACE_BLOCK, 1, context);
        if (REDSTONE_COMPONENTS.contains(event.getBlockPlaced().getType())) {
            objectiveHandlers.record(player, ChallengeObjectiveType.REDSTONE_AUTOMATION, 1, context);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerXp(PlayerExpChangeEvent event) {
        if (event.getAmount() <= 0) {
            return;
        }
        objectiveHandlers.record(event.getPlayer(), ChallengeObjectiveType.XP_PICKUP, event.getAmount());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        String materialKey = null;
        if (event.getCaught() instanceof org.bukkit.entity.Item itemEntity) {
            ItemStack caughtStack = itemEntity.getItemStack();
            if (caughtStack != null) {
                materialKey = caughtStack.getType().name();
            }
        }
        Location sourceLocation = event.getCaught() != null
                ? event.getCaught().getLocation()
                : (event.getHook() != null ? event.getHook().getLocation() : event.getPlayer().getLocation());
        ChallengeObjectiveContext context = new ChallengeObjectiveContext(
                null,
                null,
                false,
                true,
                materialKey == null ? null : materialKey.toUpperCase(java.util.Locale.ROOT),
                null,
                contextResolver.biomeKey(sourceLocation),
                contextResolver.dimensionKey(sourceLocation),
                null
        );
        objectiveHandlers.record(event.getPlayer(), ChallengeObjectiveType.FISH_CATCH, 1, context);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack crafted = event.getCurrentItem();
        if (crafted == null || crafted.getType().isAir()) {
            return;
        }
        int craftedAmount = resolveCraftAmount(event, crafted);
        if (craftedAmount <= 0) {
            return;
        }
        String biomeKey = contextResolver.biomeKey(player);
        String dimensionKey = contextResolver.dimensionKey(player);
        if (isTrackedFood(crafted.getType())) {
            objectiveHandlers.record(
                    player,
                    ChallengeObjectiveType.FOOD_CRAFT,
                    craftedAmount,
                    ChallengeObjectiveContext.materialAction(
                            crafted.getType().name(),
                            biomeKey,
                            dimensionKey
                    )
            );
        }
        if (isTrackedAdvancedCraft(crafted.getType())) {
            objectiveHandlers.record(
                    player,
                    ChallengeObjectiveType.ITEM_CRAFT,
                    craftedAmount,
                    ChallengeObjectiveContext.materialAction(
                            crafted.getType().name(),
                            biomeKey,
                            dimensionKey
                    )
            );
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Material outputType = event.getItemType();
        if (!isTrackedFood(outputType)) {
            return;
        }
        int amount = Math.max(0, event.getItemAmount());
        if (amount <= 0) {
            return;
        }
        objectiveHandlers.record(
                event.getPlayer(),
                ChallengeObjectiveType.FOOD_CRAFT,
                amount,
                ChallengeObjectiveContext.materialAction(
                        outputType.name(),
                        contextResolver.biomeKey(event.getBlock().getLocation()),
                        contextResolver.dimensionKey(event.getBlock().getLocation())
                )
        );
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        Player actor = resolveBrewActor(event);
        if (actor == null || !actor.isOnline()) {
            return;
        }
        BrewerInventory inventory = event.getContents();
        Map<String, Integer> brewedByVariant = brewedPotionVariants(inventory);
        if (brewedByVariant.isEmpty()) {
            return;
        }
        String biomeKey = contextResolver.biomeKey(event.getBlock().getLocation());
        String dimensionKey = contextResolver.dimensionKey(event.getBlock().getLocation());
        for (Map.Entry<String, Integer> entry : brewedByVariant.entrySet()) {
            int amount = Math.max(0, entry.getValue());
            if (amount <= 0) {
                continue;
            }
            objectiveHandlers.record(
                    actor,
                    ChallengeObjectiveType.BREW_POTION,
                    amount,
                    ChallengeObjectiveContext.materialAction(entry.getKey(), biomeKey, dimensionKey)
            );
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) {
            return;
        }
        ChallengeObjectiveContext context = ChallengeObjectiveContext.entityAction(
                event.getEntityType().name(),
                contextResolver.biomeKey(player),
                contextResolver.dimensionKey(player)
        );
        objectiveHandlers.record(player, ChallengeObjectiveType.ANIMAL_INTERACTION, 1, context);
        objectiveHandlers.record(player, ChallengeObjectiveType.FARMING_ECOSYSTEM, 1, context);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)) {
            return;
        }
        objectiveHandlers.record(player, ChallengeObjectiveType.ANIMAL_INTERACTION, 1,
                ChallengeObjectiveContext.entityAction(
                        event.getEntityType().name(),
                        contextResolver.biomeKey(player),
                        contextResolver.dimensionKey(player)
                ));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = dimensionTravelCooldowns.get(player.getUniqueId());
        if (last != null && (now - last) < DIMENSION_TRAVEL_COOLDOWN_MILLIS) {
            return;
        }
        dimensionTravelCooldowns.put(player.getUniqueId(), now);
        objectiveHandlers.record(player, ChallengeObjectiveType.DIMENSION_TRAVEL, 1);
        challengeService.recordSecretQuestTrigger(player, CityChallengeService.SECRET_TRIGGER_DIMENSION_TRAVEL, player.getLocation());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        challengeService.markPlayerActivity(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        challengeService.markPlayerInactive(event.getPlayer());
        dimensionTravelCooldowns.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getWorld().equals(event.getTo().getWorld())
                && event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        challengeService.markPlayerActivity(event.getPlayer());
        challengeService.recordExplorationDiscovery(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler(ignoreCancelled = true)
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        String key = event.getAdvancement().getKey().getKey().toLowerCase();
        if (challengeService.settings().antiAbuse().explorationAdvancementFallbackEnabled() && isStructureAdvancement(key)) {
            challengeService.recordAdvancementOnce(event.getPlayer(), ChallengeObjectiveType.STRUCTURE_DISCOVERY, key);
        }
        if (isArchaeologyAdvancement(key)) {
            challengeService.recordAdvancementOnce(event.getPlayer(), ChallengeObjectiveType.ARCHAEOLOGY_BRUSH, key);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerHarvestBlock(PlayerHarvestBlockEvent event) {
        Block block = event.getHarvestedBlock();
        if (block == null) {
            return;
        }
        Material blockType = block.getType();
        if (blockType != Material.SUSPICIOUS_SAND && blockType != Material.SUSPICIOUS_GRAVEL) {
            return;
        }
        objectiveHandlers.record(
                event.getPlayer(),
                ChallengeObjectiveType.ARCHAEOLOGY_BRUSH,
                1,
                ChallengeObjectiveContext.materialAction(
                        blockType.name(),
                        contextResolver.biomeKey(block.getLocation()),
                        contextResolver.dimensionKey(block.getLocation())
                ).withDedupeKey("archaeology:"
                        + block.getWorld().getUID() + ':'
                        + block.getX() + ':'
                        + block.getY() + ':'
                        + block.getZ())
        );
        challengeService.recordSecretQuestTrigger(
                event.getPlayer(),
                CityChallengeService.SECRET_TRIGGER_ARCHAEOLOGY,
                block.getLocation()
        );
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock().getType() == Material.BREWING_STAND) {
            trackBrewingActor(event.getPlayer(), event.getClickedBlock().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDispenseLoot(BlockDispenseLootEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (player == null || block == null || block.getType() != Material.VAULT) {
            return;
        }
        boolean ominous = false;
        if (block.getBlockData() instanceof Vault vaultData) {
            ominous = vaultData.isOminous();
        }
        String lootTableKey = null;
        if (event.getLootTable() != null && event.getLootTable().getKey() != null) {
            lootTableKey = event.getLootTable().getKey().asString();
            if (!ominous && lootTableKey.toLowerCase(Locale.ROOT).contains("ominous")) {
                ominous = true;
            }
        }
        challengeService.recordTrialVaultOpen(player, block.getLocation(), ominous, lootTableKey);
        challengeService.recordSecretQuestTrigger(player, CityChallengeService.SECRET_TRIGGER_TRIAL_VAULT, block.getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        challengeService.recordStructureLoot(player, event.getInventory());
    }

    @EventHandler(ignoreCancelled = true)
    public void onLootableReplenish(LootableInventoryReplenishEvent event) {
        challengeService.recordStructureLoot(event.getPlayer(), event.getInventory());
    }

    @EventHandler(ignoreCancelled = true)
    public void onRaidFinish(RaidFinishEvent event) {
        if (event.getWinners() == null || event.getWinners().isEmpty()) {
            return;
        }
        for (Player winner : event.getWinners()) {
            if (winner == null || !winner.isOnline()) {
                continue;
            }
            objectiveHandlers.record(winner, ChallengeObjectiveType.RAID_WIN, 1);
            challengeService.recordSecretQuestTrigger(winner, CityChallengeService.SECRET_TRIGGER_RAID, winner.getLocation());
        }
    }

    private static boolean isMatureCrop(Block block) {
        Material type = block.getType();
        if (type != Material.WHEAT
                && type != Material.CARROTS
                && type != Material.POTATOES
                && type != Material.BEETROOTS
                && type != Material.NETHER_WART) {
            return false;
        }
        if (!(block.getBlockData() instanceof Ageable ageable)) {
            return false;
        }
        return ageable.getAge() >= ageable.getMaximumAge();
    }

    private void trackBrewingActor(Player player, Location location) {
        if (player == null || location == null) {
            return;
        }
        brewingStandActors.put(
                CityChallengeService.blockKey(location),
                new BrewActorSnapshot(player.getUniqueId(), System.currentTimeMillis())
        );
    }

    private Player resolveBrewActor(BrewEvent event) {
        if (event == null || event.getBlock() == null) {
            return null;
        }
        String standKey = CityChallengeService.blockKey(event.getBlock().getLocation());
        BrewActorSnapshot snapshot = brewingStandActors.get(standKey);
        if (snapshot != null) {
            long age = Math.max(0L, System.currentTimeMillis() - snapshot.touchedAt());
            if (age <= BREW_ACTOR_TTL_MILLIS) {
                Player player = org.bukkit.Bukkit.getPlayer(snapshot.playerId());
                if (player != null && player.isOnline()) {
                    return player;
                }
            } else {
                brewingStandActors.remove(standKey);
            }
        }
        for (org.bukkit.entity.HumanEntity viewer : event.getContents().getViewers()) {
            if (viewer instanceof Player player && player.isOnline()) {
                return player;
            }
        }
        return null;
    }

    private static Map<String, Integer> brewedPotionVariants(BrewerInventory inventory) {
        Map<String, Integer> result = new HashMap<>();
        if (inventory == null) {
            return result;
        }
        for (int slot = 0; slot < 3; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            String variantKey = resolvePotionVariantKey(stack);
            if (variantKey == null || variantKey.isBlank()) {
                continue;
            }
            result.merge(variantKey, stack.getAmount(), Integer::sum);
        }
        return result;
    }

    private static String resolvePotionVariantKey(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        Material type = stack.getType();
        if (type != Material.POTION && type != Material.SPLASH_POTION && type != Material.LINGERING_POTION) {
            return null;
        }
        if (!(stack.getItemMeta() instanceof PotionMeta potionMeta)) {
            return null;
        }
        PotionType potionType = potionMeta.getBasePotionType();
        if (potionType == null) {
            return null;
        }
        String normalizedType = normalizePotionType(potionType.name());
        if (normalizedType == null || NON_SCORING_POTION_TYPES.contains(normalizedType)) {
            return null;
        }

        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.add("POTION_TYPE_" + normalizedType);
        if (type == Material.SPLASH_POTION) {
            tags.add("POTION_SPLASH");
        } else if (type == Material.LINGERING_POTION) {
            tags.add("POTION_LINGERING");
        }
        if (isLevelTwoPotion(potionMeta, potionType.name())) {
            tags.add("POTION_LEVEL_II");
        }
        return String.join("|", tags);
    }

    private static String normalizePotionType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return null;
        }
        String normalized = rawType.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("STRONG_")) {
            normalized = normalized.substring("STRONG_".length());
        } else if (normalized.startsWith("LONG_")) {
            normalized = normalized.substring("LONG_".length());
        }
        return normalized;
    }

    private static boolean isLevelTwoPotion(PotionMeta meta, String rawType) {
        if (rawType != null) {
            String normalized = rawType.trim().toUpperCase(Locale.ROOT);
            if (normalized.startsWith("STRONG_") || normalized.endsWith("_II")) {
                return true;
            }
        }
        try {
            Object upgraded = PotionMeta.class.getMethod("isUpgraded").invoke(meta);
            if (upgraded instanceof Boolean value) {
                return value;
            }
        } catch (ReflectiveOperationException ignored) {
            // Support old/new API variants without hard dependency.
        }
        return false;
    }

    private static boolean isTrackedFood(Material material) {
        return material != null && TRACKED_FOOD_OUTPUTS.contains(material);
    }

    private static boolean isTrackedAdvancedCraft(Material material) {
        return material != null && TRACKED_ADVANCED_CRAFT_OUTPUTS.contains(material);
    }

    private static String minedMaterialKey(Block block) {
        if (block == null) {
            return null;
        }
        return MINED_BLOCK_ALIASES.getOrDefault(block.getType(), block.getType().name());
    }

    private static boolean isNetherDimension(String dimensionKey) {
        return dimensionKey != null && dimensionKey.toUpperCase(Locale.ROOT).contains("NETHER");
    }

    private static int resolveCraftAmount(CraftItemEvent event, ItemStack crafted) {
        int perCraft = Math.max(1, crafted.getAmount());
        if (!event.isShiftClick()) {
            return perCraft;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return perCraft;
        }

        org.bukkit.inventory.CraftingInventory craftingInventory = event.getInventory();
        ItemStack[] matrix = craftingInventory.getMatrix();
        if (matrix == null || matrix.length == 0) {
            return perCraft;
        }

        EnumMap<Material, Integer> totalByMaterial = new EnumMap<>(Material.class);
        EnumMap<Material, Integer> requiredPerCraft = new EnumMap<>(Material.class);
        for (ItemStack ingredient : matrix) {
            if (ingredient == null || ingredient.getType().isAir() || ingredient.getAmount() <= 0) {
                continue;
            }
            totalByMaterial.merge(ingredient.getType(), ingredient.getAmount(), Integer::sum);
            requiredPerCraft.merge(ingredient.getType(), 1, Integer::sum);
        }
        if (requiredPerCraft.isEmpty()) {
            return perCraft;
        }

        int craftOperations = Integer.MAX_VALUE;
        for (var entry : requiredPerCraft.entrySet()) {
            int available = totalByMaterial.getOrDefault(entry.getKey(), 0);
            int needed = Math.max(1, entry.getValue());
            craftOperations = Math.min(craftOperations, available / needed);
        }
        if (craftOperations <= 0 || craftOperations == Integer.MAX_VALUE) {
            craftOperations = 1;
        }

        int maxInsertable = maxInsertableItems(player, crafted);
        if (maxInsertable <= 0) {
            return 0;
        }
        long byRecipe = (long) craftOperations * perCraft;
        return (int) Math.min(Integer.MAX_VALUE, Math.min(byRecipe, maxInsertable));
    }

    private static int maxInsertableItems(Player player, ItemStack result) {
        int maxStack = Math.max(1, result.getMaxStackSize());
        int total = 0;
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (ItemStack item : storage) {
            if (item == null || item.getType().isAir()) {
                total += maxStack;
                continue;
            }
            if (!item.isSimilar(result)) {
                continue;
            }
            total += Math.max(0, maxStack - item.getAmount());
        }
        return total;
    }

    private static boolean isStructureAdvancement(String key) {
        return key.contains("adventure/adventuring_time")
                || key.contains("nether/find_fortress")
                || key.contains("nether/find_bastion")
                || key.contains("end/find_end_city")
                || key.contains("adventure/hero_of_the_village")
                || key.contains("adventure/sleep_in_bed");
    }

    private static boolean isArchaeologyAdvancement(String key) {
        return key.contains("husbandry/obtain_sniffer_egg")
                || key.contains("adventure/craft_decorated_pot");
    }

    private static String resolveSpawnReason(EntityDeathEvent event) {
        try {
            Object reason = event.getEntity().getClass().getMethod("getEntitySpawnReason").invoke(event.getEntity());
            if (reason instanceof Enum<?> enumValue) {
                return enumValue.name();
            }
            return reason == null ? null : reason.toString();
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

}
