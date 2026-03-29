package it.patric.cittaexp.defense;

import it.patric.cittaexp.challenges.ChallengeRewardType;
import it.patric.cittaexp.challenges.ChallengeStore;
import it.patric.cittaexp.custommobs.CustomMobBossRuntimeSnapshot;
import it.patric.cittaexp.custommobs.CustomMobRuntimeScheduler;
import it.patric.cittaexp.custommobs.CustomMobSpawnContext;
import it.patric.cittaexp.custommobs.CustomMobSpawnService;
import it.patric.cittaexp.economy.EconomyBalanceCategory;
import it.patric.cittaexp.economy.EconomyValueService;
import it.patric.cittaexp.custommobs.MobEffectsEngine;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.itemsadder.ItemsAdderRewardResolver;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.permissions.TownMemberPermission;
import it.patric.cittaexp.permissions.TownMemberPermissionService;
import it.patric.cittaexp.text.MiniMessageHelper;
import it.patric.cittaexp.text.UiChatStyle;
import it.patric.cittaexp.utils.EntityGlowService;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.vault.CityItemVaultService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.william278.husktowns.claim.Position;
import net.william278.husktowns.claim.TownClaim;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Privilege;
import net.william278.husktowns.town.Spawn;
import net.william278.husktowns.town.Town;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class CityDefenseService implements Listener {

    public record StartResult(boolean success, String reason) {
    }

    public record CityDefenseTierView(
            CityDefenseTier tier,
            int minCityLevel,
            int waves,
            double guardianHp,
            BigDecimal startCost,
            BigDecimal rewardMoney,
            long cooldownSeconds,
            long cooldownRemainingSeconds,
            long maxDurationSeconds,
            int rewardXp,
            Map<Material, Integer> rewardItems,
            List<ItemsAdderRewardResolver.RewardSpec> firstClearUniqueRewards,
            boolean completedBefore,
            boolean retryAdjusted,
            boolean firstClearUniqueRewardAvailable
    ) {
    }

    public record CityDefenseSessionSnapshot(
            String sessionId,
            int townId,
            String townName,
            CityDefenseTier tier,
            int currentWave,
            int totalWaves,
            String phase,
            int aliveMobs,
            double guardianHp,
            double guardianHpMax,
            String bossName,
            String bossPhase,
            double bossHp,
            double bossHpMax,
            long remainingSeconds,
            long maxDurationSeconds,
            Location guardianLocation
    ) {
    }

    public record Diagnostics(
            int activeGlobal,
            int activeCities,
            int activeBossEncounters,
            long rejectedByCap,
            long rejectedByCooldown,
            long abortedOnRestart,
            String lastError
    ) {
    }

    private enum Phase {
        PREP,
        COMBAT,
        INTER_WAVE
    }

    private record CandidateNode(Location location, boolean insideClaim, double distanceSquared) {
    }

    private record SpawnFront(int index, Location anchor, boolean insideClaim, double weight) {
    }

    private record SpawnPlan(List<CandidateNode> candidates, List<SpawnFront> fronts) {
    }

    private static final String GUARDIAN_TAG = "cittaexp-defense-guardian";
    private static final String MOB_TAG = "cittaexp-defense-mob";
    private static final String BREACHER_TAG = "cittaexp-defense-breacher";
    private static final String RUNNER_TAG = "cittaexp-defense-runner";
    private static final String LEAPER_TAG = "cittaexp-defense-leaper";
    private static final String DEFAULT_ERROR = "-";
    private static final Set<Material> UNBREAKABLE_BREACH_MATERIALS = Set.of(
            Material.BEDROCK,
            Material.BARRIER,
            Material.END_PORTAL_FRAME,
            Material.END_PORTAL,
            Material.END_GATEWAY,
            Material.NETHER_PORTAL,
            Material.REINFORCED_DEEPSLATE,
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.JIGSAW,
            Material.STRUCTURE_BLOCK,
            Material.STRUCTURE_VOID,
            Material.LIGHT
    );

    private final Plugin plugin;
    private final PluginConfigUtils cfg;
    private final HuskTownsApiHook huskTownsApiHook;
    private final CityLevelService cityLevelService;
    private final CityItemVaultService cityItemVaultService;
    private final TownMemberPermissionService permissionService;
    private final ChallengeStore store;
    private final CityDefenseSettings settings;
    private final EconomyValueService economyValueService;
    private final CustomMobSpawnService customMobSpawnService;
    private final MobEffectsEngine mobEffectsEngine;
    private final CustomMobRuntimeScheduler runtimeScheduler;
    private final DefenseEncounterAudienceService encounterAudienceService;
    private final EntityGlowService glowService;
    private final ItemsAdderRewardResolver itemsAdderRewardResolver;
    private final ScheduledExecutorService ioExecutor;
    private final ConcurrentMap<Integer, SessionState> sessionsByTown;
    private final ConcurrentMap<Integer, Set<CityDefenseTier>> successfulTiersByTown;
    private final ConcurrentMap<String, Instant> cooldowns;
    private final AtomicLong rejectedByCap;
    private final AtomicLong rejectedByCooldown;
    private final AtomicLong abortedOnRestart;
    private volatile String lastError;
    private volatile CustomMobRuntimeScheduler.TaskHandle tickTask;

    public CityDefenseService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityLevelService cityLevelService,
            CityItemVaultService cityItemVaultService,
            TownMemberPermissionService permissionService,
            ChallengeStore store,
            CityDefenseSettings settings,
            EconomyValueService economyValueService,
            CustomMobSpawnService customMobSpawnService,
            MobEffectsEngine mobEffectsEngine
    ) {
        this.plugin = plugin;
        this.cfg = new PluginConfigUtils(plugin);
        this.huskTownsApiHook = huskTownsApiHook;
        this.cityLevelService = cityLevelService;
        this.cityItemVaultService = cityItemVaultService;
        this.permissionService = permissionService;
        this.store = store;
        this.settings = settings;
        this.economyValueService = economyValueService;
        this.customMobSpawnService = customMobSpawnService;
        this.mobEffectsEngine = mobEffectsEngine;
        this.runtimeScheduler = new CustomMobRuntimeScheduler(plugin);
        this.encounterAudienceService = new DefenseEncounterAudienceService(huskTownsApiHook);
        this.glowService = new EntityGlowService(plugin);
        this.itemsAdderRewardResolver = new ItemsAdderRewardResolver(plugin);
        this.ioExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "cittaexp-defense-io");
            thread.setDaemon(true);
            return thread;
        });
        this.sessionsByTown = new ConcurrentHashMap<>();
        this.successfulTiersByTown = new ConcurrentHashMap<>();
        this.cooldowns = new ConcurrentHashMap<>();
        this.rejectedByCap = new AtomicLong(0L);
        this.rejectedByCooldown = new AtomicLong(0L);
        this.abortedOnRestart = new AtomicLong(0L);
        this.lastError = DEFAULT_ERROR;
        this.tickTask = null;
    }

    public void start() {
        if (!settings.enabled()) {
            plugin.getLogger().info("[defense] disabilitato da configurazione.");
            return;
        }
        ioExecutor.execute(() -> {
            try {
                cooldowns.clear();
                successfulTiersByTown.clear();
                cooldowns.putAll(store.loadDefenseCooldowns());
                long aborted = store.abortActiveDefenseSessionsOnStartup(Instant.now(), "aborted_on_restart");
                abortedOnRestart.set(Math.max(0L, aborted));
            } catch (Exception exception) {
                lastError = normalizeError(exception);
                plugin.getLogger().warning("[defense] bootstrap failed reason=" + lastError);
            }
        });
        tickTask = runtimeScheduler.scheduleRepeating(20L, 20L, this::tickSessions);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (SessionState session : sessionsByTown.values()) {
            endSession(session, false, "aborted_plugin_disable");
        }
        sessionsByTown.clear();
        successfulTiersByTown.clear();
        ioExecutor.shutdown();
        try {
            ioExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public Diagnostics diagnostics() {
        return new Diagnostics(
                sessionsByTown.size(),
                sessionsByTown.size(),
                (int) sessionsByTown.values().stream().filter(session -> session.bossEncounter != null).count(),
                rejectedByCap.get(),
                rejectedByCooldown.get(),
                abortedOnRestart.get(),
                safeText(lastError, DEFAULT_ERROR)
        );
    }

    public Optional<CityDefenseSessionSnapshot> snapshotForTown(int townId) {
        SessionState session = sessionsByTown.get(townId);
        if (session == null) {
            return Optional.empty();
        }
        return Optional.of(toSnapshot(session));
    }

    public boolean hasActiveSession(int townId) {
        return sessionsByTown.containsKey(townId);
    }

    public boolean hasCompletedTier(int townId, CityDefenseTier tier) {
        if (townId <= 0 || tier == null) {
            return false;
        }
        return successfulTiersForTown(townId).contains(tier);
    }

    public List<CityDefenseTierView> tierViewsForTown(int townId) {
        Instant now = Instant.now();
        CityLevelService.LevelStatus status = cityLevelService.statusForTown(townId, false);
        List<CityDefenseTierView> result = new ArrayList<>();
        for (CityDefenseTier tier : CityDefenseTier.values()) {
            CityDefenseLevelSpec spec = settings.level(tier);
            long remaining = Math.max(0L, cooldownRemainingSeconds(townId, tier, now));
            boolean completedBefore = successfulTiersForTown(townId).contains(tier);
            BigDecimal effectiveStartCost = economyValueService.effectiveMoney(EconomyBalanceCategory.DEFENSE_START_COST, spec.startCost());
            CityDefenseRewardPolicy.RewardOutcome rewardOutcome = effectiveRewardOutcome(spec, completedBefore);
            result.add(new CityDefenseTierView(
                    tier,
                    spec.minCityLevel(),
                    spec.waves(),
                    spec.guardianHp(),
                    effectiveStartCost,
                    rewardOutcome.rewardMoney(),
                    spec.cooldownSeconds(),
                    remaining,
                    spec.maxDurationSeconds(),
                    spec.rewardXp(),
                    rewardOutcome.rewardItems(),
                    List.copyOf(spec.firstClearUniqueRewards()),
                    completedBefore,
                    rewardOutcome.retryAdjusted(),
                    !completedBefore && !spec.firstClearUniqueRewards().isEmpty()
            ));
        }
        result.sort(java.util.Comparator.comparingInt(view -> view.tier().ordinal()));
        return List.copyOf(result);
    }

    public StartResult start(Player actor, CityDefenseTier tier) {
        return startInternal(actor, tier, false);
    }

    public StartResult forceStart(Player actor, CityDefenseTier tier) {
        return startInternal(actor, tier, true);
    }

    public StartResult forceStop(Player actor) {
        if (!settings.enabled()) {
            return new StartResult(false, "disabled");
        }
        Optional<Member> member = huskTownsApiHook.getUserTown(actor);
        if (member.isEmpty()) {
            return new StartResult(false, "no-town");
        }
        Town town = member.get().town();
        int townId = town.getId();
        SessionState session = sessionsByTown.get(townId);
        if (session == null) {
            return new StartResult(false, "no-active-session");
        }
        forceAbortSession(session, "staff-force-stop");
        return new StartResult(true, "stopped");
    }

    public StartResult abort(Player actor) {
        if (!settings.enabled()) {
            return new StartResult(false, "disabled");
        }
        Optional<Member> member = huskTownsApiHook.getUserTown(actor);
        if (member.isEmpty()) {
            return new StartResult(false, "no-town");
        }
        Town town = member.get().town();
        if (!permissionService.isAllowed(town.getId(), town.getMayor(), actor.getUniqueId(), TownMemberPermission.CITY_DEFENSE_MANAGE)
                || !permissionService.isAllowed(town.getId(), town.getMayor(), actor.getUniqueId(), TownMemberPermission.MOB_INVASION_MANAGE)) {
            return new StartResult(false, "no-permission");
        }
        SessionState session = sessionsByTown.get(town.getId());
        if (session == null) {
            return new StartResult(false, "no-active-session");
        }
        forceAbortSession(session, "player-stop");
        return new StartResult(true, "stopped");
    }

    private StartResult startInternal(Player actor, CityDefenseTier tier, boolean force) {
        if (!settings.enabled()) {
            return new StartResult(false, "disabled");
        }
        Optional<Member> member = huskTownsApiHook.getUserTown(actor);
        if (member.isEmpty()) {
            return new StartResult(false, "no-town");
        }
        Town town = member.get().town();
        int townId = town.getId();
        if (!force
                && (!permissionService.isAllowed(townId, town.getMayor(), actor.getUniqueId(), TownMemberPermission.CITY_DEFENSE_MANAGE)
                || !permissionService.isAllowed(townId, town.getMayor(), actor.getUniqueId(), TownMemberPermission.CITY_DEFENSE_SPEND)
                || !permissionService.isAllowed(townId, town.getMayor(), actor.getUniqueId(), TownMemberPermission.MOB_INVASION_START))) {
            return new StartResult(false, "no-permission");
        }
        if (sessionsByTown.containsKey(townId)) {
            return new StartResult(false, "already-active");
        }
        if (!force && sessionsByTown.size() >= settings.globalActiveCap()) {
            rejectedByCap.incrementAndGet();
            return new StartResult(false, "global-cap-reached");
        }
        CityDefenseLevelSpec spec = settings.level(tier);
        if (!force) {
            CityLevelService.LevelStatus status = cityLevelService.statusForTown(townId, false);
            if (status.level() < spec.minCityLevel()) {
                return new StartResult(false, "level-too-low");
            }
        }
        BigDecimal effectiveStartCost = economyValueService.effectiveMoney(EconomyBalanceCategory.DEFENSE_START_COST, spec.startCost());
        if (town.getSpawn().isEmpty()) {
            return new StartResult(false, "town-spawn-required");
        }
        if (!force) {
            long cooldownRemaining = cooldownRemainingSeconds(townId, tier, Instant.now());
            if (cooldownRemaining > 0L) {
                rejectedByCooldown.incrementAndGet();
                return new StartResult(false, "cooldown-active");
            }
            if (town.getMoney().compareTo(effectiveStartCost) < 0) {
                return new StartResult(false, "insufficient-balance");
            }
        }

        Location guardianLocation = resolveSpawnLocation(town.getSpawn().get());
        if (guardianLocation == null || guardianLocation.getWorld() == null) {
            return new StartResult(false, "spawn-world-missing");
        }
        SpawnPlan spawnPlan = resolveSpawnPlan(townId, guardianLocation);
        if (spawnPlan == null
                || spawnPlan.fronts().size() < settings.spawnSettings().frontCountMin()
                || spawnPlan.candidates().size() < Math.max(settings.minSpawnPoints(), settings.spawnSettings().groupSizeMin())) {
            return new StartResult(false, "spawn-points-missing");
        }

        Villager guardian = spawnGuardian(guardianLocation, town.getName());
        if (guardian == null) {
            return new StartResult(false, "guardian-spawn-failed");
        }

        if (!force) {
            try {
                huskTownsApiHook.editTown(actor, townId, mutableTown -> {
                    BigDecimal current = mutableTown.getMoney();
                    if (current.compareTo(effectiveStartCost) < 0) {
                        throw new IllegalStateException("insufficient-balance");
                    }
                    mutableTown.setMoney(current.subtract(effectiveStartCost));
                });
            } catch (RuntimeException exception) {
                guardian.remove();
                lastError = normalizeError(exception);
                return new StartResult(false, "balance-withdraw-failed");
            }
        }

        Instant now = Instant.now();
        SessionState session = new SessionState(
                UUID.randomUUID().toString(),
                townId,
                town.getName(),
                tier,
                spec,
                successfulTiersForTown(townId).contains(tier),
                guardian.getUniqueId(),
                guardianLocation,
                spawnPlan.candidates(),
                spawnPlan.fronts(),
                now,
                now.plusSeconds(spec.maxDurationSeconds()),
                now.plusSeconds(settings.prepSeconds())
        );
        sessionsByTown.put(townId, session);
        syncGuardianHologram(session);
        syncSessionBossBar(session, now);
        ioExecutor.execute(() -> store.appendDefenseSessionStart(session.sessionId, townId, town.getName(), tier.name(), now));
        announceTown(
                townId,
                cfg.chatFrame(
                        force ? "city.defense.announce.started_forced" : "city.defense.announce.started",
                        force ? "ASSALTO STAFF" : "ASSALTO INIZIATO",
                        "Grado " + tier.name(),
                        spec.waves() + " ondate stanno per arrivare",
                        List.of(
                                UiChatStyle.detail(UiChatStyle.DetailType.CONTEXT, "Citta': " + town.getName()),
                                UiChatStyle.detail(UiChatStyle.DetailType.STATUS, "Durata massima: " + formatDurationSeconds(spec.maxDurationSeconds())),
                                UiChatStyle.detail(UiChatStyle.DetailType.NOTE, force ? "Avvio forzato da staff." : "Difendi la citta fino alla fine.")
                        )
                )
        );
        return new StartResult(true, "started");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!settings.enabled()) {
            return;
        }
        SessionState session = sessionAt(event.getBlockPlaced().getLocation());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(cfg.msg(
                "city.defense.errors.anti_cheese_build",
                "<red>Costruzione sigillata vicino al Guardiano durante l'assedio.</red>"
        ));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!settings.enabled()) {
            return;
        }
        SessionState session = sessionAt(event.getBlock().getLocation());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(cfg.msg(
                "city.defense.errors.anti_cheese_break",
                "<red>Non puoi spezzare blocchi vicino al Guardiano durante l'assedio.</red>"
        ));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!settings.enabled()) {
            return;
        }
        SessionState session = sessionAt(event.getBlockClicked().getLocation());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(cfg.msg(
                "city.defense.errors.anti_cheese_bucket",
                "<red>Acqua e lava vengono respinte vicino al Guardiano durante l'assedio.</red>"
        ));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!settings.enabled()) {
            return;
        }
        SessionState session = sessionAt(event.getBlockClicked().getLocation());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(cfg.msg(
                "city.defense.errors.anti_cheese_bucket",
                "<red>Acqua e lava vengono respinte vicino al Guardiano durante l'assedio.</red>"
        ));
    }

    @EventHandler(ignoreCancelled = true)
    public void onGuardianDamage(EntityDamageEvent event) {
        if (!settings.enabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Villager guardian) || !guardian.getScoreboardTags().contains(GUARDIAN_TAG)) {
            return;
        }
        SessionState session = sessionByGuardian(guardian.getUniqueId());
        if (session == null) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        double incoming = Math.max(1.0D, event.getFinalDamage());
        applyGuardianDamage(session, incoming);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!settings.enabled()) {
            return;
        }
        Entity entity = event.getEntity();
        if (glowService.isManagedGlow(entity)) {
            glowService.clearGlow(entity);
        }
        if (entity instanceof Villager villager && villager.getScoreboardTags().contains(GUARDIAN_TAG)) {
            SessionState session = sessionByGuardian(entity.getUniqueId());
            if (session != null) {
                endSession(session, false, "guardian-died");
            }
            return;
        }
        for (SessionState session : sessionsByTown.values()) {
            if (session.activeMobIds.remove(entity.getUniqueId())) {
                session.mobStates.remove(entity.getUniqueId());
                BossEncounterState encounter = session.bossEncounter;
                if (encounter != null && entity.getUniqueId().equals(encounter.bossUuid())) {
                    announceSessionLocal(session, renderBossLocalMessage(
                            "city.defense.announce.custom_boss_defeat",
                            encounter.localBroadcasts().defeatMessage(),
                            "<green>❖ <white>{boss}</white> e stato sconfitto.</green>",
                            session,
                            safeText(encounter.bossName(), prettyEntity(entity.getType())),
                            safeText(encounter.phaseLabel(), "Primo Urto")
                    ));
                    clearBossEncounter(session);
                }
                break;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!settings.enabled()) {
            return;
        }
        Player player = event.getEntity();
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            return;
        }
        SessionState session = sessionsByTown.get(member.get().town().getId());
        if (session == null) {
            return;
        }
        if (!sameWorld(session.guardianLocation, player.getLocation())) {
            return;
        }
        double maxDistance = Math.max(32.0D, settings.antiCheeseRadius() * 4.0D);
        if (session.guardianLocation.distanceSquared(player.getLocation()) > maxDistance * maxDistance) {
            return;
        }
        double penalty = session.maxGuardianHp * settings.playerDeathPenaltyPercent();
        applyGuardianDamage(session, penalty);
        announceTown(
                session.townId,
                cfg.msg(
                        "city.defense.announce.player_death_penalty",
                        "<red>✦ {player} e caduto.</red> <gray>Il Guardiano perde il</gray> <white>{percent}%</white> <gray>della propria integrita.</gray>",
                        Placeholder.unparsed("player", player.getName()),
                        Placeholder.unparsed("percent", Integer.toString((int) Math.round(settings.playerDeathPenaltyPercent() * 100.0D)))
                )
        );
    }

    private void tickSessions() {
        if (!settings.enabled()) {
            return;
        }
        Instant now = Instant.now();
        for (SessionState session : new ArrayList<>(sessionsByTown.values())) {
            if (now.isAfter(session.deadlineAt)) {
                endSession(session, false, "timeout");
                continue;
            }
            Villager guardian = guardianEntity(session);
            if (guardian == null || !guardian.isValid() || session.guardianHp <= 0.0D) {
                endSession(session, false, "guardian-dead");
                continue;
            }
            syncGuardianHologram(session);

            switch (session.phase) {
                case PREP, INTER_WAVE -> {
                    syncSessionBossBar(session, now);
                    maybeSendCountdownActionBar(session, now);
                    if (!now.isBefore(session.phaseEndsAt)) {
                        spawnWave(session);
                    }
                }
                case COMBAT -> {
                    pruneDeadMobs(session);
                    maintainMobPressure(session, now);
                    updateBossEncounter(session);
                    syncSessionBossBar(session, now);
                    session.lastCountdownCue = null;
                    if (session.activeMobIds.isEmpty()) {
                        if (session.currentWave >= session.totalWaves) {
                            endSession(session, true, "victory");
                        } else {
                            session.phase = Phase.INTER_WAVE;
                            session.phaseEndsAt = now.plusSeconds(settings.interWaveSeconds());
                            announceTown(
                                    session.townId,
                                    cfg.msg(
                                            "city.defense.announce.wave_cleared",
                                            "<green>✦ Ondata {wave}/{total} respinta.</green> <gray>La prossima ondata arrivera tra</gray> <white>{seconds}s</white><gray>.</gray>",
                                            Placeholder.unparsed("wave", Integer.toString(session.currentWave)),
                                            Placeholder.unparsed("total", Integer.toString(session.totalWaves)),
                                            Placeholder.unparsed("seconds", Long.toString(settings.interWaveSeconds()))
                                    )
                            );
                        }
                    }
                }
            }
        }
    }

    private void spawnWave(SessionState session) {
        session.currentWave = Math.min(session.totalWaves, session.currentWave + 1);
        session.phase = Phase.COMBAT;
        session.phaseEndsAt = null;
        pruneDeadMobs(session);

        int defenders = activeDefenders(session.townId);
        double factor = Math.min(
                settings.defenderScaling().maxMultiplier(),
                1.0D + settings.defenderScaling().perExtraDefender() * Math.max(0, defenders - 1)
        );
        int regularCount = Math.max(1, session.spec.baseMobCount() + (session.currentWave - 1) * session.spec.waveIncrement());
        regularCount = (int) Math.ceil(regularCount * factor);
        if (session.currentWave == session.totalWaves) {
            regularCount = Math.max(session.spec.bossSupportCount(), regularCount);
        }
        int currentAlive = session.activeMobIds.size();
        int canSpawn = Math.max(0, settings.maxMobsAlive() - currentAlive);
        int spawnCount = Math.max(0, Math.min(regularCount, canSpawn));
        if (spawnCount <= 0) {
            announceTown(session.townId, cfg.msg(
                    "city.defense.announce.wave_skipped_cap",
                    "<yellow>✦ Ondata {wave}/{total} rimandata.</yellow> <gray>Ci sono gia troppi mob attivi in questa invasione.</gray>",
                    Placeholder.unparsed("wave", Integer.toString(session.currentWave)),
                    Placeholder.unparsed("total", Integer.toString(session.totalWaves))
            ));
            return;
        }

        Map<Integer, Integer> frontUsage = new java.util.HashMap<>();
        int remaining = spawnCount;
        while (remaining > 0) {
            SpawnFront front = pickWaveFront(session);
            if (front == null) {
                break;
            }
            int targetGroupSize = Math.min(remaining, pickGroupSize(session));
            List<Location> groupLocations = resolveGroupSpawnLocations(session, front, targetGroupSize);
            if (groupLocations.isEmpty()) {
                groupLocations = List.of(front.anchor().clone());
            }
            int spawnedInGroup = 0;
            for (Location spawnAt : groupLocations) {
                CityDefenseSpawnRef spawnRef = pickWaveSpawnRef(session);
                LivingEntity mob = spawnMob(spawnAt, spawnRef, session, front.index());
                if (mob != null) {
                    spawnedInGroup++;
                }
            }
            if (spawnedInGroup <= 0) {
                break;
            }
            frontUsage.merge(front.index(), spawnedInGroup, Integer::sum);
            remaining -= spawnedInGroup;
        }

        int actualSpawnCount = spawnCount - Math.max(0, remaining);
        if (session.currentWave == session.totalWaves) {
            SpawnFront bossFront = dominantFront(session, frontUsage);
            Location bossSpawn = resolveBossSpawnLocation(session, bossFront);
            LivingEntity boss = spawnMob(bossSpawn, session.spec.bossMob(), session, bossFront == null ? -1 : bossFront.index());
            if (boss != null) {
                buffBoss(boss, session);
                session.bossEncounter = new BossEncounterState(boss.getUniqueId(), session.townId, session.sessionId, session.currentWave);
                mobEffectsEngine.bossSnapshot(boss.getUniqueId()).ifPresent(snapshot -> {
                    session.bossEncounter.updateFromSnapshot(snapshot);
                    announceSessionLocal(session, renderBossLocalMessage(
                            "city.defense.announce.custom_boss_spawn",
                            snapshot.localBroadcasts().spawnMessage(),
                            "<dark_red>❖ Il varco si spalanca:</dark_red> <white>{boss}</white> <gray>muove sul baluardo sotto il segno di</gray> <white>{phase}</white>",
                            session,
                            snapshot.displayName(),
                            snapshot.phaseLabel()
                    ));
                });
            }
        }

        int incomingCount = actualSpawnCount + (session.currentWave == session.totalWaves ? 1 : 0);
        announceTown(
                session.townId,
                cfg.chatFrame(
                        "city.defense.announce.wave_started",
                        "ONDATA " + session.currentWave + "/" + session.totalWaves,
                        incomingCount + " nemici stanno arrivando",
                        "Invasione in corso",
                        List.of(
                                UiChatStyle.detail(UiChatStyle.DetailType.CONTEXT, "Citta': " + session.townName),
                                UiChatStyle.detail(UiChatStyle.DetailType.STATUS, "Nemici in arrivo: " + incomingCount),
                                UiChatStyle.detail(UiChatStyle.DetailType.GOAL, "Proteggi il guardiano e respingi l'attacco.")
                        )
                )
        );
        showWaveStartTitle(session, incomingCount);
    }

    private LivingEntity spawnMob(Location spawnAt, CityDefenseSpawnRef spawnRef, SessionState session, int frontIndex) {
        if (spawnAt.getWorld() == null) {
            return null;
        }
        LivingEntity living;
        try {
            living = switch (spawnRef.kind()) {
                case VANILLA -> spawnVanillaMob(spawnAt, spawnRef.vanillaType());
                case CUSTOM -> customMobSpawnService.spawn(
                        spawnRef.customMobId(),
                        spawnAt,
                        defenseSpawnContext(session)
                );
            };
        } catch (RuntimeException exception) {
            lastError = normalizeError(exception);
            plugin.getLogger().warning("[defense] mob spawn failed ref=" + spawnRef.configToken() + " reason=" + lastError);
            return null;
        }
        registerDefenseMobRuntime(session, living, frontIndex, spawnAt);
        applyMobilityProfile(living, session, spawnRef);
        if (ThreadLocalRandom.current().nextDouble() < session.spec.eliteChance()) {
            buffElite(living, session);
        }
        return living;
    }

    private CustomMobSpawnContext defenseSpawnContext(SessionState session) {
        return new CustomMobSpawnContext(
                "city_defense",
                session.townId,
                session.sessionId,
                session.sessionId,
                session.sessionId + ":" + session.currentWave,
                session.currentWave,
                session.guardianId,
                session.tier.name(),
                session.tier.name() + ":" + session.sessionId,
                false,
                null,
                (entity, context) -> adoptDefenseSpawn(entity, session)
        );
    }

    private void adoptDefenseSpawn(LivingEntity entity, SessionState session) {
        registerDefenseMobRuntime(session, entity, nearestFrontIndex(session, entity.getLocation()), entity.getLocation());
    }

    private LivingEntity spawnVanillaMob(Location spawnAt, EntityType type) {
        Entity spawned = spawnAt.getWorld().spawnEntity(spawnAt, type);
        if (spawned instanceof LivingEntity living) {
            glowService.applyRedGlow(living);
            return living;
        }
        spawned.remove();
        throw new IllegalStateException("Spawned non-living entity for type " + type.name());
    }

    private void registerDefenseMobRuntime(SessionState session, LivingEntity entity, int frontIndex, Location referenceLocation) {
        glowService.applyRedGlow(entity);
        entity.addScoreboardTag(MOB_TAG);
        entity.setRemoveWhenFarAway(false);
        if (isBreacherEntity(entity)) {
            entity.addScoreboardTag(BREACHER_TAG);
        }
        session.activeMobIds.add(entity.getUniqueId());
        session.mobStates.put(entity.getUniqueId(), new MobRuntimeState(frontIndex, referenceLocation, Instant.now()));
        if (entity instanceof Mob mob) {
            Villager guardian = guardianEntity(session);
            if (guardian != null && guardian.isValid()) {
                mob.setTarget(guardian);
            }
        }
    }

    private void applyMobilityProfile(LivingEntity entity, SessionState session, CityDefenseSpawnRef spawnRef) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        boolean explicitRunner = entity.getScoreboardTags().contains("cittaexp-tag:runner");
        boolean explicitFrenzy = entity.getScoreboardTags().contains("cittaexp-tag:frenzy");
        boolean explicitLeaper = entity.getScoreboardTags().contains("cittaexp-tag:leaper");
        boolean elitePool = spawnRef != null && session.spec.eliteMobs().contains(spawnRef);

        if (explicitRunner) {
            applyRunnerBuff(entity, explicitFrenzy ? 1 : 0);
        } else if (spawnRef != null && spawnRef.isVanilla() && shouldGrantVanillaRunner(entity.getType(), session, elitePool)) {
            applyRunnerBuff(entity, vanillaRunnerAmplifier(session, elitePool));
        }

        if (explicitLeaper) {
            applyLeaperBuff(entity, 1);
        } else if (spawnRef != null && spawnRef.isVanilla() && shouldGrantVanillaLeaper(entity.getType(), session, elitePool)) {
            applyLeaperBuff(entity, elitePool || session.tier.number() >= 12 ? 1 : 0);
        }
    }

    private void applyRunnerBuff(LivingEntity entity, int amplifier) {
        entity.addScoreboardTag(RUNNER_TAG);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 72_000, Math.max(0, amplifier), true, false, true));
    }

    private void applyLeaperBuff(LivingEntity entity, int jumpAmplifier) {
        entity.addScoreboardTag(LEAPER_TAG);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 72_000, Math.max(0, jumpAmplifier), true, false, true));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 72_000, 0, true, false, true));
    }

    private boolean shouldGrantVanillaRunner(EntityType type, SessionState session, boolean elitePool) {
        if (!isVanillaRunnerType(type)) {
            return false;
        }
        double chance = elitePool ? 0.32D : 0.18D;
        chance += Math.min(0.18D, session.tier.number() * 0.008D);
        return ThreadLocalRandom.current().nextDouble() < Math.min(0.70D, chance);
    }

    private boolean shouldGrantVanillaLeaper(EntityType type, SessionState session, boolean elitePool) {
        if (!isVanillaLeaperType(type)) {
            return false;
        }
        double chance = elitePool ? 0.22D : 0.10D;
        chance += Math.min(0.16D, session.tier.number() * 0.007D);
        return ThreadLocalRandom.current().nextDouble() < Math.min(0.55D, chance);
    }

    private int vanillaRunnerAmplifier(SessionState session, boolean elitePool) {
        if (elitePool || session.tier.number() >= 16) {
            return ThreadLocalRandom.current().nextDouble() < 0.45D ? 1 : 0;
        }
        return 0;
    }

    private static boolean isVanillaRunnerType(EntityType type) {
        return switch (type) {
            case ZOMBIE, HUSK, SKELETON, STRAY, DROWNED, PILLAGER, VINDICATOR, WITHER_SKELETON, SPIDER, CAVE_SPIDER, ENDERMAN, PIGLIN_BRUTE -> true;
            default -> false;
        };
    }

    private static boolean isVanillaLeaperType(EntityType type) {
        return switch (type) {
            case ZOMBIE, HUSK, DROWNED, VINDICATOR, WITHER_SKELETON, SPIDER, CAVE_SPIDER, ENDERMAN, PIGLIN_BRUTE -> true;
            default -> false;
        };
    }

    private void buffElite(LivingEntity entity, SessionState session) {
        double baseHealth = entity.getAttribute(Attribute.MAX_HEALTH) == null ? entity.getHealth() : entity.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        double boosted = Math.max(8.0D, baseHealth * 1.8D);
        if (entity.getAttribute(Attribute.MAX_HEALTH) != null) {
            entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(boosted);
        }
        entity.setHealth(Math.min(boosted, entity.getMaxHealth()));
        entity.customName(prefixDefenseName(
                entity,
                cfg.msg("city.defense.mob.elite_prefix", "<red>Campione</red>")
        ));
        entity.setCustomNameVisible(true);
    }

    private void buffBoss(LivingEntity boss, SessionState session) {
        double baseHealth = boss.getAttribute(Attribute.MAX_HEALTH) == null ? boss.getHealth() : boss.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
        double boosted = Math.max(40.0D, baseHealth * 3.2D);
        if (boss.getAttribute(Attribute.MAX_HEALTH) != null) {
            boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(boosted);
        }
        boss.setHealth(Math.min(boosted, boss.getMaxHealth()));
        boss.customName(prefixDefenseName(
                boss,
                cfg.msg("city.defense.mob.boss_prefix", "<dark_red>Signore d'assedio</dark_red>")
        ));
        boss.setCustomNameVisible(true);
    }

    private SpawnFront pickWaveFront(SessionState session) {
        if (session.fronts.isEmpty()) {
            return null;
        }
        double totalWeight = 0.0D;
        for (SpawnFront front : session.fronts) {
            totalWeight += Math.max(0.1D, front.weight());
        }
        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cursor = 0.0D;
        for (SpawnFront front : session.fronts) {
            cursor += Math.max(0.1D, front.weight());
            if (roll <= cursor) {
                return front;
            }
        }
        return session.fronts.get(session.fronts.size() - 1);
    }

    private int pickGroupSize(SessionState session) {
        CityDefenseSettings.SpawnSettings spawnSettings = settings.spawnSettings();
        int min = spawnSettings.groupSizeMin();
        int scaledMax = spawnSettings.groupSizeMin()
                + Math.max(0, session.currentWave / 4)
                + Math.max(0, session.tier.number() / 10);
        int max = Math.max(min, Math.min(spawnSettings.groupSizeMax(), scaledMax));
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private List<Location> resolveGroupSpawnLocations(SessionState session, SpawnFront front, int groupSize) {
        List<CandidateNode> nearby = session.candidateNodes.stream()
                .filter(node -> node.location().getWorld() != null)
                .filter(node -> sameWorld(node.location(), front.anchor()))
                .filter(node -> node.location().distanceSquared(front.anchor()) <= Math.pow(Math.max(1, settings.spawnSettings().frontJitterRadius()), 2))
                .toList();
        List<CandidateNode> pool = nearby.isEmpty()
                ? session.candidateNodes.stream()
                .filter(node -> sameWorld(node.location(), front.anchor()))
                .sorted(java.util.Comparator.comparingDouble(node -> node.location().distanceSquared(front.anchor())))
                .limit(Math.max(groupSize * 3L, 8L))
                .toList()
                : nearby;
        if (pool.isEmpty()) {
            return List.of();
        }
        CandidateNode clusterCenter = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        List<CandidateNode> ordered = new ArrayList<>(pool);
        ordered.sort(java.util.Comparator
                .comparingDouble((CandidateNode node) -> node.location().distanceSquared(clusterCenter.location()))
                .thenComparingDouble(node -> ThreadLocalRandom.current().nextDouble()));
        List<Location> selected = new ArrayList<>();
        double minSpacingSquared = Math.pow(Math.max(1, settings.spawnSettings().intraGroupSpacing()), 2);
        for (CandidateNode node : ordered) {
            Location candidate = node.location().clone();
            boolean tooClose = selected.stream().anyMatch(existing -> existing.distanceSquared(candidate) < minSpacingSquared);
            if (tooClose) {
                continue;
            }
            selected.add(candidate);
            if (selected.size() >= groupSize) {
                break;
            }
        }
        return List.copyOf(selected);
    }

    private SpawnFront dominantFront(SessionState session, Map<Integer, Integer> frontUsage) {
        if (session.fronts.isEmpty()) {
            return null;
        }
        SpawnFront fallback = session.fronts.stream()
                .max(java.util.Comparator.comparingDouble(SpawnFront::weight))
                .orElse(session.fronts.get(0));
        if (frontUsage == null || frontUsage.isEmpty()) {
            return fallback;
        }
        int frontIndex = frontUsage.entrySet().stream()
                .max(Map.Entry.<Integer, Integer>comparingByValue()
                        .thenComparing(entry -> session.fronts.stream()
                                .filter(front -> front.index() == entry.getKey())
                                .mapToDouble(SpawnFront::weight)
                                .findFirst()
                                .orElse(0.0D)))
                .map(Map.Entry::getKey)
                .orElse(fallback.index());
        return session.fronts.stream()
                .filter(front -> front.index() == frontIndex)
                .findFirst()
                .orElse(fallback);
    }

    private Location resolveBossSpawnLocation(SessionState session, SpawnFront bossFront) {
        if (bossFront != null) {
            List<Location> grouped = resolveGroupSpawnLocations(session, bossFront, 1);
            if (!grouped.isEmpty()) {
                return grouped.get(0);
            }
            return bossFront.anchor().clone();
        }
        return session.candidateNodes.stream()
                .max(java.util.Comparator.comparingDouble(CandidateNode::distanceSquared))
                .map(node -> node.location().clone())
                .orElse(session.guardianLocation.clone().add(settings.spawnSettings().maxDistanceFromGuardian(), 0.0D, 0.0D));
    }

    private int nearestFrontIndex(SessionState session, Location location) {
        if (location == null || session.fronts.isEmpty()) {
            return -1;
        }
        return session.fronts.stream()
                .min(java.util.Comparator.comparingDouble(front -> front.anchor().distanceSquared(location)))
                .map(SpawnFront::index)
                .orElse(-1);
    }

    private boolean isBreacherEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (entity.getScoreboardTags().contains(BREACHER_TAG) || entity.getScoreboardTags().contains("cittaexp-tag:breacher")) {
            return true;
        }
        return settings.breachSettings().isVanillaBreacher(entity.getType());
    }

    private void maintainMobPressure(SessionState session, Instant now) {
        Villager guardian = guardianEntity(session);
        if (guardian == null || !guardian.isValid()) {
            return;
        }
        long sampleEveryMillis = TimeUnit.SECONDS.toMillis(settings.recoverySettings().stuckSampleSeconds());
        long stuckTimeoutMillis = TimeUnit.SECONDS.toMillis(settings.recoverySettings().stuckTimeoutSeconds());
        for (UUID mobId : new ArrayList<>(session.activeMobIds)) {
            Entity entity = Bukkit.getEntity(mobId);
            if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
                continue;
            }
            if (!(living instanceof Mob mob)) {
                continue;
            }
            if (mob.getTarget() == null || !mob.getTarget().isValid()) {
                mob.setTarget(guardian);
            }
            MobRuntimeState state = session.mobStates.computeIfAbsent(mobId, ignored -> new MobRuntimeState(nearestFrontIndex(session, living.getLocation()), living.getLocation(), now));
            if (state.lastSampleAt != null && now.toEpochMilli() - state.lastSampleAt.toEpochMilli() < sampleEveryMillis) {
                continue;
            }
            Location current = living.getLocation();
            boolean progressed = hasProgressedTowardGuardian(state.lastSampleLocation, current, guardian.getLocation(), settings.recoverySettings().stuckDistanceThreshold());
            state.lastSampleAt = now;
            state.lastSampleLocation = current.clone();
            if (progressed) {
                state.lastProgressAt = now;
                continue;
            }
            if (state.lastProgressAt != null && now.toEpochMilli() - state.lastProgressAt.toEpochMilli() < stuckTimeoutMillis) {
                continue;
            }
            boolean recovered = false;
            if (isBreacherEntity(living)) {
                recovered = tryBreakPath(session, living, guardian, state, now);
            }
            if (!recovered) {
                recovered = tryRepositionMob(session, living, guardian, state, now);
            }
            if (recovered) {
                state.lastProgressAt = now;
                state.lastSampleLocation = living.getLocation().clone();
            }
        }
    }

    private static boolean hasProgressedTowardGuardian(Location previous, Location current, Location guardian, double threshold) {
        if (previous == null || current == null || guardian == null || !sameWorld(previous, current) || !sameWorld(current, guardian)) {
            return true;
        }
        double moved = previous.distance(current);
        if (moved >= threshold) {
            return true;
        }
        double previousDistance = previous.distance(guardian);
        double currentDistance = current.distance(guardian);
        return previousDistance - currentDistance >= Math.max(0.5D, threshold * 0.4D);
    }

    private boolean tryBreakPath(SessionState session, LivingEntity living, Villager guardian, MobRuntimeState state, Instant now) {
        CityDefenseSettings.BreachSettings breachSettings = settings.breachSettings();
        if (!breachSettings.enabled() || state.breaksUsed >= breachSettings.maxBreaksPerMob()) {
            return false;
        }
        if (now.toEpochMilli() - state.lastProgressAt.toEpochMilli() < TimeUnit.SECONDS.toMillis(breachSettings.pathFailSeconds())) {
            return false;
        }
        if (now.toEpochMilli() - state.lastBreakAt.toEpochMilli() < TimeUnit.SECONDS.toMillis(breachSettings.breakCooldownSeconds())) {
            return false;
        }
        Block block = findBreacherBlock(living.getLocation(), guardian.getLocation(), session);
        if (block == null) {
            return false;
        }
        block.setType(Material.AIR, false);
        state.breaksUsed++;
        state.lastBreakAt = now;
        return true;
    }

    private Block findBreacherBlock(Location origin, Location guardianLocation, SessionState session) {
        if (origin == null || guardianLocation == null || origin.getWorld() == null || guardianLocation.getWorld() == null) {
            return null;
        }
        Vector forward = guardianLocation.toVector().subtract(origin.toVector());
        if (forward.lengthSquared() <= 0.01D) {
            return null;
        }
        forward = forward.normalize();
        Vector lateral = new Vector(-forward.getZ(), 0.0D, forward.getX()).normalize();
        CityDefenseSettings.BreachSettings breachSettings = settings.breachSettings();
        for (int depth = 1; depth <= breachSettings.searchDepth(); depth++) {
            Vector base = forward.clone().multiply(depth + 0.75D);
            Vector[] offsets = new Vector[] {
                    new Vector(0.0D, 0.0D, 0.0D),
                    lateral.clone(),
                    lateral.clone().multiply(-1.0D)
            };
            for (Vector offset : offsets) {
                Location probeBase = origin.clone().add(base).add(offset);
                for (int yOffset = 0; yOffset <= breachSettings.maxVerticalDelta(); yOffset++) {
                    Block block = probeBase.clone().add(0.0D, yOffset, 0.0D).getBlock();
                    if (isBreakableBreacherBlock(block, guardianLocation, session)) {
                        return block;
                    }
                }
            }
        }
        return null;
    }

    private boolean isBreakableBreacherBlock(Block block, Location guardianLocation, SessionState session) {
        if (block == null || block.getWorld() == null || guardianLocation == null || !sameWorld(block.getLocation(), guardianLocation)) {
            return false;
        }
        if (block.getLocation().distanceSquared(guardianLocation) <= 4.0D) {
            return false;
        }
        if (block.getLocation().distanceSquared(session.guardianLocation) > Math.pow(settings.spawnSettings().maxDistanceFromGuardian() + 8.0D, 2.0D)) {
            return false;
        }
        Material type = block.getType();
        if (type.isAir() || block.isLiquid() || block.isPassable()) {
            return false;
        }
        return !UNBREAKABLE_BREACH_MATERIALS.contains(type);
    }

    private boolean tryRepositionMob(SessionState session, LivingEntity living, Villager guardian, MobRuntimeState state, Instant now) {
        CityDefenseSettings.RecoverySettings recoverySettings = settings.recoverySettings();
        if (state.repositionAttempts >= recoverySettings.maxAttemptsPerWave()) {
            return false;
        }
        Location target = resolveRecoveryLocation(session, living.getLocation(), guardian.getLocation(), state);
        if (target == null) {
            return false;
        }
        boolean teleported = living.teleport(target);
        if (teleported) {
            state.repositionAttempts++;
            if (living instanceof Mob mob) {
                mob.setTarget(guardian);
            }
        }
        return teleported;
    }

    private Location resolveRecoveryLocation(SessionState session, Location origin, Location guardianLocation, MobRuntimeState state) {
        if (origin == null || guardianLocation == null) {
            return null;
        }
        int maxStep = settings.recoverySettings().repositionStepDistance() * Math.max(1, state.repositionAttempts + 1);
        double currentGuardianDistance = origin.distanceSquared(guardianLocation);
        List<CandidateNode> preferred = new ArrayList<>(session.candidateNodes);
        if (state.frontIndex >= 0) {
            preferred.sort(java.util.Comparator.comparingDouble(node -> {
                SpawnFront front = session.fronts.stream().filter(candidate -> candidate.index() == state.frontIndex).findFirst().orElse(null);
                if (front == null) {
                    return node.location().distanceSquared(origin);
                }
                return node.location().distanceSquared(front.anchor());
            }));
        }
        for (CandidateNode node : preferred) {
            Location candidate = node.location();
            if (!sameWorld(candidate, origin)) {
                continue;
            }
            if (candidate.distance(origin) > maxStep) {
                continue;
            }
            if (candidate.distanceSquared(guardianLocation) >= currentGuardianDistance) {
                continue;
            }
            return candidate.clone();
        }
        Vector towardGuardian = guardianLocation.toVector().subtract(origin.toVector());
        if (towardGuardian.lengthSquared() <= 0.01D) {
            return null;
        }
        towardGuardian = towardGuardian.normalize().multiply(settings.recoverySettings().repositionStepDistance());
        Location fallback = origin.clone().add(towardGuardian);
        int y = fallback.getWorld().getHighestBlockYAt(fallback);
        fallback.setY(y + 1.0D);
        if (!isUsableCandidateBlock(fallback, session.guardianLocation)) {
            return null;
        }
        return fallback;
    }

    private void updateBossEncounter(SessionState session) {
        BossEncounterState encounter = session.bossEncounter;
        if (encounter == null) {
            return;
        }
        Entity bossEntity = Bukkit.getEntity(encounter.bossUuid());
        if (!(bossEntity instanceof LivingEntity boss) || !boss.isValid() || boss.isDead()) {
            clearBossEncounter(session);
            return;
        }
        Optional<CustomMobBossRuntimeSnapshot> snapshotOptional = mobEffectsEngine.bossSnapshot(encounter.bossUuid());
        if (snapshotOptional.isEmpty()) {
            encounterAudienceService.clearBossBar(encounter);
            return;
        }
        CustomMobBossRuntimeSnapshot snapshot = snapshotOptional.get();
        int previousPhase = encounter.phaseNumber();
        encounter.updateFromSnapshot(snapshot);
        if (previousPhase != snapshot.phaseNumber() && snapshot.phaseNumber() > 1) {
            announceSessionLocal(session, renderBossLocalMessage(
                    "city.defense.announce.custom_boss_phase",
                    snapshot.localBroadcasts().phaseMessage(),
                    "<gold>❖ {boss}</gold> <gray>spezza il ritmo dell'assedio ed entra in</gray> <white>{phase}</white>",
                    session,
                    snapshot.displayName(),
                    snapshot.phaseLabel()
            ));
        }
        encounterAudienceService.syncBossBar(
                encounter,
                session.townId,
                session.townName,
                session.guardianLocation,
                localDefenseRadiusSquared(),
                boss,
                snapshot
        );
    }

    private void syncSessionBossBar(SessionState session, Instant now) {
        long phaseRemaining = session.phaseEndsAt == null ? 0L : Math.max(0L, session.phaseEndsAt.getEpochSecond() - now.getEpochSecond());
        int aliveMobs = session.activeMobIds.size();
        float progress;
        BossBar.Color color;
        String fallback;
        String configPath;

        switch (session.phase) {
            case PREP -> {
                long total = Math.max(1L, settings.prepSeconds());
                progress = boundedProgress(phaseRemaining / (float) total);
                color = BossBar.Color.YELLOW;
                configPath = "city.defense.bossbar.prep";
                fallback = "<gold>✦ Invasione in arrivo</gold> <gray>· Tier</gray> <white>{tier}</white> <gray>· Prima ondata tra</gray> <white>{seconds}s</white>";
            }
            case INTER_WAVE -> {
                long total = Math.max(1L, settings.interWaveSeconds());
                progress = boundedProgress(phaseRemaining / (float) total);
                color = BossBar.Color.BLUE;
                configPath = "city.defense.bossbar.inter_wave";
                fallback = "<aqua>✦ Pausa tra ondate</aqua> <gray>· Ondata</gray> <white>{next_wave}/{total_waves}</white> <gray>tra</gray> <white>{seconds}s</white>";
            }
            case COMBAT -> {
                progress = boundedProgress((float) (session.guardianHp / Math.max(1.0D, session.maxGuardianHp)));
                color = session.currentWave >= session.totalWaves ? BossBar.Color.RED : BossBar.Color.GREEN;
                if (session.bossEncounter != null && session.bossEncounter.bossName() != null && !session.bossEncounter.bossName().isBlank()) {
                    configPath = "city.defense.bossbar.combat_boss";
                    fallback = "<dark_red>✦ Varco Maggiore</dark_red> <gray>·</gray> <white>{boss}</white> <gray>· {phase}</gray> <gray>· Guardiano:</gray> <white>{guardian_hp}%</white>";
                } else {
                    configPath = "city.defense.bossbar.combat";
                    fallback = "<red>✦ Difesa in corso</red> <gray>· Ondata</gray> <white>{wave}/{total_waves}</white> <gray>· Ostili:</gray> <white>{alive}</white> <gray>· Guardiano:</gray> <white>{guardian_hp}%</white>";
                }
            }
            default -> {
                progress = 1.0F;
                color = BossBar.Color.WHITE;
                configPath = "city.defense.bossbar.combat";
                fallback = "<gray>Difesa in corso</gray>";
            }
        }

        int guardianPercent = (int) Math.round(Math.max(0.0D, Math.min(100.0D, (session.guardianHp / Math.max(1.0D, session.maxGuardianHp)) * 100.0D)));
        String titleTemplate = cfg.cfgString(configPath, fallback);
        Component title = MiniMessageHelper.parse(
                titleTemplate,
                Placeholder.unparsed("town", session.townName),
                Placeholder.unparsed("tier", session.tier.name()),
                Placeholder.unparsed("wave", Integer.toString(Math.max(1, session.currentWave))),
                Placeholder.unparsed("next_wave", Integer.toString(Math.min(session.totalWaves, Math.max(1, session.currentWave + (session.phase == Phase.COMBAT ? 0 : 1))))),
                Placeholder.unparsed("total_waves", Integer.toString(session.totalWaves)),
                Placeholder.unparsed("seconds", Long.toString(phaseRemaining)),
                Placeholder.unparsed("alive", Integer.toString(aliveMobs)),
                Placeholder.unparsed("guardian_hp", Integer.toString(guardianPercent)),
                Placeholder.parsed("boss", session.bossEncounter == null ? "-" : safeText(session.bossEncounter.bossName(), "-")),
                Placeholder.unparsed("phase", session.bossEncounter == null ? "-" : safeText(session.bossEncounter.phaseLabel(), "Primo Urto"))
        );
        encounterAudienceService.syncSessionBar(
                session.sessionBar,
                session.townId,
                session.guardianLocation,
                localDefenseRadiusSquared(),
                title,
                progress,
                color,
                BossBar.Overlay.PROGRESS
        );
    }

    private void maybeSendCountdownActionBar(SessionState session, Instant now) {
        if (session.phase != Phase.PREP && session.phase != Phase.INTER_WAVE) {
            session.lastCountdownCue = null;
            return;
        }
        if (session.phaseEndsAt == null) {
            session.lastCountdownCue = null;
            return;
        }
        long seconds = Math.max(0L, session.phaseEndsAt.getEpochSecond() - now.getEpochSecond());
        if (seconds <= 0L || seconds > 10L) {
            session.lastCountdownCue = null;
            return;
        }
        String cue = session.phase.name() + ":" + seconds + ":" + session.currentWave;
        if (cue.equals(session.lastCountdownCue)) {
            return;
        }
        session.lastCountdownCue = cue;
        encounterAudienceService.sendActionBarLocal(
                session.townId,
                session.guardianLocation,
                localDefenseRadiusSquared(),
                cfg.msg(
                        "city.defense.actionbar.countdown",
                        "<gold>✦ Ondata {next_wave}/{total_waves}</gold> <gray>arriva tra</gray> <white>{seconds}</white> <gray>secondi</gray>",
                        Placeholder.unparsed("next_wave", Integer.toString(Math.min(session.totalWaves, Math.max(1, session.currentWave + 1)))),
                        Placeholder.unparsed("total_waves", Integer.toString(session.totalWaves)),
                        Placeholder.unparsed("seconds", Long.toString(seconds))
                )
        );
    }

    private void showWaveStartTitle(SessionState session, int incomingCount) {
        Title title = Title.title(
                cfg.msg(
                        "city.defense.title.wave_started.title",
                        "<gold><bold>ONDATA {wave}/{total}</bold></gold>",
                        Placeholder.unparsed("wave", Integer.toString(session.currentWave)),
                        Placeholder.unparsed("total", Integer.toString(session.totalWaves))
                ),
                cfg.msg(
                        "city.defense.title.wave_started.subtitle",
                        "<red>Nuovi nemici in arrivo:</red> <white>{count}</white> <gray>ostili</gray>",
                        Placeholder.unparsed("count", Integer.toString(Math.max(1, incomingCount))),
                        Placeholder.unparsed("tier", session.tier.name())
                )
        );
        encounterAudienceService.showTitleLocal(
                session.townId,
                session.guardianLocation,
                localDefenseRadiusSquared(),
                title
        );
    }

    private static float boundedProgress(float progress) {
        return Math.max(0.0F, Math.min(1.0F, progress));
    }

    private void syncGuardianHologram(SessionState session) {
        Villager guardian = guardianEntity(session);
        if (guardian == null || !guardian.isValid()) {
            clearGuardianHologram(session);
            return;
        }
        TextDisplay display = guardianHologram(session);
        if (display == null || !display.isValid()) {
            display = spawnGuardianHologram(guardian);
            if (display == null) {
                return;
            }
            session.guardianHologramId = display.getUniqueId();
        }
        display.teleport(guardian.getLocation().clone().add(0.0D, 2.55D, 0.0D));
        display.text(renderGuardianHologram(session));
    }

    private TextDisplay spawnGuardianHologram(Villager guardian) {
        Location at = guardian.getLocation().clone().add(0.0D, 2.55D, 0.0D);
        if (at.getWorld() == null) {
            return null;
        }
        Entity spawned = at.getWorld().spawnEntity(at, EntityType.TEXT_DISPLAY);
        if (!(spawned instanceof TextDisplay display)) {
            spawned.remove();
            return null;
        }
        display.setPersistent(false);
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(false);
        display.setDefaultBackground(false);
        display.setLineWidth(220);
        return display;
    }

    private TextDisplay guardianHologram(SessionState session) {
        if (session.guardianHologramId == null) {
            return null;
        }
        Entity entity = Bukkit.getEntity(session.guardianHologramId);
        return entity instanceof TextDisplay display && display.isValid() ? display : null;
    }

    private void clearGuardianHologram(SessionState session) {
        TextDisplay display = guardianHologram(session);
        if (display != null) {
            display.remove();
        }
        session.guardianHologramId = null;
    }

    private Component renderGuardianHologram(SessionState session) {
        int guardianPercent = (int) Math.round(Math.max(0.0D, Math.min(100.0D, (session.guardianHp / Math.max(1.0D, session.maxGuardianHp)) * 100.0D)));
        String template = cfg.cfgString(
                "city.defense.guardian.hologram",
                "<gradient:#ffd36e:#ff9a3d><bold>✦ GUARDIANO DELLA CITTA ✦</bold></gradient><newline>"
                        + "<white><bold>{town}</bold></white><newline>"
                        + "<gray></gray> {bar} <gray></gray><newline>"
                        + "<gray>Integrita:</gray> <white>{hp}/{max}</white> <gray>HP</gray> <white>({percent}%)</white><newline>"
                        + "<gray>Ondata:</gray> <white>{wave}/{total}</white> <dark_gray>•</dark_gray> <gray>Stato:</gray> {phase}"
        );
        return MiniMessageHelper.parse(
                template,
                Placeholder.unparsed("town", session.townName),
                Placeholder.unparsed("hp", Integer.toString((int) Math.round(session.guardianHp))),
                Placeholder.unparsed("max", Integer.toString((int) Math.round(session.maxGuardianHp))),
                Placeholder.unparsed("percent", Integer.toString(guardianPercent)),
                Placeholder.unparsed("wave", Integer.toString(Math.max(0, session.currentWave))),
                Placeholder.unparsed("total", Integer.toString(session.totalWaves)),
                Placeholder.parsed("phase", prettyDefensePhase(session.phase)),
                Placeholder.parsed("bar", renderGuardianHpBar(guardianPercent))
        );
    }

    private static String prettyDefensePhase(Phase phase) {
        return switch (phase) {
            case PREP -> "<yellow><bold>Preparazione</bold></yellow>";
            case INTER_WAVE -> "<aqua><bold>Pausa tra ondate</bold></aqua>";
            case COMBAT -> "<red><bold>Invasione in corso</bold></red>";
        };
    }

    private static String renderGuardianHpBar(int guardianPercent) {
        int filled = Math.max(0, Math.min(20, (int) Math.round((guardianPercent / 100.0D) * 20.0D)));
        String filledColor = guardianPercent >= 70
                ? "<green>"
                : guardianPercent >= 35
                ? "<yellow>"
                : "<red>";
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            if (i < filled) {
                bar.append(filledColor).append("█</").append(filledColor.substring(1));
            } else {
                bar.append("<dark_gray>█</dark_gray>");
            }
        }
        return bar.toString();
    }

    private void clearBossEncounter(SessionState session) {
        BossEncounterState encounter = session.bossEncounter;
        if (encounter != null) {
            encounterAudienceService.clearBossBar(encounter);
        }
        session.bossEncounter = null;
    }

    private Component renderBossLocalMessage(
            String configPath,
            String authoredTemplate,
            String fallback,
            SessionState session,
            String bossName,
            String phaseLabel
    ) {
        String template = (authoredTemplate != null && !authoredTemplate.isBlank())
                ? authoredTemplate
                : cfg.cfgString(configPath, fallback);
        return MiniMessageHelper.parse(
                template,
                Placeholder.parsed("boss", bossName),
                Placeholder.unparsed("phase", phaseLabel),
                Placeholder.unparsed("town", session.townName),
                Placeholder.unparsed("tier", session.tier.name())
        );
    }

    private void announceSessionLocal(SessionState session, Component message) {
        encounterAudienceService.broadcastLocal(
                session.townId,
                session.guardianLocation,
                localDefenseRadiusSquared(),
                message
        );
    }

    private double localDefenseRadiusSquared() {
        double radius = Math.max(48.0D, settings.antiCheeseRadius() * 5.0D);
        return radius * radius;
    }

    private CityDefenseSpawnRef pickWaveSpawnRef(SessionState session) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (!session.spec.eliteMobs().isEmpty() && random.nextDouble() < session.spec.eliteChance()) {
            return session.spec.eliteMobs().get(random.nextInt(session.spec.eliteMobs().size()));
        }
        return session.spec.regularMobs().get(random.nextInt(session.spec.regularMobs().size()));
    }

    private Component prefixDefenseName(LivingEntity entity, Component prefix) {
        Component baseName = entity.customName();
        if (baseName == null) {
            baseName = Component.text(prettyEntity(entity.getType()));
        }
        if (prefix instanceof TextComponent textComponent && textComponent.content().isBlank() && textComponent.children().isEmpty()) {
            return baseName;
        }
        return Component.empty()
                .append(prefix)
                .append(Component.space())
                .append(baseName);
    }

    private void applyGuardianDamage(SessionState session, double rawDamage) {
        if (rawDamage <= 0.0D) {
            return;
        }
        session.guardianHp = Math.max(0.0D, session.guardianHp - rawDamage);
        if (session.guardianHp <= 0.0D) {
            endSession(session, false, "guardian-hp-zero");
        }
    }

    private void endSession(SessionState session, boolean victory, String reason) {
        SessionState removed = sessionsByTown.remove(session.townId);
        if (removed == null) {
            return;
        }
        mobEffectsEngine.cancelRuntimeForSession(session.sessionId);
        encounterAudienceService.clearSessionBar(session.sessionBar);
        clearBossEncounter(session);
        clearGuardianHologram(session);
        cleanupSessionEntities(session);
        Instant endedAt = Instant.now();
        setCooldown(session.townId, session.tier, endedAt.plusSeconds(session.spec.cooldownSeconds()));

        long xpScaled = 0L;
        double rewardMoneyAmount = 0.0D;
        String rewardItemsPayload = "-";
        Map<Material, Integer> effectiveRewardItems = session.effectiveRewardItems;
        if (victory) {
            rewardMoneyAmount = session.effectiveRewardMoney.doubleValue();
            xpScaled = Math.max(0L, cityLevelService.toScaledXp(session.spec.rewardXp()));
            rewardItemsPayload = serializeItems(effectiveRewardItems);
            applyRewards(session, rewardMoneyAmount, xpScaled, rewardItemsPayload, effectiveRewardItems, endedAt);
            successfulTiersByTown.compute(session.townId, (townId, tiers) -> {
                java.util.LinkedHashSet<CityDefenseTier> updated = new java.util.LinkedHashSet<>(tiers == null ? Set.of() : tiers);
                updated.add(session.tier);
                return Set.copyOf(updated);
            });
            announceTown(
                    session.townId,
                    cfg.chatFrame(
                            "city.defense.announce.victory",
                            "ASSALTO RESPINTO",
                            "Il grado " + session.tier.name() + " e stato completato",
                            "Ricompense depositate nel tesoro cittadino",
                            buildVictoryDetails(session, rewardMoneyAmount, effectiveRewardItems)
                    )
            );
            if (!session.completedBefore && !session.spec.firstClearUniqueRewards().isEmpty()) {
                announceTown(
                        session.townId,
                        cfg.chatFrame(
                                "city.defense.announce.first_clear_unique",
                                "RICOMPENSA UNICA",
                                "Prima vittoria sul grado " + session.tier.name(),
                                "Il premio esclusivo e stato sbloccato",
                                buildFirstClearDetails(session)
                        )
                );
            }
        } else {
            announceTown(
                    session.townId,
                    cfg.chatFrame(
                            "city.defense.announce.defeat",
                            "ASSALTO FALLITO",
                            "L'assalto e stato perso",
                            "Il guardiano non ha retto l'assedio",
                            List.of(
                                    UiChatStyle.detail(UiChatStyle.DetailType.CONTEXT, "Citta': " + session.townName),
                                    UiChatStyle.detail(UiChatStyle.DetailType.WARNING, "Motivo: " + describeEndReason(reason, session)),
                                    UiChatStyle.detail(UiChatStyle.DetailType.NOTE, "Riorganizza la citta prima del prossimo tentativo.")
                            )
                    )
            );
        }
        double finalRewardMoneyAmount = rewardMoneyAmount;
        long finalXpScaled = xpScaled;
        String finalItemsPayload = rewardItemsPayload;
        ioExecutor.execute(() -> store.completeDefenseSession(
                session.sessionId,
                victory ? "VICTORY" : "DEFEAT",
                reason,
                endedAt,
                finalRewardMoneyAmount,
                finalXpScaled,
                finalItemsPayload
        ));
    }

    private void forceAbortSession(SessionState session, String reason) {
        SessionState removed = sessionsByTown.remove(session.townId);
        if (removed == null) {
            return;
        }
        mobEffectsEngine.cancelRuntimeForSession(session.sessionId);
        encounterAudienceService.clearSessionBar(session.sessionBar);
        clearBossEncounter(session);
        clearGuardianHologram(session);
        cleanupSessionEntities(session);
        Instant endedAt = Instant.now();
        ioExecutor.execute(() -> store.completeDefenseSession(
                session.sessionId,
                "ABORTED",
                reason,
                endedAt,
                0.0D,
                0L,
                "-"
        ));
    }

    private void applyRewards(
            SessionState session,
            double rewardMoneyAmount,
            long xpScaled,
            String rewardItemsPayload,
            Map<Material, Integer> effectiveRewardItems,
            Instant now
    ) {
        ioExecutor.execute(() -> {
            String moneyGrant = "defense:money:" + session.sessionId;
            boolean moneyFirst = store.markDefenseRewardGrant(
                    moneyGrant,
                    session.townId,
                    session.sessionId,
                    ChallengeRewardType.MONEY_CITY,
                    Double.toString(rewardMoneyAmount),
                    now
            );
            if (moneyFirst && rewardMoneyAmount > 0.0D) {
                creditTownMoneyReward(session.townId, rewardMoneyAmount, "defense:" + session.tier.id());
            }

            String xpGrant = "defense:xp:" + session.sessionId;
            boolean xpFirst = store.markDefenseRewardGrant(
                    xpGrant,
                    session.townId,
                    session.sessionId,
                    ChallengeRewardType.XP_CITY,
                    "xpScaled=" + xpScaled,
                    now
            );
            if (xpFirst && xpScaled > 0L) {
                cityLevelService.grantXpBonus(session.townId, xpScaled, null, "city-defense:" + session.tier.id());
            }

            String itemsGrant = "defense:items:" + session.sessionId;
            boolean itemsFirst = store.markDefenseRewardGrant(
                    itemsGrant,
                    session.townId,
                    session.sessionId,
                    ChallengeRewardType.ITEM_VAULT,
                    rewardItemsPayload,
                    now
            );
            if (itemsFirst) {
                cityItemVaultService.depositRewardItems(session.townId, effectiveRewardItems, "defense:" + session.tier.id());
            }

            if (!session.completedBefore && !session.spec.firstClearUniqueRewards().isEmpty()) {
                java.util.List<ItemStack> uniqueStacks = itemsAdderRewardResolver.resolveAll(session.spec.firstClearUniqueRewards());
                if (!uniqueStacks.isEmpty()) {
                    String uniqueGrant = "defense:unique:" + session.sessionId;
                    String uniquePayload = serializeUniqueRewards(session.spec.firstClearUniqueRewards());
                    boolean uniqueFirst = store.markDefenseRewardGrant(
                            uniqueGrant,
                            session.townId,
                            session.sessionId,
                            ChallengeRewardType.ITEM_VAULT,
                            uniquePayload,
                            now
                    );
                    if (uniqueFirst) {
                        cityItemVaultService.depositRewardStacks(session.townId, uniqueStacks, "defense-unique:" + session.tier.id());
                    }
                } else if (itemsAdderRewardResolver.available()) {
                    plugin.getLogger().warning("[defense] nessun reward IA valido risolto per first clear tier=" + session.tier.name());
                } else {
                    plugin.getLogger().warning("[defense] reward IA first-clear saltati: ItemsAdder non disponibile tier=" + session.tier.name());
                }
            }
        });
    }

    private void cleanupSessionEntities(SessionState session) {
        Villager guardian = guardianEntity(session);
        if (guardian != null) {
            guardian.remove();
        }
        for (UUID mobId : new ArrayList<>(session.activeMobIds)) {
            Entity mob = Bukkit.getEntity(mobId);
            if (mob != null && mob.isValid()) {
                if (glowService.isManagedGlow(mob)) {
                    glowService.clearGlow(mob);
                }
                mob.remove();
            }
        }
        session.activeMobIds.clear();
        session.mobStates.clear();
    }

    private CityDefenseRewardPolicy.RewardOutcome effectiveRewardOutcome(CityDefenseLevelSpec spec, boolean completedBefore) {
        BigDecimal effectiveRewardMoney = economyValueService.effectiveMoney(EconomyBalanceCategory.DEFENSE_MONEY_REWARD, spec.rewardMoney());
        Map<Material, Integer> economicItems = economyValueService.scaleEconomicRewardItems(spec.rewardItems());
        return CityDefenseRewardPolicy.resolve(spec, settings.retryPolicy(), completedBefore, effectiveRewardMoney, economicItems);
    }

    private Set<CityDefenseTier> successfulTiersForTown(int townId) {
        return successfulTiersByTown.computeIfAbsent(townId, this::loadSuccessfulTiers);
    }

    private Set<CityDefenseTier> loadSuccessfulTiers(int townId) {
        java.util.LinkedHashSet<CityDefenseTier> result = new java.util.LinkedHashSet<>();
        for (String raw : store.loadSuccessfulDefenseTiers(townId)) {
            try {
                result.add(CityDefenseTier.valueOf(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Set.copyOf(result);
    }

    private void creditTownMoneyReward(int townId, double amount, String reason) {
        if (townId <= 0 || amount <= 0.0D) {
            return;
        }
        Optional<Town> town = huskTownsApiHook.getTownById(townId);
        if (town.isEmpty()) {
            plugin.getLogger().warning("[defense] impossibile accreditare money reward, town mancante id=" + townId
                    + " reason=" + reason);
            return;
        }
        UUID actorId = resolveRewardActor(town.get()).orElse(town.get().getMayor());
        BigDecimal delta = BigDecimal.valueOf(amount);
        try {
            huskTownsApiHook.editTown(actorId, townId, mutableTown ->
                    mutableTown.setMoney(mutableTown.getMoney().add(delta)));
            economyValueService.recordRewardEmission(amount);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("[defense] accredito money reward fallito townId=" + townId
                    + " amount=" + amount + " reason=" + reason + " error=" + exception.getMessage());
        }
    }

    private Villager spawnGuardian(Location location, String townName) {
        if (location.getWorld() == null) {
            return null;
        }
        Entity entity = location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        if (!(entity instanceof Villager villager)) {
            entity.remove();
            return null;
        }
        villager.addScoreboardTag(GUARDIAN_TAG);
        villager.setAI(false);
        villager.setPersistent(true);
        villager.setSilent(true);
        villager.setBaby();
        villager.setAgeLock(true);
        villager.setCollidable(true);
        if (villager.getAttribute(Attribute.MAX_HEALTH) != null) {
            villager.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1024.0D);
        }
        villager.setHealth(Math.min(villager.getMaxHealth(), 1024.0D));
        villager.customName(cfg.msg(
                "city.defense.guardian.name",
                "<gold>Guardiano della citta</gold> <gray>{town}</gray>",
                Placeholder.unparsed("town", townName)
        ));
        villager.setCustomNameVisible(false);
        return villager;
    }

    private long cooldownRemainingSeconds(int townId, CityDefenseTier tier, Instant now) {
        Instant availableAt = cooldowns.get(cooldownKey(townId, tier));
        if (availableAt == null || !availableAt.isAfter(now)) {
            return 0L;
        }
        return Math.max(0L, availableAt.getEpochSecond() - now.getEpochSecond());
    }

    private void setCooldown(int townId, CityDefenseTier tier, Instant availableAt) {
        cooldowns.put(cooldownKey(townId, tier), availableAt);
        ioExecutor.execute(() -> store.upsertDefenseCooldown(townId, tier.name(), availableAt, Instant.now()));
    }

    private static String cooldownKey(int townId, CityDefenseTier tier) {
        return townId + "|" + tier.name();
    }

    private SessionState sessionAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        double radiusSquared = settings.antiCheeseRadius() * settings.antiCheeseRadius();
        for (SessionState session : sessionsByTown.values()) {
            if (!sameWorld(session.guardianLocation, location)) {
                continue;
            }
            if (session.guardianLocation.distanceSquared(location) <= radiusSquared) {
                return session;
            }
        }
        return null;
    }

    private static boolean sameWorld(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) {
            return false;
        }
        return a.getWorld().getUID().equals(b.getWorld().getUID());
    }

    private SessionState sessionByGuardian(UUID guardianId) {
        if (guardianId == null) {
            return null;
        }
        for (SessionState session : sessionsByTown.values()) {
            if (guardianId.equals(session.guardianId)) {
                return session;
            }
        }
        return null;
    }

    private void pruneDeadMobs(SessionState session) {
        session.activeMobIds.removeIf(id -> {
            Entity entity = Bukkit.getEntity(id);
            return !(entity instanceof LivingEntity living) || !living.isValid() || living.isDead();
        });
        session.mobStates.keySet().removeIf(id -> !session.activeMobIds.contains(id));
    }

    private int activeDefenders(int townId) {
        Set<UUID> memberIds = huskTownsApiHook.getTownMembers(townId).keySet();
        int online = 0;
        for (UUID memberId : memberIds) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null && player.isOnline()) {
                online++;
            }
        }
        return Math.max(1, online);
    }

    private Villager guardianEntity(SessionState session) {
        Entity entity = Bukkit.getEntity(session.guardianId);
        if (entity instanceof Villager villager && villager.isValid()) {
            return villager;
        }
        return null;
    }

    private SpawnPlan resolveSpawnPlan(int townId, Location center) {
        List<CandidateNode> candidates = resolveCandidateNodes(townId, center);
        if (candidates.isEmpty()) {
            return null;
        }
        List<SpawnFront> fronts = resolveSpawnFronts(candidates, center);
        if (fronts.isEmpty()) {
            return null;
        }
        return new SpawnPlan(List.copyOf(candidates), List.copyOf(fronts));
    }

    private List<CandidateNode> resolveCandidateNodes(int townId, Location center) {
        if (center == null || center.getWorld() == null) {
            return List.of();
        }
        CityDefenseSettings.SpawnSettings spawnSettings = settings.spawnSettings();
        World world = center.getWorld();
        int minDistance = spawnSettings.minDistanceFromGuardian();
        int maxDistance = spawnSettings.maxDistanceFromGuardian();
        int minDistanceSquared = minDistance * minDistance;
        int maxDistanceSquared = maxDistance * maxDistance;
        int chunkRadius = Math.max(1, (int) Math.ceil(maxDistance / 16.0D));
        int chunkCenterX = center.getBlockX() >> 4;
        int chunkCenterZ = center.getBlockZ() >> 4;
        List<CandidateNode> candidates = new ArrayList<>();
        int spacingSquared = Math.max(4, settings.spawnSettings().intraGroupSpacing() * settings.spawnSettings().intraGroupSpacing());

        for (int chunkOffsetX = -chunkRadius; chunkOffsetX <= chunkRadius; chunkOffsetX++) {
            for (int chunkOffsetZ = -chunkRadius; chunkOffsetZ <= chunkRadius; chunkOffsetZ++) {
                Chunk chunk = world.getChunkAt(chunkCenterX + chunkOffsetX, chunkCenterZ + chunkOffsetZ);
                Optional<TownClaim> claim = huskTownsApiHook.getClaimAt(chunk);
                boolean insideClaim = claim.isPresent() && claim.get().town().getId() == townId;
                boolean otherTownClaim = claim.isPresent() && claim.get().town().getId() != townId;
                if (otherTownClaim) {
                    continue;
                }
                if (insideClaim && !spawnSettings.allowInsideClaims()) {
                    continue;
                }
                if (!insideClaim && !spawnSettings.allowOutsideClaims()) {
                    continue;
                }
                for (int localX = 2; localX < 16; localX += 4) {
                    for (int localZ = 2; localZ < 16; localZ += 4) {
                        int blockX = (chunk.getX() << 4) + localX;
                        int blockZ = (chunk.getZ() << 4) + localZ;
                        int dx = blockX - center.getBlockX();
                        int dz = blockZ - center.getBlockZ();
                        int horizontalDistanceSquared = (dx * dx) + (dz * dz);
                        if (horizontalDistanceSquared < minDistanceSquared || horizontalDistanceSquared > maxDistanceSquared) {
                            continue;
                        }
                        int y = world.getHighestBlockYAt(blockX, blockZ) + 1;
                        Location candidate = new Location(world, blockX + 0.5D, y, blockZ + 0.5D);
                        if (!isUsableCandidateBlock(candidate, center)) {
                            continue;
                        }
                        boolean tooClose = candidates.stream().anyMatch(existing -> sameWorld(existing.location(), candidate) && existing.location().distanceSquared(candidate) < spacingSquared);
                        if (tooClose) {
                            continue;
                        }
                        candidates.add(new CandidateNode(candidate, insideClaim, horizontalDistanceSquared));
                    }
                }
            }
        }
        return candidates;
    }

    private boolean isUsableCandidateBlock(Location candidate, Location guardianLocation) {
        if (candidate == null || candidate.getWorld() == null || guardianLocation == null || !sameWorld(candidate, guardianLocation)) {
            return false;
        }
        Block feet = candidate.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);
        if (!feet.isPassable() || !head.isPassable()) {
            return false;
        }
        if (ground.isPassable() || ground.isLiquid()) {
            return false;
        }
        return candidate.distanceSquared(guardianLocation) >= Math.pow(settings.spawnSettings().minDistanceFromGuardian(), 2);
    }

    private List<SpawnFront> resolveSpawnFronts(List<CandidateNode> candidates, Location center) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        CityDefenseSettings.SpawnSettings spawnSettings = settings.spawnSettings();
        int desired = Math.min(candidates.size(), random.nextInt(spawnSettings.frontCountMin(), spawnSettings.frontCountMax() + 1));
        List<CandidateNode> inside = candidates.stream().filter(CandidateNode::insideClaim).toList();
        List<CandidateNode> outside = candidates.stream().filter(node -> !node.insideClaim()).toList();
        List<SpawnFront> fronts = new ArrayList<>();
        int attempts = Math.max(24, desired * 8);
        for (int attempt = 0; attempt < attempts && fronts.size() < desired; attempt++) {
            List<CandidateNode> pool = pickFrontPool(inside, outside, random);
            if (pool.isEmpty()) {
                pool = candidates;
            }
            CandidateNode candidate = pool.get(random.nextInt(pool.size()));
            boolean tooClose = fronts.stream().anyMatch(front -> front.anchor().distanceSquared(candidate.location()) < Math.pow(Math.max(4, spawnSettings.frontJitterRadius() * 2), 2));
            if (tooClose) {
                continue;
            }
            double distanceFactor = Math.max(1.0D, Math.sqrt(candidate.distanceSquared()) / Math.max(1.0D, spawnSettings.maxDistanceFromGuardian()));
            double weight = distanceFactor * (candidate.insideClaim() ? 0.9D : 1.2D);
            fronts.add(new SpawnFront(fronts.size(), candidate.location().clone(), candidate.insideClaim(), weight));
        }
        if (fronts.isEmpty()) {
            CandidateNode fallback = candidates.stream()
                    .max(java.util.Comparator.comparingDouble(CandidateNode::distanceSquared))
                    .orElse(candidates.get(0));
            fronts.add(new SpawnFront(0, fallback.location().clone(), fallback.insideClaim(), 1.0D));
        }
        return fronts;
    }

    private static List<CandidateNode> pickFrontPool(List<CandidateNode> inside, List<CandidateNode> outside, ThreadLocalRandom random) {
        if (!inside.isEmpty() && !outside.isEmpty()) {
            return random.nextDouble() < 0.65D ? outside : inside;
        }
        return outside.isEmpty() ? inside : outside;
    }

    private Location resolveSpawnLocation(Spawn spawn) {
        if (spawn == null) {
            return null;
        }
        Position position = spawn.getPosition();
        if (position == null || position.getWorld() == null) {
            return null;
        }
        org.bukkit.World world = Bukkit.getWorld(position.getWorld().getName());
        if (world == null) {
            return null;
        }
        return new Location(world, position.getX(), position.getY(), position.getZ(), position.getYaw(), position.getPitch());
    }

    private CityDefenseSessionSnapshot toSnapshot(SessionState session) {
        long remaining = Math.max(0L, session.deadlineAt.getEpochSecond() - Instant.now().getEpochSecond());
        BossEncounterState encounter = session.bossEncounter;
        return new CityDefenseSessionSnapshot(
                session.sessionId,
                session.townId,
                session.townName,
                session.tier,
                Math.max(0, session.currentWave),
                session.totalWaves,
                plainDefensePhase(session.phase),
                session.activeMobIds.size(),
                session.guardianHp,
                session.maxGuardianHp,
                safeText(encounter == null ? null : encounter.bossName(), "-"),
                safeText(encounter == null ? null : encounter.phaseLabel(), "-"),
                currentBossHp(session),
                currentBossHpMax(session),
                remaining,
                session.spec.maxDurationSeconds(),
                session.guardianLocation.clone()
        );
    }

    private double currentBossHp(SessionState session) {
        BossEncounterState encounter = session.bossEncounter;
        if (encounter == null) {
            return 0.0D;
        }
        Entity entity = Bukkit.getEntity(encounter.bossUuid());
        return entity instanceof LivingEntity living && living.isValid() ? living.getHealth() : 0.0D;
    }

    private double currentBossHpMax(SessionState session) {
        BossEncounterState encounter = session.bossEncounter;
        if (encounter == null) {
            return 0.0D;
        }
        Entity entity = Bukkit.getEntity(encounter.bossUuid());
        if (!(entity instanceof LivingEntity living) || !living.isValid()) {
            return 0.0D;
        }
        return living.getAttribute(Attribute.MAX_HEALTH) == null ? living.getMaxHealth() : living.getAttribute(Attribute.MAX_HEALTH).getValue();
    }

    private void announceTown(int townId, Component message) {
        for (UUID memberId : huskTownsApiHook.getTownMembers(townId).keySet()) {
            Player online = Bukkit.getPlayer(memberId);
            if (online != null && online.isOnline()) {
                online.sendMessage(message);
            }
        }
    }

    private Optional<UUID> resolveRewardActor(Town town) {
        if (town == null) {
            return Optional.empty();
        }
        for (UUID userId : town.getMembers().keySet()) {
            if (userId != null && Bukkit.getPlayer(userId) != null) {
                return Optional.of(userId);
            }
        }
        return Optional.empty();
    }

    private String describeEndReason(String reason, SessionState session) {
        if (reason == null || reason.isBlank()) {
            return "difesa fallita";
        }
        return switch (reason) {
            case "timeout" -> "tempo massimo del grado esaurito (" + formatDurationSeconds(session.spec.maxDurationSeconds()) + ")";
            case "guardian-hp-zero", "guardian-died", "guardian-dead" -> "il Guardiano della citta e stato sconfitto";
            case "aborted_plugin_disable", "aborted_on_restart" -> "invasione interrotta dal sistema";
            case "staff-force-stop", "player-stop" -> "invasione interrotta";
            default -> safeText(reason, "difesa fallita");
        };
    }

    private List<UiChatStyle.DetailLine> buildVictoryDetails(
            SessionState session,
            double rewardMoneyAmount,
            Map<Material, Integer> effectiveRewardItems
    ) {
        List<UiChatStyle.DetailLine> details = new ArrayList<>();
        details.add(UiChatStyle.detail(UiChatStyle.DetailType.CONTEXT, "Citta': " + session.townName));
        details.add(UiChatStyle.detail(UiChatStyle.DetailType.REWARD, "+" + format(session.spec.rewardXp()) + " XP citta"));
        details.add(UiChatStyle.detail(UiChatStyle.DetailType.REWARD, "+" + formatMoney(BigDecimal.valueOf(rewardMoneyAmount)) + " Monete citta"));
        if (effectiveRewardItems != null && !effectiveRewardItems.isEmpty()) {
            effectiveRewardItems.entrySet().stream()
                    .filter(entry -> entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0)
                    .sorted(Comparator.comparing(entry -> entry.getKey().name(), String.CASE_INSENSITIVE_ORDER))
                    .limit(5)
                    .forEach(entry -> details.add(UiChatStyle.detail(
                            UiChatStyle.DetailType.REWARD,
                            entry.getValue() + "x " + prettyMaterial(entry.getKey())
                    )));
            if (effectiveRewardItems.size() > 5) {
                details.add(UiChatStyle.detail(UiChatStyle.DetailType.NOTE,
                        "+" + (effectiveRewardItems.size() - 5) + " altri materiali nel vault"));
            }
        }
        details.add(UiChatStyle.detail(UiChatStyle.DetailType.NOTE, "Il tesoro della citta ha ricevuto i premi."));
        return List.copyOf(details);
    }

    private List<UiChatStyle.DetailLine> buildFirstClearDetails(SessionState session) {
        List<UiChatStyle.DetailLine> details = new ArrayList<>();
        details.add(UiChatStyle.detail(UiChatStyle.DetailType.CONTEXT, "Citta': " + session.townName));
        session.spec.firstClearUniqueRewards().stream()
                .filter(spec -> spec != null && spec.itemId() != null && !spec.itemId().isBlank() && spec.amount() > 0)
                .sorted(Comparator.comparing(it.patric.cittaexp.itemsadder.ItemsAdderRewardResolver.RewardSpec::itemId, String.CASE_INSENSITIVE_ORDER))
                .forEach(spec -> details.add(UiChatStyle.detail(
                        UiChatStyle.DetailType.SPECIAL,
                        spec.amount() + "x " + spec.itemId()
                )));
        details.add(UiChatStyle.detail(UiChatStyle.DetailType.NOTE, "Controlla il vault della citta per ritirare la ricompensa unica."));
        return List.copyOf(details);
    }

    private static String formatDurationSeconds(long seconds) {
        long total = Math.max(0L, seconds);
        long hours = total / 3600L;
        long minutes = (total % 3600L) / 60L;
        long secs = total % 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%dh %02dm %02ds", hours, minutes, secs);
        }
        if (minutes > 0L) {
            return String.format(Locale.ROOT, "%dm %02ds", minutes, secs);
        }
        return secs + "s";
    }

    private static String formatMoney(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return "0";
        }
        BigDecimal normalized = amount.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0).toPlainString() : normalized.toPlainString();
    }

    private static String format(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String prettyMaterial(Material material) {
        if (material == null) {
            return "Materiale";
        }
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static String plainDefensePhase(Phase phase) {
        return switch (phase) {
            case PREP -> "Preparazione";
            case INTER_WAVE -> "Pausa tra ondate";
            case COMBAT -> "Invasione in corso";
        };
    }

    private static String prettyEntity(EntityType type) {
        if (type == null) {
            return "mob";
        }
        String raw = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = raw.split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private static String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static String normalizeError(Throwable throwable) {
        if (throwable == null) {
            return DEFAULT_ERROR;
        }
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return safeText(cause.getMessage(), cause.getClass().getSimpleName());
    }

    private static String serializeItems(Map<Material, Integer> items) {
        if (items == null || items.isEmpty()) {
            return "-";
        }
        return items.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0)
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(Enum::name)))
                .map(entry -> entry.getKey().name() + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
    }

    private static String serializeUniqueRewards(List<ItemsAdderRewardResolver.RewardSpec> rewards) {
        if (rewards == null || rewards.isEmpty()) {
            return "-";
        }
        return rewards.stream()
                .filter(reward -> reward != null && reward.itemId() != null && !reward.itemId().isBlank() && reward.amount() > 0)
                .map(reward -> reward.itemId() + "=" + reward.amount())
                .collect(Collectors.joining(";"));
    }

    private final class SessionState {
        private final String sessionId;
        private final int townId;
        private final String townName;
        private final CityDefenseTier tier;
        private final CityDefenseLevelSpec spec;
        private final boolean completedBefore;
        private final BigDecimal effectiveRewardMoney;
        private final Map<Material, Integer> effectiveRewardItems;
        private final UUID guardianId;
        private final Location guardianLocation;
        private final List<CandidateNode> candidateNodes;
        private final List<SpawnFront> fronts;
        private final Instant startedAt;
        private final Instant deadlineAt;
        private final int totalWaves;
        private final double maxGuardianHp;
        private final Set<UUID> activeMobIds;
        private final ConcurrentMap<UUID, MobRuntimeState> mobStates;
        private final DefenseSessionBarState sessionBar;

        private volatile double guardianHp;
        private volatile int currentWave;
        private volatile Phase phase;
        private volatile Instant phaseEndsAt;
        private volatile BossEncounterState bossEncounter;
        private volatile String lastCountdownCue;
        private volatile UUID guardianHologramId;

        private SessionState(
                String sessionId,
                int townId,
                String townName,
                CityDefenseTier tier,
                CityDefenseLevelSpec spec,
                boolean completedBefore,
                UUID guardianId,
                Location guardianLocation,
                List<CandidateNode> candidateNodes,
                List<SpawnFront> fronts,
                Instant startedAt,
                Instant deadlineAt,
                Instant prepEndAt
        ) {
            this.sessionId = sessionId;
            this.townId = townId;
            this.townName = townName;
            this.tier = tier;
            this.spec = spec;
            this.completedBefore = completedBefore;
            CityDefenseRewardPolicy.RewardOutcome rewardOutcome = effectiveRewardOutcome(spec, completedBefore);
            this.effectiveRewardMoney = rewardOutcome.rewardMoney();
            this.effectiveRewardItems = rewardOutcome.rewardItems();
            this.guardianId = guardianId;
            this.guardianLocation = guardianLocation;
            this.candidateNodes = candidateNodes;
            this.fronts = fronts;
            this.startedAt = startedAt;
            this.deadlineAt = deadlineAt;
            this.totalWaves = Math.max(1, spec.waves());
            this.maxGuardianHp = Math.max(1.0D, spec.guardianHp());
            this.guardianHp = this.maxGuardianHp;
            this.currentWave = 0;
            this.phase = Phase.PREP;
            this.phaseEndsAt = prepEndAt;
            this.activeMobIds = ConcurrentHashMap.newKeySet();
            this.mobStates = new ConcurrentHashMap<>();
            this.sessionBar = new DefenseSessionBarState();
            this.bossEncounter = null;
            this.lastCountdownCue = null;
            this.guardianHologramId = null;
        }
    }

    private static final class MobRuntimeState {
        private final int frontIndex;
        private volatile Location lastSampleLocation;
        private volatile Instant lastSampleAt;
        private volatile Instant lastProgressAt;
        private volatile Instant lastBreakAt;
        private volatile int breaksUsed;
        private volatile int repositionAttempts;

        private MobRuntimeState(int frontIndex, Location spawnLocation, Instant now) {
            this.frontIndex = frontIndex;
            this.lastSampleLocation = spawnLocation == null ? null : spawnLocation.clone();
            this.lastSampleAt = now;
            this.lastProgressAt = now;
            this.lastBreakAt = Instant.EPOCH;
            this.breaksUsed = 0;
            this.repositionAttempts = 0;
        }
    }
}
