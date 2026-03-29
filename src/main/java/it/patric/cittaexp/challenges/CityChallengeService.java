package it.patric.cittaexp.challenges;

import com.destroystokyo.paper.loottable.LootableInventory;
import it.patric.cittaexp.economy.EconomyBalanceCategory;
import it.patric.cittaexp.economy.EconomyValueService;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import it.patric.cittaexp.levels.PrincipalStage;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.text.MiniMessageHelper;
import it.patric.cittaexp.text.TimedTitleHelper;
import it.patric.cittaexp.utils.PluginConfigUtils;
import it.patric.cittaexp.vault.CityItemVaultService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.william278.husktowns.town.Member;
import net.william278.husktowns.town.Town;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

public final class CityChallengeService {

    public record RegenerateResult(
            boolean success,
            int activeCount,
            int dailyCount,
            int weeklyCount,
            int monthlyCount,
            int seasonalCount,
            int eventsCount,
            String error
    ) {
    }

    public record DailySprintForceResult(
            boolean success,
            String reason,
            ChallengeMode mode,
            String instanceId
    ) {
    }

    public record QuestCapacityProfile(
            int dailyStandardCount,
            int dailyRaceCount,
            int weeklyVisibleSlots,
            int monthlyVisibleSlots,
            int seasonalVisibleSlots,
            int eventsVisibleSlots
    ) {
    }

    public record MayorGovernanceSnapshot(
            String seasonKey,
            String seasonLabel,
            Integer vetoCategory,
            int rerollUsed,
            int rerollMax,
            int weeklyCompletedCount,
            boolean milestone1Done,
            boolean milestone2Done,
            boolean milestone3Done,
            boolean seasonalFinalDone
    ) {
    }

    public record WeeklyFirstCompletionStatus(
            boolean enabled,
            boolean claimed
    ) {
    }

    public record Diagnostics(
            boolean enabled,
            String dailyCycleKey,
            String weeklyCycleKey,
            String monthlyCycleKey,
            String storeMode,
            boolean storeMySqlUp,
            int storeOutboxPending,
            int storeOutboxFailed,
            String storeLastSyncOk,
            String storeLastSyncError,
            long storeReplayLagSeconds,
            int activeCount,
            int raceCount,
            int dailyCount,
            int weeklyCount,
            int monthlyCount,
            int seasonalCount,
            int eventsCount,
            int storyModeBaseCount,
            int storyModeEliteCount,
            int rewardLedgerSize,
            Map<String, Integer> candidateCounts,
            Map<String, String> generationBlockedReasons,
            Map<String, Long> droppedByReason,
            Map<String, Long> boardSignalHits,
            Map<String, Long> boardHardRuleHits,
            String boardLastFallbackReason,
            long throttleHits,
            long capHits,
            long afkHits,
            long suspicionWarningHits,
            long suspicionClampHits,
            long suspicionReviewFlags,
            long dedupeDrops,
            long structureLootValidHits,
            long explorationValidHits,
            int placedRegistrySize,
            int streakActiveCities,
            long streakResets,
            int streakMax,
            boolean eventWindowActive,
            int eventLeaderboardSize,
            int eventRewardGrants,
            long fairnessFallbackHits,
            long personalRewardGrantCount,
            long personalRewardSkipCount,
            long dailyFirstRewardGrantCount,
            long dailyFirstRewardSkipCount,
            long weeklyFirstRewardGrantCount,
            long weeklyFirstRewardSkipCount,
            double completionRate,
            double abandonmentRate,
            double antiAbuseHitRate,
            double winnerDistribution,
            double duplicateGrantRate,
            double contributionSpread,
            double topCarryRatio,
            double rerollUsageRate,
            double vetoUsageRate,
            String lastError,
            String lastAntiAbuseError,
            String lastEventFinalizeError
    ) {
    }

    private record ModeDefinitionStats(
            int proceduralRawCount,
            int proceduralAllowedCount,
            int staticAllowedCount,
            int fallbackCount,
            int finalCount,
            String reason
    ) {
    }

    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT);
    private static final DateTimeFormatter WEEK_KEY = DateTimeFormatter.ofPattern("YYYY-'W'ww", Locale.ROOT);
    private static final String M79_CUTOVER_KEY = "challenges.cutover.m79";
    private static final String M79_CUTOVER_VERSION = "m79-v1";
    private static final String M11_CUTOVER_KEY = "challenges.cutover.m11";
    private static final String M11_CUTOVER_VERSION = "m11-v1";
    private static final String GENERATION_SEED_PREFIX = "challenge-generation-seed:";
    private static final LocalTime DAILY_SPRINT_A = LocalTime.of(18, 30);
    private static final LocalTime DAILY_SPRINT_B = LocalTime.of(21, 30);
    private static final LocalTime WEEKLY_CLASH_AT = LocalTime.of(21, 0);
    private static final LocalTime MONTHLY_CROWN_AT = LocalTime.of(18, 0);
    private static final int DAILY_SPRINT_TIMEOUT_MINUTES = 45;
    private static final int WEEKLY_CLASH_TIMEOUT_MINUTES = 90;
    private static final int MONTHLY_CROWN_TIMEOUT_MINUTES = 120;
    private static final int DAILY_REVEAL_MINUTES = 10;
    private static final int DAILY_STANDARD_BOARD_COUNT = 8;
    private static final int DAILY_RACE_BOARD_COUNT = 2;
    private static final int WEEKLY_MONTHLY_BATCH_BASE = 2;
    private static final int WEEKLY_MONTHLY_BATCH_STEP_LEVELS = 10;
    private static final int WEEKLY_MONTHLY_BATCH_INCREMENT = 2;
    private static final int WEEKLY_MONTHLY_BATCH_MAX = 18;
    private static final int MONTHLY_LEDGER_GRAND_COUNT = 1;
    private static final int MONTHLY_LEDGER_MYSTERY_COUNT = 1;
    private static final double SEASONAL_SECRET_XP_MULTIPLIER = 2.50D;
    private static final double SEASONAL_SECRET_MONEY_MULTIPLIER = 2.50D;
    private static final int SEASONAL_SECRET_ITEM_MULTIPLIER = 3;
    private static final double SEASONAL_XP_MULTIPLIER = 2.10D;
    private static final double WEEKLY_MONEY_MULTIPLIER = 1.60D;
    private static final double MONTHLY_MONEY_MULTIPLIER = 1.75D;
    private static final double SEASONAL_MONEY_MULTIPLIER = 2.00D;
    private static final double WEEKLY_MATERIAL_MULTIPLIER = 0.75D;
    private static final double MONTHLY_MATERIAL_MULTIPLIER = 0.70D;
    private static final double SEASONAL_MATERIAL_MULTIPLIER = 0.65D;
    private static final List<Integer> SEASON_CODEX_MILESTONE_THRESHOLDS = List.of(20, 40, 70, 100, 140, 180);
    private static final double SEASONAL_VARIETY_FAMILY_DECAY = 0.42D;
    private static final double SEASONAL_VARIETY_OBJECTIVE_DECAY = 0.68D;
    private static final double SEASONAL_VARIETY_SLOT_DECAY = 0.84D;
    private static final double SEASONAL_VARIETY_ID_DECAY = 0.12D;
    private static final double SEASONAL_VARIETY_MIN_FACTOR = 0.08D;
    private static final int SEASONAL_FAMILY_HARD_CAP = 8;
    private static final int SEASONAL_OBJECTIVE_HARD_CAP = 2;
    public static final String SECRET_QUEST_CHALLENGE_ID = "proc_secret_quest";
    public static final String SECRET_TRIGGER_MINE_BLOCK = "mine_block";
    public static final String SECRET_TRIGGER_VISIT_LOCATION = "visit_location";
    public static final String SECRET_TRIGGER_KILL_MOB = "kill_mob";
    public static final String SECRET_TRIGGER_ARCHAEOLOGY = "archaeology";
    public static final String SECRET_TRIGGER_TRIAL_VAULT = "trial_vault";
    public static final String SECRET_TRIGGER_RAID = "raid";
    public static final String SECRET_TRIGGER_DIMENSION_TRAVEL = "dimension_travel";
    public static final String SECRET_TRIGGER_STRUCTURE_DISCOVERY = "structure_discovery";
    private static final Set<ChallengeObjectiveType> TEAM_PHASE_GATHER_OBJECTIVES = Set.of(
            ChallengeObjectiveType.RESOURCE_CONTRIBUTION,
            ChallengeObjectiveType.BLOCK_MINE
    );
    private static final Set<ChallengeObjectiveType> TEAM_PHASE_BUILD_OBJECTIVES = Set.of(
            ChallengeObjectiveType.PLACE_BLOCK,
            ChallengeObjectiveType.CONSTRUCTION
    );
    private static final Set<ChallengeObjectiveType> TEAM_PHASE_BOSS_OBJECTIVES = Set.of(
            ChallengeObjectiveType.BOSS_KILL
    );
    private static final Set<String> BLOCK_MINE_X3 = Set.of(
            "ANCIENT_DEBRIS",
            "DIAMOND_ORE",
            "DEEPSLATE_DIAMOND_ORE",
            "EMERALD_ORE",
            "DEEPSLATE_EMERALD_ORE"
    );
    private static final Set<String> BLOCK_MINE_X2 = Set.of(
            "GOLD_ORE",
            "DEEPSLATE_GOLD_ORE",
            "NETHER_GOLD_ORE",
            "REDSTONE_ORE",
            "DEEPSLATE_REDSTONE_ORE",
            "LAPIS_ORE",
            "DEEPSLATE_LAPIS_ORE"
    );
    private static final Set<String> RESOURCE_X3 = Set.of(
            "NETHER_STAR",
            "ANCIENT_DEBRIS",
            "NETHERITE_SCRAP",
            "NETHERITE_INGOT",
            "NETHERITE_BLOCK",
            "DIAMOND",
            "DIAMOND_BLOCK",
            "EMERALD",
            "EMERALD_BLOCK"
    );
    private static final Set<String> RESOURCE_X2 = Set.of(
            "GOLD_INGOT",
            "GOLD_BLOCK",
            "IRON_INGOT",
            "IRON_BLOCK",
            "REDSTONE",
            "REDSTONE_BLOCK",
            "LAPIS_LAZULI",
            "LAPIS_BLOCK",
            "COPPER_INGOT",
            "COPPER_BLOCK",
            "QUARTZ",
            "QUARTZ_BLOCK"
    );
    private static final Set<String> ITEM_CRAFT_X20 = Set.of(
            "BEACON"
    );
    private static final Set<String> ITEM_CRAFT_X5 = Set.of(
            "DIAMOND_HELMET",
            "DIAMOND_CHESTPLATE",
            "DIAMOND_LEGGINGS",
            "DIAMOND_BOOTS",
            "NETHERITE_HELMET",
            "NETHERITE_CHESTPLATE",
            "NETHERITE_LEGGINGS",
            "NETHERITE_BOOTS"
    );
    private static final Set<String> ITEM_CRAFT_X2 = Set.of(
            "PISTON",
            "STICKY_PISTON",
            "REPEATER",
            "COMPARATOR",
            "OBSERVER",
            "HOPPER"
    );
    private static final String TARGET_FORMULA_VERSION = BoardTargetFormulaService.FORMULA_VERSION;

    private final Plugin plugin;
    private final HuskTownsApiHook huskTownsApiHook;
    private final CityLevelService cityLevelService;
    private final CityItemVaultService cityItemVaultService;
    private final PluginConfigUtils configUtils;
    private final ChallengeStore store;
    private final CityChallengeSettings settings;
    private final EconomyValueService economyValueService;
    private final PlayerObjectiveCheckpointStore checkpointStore;
    private final PlacedBlockTracker placedBlockTracker;
    private final ObjectiveDedupeStore objectiveDedupeStore;
    private final SuspicionScoreStore suspicionScoreStore;
    private final PlayerContributionCapStore playerContributionCapStore;
    private final TownDailyCategoryCapStore townDailyCategoryCapStore;
    private final PlayerActivityTracker playerActivityTracker;
    private final ChallengeAntiAbuseService antiAbuseService;
    private final StructureLootDetectionService structureLootDetectionService;
    private final ExplorationDetectionService explorationDetectionService;
    private final ChallengeCommandTemplateEngine templateEngine;
    private final ChallengeTargetScalingService targetScalingService;
    private final BoardTargetFormulaService boardTargetFormulaService;
    private final ChallengePersonalRewardService personalRewardService;
    private final ChallengeCompetitiveFinalizeService competitiveFinalizeService;
    private final ChallengeTaxonomyService taxonomyService;
    private final ChallengeRewardPolicyService rewardPolicyService;
    private final ProceduralChallengeGenerator proceduralGenerator;
    private final ChallengeSignatureService signatureService;
    private final ChallengeContextResolver challengeContextResolver;
    private final CityChallengeStreakService streakService;
    private final MonthlyEventService monthlyEventService;
    private final ChallengeTextCatalog textCatalog;
    private final ChallengeProgressBossBarService progressBossBarService;
    private final CityAtlasService atlasService;
    private final CityStoryModeService storyModeService;
    private final BoardSignalResolver boardSignalResolver;
    private final BoardHardRuleEngine boardHardRuleEngine;
    private final ScheduledExecutorService ioExecutor;

    private final Map<String, ChallengeDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, ChallengeRewardBundle> rewardBundles = new ConcurrentHashMap<>();
    private final Map<String, ChallengeInstance> instances = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, TownChallengeProgress>> progressByInstance = new ConcurrentHashMap<>();
    private final Map<String, Integer> dailyTargetByBucket = new ConcurrentHashMap<>();
    private final Map<String, Long> templateDroppedByReason = new ConcurrentHashMap<>();
    private final Map<String, Long> runtimeDroppedByReason = new ConcurrentHashMap<>();
    private final Map<String, Long> boardSignalHits = new ConcurrentHashMap<>();
    private final Map<String, Long> boardHardRuleHits = new ConcurrentHashMap<>();
    private final Map<ChallengeMode, ModeDefinitionStats> modeDefinitionStats = new ConcurrentHashMap<>();
    private final Map<String, CityChallengeRepository.GovernanceState> governanceByTownSeason = new ConcurrentHashMap<>();
    private final Map<String, CityChallengeRepository.MilestoneState> milestonesByTownSeason = new ConcurrentHashMap<>();
    private final List<CityChallengeRepository.SelectionHistoryEntry> selectionHistory = java.util.Collections.synchronizedList(new ArrayList<>());
    private final java.util.Set<String> rewardLedgerKeys = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<ChallengeCycleType, CityChallengeRepository.CycleState> cycleState = new ConcurrentHashMap<>();

    private volatile String lastError = "-";
    private volatile String currentDailyKey = "-";
    private volatile String currentWeeklyKey = "-";
    private volatile String currentMonthlyKey = "-";
    private volatile String currentSeasonalKey = "-";
    private volatile String previousWeeklyKey = "-";
    private volatile int samplerTaskId = -1;
    private volatile long lastPlacedPruneEpochSecond = 0L;
    private volatile String lastEventFinalizeError = "-";
    private volatile int lastEventLeaderboardSize = 0;
    private volatile int eventRewardGrantCount = 0;
    private volatile long fairnessFallbackHits = 0L;
    private volatile long personalRewardGrantCount = 0L;
    private volatile long personalRewardSkipCount = 0L;
    private volatile long dailyFirstRewardGrantCount = 0L;
    private volatile long dailyFirstRewardSkipCount = 0L;
    private volatile long weeklyFirstRewardGrantCount = 0L;
    private volatile long weeklyFirstRewardSkipCount = 0L;
    private volatile long dedupeDrops = 0L;
    private volatile long structureLootValidHits = 0L;
    private volatile long explorationValidHits = 0L;
    private volatile long acceptedObjectiveHits = 0L;
    private volatile long deniedObjectiveHits = 0L;
    private volatile long duplicateGrantAttempts = 0L;
    private volatile int currentOnlineSnapshot = 0;
    private volatile long lastAtlasSealSyncEpochSecond = 0L;
    private volatile String lastBoardFallbackReason = "-";
    private volatile String regenerateSalt = "";
    private volatile ScheduledFuture<?> dailySprint1830Task;
    private volatile ScheduledFuture<?> dailySprint2130Task;

    private final Map<String, Long> cycleXpGrantedScaled = new ConcurrentHashMap<>();
    private final Map<String, Long> cycleExcellenceXpGrantedScaled = new ConcurrentHashMap<>();
    private final Map<String, Integer> cycleRewardsGranted = new ConcurrentHashMap<>();

    public CityChallengeService(
            Plugin plugin,
            HuskTownsApiHook huskTownsApiHook,
            CityLevelService cityLevelService,
            CityItemVaultService cityItemVaultService,
            ChallengeStore store,
            CityChallengeSettings settings,
            EconomyValueService economyValueService
    ) {
        this.plugin = plugin;
        this.huskTownsApiHook = huskTownsApiHook;
        this.cityLevelService = cityLevelService;
        this.cityItemVaultService = cityItemVaultService;
        this.configUtils = new PluginConfigUtils(plugin);
        this.store = store;
        this.settings = settings;
        this.economyValueService = economyValueService;
        this.checkpointStore = new PlayerObjectiveCheckpointStore(store);
        this.placedBlockTracker = new PlacedBlockTracker(store);
        this.objectiveDedupeStore = new ObjectiveDedupeStore(store);
        this.suspicionScoreStore = new SuspicionScoreStore(store);
        this.playerContributionCapStore = new PlayerContributionCapStore(store);
        this.townDailyCategoryCapStore = new TownDailyCategoryCapStore(store);
        this.playerActivityTracker = new PlayerActivityTracker();
        this.antiAbuseService = new ChallengeAntiAbuseService(
                settings.antiAbuse(),
                placedBlockTracker,
                playerContributionCapStore,
                townDailyCategoryCapStore,
                playerActivityTracker,
                suspicionScoreStore
        );
        this.structureLootDetectionService = new StructureLootDetectionService(settings.antiAbuse().structureLootTableAllowPrefixes());
        this.explorationDetectionService = new ExplorationDetectionService(settings.antiAbuse().explorationStructureAllowlist());
        this.templateEngine = new ChallengeCommandTemplateEngine(settings);
        this.targetScalingService = new ChallengeTargetScalingService(settings);
        this.boardTargetFormulaService = new BoardTargetFormulaService(settings);
        this.personalRewardService = new ChallengePersonalRewardService(settings);
        this.competitiveFinalizeService = new ChallengeCompetitiveFinalizeService();
        this.taxonomyService = new ChallengeTaxonomyService(settings);
        this.rewardPolicyService = new ChallengeRewardPolicyService(plugin, this.taxonomyService);
        this.proceduralGenerator = new ProceduralChallengeGenerator(plugin, this.taxonomyService);
        this.signatureService = new ChallengeSignatureService(
                proceduralGenerator.signatureTownDays(),
                proceduralGenerator.signatureGlobalDays()
        );
        this.challengeContextResolver = new ChallengeContextResolver();
        this.streakService = new CityChallengeStreakService(store, settings.streak());
        this.monthlyEventService = new MonthlyEventService();
        this.textCatalog = ChallengeTextCatalog.load(plugin);
        this.progressBossBarService = new ChallengeProgressBossBarService(plugin, this.textCatalog);
        CityAtlasSettings atlasSettings = CityAtlasSettings.load(plugin);
        this.atlasService = new CityAtlasService(
                plugin,
                huskTownsApiHook,
                cityLevelService,
                cityItemVaultService,
                store,
                atlasSettings,
                economyValueService
        );
        this.storyModeService = new CityStoryModeService(
                plugin,
                huskTownsApiHook,
                cityLevelService,
                cityItemVaultService,
                store,
                this.atlasService,
                atlasSettings,
                economyValueService
        );
        this.boardSignalResolver = new BoardSignalResolver(settings, cityLevelService, atlasService);
        this.boardHardRuleEngine = new BoardHardRuleEngine(settings, taxonomyService);
        this.ioExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "cittaexp-challenges-io");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (!settings.enabled()) {
            plugin.getLogger().info("[challenges] disabilitato da configurazione.");
            return;
        }
        ioExecutor.execute(this::bootstrap);
        long lifecycleTick = Math.max(5L, settings.schedulerTickSeconds());
        ioExecutor.scheduleAtFixedRate(this::safeLifecycleTick, lifecycleTick, lifecycleTick, TimeUnit.SECONDS);

        long samplerTicks = Math.max(1L, settings.playtimeTickMinutes()) * 60L * 20L;
        samplerTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::sampleOnlinePlayersSafe, samplerTicks, samplerTicks);
    }

    public void stop() {
        cancelDailySprintTasks();
        if (samplerTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(samplerTaskId);
            samplerTaskId = -1;
        }
        progressBossBarService.clear();
        ioExecutor.shutdown();
        try {
            ioExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public Diagnostics diagnostics() {
        int active = (int) instances.values().stream().filter(ChallengeInstance::active).count();
        int race = (int) instances.values().stream().filter(instance -> instance.active() && instance.mode().race()).count();
        int daily = (int) instances.values().stream().filter(instance -> instance.active() && instance.cycleType() == ChallengeCycleType.DAILY).count();
        int weekly = (int) instances.values().stream().filter(instance -> instance.active() && instance.cycleType() == ChallengeCycleType.WEEKLY).count();
        int monthly = (int) instances.values().stream().filter(instance -> instance.active() && instance.cycleType() == ChallengeCycleType.MONTHLY).count();
        int seasonal = (int) instances.values().stream().filter(instance -> instance.active() && instance.cycleType() == ChallengeCycleType.SEASONAL).count();
        int events = (int) instances.values().stream().filter(instance -> instance.active() && (instance.mode().dailyRace() || instance.mode().m8Race())).count();
        ChallengeStore.Diagnostics storeDiagnostics = store.diagnostics();
        ChallengeAntiAbuseService.Diagnostics anti = antiAbuseService.diagnostics();
        CityChallengeStreakService.Diagnostics streak = streakService.diagnostics();
        CityStoryModeService.StoryModeDiagnostics storyDiagnostics = storyModeService.diagnostics(0);
        boolean eventActive = activeEventInstance() != null;
        Map<String, Long> droppedByReason = new HashMap<>(anti.droppedByReason());
        templateDroppedByReason.forEach((key, value) -> addLong(droppedByReason, key, value));
        runtimeDroppedByReason.forEach((key, value) -> addLong(droppedByReason, key, value));
        Map<String, Long> signalStats = new HashMap<>(boardSignalHits);
        Map<String, Long> hardRuleStats = new HashMap<>(boardHardRuleHits);
        Map<String, Integer> candidateCounts = questCandidateCountSummary();
        Map<String, String> generationBlockedReasons = questGenerationReasonSummary();
        KpiSnapshot kpi = kpiSnapshot();
        return new Diagnostics(
                settings.enabled(),
                currentDailyKey,
                currentWeeklyKey,
                currentMonthlyKey,
                storeDiagnostics.mode(),
                storeDiagnostics.mysqlUp(),
                storeDiagnostics.outboxPending(),
                storeDiagnostics.outboxFailed(),
                storeDiagnostics.lastSyncOk(),
                storeDiagnostics.lastSyncError(),
                storeDiagnostics.replayLagSeconds(),
                active,
                race,
                daily,
                weekly,
                monthly,
                seasonal,
                events,
                storyDiagnostics.baseChapterCount(),
                storyDiagnostics.eliteChapterCount(),
                rewardLedgerKeys.size(),
                Map.copyOf(candidateCounts),
                Map.copyOf(generationBlockedReasons),
                Map.copyOf(droppedByReason),
                Map.copyOf(signalStats),
                Map.copyOf(hardRuleStats),
                safeText(lastBoardFallbackReason, "-"),
                anti.throttleHits(),
                anti.capHits(),
                anti.afkHits(),
                anti.suspicionWarningHits(),
                anti.suspicionClampHits(),
                anti.suspicionReviewFlags(),
                Math.max(0L, dedupeDrops),
                Math.max(0L, structureLootValidHits),
                Math.max(0L, explorationValidHits),
                anti.placedRegistrySize(),
                streak.activeCities(),
                streak.resets(),
                streak.maxStreak(),
                eventActive,
                Math.max(0, lastEventLeaderboardSize),
                Math.max(0, eventRewardGrantCount),
                Math.max(0L, fairnessFallbackHits),
                Math.max(0L, personalRewardGrantCount),
                Math.max(0L, personalRewardSkipCount),
                Math.max(0L, dailyFirstRewardGrantCount),
                Math.max(0L, dailyFirstRewardSkipCount),
                Math.max(0L, weeklyFirstRewardGrantCount),
                Math.max(0L, weeklyFirstRewardSkipCount),
                kpi.completionRate(),
                kpi.abandonmentRate(),
                kpi.antiAbuseHitRate(),
                kpi.winnerDistribution(),
                kpi.duplicateGrantRate(),
                kpi.contributionSpread(),
                kpi.topCarryRatio(),
                kpi.rerollUsageRate(),
                kpi.vetoUsageRate(),
                lastError,
                anti.lastError(),
                lastEventFinalizeError
        );
    }

    public CompletableFuture<RegenerateResult> regenerateAllNow() {
        CompletableFuture<RegenerateResult> future = new CompletableFuture<>();
        if (!settings.enabled()) {
            future.complete(new RegenerateResult(false, 0, 0, 0, 0, 0, 0, "challenges-disabled"));
            return future;
        }
        ioExecutor.execute(() -> {
            regenerateSalt = UUID.randomUUID().toString();
            try {
                store.clearRuntimeStateForRegenerate();
                instances.clear();
                progressByInstance.clear();
                rewardLedgerKeys.clear();
                templateDroppedByReason.clear();
                runtimeDroppedByReason.clear();
                boardSignalHits.clear();
                boardHardRuleHits.clear();
                modeDefinitionStats.clear();
                lastBoardFallbackReason = "-";
                fairnessFallbackHits = 0L;
                personalRewardGrantCount = 0L;
                personalRewardSkipCount = 0L;
                dailyFirstRewardGrantCount = 0L;
                dailyFirstRewardSkipCount = 0L;
                dedupeDrops = 0L;
                structureLootValidHits = 0L;
                explorationValidHits = 0L;
                acceptedObjectiveHits = 0L;
                deniedObjectiveHits = 0L;
                duplicateGrantAttempts = 0L;
                cycleXpGrantedScaled.clear();
                cycleExcellenceXpGrantedScaled.clear();
                cycleRewardsGranted.clear();
                governanceByTownSeason.clear();
                milestonesByTownSeason.clear();
                selectionHistory.clear();
                playerContributionCapStore.clearRuntime();
                townDailyCategoryCapStore.clearRuntime();
                suspicionScoreStore.clearRuntime();
                cycleState.clear();
                safeLifecycleTick();
                int active = (int) instances.values().stream().filter(ChallengeInstance::active).count();
                int daily = (int) instances.values().stream().filter(instance -> instance.active() && instance.cycleType() == ChallengeCycleType.DAILY).count();
                int weekly = (int) instances.values().stream().filter(instance -> instance.active() && instance.cycleType() == ChallengeCycleType.WEEKLY).count();
                int monthly = (int) instances.values().stream().filter(instance -> instance.active() && instance.cycleType() == ChallengeCycleType.MONTHLY).count();
                int seasonal = (int) instances.values().stream().filter(instance -> instance.active() && instance.cycleType() == ChallengeCycleType.SEASONAL).count();
                int events = (int) instances.values().stream().filter(instance -> instance.active() && (instance.mode().dailyRace() || instance.mode().m8Race())).count();
                future.complete(new RegenerateResult(true, active, daily, weekly, monthly, seasonal, events, "-"));
            } catch (Exception exception) {
                String reason = normalizeError(exception);
                lastError = reason;
                future.complete(new RegenerateResult(false, 0, 0, 0, 0, 0, 0, reason));
            } finally {
                regenerateSalt = "";
            }
        });
        return future;
    }

    public List<ChallengeSnapshot> listActiveForTown(int townId) {
        return listActiveForTown(townId, null);
    }

    public List<ChallengeSnapshot> listActiveForTownForPlayer(int townId, UUID playerId) {
        return listActiveForTown(townId, playerId);
    }

    public List<ChallengeSnapshot> listVisibleRacesForTownForPlayer(int townId, UUID playerId) {
        if (!settings.enabled() || townId <= 0) {
            return List.of();
        }
        Map<Integer, String> townNames = indexTownNames();
        Instant now = Instant.now();
        return instances.values().stream()
                .filter(instance -> instance.mode().race())
                .filter(instance -> visibleToTown(instance, townId))
                .filter(instance -> isRaceVisible(instance, now))
                .sorted(Comparator
                        .comparing((ChallengeInstance instance) -> instance.cycleStartAt() == null ? Instant.EPOCH : instance.cycleStartAt())
                        .thenComparing(ChallengeInstance::instanceId))
                .map(instance -> toSnapshot(instance, townId, playerId, townNames, now))
                .toList();
    }

    public Optional<ChallengeSnapshot> currentRaceSnapshotForTownForPlayer(int townId, UUID playerId, ChallengeMode mode) {
        if (!settings.enabled() || townId <= 0 || mode == null || !mode.race()) {
            return Optional.empty();
        }
        ensureTownQuestCoverage(townId);
        String cycleKey = currentVisibleRaceCycleKey(mode);
        if (cycleKey == null || cycleKey.isBlank()) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        Map<Integer, String> townNames = indexTownNames();
        return instances.values().stream()
                .filter(instance -> instance != null)
                .filter(instance -> instance.mode() == mode)
                .filter(instance -> cycleKey.equalsIgnoreCase(instance.cycleKey()))
                .filter(instance -> visibleToTown(instance, townId))
                .sorted(Comparator
                        .comparing((ChallengeInstance instance) -> instance.startedAt() == null ? Instant.EPOCH : instance.startedAt())
                        .thenComparing(ChallengeInstance::instanceId))
                .findFirst()
                .map(instance -> toSnapshot(instance, townId, playerId, townNames, now));
    }

    public Optional<ChallengeSnapshot> snapshotForTownPlayerByInstanceId(int townId, UUID playerId, String instanceId) {
        if (!settings.enabled() || townId <= 0 || instanceId == null || instanceId.isBlank()) {
            return Optional.empty();
        }
        ChallengeInstance instance = instances.get(instanceId);
        if (instance == null || !visibleToTown(instance, townId)) {
            return Optional.empty();
        }
        Map<Integer, String> townNames = indexTownNames();
        return Optional.of(toSnapshot(instance, townId, playerId, townNames, Instant.now()));
    }

    public Optional<ChallengeInstanceStatus> instanceStatus(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return Optional.empty();
        }
        ChallengeInstance instance = instances.get(instanceId);
        return instance == null ? Optional.empty() : Optional.of(instance.status());
    }

    public Optional<String> createStaffManagedRaceInstance(
            String eventId,
            ChallengeMode mode,
            String challengeId,
            String titleOverride,
            Instant startAt,
            Instant endAt
    ) {
        if (!settings.enabled() || eventId == null || eventId.isBlank() || mode == null || !mode.race()) {
            return Optional.empty();
        }
        String instanceId = "staff-event:" + eventId;
        ChallengeInstance existing = instances.get(instanceId);
        if (existing != null) {
            return Optional.of(existing.instanceId());
        }
        List<ChallengeDefinition> candidates = definitionsForMode(mode);
        if (challengeId != null && !challengeId.isBlank()) {
            candidates = candidates.stream()
                    .filter(definition -> definition != null && challengeId.equalsIgnoreCase(definition.id()))
                    .toList();
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        String scopeSeed = generationSeed("staff-event:" + eventId + ':' + mode.name());
        ChallengeDefinition definition = weightedPickDeterministic(candidates, 1, "staff-event:" + eventId + ':' + scopeSeed).getFirst();
        ChallengeInstance created = newActiveInstance(
                definition,
                instanceId,
                null,
                "staff:" + eventId,
                mode.cycleType(),
                "staff-event",
                mode,
                startAt,
                endAt
        );
        if (titleOverride != null && !titleOverride.isBlank()) {
            created = new ChallengeInstance(
                    created.instanceId(),
                    created.townId(),
                    created.cycleKey(),
                    created.cycleType(),
                    created.windowKey(),
                    created.challengeId(),
                    titleOverride.trim(),
                    created.objectiveType(),
                    created.category(),
                    created.baseTarget(),
                    created.target(),
                    created.excellenceTarget(),
                    created.fairnessMultiplier(),
                    created.activeContributors7d(),
                    created.sizeSnapshot(),
                    created.onlineSnapshot(),
                    created.targetFormulaVersion(),
                    created.variantType(),
                    created.focusType(),
                    created.focusKey(),
                    created.focusLabel(),
                    created.biomeKey(),
                    created.dimensionKey(),
                    created.signatureKey(),
                    created.mode(),
                    created.status(),
                    created.winnerTownId(),
                    created.winnerPlayerId(),
                    created.cycleStartAt(),
                    created.cycleEndAt(),
                    created.startedAt(),
                    created.endedAt()
            );
        }
        instances.put(created.instanceId(), created);
        store.upsertInstance(created);
        announceRaceStartedOnce(created);
        return Optional.of(created.instanceId());
    }

    public boolean forceExpireInstance(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return false;
        }
        ChallengeInstance instance = instances.get(instanceId);
        if (instance == null || instance.status() != ChallengeInstanceStatus.ACTIVE) {
            return false;
        }
        Instant now = Instant.now();
        if (instance.mode().monthlyEvent() && !instance.mode().m8Race()) {
            try {
                finalizeMonthlyEventInstance(instance);
            } catch (Exception exception) {
                lastEventFinalizeError = normalizeError(exception);
                plugin.getLogger().warning("[challenges] force-expire monthly event failed instance="
                        + instance.instanceId() + ", reason=" + lastEventFinalizeError);
            }
        }
        if (instance.mode().race()) {
            try {
                finalizeRaceInstance(instance);
            } catch (Exception exception) {
                lastEventFinalizeError = normalizeError(exception);
                plugin.getLogger().warning("[challenges] force-expire race failed instance="
                        + instance.instanceId() + ", reason=" + lastEventFinalizeError);
            }
        }
        ChallengeInstance expired = new ChallengeInstance(
                instance.instanceId(),
                instance.townId(),
                instance.cycleKey(),
                instance.cycleType(),
                instance.windowKey(),
                instance.challengeId(),
                instance.challengeName(),
                instance.objectiveType(),
                instance.category(),
                instance.baseTarget(),
                instance.target(),
                instance.excellenceTarget(),
                instance.fairnessMultiplier(),
                instance.activeContributors7d(),
                instance.sizeSnapshot(),
                instance.onlineSnapshot(),
                instance.targetFormulaVersion(),
                instance.variantType(),
                instance.focusType(),
                instance.focusKey(),
                instance.focusLabel(),
                instance.biomeKey(),
                instance.dimensionKey(),
                instance.signatureKey(),
                instance.mode(),
                ChallengeInstanceStatus.EXPIRED,
                instance.winnerTownId(),
                instance.winnerPlayerId(),
                instance.cycleStartAt(),
                now,
                instance.startedAt(),
                now
        );
        instances.put(expired.instanceId(), expired);
        store.upsertInstance(expired);
        persistCycleRecapForInstance(expired);
        return true;
    }

    private List<ChallengeSnapshot> listActiveForTown(int townId, UUID playerId) {
        ensureTownQuestCoverage(townId);
        Map<Integer, String> townNames = indexTownNames();
        Instant now = Instant.now();
        return instances.values().stream()
                .filter(ChallengeInstance::active)
                .filter(instance -> visibleToTown(instance, townId))
                .sorted(Comparator
                        .comparing((ChallengeInstance i) -> i.cycleType().ordinal())
                        .thenComparing(i -> i.mode().ordinal())
                        .thenComparing(ChallengeInstance::challengeName))
                .map(instance -> toSnapshot(instance, townId, playerId, townNames, now))
                .toList();
    }

    public List<ChallengeSnapshot> listActiveForTownByCycle(int townId, ChallengeCycleType cycleType) {
        if (cycleType == null) {
            return List.of();
        }
        ensureTownQuestCoverage(townId);
        Map<Integer, String> townNames = indexTownNames();
        Instant now = Instant.now();
        return instances.values().stream()
                .filter(ChallengeInstance::active)
                .filter(instance -> instance.cycleType() == cycleType)
                .filter(instance -> visibleToTown(instance, townId))
                .sorted(Comparator
                        .comparing((ChallengeInstance i) -> i.mode().ordinal())
                        .thenComparing(ChallengeInstance::challengeName))
                .map(instance -> toSnapshot(instance, townId, null, townNames, now))
                .toList();
    }

    private boolean visibleToTown(ChallengeInstance instance, int townId) {
        if (instance == null || townId <= 0) {
            return false;
        }
        if (!(instance.globalInstance() || (instance.townId() != null && instance.townId() == townId))) {
            return false;
        }
        return !instance.mode().m8Race() || isTownEligibleForMode(townId, instance.mode());
    }

    public CityProgressionHubSnapshot progressionHubSnapshot(int townId) {
        ensureTownQuestCoverage(townId);
        List<ChallengeSnapshot> active = listActiveForTown(townId);
        Map<String, ChallengeRewardPreview> rewards = new HashMap<>();
        for (ChallengeSnapshot snapshot : active) {
            ChallengeInstance instance = instances.get(snapshot.instanceId());
            if (instance == null) {
                continue;
            }
            rewards.put(snapshot.instanceId(), rewardPreview(instance));
        }
        return new CityProgressionHubSnapshot(
                cityLevelService.statusForTown(townId, false),
                cycleProgress(active),
                topContributors(townId),
                streakService.snapshotForTown(townId),
                monthlyEventSnapshot(townId),
                active,
                Map.copyOf(rewards)
        );
    }

    public ChallengeRewardPreview rewardPreviewForInstance(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return ChallengeRewardPreview.EMPTY;
        }
        ChallengeInstance instance = instances.get(instanceId);
        if (instance == null) {
            return ChallengeRewardPreview.EMPTY;
        }
        return rewardPreview(instance);
    }

    public ChallengeTextCatalog textCatalog() {
        return textCatalog;
    }

    public CityChallengeSettings settings() {
        return settings;
    }

    public QuestCapacityProfile questCapacityProfile() {
        return new QuestCapacityProfile(
                Math.max(1, settings.dailyStandardCount()),
                Math.max(1, settings.raceWindows().size()),
                Math.max(1, settings.weeklyStandardCount()),
                Math.max(1, settings.monthlyStandardCount()),
                Math.max(1, settings.seasonalRaceCount()),
                Math.max(1, settings.eventsQuestCount())
        );
    }

    private void ensureTownQuestCoverage(int townId) {
        if (!settings.enabled() || townId <= 0) {
            return;
        }
        boolean missingDaily = instances.values().stream()
                .noneMatch(instance -> instance.active()
                        && instance.cycleType() == ChallengeCycleType.DAILY
                        && instance.townId() != null
                        && instance.townId() == townId
                        && instance.mode() == ChallengeMode.DAILY_STANDARD);
        boolean missingWeekly = instances.values().stream()
                .noneMatch(instance -> instance.active()
                        && instance.cycleType() == ChallengeCycleType.WEEKLY
                        && instance.townId() != null
                        && instance.townId() == townId
                        && instance.mode().weeklyStandard());
        boolean missingMonthly = instances.values().stream()
                .noneMatch(instance -> instance.active()
                        && instance.cycleType() == ChallengeCycleType.MONTHLY
                        && instance.townId() != null
                        && instance.townId() == townId
                        && instance.mode().monthlyLedger());
        boolean missingSeasonal = instances.values().stream()
                .noneMatch(instance -> instance.active()
                        && instance.cycleType() == ChallengeCycleType.SEASONAL
                        && instance.townId() != null
                        && instance.townId() == townId
                        && instance.mode().seasonCodex());
        if (!(missingDaily || missingWeekly || missingMonthly || missingSeasonal)) {
            return;
        }
        safeLifecycleTick();
    }

    public MayorGovernanceSnapshot mayorSnapshot(int townId) {
        String seasonKey = seasonKeyNow();
        String key = governanceKey(townId, seasonKey);
        CityChallengeRepository.GovernanceState governance = governanceByTownSeason.get(key);
        CityChallengeRepository.MilestoneState milestone = milestonesByTownSeason.get(key);
        return new MayorGovernanceSnapshot(
                seasonKey,
                ChallengeSeasonResolver.seasonLabel(LocalDate.now(settings.timezone())),
                governance == null ? null : governance.vetoCategory(),
                governance == null ? 0 : governance.rerollUsed(),
                Math.max(0, settings.governance().rerollWeeklyMaxPerCycle()),
                milestone == null ? 0 : milestone.weeklyCompletedCount(),
                milestone != null && milestone.milestone1Done(),
                milestone != null && milestone.milestone2Done(),
                milestone != null && milestone.milestone3Done(),
                milestone != null && milestone.seasonalFinalDone()
        );
    }

    public int seasonCodexPoints(int townId) {
        if (townId <= 0) {
            return 0;
        }
        String cycleKey = currentSeasonalKey == null || currentSeasonalKey.isBlank() || "-".equals(currentSeasonalKey)
                ? seasonKeyNow()
                : currentSeasonalKey;
        return parseIntSafe(store.getRuntimeState(seasonCodexPointsStateKey(cycleKey, townId)).orElse("0"), 0);
    }

    public String seasonCodexActLabel(int townId) {
        return "Atti I-III + Speciali";
    }

    public Optional<CycleRecapSnapshot> latestRecapForTown(int townId) {
        if (townId <= 0) {
            return Optional.empty();
        }
        return store.loadLatestRecap(townId);
    }

    public List<CycleRecapSnapshot> recapHistoryForTown(int townId, int page, int pageSize) {
        if (townId <= 0) {
            return List.of();
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, pageSize);
        return store.loadRecapHistory(townId, safeSize, safePage * safeSize);
    }

    public ContributorBreakdownSnapshot contributorBreakdownForInstance(int townId, String instanceId) {
        if (townId <= 0 || instanceId == null || instanceId.isBlank()) {
            return new ContributorBreakdownSnapshot(instanceId == null ? "-" : instanceId, Math.max(0, townId), 0, 0.0D, List.of());
        }
        Map<UUID, Integer> contributions = playerContributionCapStore.contributionsForTownInstance(instanceId, townId);
        int total = contributions.values().stream().mapToInt(value -> Math.max(0, value)).sum();
        List<ContributorBreakdownSnapshot.Entry> top = contributions.entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<UUID, Integer> entry) -> entry.getValue()).reversed()
                        .thenComparing(entry -> entry.getKey().toString()))
                .limit(3)
                .map(entry -> new ContributorBreakdownSnapshot.Entry(entry.getKey(), Math.max(0, entry.getValue())))
                .toList();
        int topValue = top.isEmpty() ? 0 : Math.max(0, top.getFirst().contribution());
        double carryRatio = total <= 0 ? 0.0D : ((double) topValue / (double) total);
        return new ContributorBreakdownSnapshot(instanceId, townId, total, carryRatio, top);
    }

    public void recordObjective(Player player, ChallengeObjectiveType type, int amount) {
        recordObjective(player, type, amount, ChallengeObjectiveContext.DEFAULT_ACTION);
    }

    public void recordObjective(Player player, ChallengeObjectiveType type, int amount, ChallengeObjectiveContext context) {
        if (!settings.enabled() || amount <= 0 || type == null || player == null) {
            return;
        }
        ChallengeObjectiveContext safeContext = context == null ? ChallengeObjectiveContext.DEFAULT_ACTION : context;
        if (safeContext.markActive()) {
            antiAbuseService.markActive(player.getUniqueId(), Instant.now());
        }
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            return;
        }
        int townId = member.get().town().getId();
        String townName = member.get().town().getName();
        UUID playerId = player.getUniqueId();
        ioExecutor.execute(() -> recordObjectiveInternal(townId, townName, playerId, type, amount, safeContext));
    }

    public void recordObjectiveForTown(int townId, String townName, UUID playerId, ChallengeObjectiveType type, int amount) {
        recordObjectiveForTown(townId, townName, playerId, type, amount, ChallengeObjectiveContext.DEFAULT_ACTION);
    }

    public void recordObjectiveForTown(
            int townId,
            String townName,
            UUID playerId,
            ChallengeObjectiveType type,
            int amount,
            ChallengeObjectiveContext context
    ) {
        if (!settings.enabled() || amount <= 0 || type == null || townId <= 0) {
            return;
        }
        ChallengeObjectiveContext safeContext = context == null ? ChallengeObjectiveContext.DEFAULT_ACTION : context;
        if (safeContext.markActive() && playerId != null) {
            antiAbuseService.markActive(playerId, Instant.now());
        }
        ioExecutor.execute(() -> recordObjectiveInternal(townId, townName, playerId, type, amount, safeContext));
    }

    public void recordStatisticCounter(Player player, ChallengeObjectiveType type, long absoluteValue) {
        if (!settings.enabled() || player == null || type == null || absoluteValue < 0L) {
            return;
        }
        if (!settings.antiAbuse().isSampledAllowed(type)) {
            return;
        }
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            return;
        }
        int townId = member.get().town().getId();
        String townName = member.get().town().getName();
        UUID playerId = player.getUniqueId();
        ioExecutor.execute(() -> {
            Long previous = checkpointStore.get(playerId, type.id());
            if (previous == null) {
                checkpointStore.put(playerId, type.id(), absoluteValue);
                return;
            }
            if (absoluteValue < previous) {
                checkpointStore.put(playerId, type.id(), absoluteValue);
                return;
            }
            long delta = absoluteValue - previous;
            checkpointStore.put(playerId, type.id(), absoluteValue);
            if (delta > 0L) {
                recordObjectiveInternal(townId, townName, playerId, type, clampDelta(delta), ChallengeObjectiveContext.SAMPLED);
            }
        });
    }

    public void recordAdvancementOnce(Player player, ChallengeObjectiveType type, String advancementKey) {
        if (!settings.enabled() || player == null || type == null || advancementKey == null || advancementKey.isBlank()) {
            return;
        }
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            return;
        }
        int townId = member.get().town().getId();
        String townName = member.get().town().getName();
        UUID playerId = player.getUniqueId();
        String checkpointType = type.id() + ":adv:" + advancementKey;
        ioExecutor.execute(() -> {
            if (checkpointStore.contains(playerId, checkpointType)) {
                return;
            }
            checkpointStore.put(playerId, checkpointType, 1L);
            recordObjectiveInternal(townId, townName, playerId, type, 1, ChallengeObjectiveContext.DEFAULT_ACTION);
        });
    }

    public void markPlayerActivity(Player player) {
        if (player == null || !settings.enabled()) {
            return;
        }
        antiAbuseService.markActive(player.getUniqueId(), Instant.now());
    }

    public void markPlayerInactive(Player player) {
        if (player == null || !settings.enabled()) {
            return;
        }
        antiAbuseService.markInactive(player.getUniqueId());
    }

    public void recordStructureLoot(Player player, Inventory inventory) {
        if (!settings.enabled() || player == null || inventory == null) {
            return;
        }
        Optional<StructureLootDetectionService.Detection> detection = structureLootDetectionService.resolve(inventory);
        if (detection.isEmpty()) {
            return;
        }
        recordStructureLootFromDetection(player, detection.get());
    }

    public void recordStructureLoot(Player player, LootableInventory lootableInventory) {
        if (!settings.enabled() || player == null || lootableInventory == null) {
            return;
        }
        Optional<StructureLootDetectionService.Detection> detection = structureLootDetectionService.resolve(lootableInventory);
        if (detection.isEmpty()) {
            return;
        }
        recordStructureLootFromDetection(player, detection.get());
    }

    private void recordStructureLootFromDetection(Player player, StructureLootDetectionService.Detection detection) {
        structureLootValidHits++;
        String dedupeKey = detection.dedupeKey();
        Location sourceLocation = detection.location();
        String biomeKey = sourceLocation != null
                ? challengeContextResolver.biomeKey(sourceLocation)
                : challengeContextResolver.biomeKey(player);
        String dimensionKey = sourceLocation != null
                ? challengeContextResolver.dimensionKey(sourceLocation)
                : challengeContextResolver.dimensionKey(player);
        recordObjective(
                player,
                ChallengeObjectiveType.STRUCTURE_LOOT,
                1,
                ChallengeObjectiveContext.withDedupe(dedupeKey)
                        .withLocationContext(
                                biomeKey,
                                dimensionKey
                        )
        );
    }

    public void recordTrialVaultOpen(Player player, Location location, boolean ominous, String lootTableKey) {
        if (!settings.enabled() || player == null || location == null || location.getWorld() == null) {
            return;
        }
        String biomeKey = challengeContextResolver.biomeKey(location);
        String dimensionKey = challengeContextResolver.dimensionKey(location);
        String materialKey = ominous ? "OMINOUS_VAULT|VAULT" : "VAULT";
        String suffix = ominous ? "ominous" : "normal";
        if (lootTableKey != null && !lootTableKey.isBlank()) {
            suffix = suffix + ":" + lootTableKey.trim().toLowerCase(java.util.Locale.ROOT);
        }
        String dedupeKey = "trial-vault:"
                + location.getWorld().getUID() + ':'
                + location.getBlockX() + ':'
                + location.getBlockY() + ':'
                + location.getBlockZ() + ':'
                + player.getUniqueId() + ':'
                + suffix;
        recordObjective(
                player,
                ChallengeObjectiveType.TRIAL_VAULT_OPEN,
                1,
                ChallengeObjectiveContext.materialAction(materialKey, biomeKey, dimensionKey)
                        .withDedupeKey(dedupeKey)
        );
    }

    public void recordExplorationDiscovery(Player player, Location from, Location to) {
        if (!settings.enabled() || player == null || to == null) {
            return;
        }
        if (from != null
                && from.getWorld() != null
                && from.getWorld().equals(to.getWorld())
                && from.getChunk().getX() == to.getChunk().getX()
                && from.getChunk().getZ() == to.getChunk().getZ()) {
            return;
        }
        Optional<ExplorationDetectionService.Detection> detection = explorationDetectionService.resolve(to);
        if (detection.isEmpty()) {
            return;
        }
        explorationValidHits++;
        recordObjective(
                player,
                ChallengeObjectiveType.STRUCTURE_DISCOVERY,
                1,
                ChallengeObjectiveContext.withDedupe(detection.get().dedupeKey())
                        .withLocationContext(
                                challengeContextResolver.biomeKey(to),
                                challengeContextResolver.dimensionKey(to)
                        )
        );
        recordSecretQuestTrigger(player, SECRET_TRIGGER_STRUCTURE_DISCOVERY, to);
        String structureKey = detection.get().structureKey();
        if (structureKey != null && structureKey.toLowerCase(Locale.ROOT).contains("bastion")) {
            recordSecretQuestTrigger(player, SECRET_TRIGGER_VISIT_LOCATION, to);
        }
    }

    public void recordSecretQuestTrigger(Player player, String triggerKey, Location location) {
        if (!settings.enabled() || player == null) {
            return;
        }
        String safeTrigger = triggerKey == null || triggerKey.isBlank()
                ? "unknown"
                : triggerKey.trim().toLowerCase(Locale.ROOT);
        Location source = location == null ? player.getLocation() : location;
        recordObjective(
                player,
                ChallengeObjectiveType.SECRET_QUEST,
                1,
                ChallengeObjectiveContext.withDedupe("secret-trigger:" + safeTrigger)
                        .withLocationContext(
                                challengeContextResolver.biomeKey(source),
                                challengeContextResolver.dimensionKey(source)
                        )
        );
    }

    public void trackPlacedBlock(String blockKey) {
        if (!settings.enabled() || blockKey == null || blockKey.isBlank()) {
            return;
        }
        ioExecutor.execute(() -> antiAbuseService.trackPlacedBlock(blockKey, Instant.now()));
    }

    private void bootstrap() {
        try {
            store.initialize();
            definitions.clear();
            for (ChallengeMode mode : ChallengeMode.values()) {
                for (ChallengeDefinition definition : proceduralGenerator.definitionsForMode(mode)) {
                    if (!taxonomyService.isAllowed(definition, mode)) {
                        continue;
                    }
                    definitions.putIfAbsent(definition.id(), definition);
                }
            }
            rewardBundles.clear();
            rewardBundles.putAll(settings.rewardBundles());
            store.replaceDefinitions(definitions);
            if (needsM11Cutover()) {
                plugin.getLogger().info("[challenges] M11 cutover: full reset challenge state e rigenerazione modello corrente.");
                applyM11Cutover();
            }

            instances.clear();
            instances.putAll(store.loadInstances());
            progressByInstance.clear();
            store.loadProgressByInstance().forEach((instanceId, byTown) ->
                    progressByInstance.put(instanceId, new ConcurrentHashMap<>(byTown)));
            rewardLedgerKeys.clear();
            rewardLedgerKeys.addAll(store.loadRewardGrantKeys());
            governanceByTownSeason.clear();
            governanceByTownSeason.putAll(store.loadGovernanceState());
            milestonesByTownSeason.clear();
            milestonesByTownSeason.putAll(store.loadMilestoneState());
            selectionHistory.clear();
            selectionHistory.addAll(store.loadSelectionHistory());
            checkpointStore.loadFromRepository();
            placedBlockTracker.loadFromRepository();
            playerContributionCapStore.loadFromRepository();
            townDailyCategoryCapStore.loadFromRepository();
            suspicionScoreStore.loadFromRepository();
            streakService.loadFromRepository();
            cycleState.clear();
            cycleState.putAll(store.loadCycleState());
            if (needsM12Cutover()) {
                plugin.getLogger().info("[challenges] M12 cutover rilevato: reset immediato cicli attivi legacy.");
                store.clearRuntimeStateForRegenerate();
                instances.clear();
                progressByInstance.clear();
                rewardLedgerKeys.clear();
                templateDroppedByReason.clear();
                runtimeDroppedByReason.clear();
                boardSignalHits.clear();
                boardHardRuleHits.clear();
                lastBoardFallbackReason = "-";
                fairnessFallbackHits = 0L;
                personalRewardGrantCount = 0L;
                personalRewardSkipCount = 0L;
                acceptedObjectiveHits = 0L;
                deniedObjectiveHits = 0L;
                duplicateGrantAttempts = 0L;
                cycleXpGrantedScaled.clear();
                cycleExcellenceXpGrantedScaled.clear();
                cycleRewardsGranted.clear();
                governanceByTownSeason.clear();
                milestonesByTownSeason.clear();
                selectionHistory.clear();
                playerContributionCapStore.clearRuntime();
                townDailyCategoryCapStore.clearRuntime();
                cycleState.clear();
            }
            if (needsM79Cutover()) {
                plugin.getLogger().info("[challenges] M7-M9 hard-cut: invalidazione istanze legacy e rigenerazione nuovo modello.");
                applyM79Cutover();
            }
            atlasService.bootstrap();
            storyModeService.bootstrap();
            safeLifecycleTick();
            scheduleDailySprintTasks();
        } catch (Exception exception) {
            lastError = normalizeError(exception);
            plugin.getLogger().warning("[challenges] bootstrap failed reason=" + lastError);
        }
    }

    private boolean needsM12Cutover() {
        for (ChallengeInstance instance : instances.values()) {
            if (instance == null) {
                continue;
            }
            if (instance.mode().race()) {
                continue;
            }
            if (instance.townId() == null || instance.signatureKey() == null || instance.signatureKey().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private boolean needsM11Cutover() {
        String state = store.getRuntimeState(M11_CUTOVER_KEY).orElse("");
        return !M11_CUTOVER_VERSION.equalsIgnoreCase(state);
    }

    private void applyM11Cutover() {
        store.clearAllChallengeStateForM11Cutover();
        instances.clear();
        progressByInstance.clear();
        rewardLedgerKeys.clear();
        templateDroppedByReason.clear();
        runtimeDroppedByReason.clear();
        boardSignalHits.clear();
        boardHardRuleHits.clear();
        lastBoardFallbackReason = "-";
        fairnessFallbackHits = 0L;
        personalRewardGrantCount = 0L;
        personalRewardSkipCount = 0L;
        acceptedObjectiveHits = 0L;
        deniedObjectiveHits = 0L;
        duplicateGrantAttempts = 0L;
        cycleXpGrantedScaled.clear();
        cycleExcellenceXpGrantedScaled.clear();
        cycleRewardsGranted.clear();
        governanceByTownSeason.clear();
        milestonesByTownSeason.clear();
        selectionHistory.clear();
        playerContributionCapStore.clearRuntime();
        townDailyCategoryCapStore.clearRuntime();
        cycleState.clear();
        store.setRuntimeState(M11_CUTOVER_KEY, M11_CUTOVER_VERSION);
    }

    private boolean needsM79Cutover() {
        String state = store.getRuntimeState(M79_CUTOVER_KEY).orElse("");
        return !M79_CUTOVER_VERSION.equalsIgnoreCase(state);
    }

    private void applyM79Cutover() {
        Instant now = Instant.now();
        for (Map.Entry<String, ChallengeInstance> entry : new ArrayList<>(instances.entrySet())) {
            ChallengeInstance instance = entry.getValue();
            if (instance == null || !instance.active()) {
                continue;
            }
            if (!instance.mode().legacyMode()) {
                continue;
            }
            ChallengeInstance expired = new ChallengeInstance(
                    instance.instanceId(),
                    instance.townId(),
                    instance.cycleKey(),
                    instance.cycleType(),
                    instance.windowKey(),
                    instance.challengeId(),
                    instance.challengeName(),
                    instance.objectiveType(),
                    instance.category(),
                    instance.baseTarget(),
                    instance.target(),
                    instance.excellenceTarget(),
                    instance.fairnessMultiplier(),
                    instance.activeContributors7d(),
                    instance.sizeSnapshot(),
                    instance.onlineSnapshot(),
                    instance.targetFormulaVersion(),
                    instance.variantType(),
                    instance.focusType(),
                    instance.focusKey(),
                    instance.focusLabel(),
                    instance.biomeKey(),
                    instance.dimensionKey(),
                    instance.signatureKey(),
                    instance.mode(),
                    ChallengeInstanceStatus.EXPIRED,
                    instance.winnerTownId(),
                    instance.winnerPlayerId(),
                    instance.cycleStartAt(),
                    instance.cycleEndAt(),
                    instance.startedAt(),
                    now
            );
            instances.put(entry.getKey(), expired);
            store.upsertInstance(expired);
            progressByInstance.remove(entry.getKey());
        }
        cycleState.remove(ChallengeCycleType.MONTHLY);
        cycleState.remove(ChallengeCycleType.SEASONAL);
        governanceByTownSeason.clear();
        milestonesByTownSeason.clear();
        store.setRuntimeState(M79_CUTOVER_KEY, M79_CUTOVER_VERSION);
    }

    private void safeLifecycleTick() {
        try {
            lifecycleTick();
        } catch (Exception exception) {
            lastError = normalizeError(exception);
            plugin.getLogger().warning("[challenges] lifecycle tick failed reason=" + lastError);
        }
    }

    private void lifecycleTick() {
        ZoneId zone = settings.timezone();
        LocalDateTime now = LocalDateTime.now(zone);
        currentOnlineSnapshot = onlinePlayerSnapshotNow();
        LocalDate today = now.toLocalDate();
        currentDailyKey = today.format(DAY_KEY);
        String calculatedWeeklyKey = today.format(WEEK_KEY);
        previousWeeklyKey = today.minusWeeks(1).format(WEEK_KEY);
        boolean weeklyRolloverChanged = !calculatedWeeklyKey.equals(currentWeeklyKey);
        currentWeeklyKey = calculatedWeeklyKey;
        currentMonthlyKey = today.format(MONTH_KEY);
        SeasonWindow seasonWindow = resolveSeasonWindow(now);
        currentSeasonalKey = seasonWindow.cycleKey();
        if (weeklyRolloverChanged) {
            streakService.onWeeklyCycleRollover(previousWeeklyKey);
        }

        ensureDailyStandard(currentDailyKey, now);
        ensureRaceWindows(currentDailyKey, now);
        ensureWeeklyStandard(currentWeeklyKey, now);
        ensureMonthlyLedger(currentMonthlyKey, now);
        ensureWeeklyClash(currentWeeklyKey, now);
        ensureMonthlyCrown(currentMonthlyKey, now);
        ensureSeasonCodex(seasonWindow, now);
        refreshEventDiagnostics();
        Instant instantNow = Instant.now();
        expireOldInstances(instantNow);
        refreshDailyBucketTargets(currentDailyKey);
        if (instantNow.getEpochSecond() - lastPlacedPruneEpochSecond >= 60L) {
            antiAbuseService.prune(instantNow);
            lastPlacedPruneEpochSecond = instantNow.getEpochSecond();
        }
        if (instantNow.getEpochSecond() - lastAtlasSealSyncEpochSecond >= 15L) {
            atlasService.syncAllTownSeals();
            lastAtlasSealSyncEpochSecond = instantNow.getEpochSecond();
        }
    }

    private int dailyStandardTargetCount() {
        return Math.max(1, questCapacityProfile().dailyStandardCount());
    }

    private int weeklyStandardTargetCount() {
        return Math.max(1, questCapacityProfile().weeklyVisibleSlots());
    }

    private int questBatchCapacityForTown(int townId) {
        int level = 1;
        try {
            if (townId > 0) {
                level = Math.max(1, cityLevelService.statusForTown(townId, false).level());
            }
        } catch (Exception ignored) {
            level = 1;
        }
        int bracket = Math.max(0, (level - 1) / WEEKLY_MONTHLY_BATCH_STEP_LEVELS);
        int capacity = WEEKLY_MONTHLY_BATCH_BASE + (bracket * WEEKLY_MONTHLY_BATCH_INCREMENT);
        int maxVisible = Math.max(1, Math.min(questCapacityProfile().weeklyVisibleSlots(), questCapacityProfile().monthlyVisibleSlots()));
        return Math.max(WEEKLY_MONTHLY_BATCH_BASE, Math.min(maxVisible, capacity));
    }

    private int monthlyLedgerContractCountForTown(int townId) {
        int capacity = questBatchCapacityForTown(townId);
        return Math.max(0, capacity - MONTHLY_LEDGER_GRAND_COUNT - MONTHLY_LEDGER_MYSTERY_COUNT);
    }

    private void ensureDailyStandard(String cycleKey, LocalDateTime now) {
        LocalDateTime cycleStart = now.toLocalDate().atTime(0, 5);
        if (now.toLocalTime().isBefore(LocalTime.of(0, 5))) {
            return;
        }
        LocalDateTime cycleEnd = now.toLocalDate().plusDays(1).atStartOfDay();
        ensurePerTownInstances(
                cycleKey,
                ChallengeCycleType.DAILY,
                "daily-standard",
                ChallengeMode.DAILY_STANDARD,
                cycleStart.atZone(settings.timezone()).toInstant(),
                cycleEnd.atZone(settings.timezone()).toInstant(),
                dailyStandardTargetCount()
        );
        store.upsertCycleState(ChallengeCycleType.DAILY, cycleKey, Instant.now());
    }

    private void ensureRaceWindows(String cycleKey, LocalDateTime now) {
        List<LocalTime> windows = settings.raceWindows();
        LocalTime first = windows.isEmpty() ? DAILY_SPRINT_A : windows.getFirst();
        LocalTime second = windows.size() < 2 ? DAILY_SPRINT_B : windows.get(1);
        LocalDate cycleDate = now.toLocalDate();
        ensureDailySprintWindow(cycleKey, cycleDate, now, first, ChallengeMode.DAILY_SPRINT_1830);
        ensureDailySprintWindow(cycleKey, cycleDate, now, second, ChallengeMode.DAILY_SPRINT_2130);
    }

    private void ensureDailySprintWindow(
            String cycleKey,
            LocalDate cycleDate,
            LocalDateTime now,
            LocalTime sprintWindow,
            ChallengeMode mode
    ) {
        LocalDate effectiveDate = cycleDate == null ? now.toLocalDate() : cycleDate;
        LocalDateTime start = LocalDateTime.of(effectiveDate, sprintWindow);
        LocalDateTime revealAt = start.minusMinutes(DAILY_REVEAL_MINUTES);
        LocalDateTime end = start.plusMinutes(DAILY_SPRINT_TIMEOUT_MINUTES);
        String suffix = String.format(Locale.ROOT, "%02d%02d", sprintWindow.getHour(), sprintWindow.getMinute());
        String instanceId = "daily:" + cycleKey + ":sprint:" + suffix;
        ChallengeInstance existing = instances.get(instanceId);
        if (existing == null) {
            List<ChallengeDefinition> candidates = filterDailySprintCandidates(cycleKey, mode, definitionsForMode(mode));
            if (candidates.isEmpty()) {
                return;
            }
            String scopeSeed = generationSeed("daily-sprint:" + cycleKey + ':' + suffix + ':' + mode.name());
            ChallengeDefinition definition = weightedPickDeterministic(candidates, 1, "sprint:" + cycleKey + ':' + suffix + ':' + scopeSeed).getFirst();
            ChallengeInstance created = newActiveInstance(
                    definition,
                    instanceId,
                    null,
                    cycleKey,
                    ChallengeCycleType.DAILY,
                    "daily-sprint-" + suffix,
                    mode,
                    start.atZone(settings.timezone()).toInstant(),
                    end.atZone(settings.timezone()).toInstant()
            );
            instances.put(instanceId, created);
            store.upsertInstance(created);
            existing = created;
        }
        if (!now.isBefore(start) && !now.isAfter(end)) {
            announceRaceStartedOnce(existing);
        }
    }

    public DailySprintForceResult forceDailySprintToday(ChallengeMode mode) {
        if (!settings.enabled()) {
            return new DailySprintForceResult(false, "disabled", mode, null);
        }
        if (mode == null || (mode != ChallengeMode.DAILY_SPRINT_1830 && mode != ChallengeMode.DAILY_SPRINT_2130)) {
            return new DailySprintForceResult(false, "invalid-mode", mode, null);
        }
        ZoneId zone = settings.timezone();
        LocalDateTime now = LocalDateTime.now(zone);
        LocalDate today = now.toLocalDate();
        LocalTime window = sprintWindowForMode(mode);
        String cycleKey = today.format(DAY_KEY);
        ensureDailySprintWindow(cycleKey, today, now, window, mode);
        String instanceId = dailySprintInstanceId(cycleKey, window);
        ChallengeInstance instance = instances.get(instanceId);
        if (instance == null) {
            return new DailySprintForceResult(false, "no-instance", mode, instanceId);
        }
        if (!now.isBefore(instance.cycleStartAt().atZone(zone).toLocalDateTime())
                && !now.isAfter(instance.cycleEndAt().atZone(zone).toLocalDateTime())) {
            announceRaceStartedOnce(instance);
        }
        return new DailySprintForceResult(true, "forced", mode, instanceId);
    }

    private void scheduleDailySprintTasks() {
        cancelDailySprintTasks();
        List<LocalTime> windows = settings.raceWindows();
        LocalTime first = windows.isEmpty() ? DAILY_SPRINT_A : windows.getFirst();
        LocalTime second = windows.size() < 2 ? DAILY_SPRINT_B : windows.get(1);
        dailySprint1830Task = scheduleDailySprintTask(first, ChallengeMode.DAILY_SPRINT_1830);
        dailySprint2130Task = scheduleDailySprintTask(second, ChallengeMode.DAILY_SPRINT_2130);
    }

    private ScheduledFuture<?> scheduleDailySprintTask(LocalTime sprintWindow, ChallengeMode mode) {
        LocalDateTime now = LocalDateTime.now(settings.timezone());
        LocalDate today = now.toLocalDate();
        LocalDate targetDate = now.toLocalTime().isBefore(sprintWindow) ? today : today.plusDays(1);
        LocalDateTime targetAt = LocalDateTime.of(targetDate, sprintWindow);
        long delayMillis = Math.max(0L, java.time.Duration.between(now, targetAt).toMillis());
        return ioExecutor.schedule(() -> {
            try {
                triggerDailySprintAt(targetDate, sprintWindow, mode);
            } finally {
                if (settings.enabled()) {
                    if (mode == ChallengeMode.DAILY_SPRINT_1830) {
                        dailySprint1830Task = scheduleDailySprintTask(sprintWindow, mode);
                    } else if (mode == ChallengeMode.DAILY_SPRINT_2130) {
                        dailySprint2130Task = scheduleDailySprintTask(sprintWindow, mode);
                    }
                }
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void triggerDailySprintAt(LocalDate cycleDate, LocalTime sprintWindow, ChallengeMode mode) {
        if (cycleDate == null || sprintWindow == null || mode == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(settings.timezone());
        ensureDailySprintWindow(cycleDate.format(DAY_KEY), cycleDate, now, sprintWindow, mode);
    }

    private void cancelDailySprintTasks() {
        if (dailySprint1830Task != null) {
            dailySprint1830Task.cancel(false);
            dailySprint1830Task = null;
        }
        if (dailySprint2130Task != null) {
            dailySprint2130Task.cancel(false);
            dailySprint2130Task = null;
        }
    }

    private List<ChallengeDefinition> filterDailySprintCandidates(
            String cycleKey,
            ChallengeMode mode,
            List<ChallengeDefinition> candidates
    ) {
        if (cycleKey == null || cycleKey.isBlank() || mode == null || !mode.dailyRace() || candidates == null || candidates.isEmpty()) {
            return candidates == null ? List.of() : candidates;
        }
        Set<String> usedDefinitionIds = instances.values().stream()
                .filter(instance -> instance != null)
                .filter(instance -> instance.mode() == ChallengeMode.DAILY_SPRINT_1830 || instance.mode() == ChallengeMode.DAILY_SPRINT_2130)
                .filter(instance -> instance.mode() != mode)
                .filter(instance -> cycleKey.equalsIgnoreCase(instance.cycleKey()))
                .map(ChallengeInstance::challengeId)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toSet());
        if (usedDefinitionIds.isEmpty()) {
            return candidates;
        }
        List<ChallengeDefinition> filtered = candidates.stream()
                .filter(definition -> definition != null && !usedDefinitionIds.contains(definition.id()))
                .toList();
        return filtered.isEmpty() ? candidates : filtered;
    }

    private LocalTime sprintWindowForMode(ChallengeMode mode) {
        List<LocalTime> windows = settings.raceWindows();
        LocalTime first = windows.isEmpty() ? DAILY_SPRINT_A : windows.getFirst();
        LocalTime second = windows.size() < 2 ? DAILY_SPRINT_B : windows.get(1);
        return mode == ChallengeMode.DAILY_SPRINT_2130 ? second : first;
    }

    private String dailySprintInstanceId(String cycleKey, LocalTime sprintWindow) {
        String suffix = String.format(Locale.ROOT, "%02d%02d", sprintWindow.getHour(), sprintWindow.getMinute());
        return "daily:" + cycleKey + ":sprint:" + suffix;
    }

    private void ensureWeeklyStandard(String cycleKey, LocalDateTime now) {
        LocalDate weekStartDate = now.toLocalDate().with(WeekFields.ISO.dayOfWeek(), 1);
        LocalDateTime cycleStart = weekStartDate.atTime(settings.weeklyRollover());
        if (now.isBefore(cycleStart)) {
            return;
        }
        LocalDateTime cycleEnd = weekStartDate.plusWeeks(1).atTime(settings.weeklyRollover());
        Instant startAt = cycleStart.atZone(settings.timezone()).toInstant();
        Instant endAt = cycleEnd.atZone(settings.timezone()).toInstant();
        for (Town town : huskTownsApiHook.getPlayableTowns()) {
            ensureInstancesForTown(
                    town.getId(),
                    cycleKey,
                    ChallengeCycleType.WEEKLY,
                    "weekly-standard",
                    ChallengeMode.WEEKLY_STANDARD,
                    startAt,
                    endAt,
                    questBatchCapacityForTown(town.getId())
            );
        }
        store.upsertCycleState(ChallengeCycleType.WEEKLY, cycleKey, Instant.now());
    }

    private void ensureMonthlyLedger(String cycleKey, LocalDateTime now) {
        LocalDate monthStartDate = now.toLocalDate().withDayOfMonth(1);
        LocalDateTime cycleStart = monthStartDate.atTime(settings.monthlyRollover());
        if (now.isBefore(cycleStart)) {
            return;
        }
        LocalDateTime cycleEnd = monthStartDate.plusMonths(1).atTime(settings.monthlyRollover());
        Instant startAt = cycleStart.atZone(settings.timezone()).toInstant();
        Instant endAt = cycleEnd.atZone(settings.timezone()).toInstant();
        for (Town town : huskTownsApiHook.getPlayableTowns()) {
            int townId = town.getId();
            int contractCount = monthlyLedgerContractCountForTown(townId);
            ensureInstancesForTown(
                    townId,
                    cycleKey,
                    ChallengeCycleType.MONTHLY,
                    "monthly-ledger-grand",
                    ChallengeMode.MONTHLY_LEDGER_GRAND,
                    startAt,
                    endAt,
                    MONTHLY_LEDGER_GRAND_COUNT
            );
            ensureInstancesForTown(
                    townId,
                    cycleKey,
                    ChallengeCycleType.MONTHLY,
                    "monthly-ledger-contract",
                    ChallengeMode.MONTHLY_LEDGER_CONTRACT,
                    startAt,
                    endAt,
                    contractCount
            );
            ensureInstancesForTown(
                    townId,
                    cycleKey,
                    ChallengeCycleType.MONTHLY,
                    "monthly-ledger-mystery",
                    ChallengeMode.MONTHLY_LEDGER_MYSTERY,
                    startAt,
                    endAt,
                    MONTHLY_LEDGER_MYSTERY_COUNT
            );
        }
        store.upsertCycleState(ChallengeCycleType.MONTHLY, cycleKey, Instant.now());
    }

    private void ensureWeeklyClash(String cycleKey, LocalDateTime now) {
        LocalDate weekStart = now.toLocalDate().with(WeekFields.ISO.dayOfWeek(), 1);
        LocalDate clashDate = weekStart.plusDays(5);
        LocalDateTime start = LocalDateTime.of(clashDate, WEEKLY_CLASH_AT);
        LocalDateTime end = start.plusMinutes(WEEKLY_CLASH_TIMEOUT_MINUTES);
        String instanceId = "weekly-clash:" + cycleKey;
        if (instances.containsKey(instanceId)) {
            if (!now.isBefore(start) && !now.isAfter(end)) {
                announceRaceStartedOnce(instances.get(instanceId));
            }
            return;
        }
        List<ChallengeDefinition> candidates = definitionsForMode(ChallengeMode.WEEKLY_CLASH);
        if (candidates.isEmpty()) {
            return;
        }
        String scopeSeed = generationSeed("weekly-clash:" + cycleKey);
        ChallengeDefinition definition = weightedPickDeterministic(candidates, 1, "weekly-clash:" + cycleKey + ':' + scopeSeed).getFirst();
        ChallengeInstance instance = newActiveInstance(
                definition,
                instanceId,
                null,
                cycleKey,
                ChallengeCycleType.WEEKLY,
                "weekly-clash",
                ChallengeMode.WEEKLY_CLASH,
                start.atZone(settings.timezone()).toInstant(),
                end.atZone(settings.timezone()).toInstant()
        );
        instances.put(instanceId, instance);
        store.upsertInstance(instance);
        if (!now.isBefore(start) && !now.isAfter(end)) {
            announceRaceStartedOnce(instance);
        }
    }

    private void ensureMonthlyCrown(String cycleKey, LocalDateTime now) {
        LocalDate firstDay = now.toLocalDate().withDayOfMonth(1);
        LocalDate firstSunday = firstDay.with(TemporalAdjusters.firstInMonth(DayOfWeek.SUNDAY));
        LocalDateTime start = LocalDateTime.of(firstSunday, MONTHLY_CROWN_AT);
        LocalDateTime end = start.plusMinutes(MONTHLY_CROWN_TIMEOUT_MINUTES);
        String crownCycleKey = cycleKey + "-crown";
        String instanceId = "monthly-crown:" + crownCycleKey;
        if (instances.containsKey(instanceId)) {
            if (!now.isBefore(start) && !now.isAfter(end)) {
                announceRaceStartedOnce(instances.get(instanceId));
            }
            return;
        }
        List<ChallengeDefinition> candidates = definitionsForMode(ChallengeMode.MONTHLY_CROWN);
        if (candidates.isEmpty()) {
            return;
        }
        String scopeSeed = generationSeed("monthly-crown:" + crownCycleKey);
        ChallengeDefinition definition = weightedPickDeterministic(candidates, 1, "monthly-crown:" + crownCycleKey + ':' + scopeSeed).getFirst();
        ChallengeInstance instance = newActiveInstance(
                definition,
                instanceId,
                null,
                crownCycleKey,
                ChallengeCycleType.MONTHLY,
                "monthly-crown",
                ChallengeMode.MONTHLY_CROWN,
                start.atZone(settings.timezone()).toInstant(),
                end.atZone(settings.timezone()).toInstant()
        );
        instances.put(instanceId, instance);
        store.upsertInstance(instance);
        if (!now.isBefore(start) && !now.isAfter(end)) {
            announceRaceStartedOnce(instance);
        }
    }

    private void ensureSeasonCodex(SeasonWindow seasonWindow, LocalDateTime now) {
        if (seasonWindow == null) {
            return;
        }
        LocalDateTime cycleStart = seasonWindow.startDate().atTime(settings.seasonalRollover());
        if (now.isBefore(cycleStart)) {
            return;
        }
        LocalDateTime cycleEnd = seasonWindow.endDateExclusive().atTime(settings.seasonalRollover());
        String cycleKey = seasonWindow.cycleKey();
        List<Town> towns = huskTownsApiHook.getPlayableTowns();
        if (towns.isEmpty()) {
            return;
        }
        Instant startAt = cycleStart.atZone(settings.timezone()).toInstant();
        Instant endAt = cycleEnd.atZone(settings.timezone()).toInstant();
        for (Town town : towns) {
            int townId = town.getId();
            ensureSeasonCodexActsForTown(townId, cycleKey, startAt, endAt);
            ensureSeasonCodexFinalsForTown(townId, cycleKey, startAt, endAt);
        }
        store.upsertCycleState(ChallengeCycleType.SEASONAL, cycleKey, Instant.now());
    }

    private void ensureSeasonCodexActsForTown(
            int townId,
            String cycleKey,
            Instant cycleStartAt,
            Instant cycleEndAt
    ) {
        int actIExisting = trimInstancesToTarget(townId, cycleKey, ChallengeMode.SEASON_CODEX_ACT_I, settings.seasonalActICount());
        int actIIExisting = trimInstancesToTarget(townId, cycleKey, ChallengeMode.SEASON_CODEX_ACT_II, settings.seasonalActIICount());
        int actIIIExisting = trimInstancesToTarget(townId, cycleKey, ChallengeMode.SEASON_CODEX_ACT_III, settings.seasonalActIIICount());
        int totalExisting = actIExisting + actIIExisting + actIIIExisting;
        int missing = Math.max(0, seasonalActTotalCount() - totalExisting);
        if (missing <= 0) {
            return;
        }

        List<ChallengeDefinition> selected = pickCurationDeterministic(
                seasonalActCandidates(),
                missing,
                ChallengeMode.SEASON_CODEX_ACT_I,
                cycleKey,
                townId,
                existingSeasonalActDefinitionsForTown(townId, cycleKey)
        );

        int indexI = actIExisting;
        int indexII = actIIExisting;
        int indexIII = actIIIExisting;
        for (ChallengeDefinition definition : selected) {
            if (definition == null) {
                continue;
            }
            if (indexI < settings.seasonalActICount()) {
                indexI++;
                createInstanceForDefinition(
                        definition,
                        indexI,
                        townId,
                        cycleKey,
                        ChallengeCycleType.SEASONAL,
                        "season-codex-act-i",
                        ChallengeMode.SEASON_CODEX_ACT_I,
                        cycleStartAt,
                        cycleEndAt
                );
                continue;
            }
            if (indexII < settings.seasonalActIICount()) {
                indexII++;
                createInstanceForDefinition(
                        definition,
                        indexII,
                        townId,
                        cycleKey,
                        ChallengeCycleType.SEASONAL,
                        "season-codex-act-ii",
                        ChallengeMode.SEASON_CODEX_ACT_II,
                        cycleStartAt,
                        cycleEndAt
                );
                continue;
            }
            if (indexIII < settings.seasonalActIIICount()) {
                indexIII++;
                createInstanceForDefinition(
                        definition,
                        indexIII,
                        townId,
                        cycleKey,
                        ChallengeCycleType.SEASONAL,
                        "season-codex-act-iii",
                        ChallengeMode.SEASON_CODEX_ACT_III,
                        cycleStartAt,
                        cycleEndAt
                );
            }
        }
    }

    private void ensureSeasonCodexFinalsForTown(
            int townId,
            String cycleKey,
            Instant cycleStartAt,
            Instant cycleEndAt
    ) {
        ensureInstancesForTown(
                townId,
                cycleKey,
                ChallengeCycleType.SEASONAL,
                "season-codex-elite",
                ChallengeMode.SEASON_CODEX_ELITE,
                cycleStartAt,
                cycleEndAt,
                settings.seasonalEliteCount(),
                seasonalEliteCandidates()
        );
        ensureInstancesForTown(
                townId,
                cycleKey,
                ChallengeCycleType.SEASONAL,
                "season-codex-hidden-relic",
                ChallengeMode.SEASON_CODEX_HIDDEN_RELIC,
                cycleStartAt,
                cycleEndAt,
                settings.seasonalHiddenRelicCount(),
                seasonalHiddenRelicCandidates()
        );
    }

    private double seasonCodexUnlockRatio(int townId, String cycleKey, ChallengeMode mode) {
        if (townId <= 0 || cycleKey == null || cycleKey.isBlank() || mode == null) {
            return 0.0D;
        }
        int total = 0;
        int completed = 0;
        for (ChallengeInstance instance : instances.values()) {
            if (instance == null || instance.mode() != mode || !cycleKey.equals(instance.cycleKey())) {
                continue;
            }
            if (instance.townId() == null || instance.townId() != townId) {
                continue;
            }
            total++;
            TownChallengeProgress progress = progressByInstance.getOrDefault(instance.instanceId(), Map.of()).get(townId);
            if (progress != null && progress.baseCompleted(instance.target())) {
                completed++;
            }
        }
        if (total <= 0) {
            return 0.0D;
        }
        return (double) completed / (double) total;
    }

    private void announceRaceStartedOnce(ChallengeInstance instance) {
        if (instance == null || instance.instanceId() == null || instance.instanceId().isBlank()) {
            return;
        }
        String key = "race-start-announced:" + instance.instanceId();
        if (store.getRuntimeState(key).isPresent()) {
            return;
        }
        store.setRuntimeState(key, Instant.now().toString());
        announceRaceStarted(instance);
    }

    private SeasonWindow resolveSeasonWindow(LocalDateTime now) {
        LocalDate date = now.toLocalDate();
        int year = date.getYear();
        int month = date.getMonthValue();
        LocalDate startDate;
        String seasonName;
        if (month >= 3 && month <= 5) {
            startDate = LocalDate.of(year, 3, 1);
            seasonName = "SPRING";
        } else if (month >= 6 && month <= 8) {
            startDate = LocalDate.of(year, 6, 1);
            seasonName = "SUMMER";
        } else if (month >= 9 && month <= 11) {
            startDate = LocalDate.of(year, 9, 1);
            seasonName = "AUTUMN";
        } else if (month == 12) {
            startDate = LocalDate.of(year, 12, 1);
            seasonName = "WINTER";
        } else {
            startDate = LocalDate.of(year - 1, 12, 1);
            seasonName = "WINTER";
        }
        LocalDate endExclusive = startDate.plusMonths(3);
        String cycleKey = startDate.getYear() + "-" + seasonName;
        String seasonLabel = switch (seasonName) {
            case "SPRING" -> "Primavera";
            case "SUMMER" -> "Estate";
            case "AUTUMN" -> "Autunno";
            default -> "Inverno";
        };
        return new SeasonWindow(cycleKey, seasonName, seasonLabel, startDate, endExclusive);
    }

    private ChallengeInstance newActiveInstance(
            ChallengeDefinition definition,
            String instanceId,
            Integer townId,
            String cycleKey,
            ChallengeCycleType cycleType,
            String windowKey,
            ChallengeMode mode,
            Instant cycleStartAt,
            Instant cycleEndAt
    ) {
        int baseTarget = definition.resolveTarget(instanceId);
        if (definition.objectiveType() == ChallengeObjectiveType.TRIAL_VAULT_OPEN) {
            baseTarget = resolveTrialVaultTarget(instanceId, cycleType);
        }
        if (isMonthlyPlaytimeObjective(definition, cycleType, townId)) {
            baseTarget = resolveMonthlyPlaytimeTarget(instanceId, townId);
        }
        int target = baseTarget;
        int activeContributors7d = 0;
        double fairnessMultiplier = 1.0D;
        Integer sizeSnapshot = null;
        Integer onlineSnapshot = null;
        String targetFormulaVersion = TARGET_FORMULA_VERSION;
        boolean authoredSecretTarget = usesAuthoredSecretTargets(definition);
        it.patric.cittaexp.levels.PrincipalStage stageSnapshot = it.patric.cittaexp.levels.PrincipalStage.AVAMPOSTO;
        if (townId != null && townId > 0) {
            try {
                stageSnapshot = cityLevelService.statusForTown(townId, false).principalStage();
                int members = Math.max(1, huskTownsApiHook.getTownMembers(townId).size());
                sizeSnapshot = members;
            } catch (Exception exception) {
                plugin.getLogger().warning("[challenges] board context fallback town=" + townId
                        + ", reason=" + normalizeError(exception));
                sizeSnapshot = 1;
            }
        }
        if (authoredSecretTarget) {
            target = Math.max(1, baseTarget);
            fairnessMultiplier = 1.0D;
            targetFormulaVersion = TARGET_FORMULA_VERSION + "+authored-secret";
        } else {
            BoardTargetFormulaService.TargetResult targetResult = boardTargetFormulaService.resolve(
                    instanceId,
                    definition,
                    mode,
                    stageSnapshot,
                    baseTarget
            );
            target = Math.max(1, targetResult.target());
            fairnessMultiplier = Math.max(0.01D, targetResult.appliedMultiplier());
            targetFormulaVersion = safeText(targetResult.formulaVersion(), TARGET_FORMULA_VERSION);
        }
        if (mode.seasonCodex() && !authoredSecretTarget) {
            double enduranceMultiplier = seasonalEnduranceMultiplier(mode, definition.objectiveType());
            if (enduranceMultiplier > 1.0D) {
                target = Math.max(1, targetScalingService.scaleTarget(target, definition.targetStep(), enduranceMultiplier));
                fairnessMultiplier = Math.max(0.01D, fairnessMultiplier * enduranceMultiplier);
                targetFormulaVersion = targetFormulaVersion + "+seasonal-endurance";
            }
        }

        if (mode.race()) {
            onlineSnapshot = onlinePlayerSnapshotNow();
            currentOnlineSnapshot = onlineSnapshot;
        }

        int scaledBaseTarget = Math.max(1, target);
        ChallengeVariantType variantType = ChallengeVariantType.GLOBAL;
        ChallengeFocusType focusType = ChallengeFocusType.NONE;
        String focusKey = null;
        String focusLabel = null;
        String biomeKey = null;
        String dimensionKey = null;
        String signatureKey = null;
        boolean pickedFromAllowedWindow = false;
        String blockedReason = "-";

        for (int attempt = 0; attempt < 8; attempt++) {
            String attemptSeed = instanceId + ":sig:" + attempt;
            int attemptTarget = scaledBaseTarget;
            ChallengeVariantType attemptVariant = definition.resolveVariant(mode, attemptSeed);
            ProceduralChallengeGenerator.ResolvedContext resolvedContext = proceduralGenerator.resolveContext(
                    definition.objectiveType(),
                    mode,
                    attemptSeed
            );
            ChallengeFocusType attemptFocusType = ChallengeFocusType.NONE;
            String attemptFocusKey = null;
            String attemptFocusLabel = null;
            if (attemptVariant == ChallengeVariantType.SPECIFIC) {
                List<ChallengeFocusOption> allowedFocusPool = filterAllowedFocusPool(
                        definition,
                        resolvedContext.dimensionKey(),
                        resolvedContext.biomeKey()
                );
                if (allowedFocusPool.isEmpty() && (resolvedContext.dimensionKey() != null || resolvedContext.biomeKey() != null)) {
                    resolvedContext = ProceduralChallengeGenerator.ResolvedContext.NONE;
                    allowedFocusPool = filterAllowedFocusPool(definition, null, null);
                }
                ChallengeFocusOption focus = resolveFocus(allowedFocusPool, attemptSeed);
                if (focus == null || focus.key() == null || focus.key().isBlank()) {
                    attemptVariant = ChallengeVariantType.GLOBAL;
                } else {
                    attemptFocusType = definition.focusType();
                    attemptFocusKey = focus.key();
                    attemptFocusLabel = effectiveFocusLabel(definition.objectiveType(), focus.key(), focus.label());
                    double multiplier = settings.specificTargetScaling().multiplierFor(focus.rarity());
                    attemptTarget = Math.max(1, targetScalingService.scaleTarget(attemptTarget, definition.targetStep(), multiplier));
                }
            }
            String attemptBiome = resolvedContext.biomeKey();
            String attemptDimension = resolvedContext.dimensionKey();
            String attemptSignature = ChallengeSignatureService.buildSignature(
                    mode,
                    definition.objectiveFamily(),
                    definition.objectiveType(),
                    attemptFocusKey,
                    attemptBiome,
                    attemptDimension
            );
            ChallengeSignatureService.Decision signatureDecision = signatureService.evaluate(
                    selectionHistory,
                    townId == null ? -1 : townId,
                    attemptSignature,
                    Instant.now()
            );
            variantType = attemptVariant;
            focusType = attemptFocusType;
            focusKey = attemptFocusKey;
            focusLabel = attemptFocusLabel;
            biomeKey = attemptBiome;
            dimensionKey = attemptDimension;
            signatureKey = attemptSignature;
            target = attemptTarget;
            blockedReason = signatureDecision.reasonCode();
            if (!signatureDecision.blocked()) {
                pickedFromAllowedWindow = true;
                break;
            }
        }
        if (!pickedFromAllowedWindow) {
            addLong(runtimeDroppedByReason, "signature-window-fallback:" + blockedReason, 1L);
        }
        if (definition.objectiveType() == ChallengeObjectiveType.MOB_KILL
                && "PLAYER".equalsIgnoreCase(safeText(focusKey, ""))) {
            target = 1;
            targetFormulaVersion = targetFormulaVersion + "+pvp-cap";
        }
        int excellenceTarget = targetScalingService.excellenceTarget(target);

        return new ChallengeInstance(
                instanceId,
                townId,
                cycleKey,
                cycleType,
                windowKey,
                definition.id(),
                definition.displayName(),
                definition.objectiveType(),
                definition.category(),
                baseTarget,
                target,
                excellenceTarget,
                fairnessMultiplier,
                activeContributors7d,
                sizeSnapshot,
                onlineSnapshot,
                targetFormulaVersion,
                variantType,
                focusType,
                focusKey,
                focusLabel,
                biomeKey,
                dimensionKey,
                signatureKey,
                mode,
                ChallengeInstanceStatus.ACTIVE,
                null,
                null,
                cycleStartAt,
                cycleEndAt,
                Instant.now(),
                null
        );
    }

    private boolean usesAuthoredSecretTargets(ChallengeDefinition definition) {
        if (definition == null) {
            return false;
        }
        String id = safeText(definition.id(), "").toLowerCase(Locale.ROOT);
        return id.startsWith("secret_");
    }

    private double seasonalEnduranceMultiplier(ChallengeMode mode, ChallengeObjectiveType objectiveType) {
        if (mode == null || objectiveType == null || !mode.seasonCodex()) {
            return 1.0D;
        }
        boolean upperTier = mode == ChallengeMode.SEASON_CODEX_ELITE || mode == ChallengeMode.SEASON_CODEX_HIDDEN_RELIC;
        return switch (objectiveType) {
            case RESOURCE_CONTRIBUTION -> upperTier ? 1.85D : 1.60D;
            case CONSTRUCTION -> upperTier ? 1.90D : 1.65D;
            case PLACE_BLOCK -> upperTier ? 2.10D : 1.80D;
            case ITEM_CRAFT -> upperTier ? 1.70D : 1.45D;
            case REDSTONE_AUTOMATION -> upperTier ? 1.70D : 1.45D;
            case VILLAGER_TRADE -> upperTier ? 1.70D : 1.45D;
            case TRANSPORT_DISTANCE -> upperTier ? 1.85D : 1.60D;
            case ARCHAEOLOGY_BRUSH -> upperTier ? 1.65D : 1.40D;
            case DIMENSION_TRAVEL -> upperTier ? 1.55D : 1.30D;
            case TRIAL_VAULT_OPEN -> upperTier ? 1.75D : 1.45D;
            case RAID_WIN -> upperTier ? 1.70D : 1.45D;
            case BOSS_KILL -> upperTier ? 1.65D : 1.40D;
            case NETHER_ACTIVITY, OCEAN_ACTIVITY -> upperTier ? 1.60D : 1.35D;
            case ECONOMY_ADVANCED -> upperTier ? 1.80D : 1.55D;
            default -> 1.0D;
        };
    }

    private void ensurePerTownInstances(
            String cycleKey,
            ChallengeCycleType cycleType,
            String windowKey,
            ChallengeMode mode,
            Instant cycleStartAt,
            Instant cycleEndAt,
            int targetCount
    ) {
        List<Town> towns = huskTownsApiHook.getPlayableTowns();
        if (towns.isEmpty()) {
            return;
        }
        for (Town town : towns) {
            int townId = town.getId();
            int already = trimInstancesToTarget(townId, cycleKey, mode, targetCount);
            int missing = Math.max(0, targetCount - already);
            if (missing <= 0) {
                continue;
            }
            List<ChallengeDefinition> candidates = definitionsForMode(mode);
            List<ChallengeDefinition> selected = pickCurationDeterministic(
                    candidates,
                    missing,
                    mode,
                    cycleKey,
                    townId
            );
            int index = already;
            for (ChallengeDefinition definition : selected) {
                index++;
                String instanceId = mode.name().toLowerCase(Locale.ROOT) + ":" + cycleKey + ":town:" + townId + ":" + index;
                if (instances.containsKey(instanceId)) {
                    continue;
                }
                ChallengeInstance instance = newActiveInstance(
                        definition,
                        instanceId,
                        townId,
                        cycleKey,
                        cycleType,
                        windowKey,
                        mode,
                        cycleStartAt,
                        cycleEndAt
                );
                instances.put(instanceId, instance);
                store.upsertInstance(instance);
                recordSelectionHistory(instance, definition);
            }
        }
    }

    private void ensureInstancesForTown(
            int townId,
            String cycleKey,
            ChallengeCycleType cycleType,
            String windowKey,
            ChallengeMode mode,
            Instant cycleStartAt,
            Instant cycleEndAt,
            int targetCount
    ) {
        ensureInstancesForTown(townId, cycleKey, cycleType, windowKey, mode, cycleStartAt, cycleEndAt, targetCount, null);
    }

    private void ensureInstancesForTown(
            int townId,
            String cycleKey,
            ChallengeCycleType cycleType,
            String windowKey,
            ChallengeMode mode,
            Instant cycleStartAt,
            Instant cycleEndAt,
            int targetCount,
            List<ChallengeDefinition> candidateOverride
    ) {
        if (townId <= 0) {
            return;
        }
        int already = trimInstancesToTarget(townId, cycleKey, mode, targetCount);
        int missing = Math.max(0, targetCount - already);
        if (missing <= 0) {
            return;
        }
        List<ChallengeDefinition> selected = pickCurationDeterministic(
                candidateOverride == null || candidateOverride.isEmpty() ? definitionsForMode(mode) : candidateOverride,
                missing,
                mode,
                cycleKey,
                townId
        );
        int index = already;
        for (ChallengeDefinition definition : selected) {
            index++;
            createInstanceForDefinition(
                    definition,
                    index,
                    townId,
                    cycleKey,
                    cycleType,
                    windowKey,
                    mode,
                    cycleStartAt,
                    cycleEndAt
            );
        }
    }

    private void createInstanceForDefinition(
            ChallengeDefinition definition,
            int index,
            int townId,
            String cycleKey,
            ChallengeCycleType cycleType,
            String windowKey,
            ChallengeMode mode,
            Instant cycleStartAt,
            Instant cycleEndAt
    ) {
        if (definition == null || index <= 0 || townId <= 0 || mode == null) {
            return;
        }
        String instanceId = mode.name().toLowerCase(Locale.ROOT) + ":" + cycleKey + ":town:" + townId + ":" + index;
        if (instances.containsKey(instanceId)) {
            return;
        }
        ChallengeInstance instance = newActiveInstance(
                definition,
                instanceId,
                townId,
                cycleKey,
                cycleType,
                windowKey,
                mode,
                cycleStartAt,
                cycleEndAt
        );
        instances.put(instanceId, instance);
        store.upsertInstance(instance);
        recordSelectionHistory(instance, definition);
    }

    private int trimInstancesToTarget(int townId, String cycleKey, ChallengeMode mode, int targetCount) {
        List<ChallengeInstance> scoped = instances.values().stream()
                .filter(ChallengeInstance::active)
                .filter(instance -> instance.mode() == mode)
                .filter(instance -> cycleKey.equals(instance.cycleKey()))
                .filter(instance -> instance.townId() != null && instance.townId() == townId)
                .sorted(Comparator
                        .comparing((ChallengeInstance instance) -> instance.startedAt() == null ? Instant.EPOCH : instance.startedAt())
                        .thenComparing(ChallengeInstance::instanceId))
                .toList();
        if (scoped.size() <= targetCount) {
            return scoped.size();
        }
        for (int index = targetCount; index < scoped.size(); index++) {
            ChallengeInstance current = scoped.get(index);
            ChallengeInstance trimmed = new ChallengeInstance(
                    current.instanceId(),
                    current.townId(),
                    current.cycleKey(),
                    current.cycleType(),
                    current.windowKey(),
                    current.challengeId(),
                    current.challengeName(),
                    current.objectiveType(),
                    current.category(),
                    current.baseTarget(),
                    current.target(),
                    current.excellenceTarget(),
                    current.fairnessMultiplier(),
                    current.activeContributors7d(),
                    current.sizeSnapshot(),
                    current.onlineSnapshot(),
                    current.targetFormulaVersion(),
                    current.variantType(),
                    current.focusType(),
                    current.focusKey(),
                    current.focusLabel(),
                    current.biomeKey(),
                    current.dimensionKey(),
                    current.signatureKey(),
                    current.mode(),
                    ChallengeInstanceStatus.EXPIRED,
                    current.winnerTownId(),
                    current.winnerPlayerId(),
                    current.cycleStartAt(),
                    current.cycleEndAt(),
                    current.startedAt(),
                    Instant.now()
            );
            instances.put(trimmed.instanceId(), trimmed);
            progressByInstance.remove(trimmed.instanceId());
            store.upsertInstance(trimmed);
            addLong(runtimeDroppedByReason, "m6-overflow-trim:" + mode.name().toLowerCase(Locale.ROOT), 1L);
        }
        return targetCount;
    }

    private void expireOldInstances(Instant now) {
        for (Map.Entry<String, ChallengeInstance> entry : new ArrayList<>(instances.entrySet())) {
            ChallengeInstance instance = entry.getValue();
            if (instance.status() != ChallengeInstanceStatus.ACTIVE) {
                continue;
            }
            if (instance.cycleEndAt() == null || instance.cycleEndAt().isAfter(now)) {
                continue;
            }
            if (instance.mode().monthlyEvent() && !instance.mode().m8Race()) {
                try {
                    finalizeMonthlyEventInstance(instance);
                } catch (Exception exception) {
                    lastEventFinalizeError = normalizeError(exception);
                    plugin.getLogger().warning("[challenges] monthly event finalize failed instance="
                            + instance.instanceId() + ", reason=" + lastEventFinalizeError);
                }
            }
            if (instance.mode().race()) {
                try {
                    finalizeRaceInstance(instance);
                } catch (Exception exception) {
                    lastEventFinalizeError = normalizeError(exception);
                    plugin.getLogger().warning("[challenges] race finalize failed instance="
                            + instance.instanceId() + ", reason=" + lastEventFinalizeError);
                }
            }
            ChallengeInstance expired = new ChallengeInstance(
                    instance.instanceId(),
                    instance.townId(),
                    instance.cycleKey(),
                    instance.cycleType(),
                    instance.windowKey(),
                    instance.challengeId(),
                    instance.challengeName(),
                    instance.objectiveType(),
                    instance.category(),
                    instance.baseTarget(),
                    instance.target(),
                    instance.excellenceTarget(),
                    instance.fairnessMultiplier(),
                    instance.activeContributors7d(),
                    instance.sizeSnapshot(),
                    instance.onlineSnapshot(),
                    instance.targetFormulaVersion(),
                    instance.variantType(),
                    instance.focusType(),
                    instance.focusKey(),
                    instance.focusLabel(),
                    instance.biomeKey(),
                    instance.dimensionKey(),
                    instance.signatureKey(),
                    instance.mode(),
                    ChallengeInstanceStatus.EXPIRED,
                    instance.winnerTownId(),
                    instance.winnerPlayerId(),
                    instance.cycleStartAt(),
                    instance.cycleEndAt(),
                    instance.startedAt(),
                    now
            );
            instances.put(expired.instanceId(), expired);
            store.upsertInstance(expired);
            persistCycleRecapForInstance(expired);
        }
    }

    private void sampleOnlinePlayersSafe() {
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                recordObjective(player, ChallengeObjectiveType.PLAYTIME_MINUTES, settings.playtimeTickMinutes(), ChallengeObjectiveContext.SAMPLED);
                recordStatisticCounter(player, ChallengeObjectiveType.VILLAGER_TRADE, player.getStatistic(org.bukkit.Statistic.TRADED_WITH_VILLAGER));
                recordStatisticCounter(player, ChallengeObjectiveType.ECONOMY_ADVANCED, player.getStatistic(org.bukkit.Statistic.TRADED_WITH_VILLAGER));
                recordStatisticCounter(player, ChallengeObjectiveType.TRANSPORT_DISTANCE, totalDistanceStat(player));
            }
        } catch (Exception exception) {
            lastError = normalizeError(exception);
            plugin.getLogger().warning("[challenges] sampler failed reason=" + lastError);
        }
    }

    private static long totalDistanceStat(Player player) {
        long total = 0L;
        total += safeStatistic(player, org.bukkit.Statistic.WALK_ONE_CM);
        total += safeStatistic(player, org.bukkit.Statistic.SPRINT_ONE_CM);
        total += safeStatistic(player, org.bukkit.Statistic.CROUCH_ONE_CM);
        total += safeStatistic(player, org.bukkit.Statistic.SWIM_ONE_CM);
        total += safeStatistic(player, org.bukkit.Statistic.AVIATE_ONE_CM);
        total += safeStatistic(player, org.bukkit.Statistic.MINECART_ONE_CM);
        total += safeStatistic(player, org.bukkit.Statistic.BOAT_ONE_CM);
        total += safeStatistic(player, org.bukkit.Statistic.HORSE_ONE_CM);
        total += safeStatistic(player, org.bukkit.Statistic.PIG_ONE_CM);
        total += safeStatistic(player, org.bukkit.Statistic.STRIDER_ONE_CM);
        return total / 100L;
    }

    private static long safeStatistic(Player player, org.bukkit.Statistic statistic) {
        try {
            return Math.max(0, player.getStatistic(statistic));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private int onlinePlayerSnapshotNow() {
        try {
            if (Bukkit.isPrimaryThread()) {
                return Math.max(0, Bukkit.getOnlinePlayers().size());
            }
            return Math.max(0, Bukkit.getScheduler()
                    .callSyncMethod(plugin, () -> Bukkit.getOnlinePlayers().size())
                    .get(2, TimeUnit.SECONDS));
        } catch (Exception exception) {
            return Math.max(0, currentOnlineSnapshot);
        }
    }

    private void recordObjectiveInternal(
            int townId,
            String townName,
            UUID playerId,
            ChallengeObjectiveType objectiveType,
            int amount,
            ChallengeObjectiveContext context
    ) {
        ChallengeObjectiveContext safeContext = context == null ? ChallengeObjectiveContext.DEFAULT_ACTION : context;
        int effectiveAmount = applyObjectiveAmountMultiplier(objectiveType, amount, safeContext);
        if (effectiveAmount <= 0) {
            return;
        }
        atlasService.recordObjective(townId, playerId, objectiveType, effectiveAmount, safeContext);
        storyModeService.syncTownRewards(townId, playerId);
        for (ChallengeInstance instance : instances.values()) {
            if (!instance.active()) {
                continue;
            }
            Instant now = Instant.now();
            if (instance.cycleStartAt() != null && now.isBefore(instance.cycleStartAt())) {
                continue;
            }
            if (instance.mode().race() && instance.winnerTownId() != null) {
                continue;
            }
            if (!instance.globalInstance() && (instance.townId() == null || instance.townId() != townId)) {
                continue;
            }
            if (instance.mode().m8Race() && !isTownEligibleForMode(townId, instance.mode())) {
                continue;
            }
            if (!objectiveMatches(instance, objectiveType)) {
                continue;
            }
            if (safeContext.sampled() && !settings.antiAbuse().isSampledAllowed(objectiveType)) {
                continue;
            }
            if (instance.specificVariant() && !matchesSpecificFocus(instance, safeContext)) {
                continue;
            }
            if (!matchesInstanceContext(instance, safeContext)) {
                continue;
            }
            if (safeContext.dedupeKey() != null && !safeContext.dedupeKey().isBlank()) {
                String dedupeKey = "inst:" + instance.instanceId()
                        + "|town:" + townId
                        + "|cycle:" + instance.cycleKey()
                        + "|ctx:" + safeContext.dedupeKey();
                boolean inserted = objectiveDedupeStore.tryMarkProcessed(
                        dedupeKey,
                        instance.instanceId(),
                        townId,
                        playerId,
                        objectiveType,
                        Instant.now()
                );
                if (!inserted) {
                    dedupeDrops++;
                    addLong(runtimeDroppedByReason, "abuse:dedupe-context", 1L);
                    continue;
                }
            }
            ChallengeAntiAbuseService.Decision abuseDecision = antiAbuseService.evaluate(
                    instance,
                    townId,
                    playerId,
                    objectiveType,
                    effectiveAmount,
                    safeContext,
                    currentDailyKey,
                    dailyTargetByBucket.getOrDefault(
                            townId + "|" + antiAbuseService.bucketForCategory(instance.category()),
                            instance.target()
                    )
            );
            if (!abuseDecision.allowed()) {
                deniedObjectiveHits++;
                continue;
            }
            Map<Integer, TownChallengeProgress> byTown = progressByInstance.computeIfAbsent(instance.instanceId(), ignored -> new ConcurrentHashMap<>());
            TownChallengeProgress current = byTown.getOrDefault(
                    townId,
                    new TownChallengeProgress(instance.instanceId(), townId, 0, null, null, Instant.now())
            );
            if (current.excellenceCompleted(instance.excellenceTarget())) {
                continue;
            }
            int acceptedAmount = Math.max(0, abuseDecision.acceptedAmount());
            if (instance.objectiveType() == ChallengeObjectiveType.TEAM_PHASE_QUEST) {
                acceptedAmount = adjustTeamPhaseAcceptedAmount(instance, current.progress(), objectiveType, acceptedAmount);
            } else if (instance.objectiveType() == ChallengeObjectiveType.SECRET_QUEST) {
                acceptedAmount = adjustSecretQuestAcceptedAmount(instance, current.progress(), safeContext, acceptedAmount);
            }
            if (acceptedAmount <= 0) {
                continue;
            }
            acceptedObjectiveHits++;
            int newProgress = Math.min(instance.excellenceTarget(), current.progress() + acceptedAmount);
            Instant completedAt = current.completedAt();
            if (completedAt == null && newProgress >= instance.target()) {
                if (instance.objectiveType() == ChallengeObjectiveType.VAULT_DELIVERY) {
                    if (tryConsumeVaultDelivery(instance, townId)) {
                        completedAt = now;
                    } else {
                        newProgress = Math.min(instance.excellenceTarget(), Math.max(0, instance.target() - 1));
                    }
                } else {
                    completedAt = now;
                }
            }
            Instant excellenceCompletedAt = current.excellenceCompletedAt();
            if (excellenceCompletedAt == null && newProgress >= instance.excellenceTarget()) {
                excellenceCompletedAt = now;
            }
            TownChallengeProgress updated = new TownChallengeProgress(
                    instance.instanceId(),
                    townId,
                    newProgress,
                    completedAt,
                    excellenceCompletedAt,
                    now
            );
            byTown.put(townId, updated);
            store.upsertProgress(updated);
            antiAbuseService.onAccepted(instance, townId, playerId, acceptedAmount, currentDailyKey);
            if (playerId != null) {
                progressBossBarService.showProgress(
                        playerId,
                        instance.challengeName(),
                        instance.objectiveType(),
                        updated.progress(),
                        instance.target(),
                        instance.excellenceTarget()
                );
            }

            if (current.completedAt() == null && completedAt != null) {
                handleCompletion(instance, updated, townName, playerId);
            }
            if (current.excellenceCompletedAt() == null && excellenceCompletedAt != null) {
                handleExcellence(instance, updated, townName, playerId);
            }
        }
    }

    private static int applyObjectiveAmountMultiplier(
            ChallengeObjectiveType objectiveType,
            int amount,
            ChallengeObjectiveContext context
    ) {
        int safeAmount = Math.max(0, amount);
        if (safeAmount <= 0) {
            return 0;
        }
        int multiplier = materialContributionMultiplier(objectiveType, context);
        if (multiplier <= 1) {
            return safeAmount;
        }
        long weighted = (long) safeAmount * multiplier;
        return (int) Math.min(Integer.MAX_VALUE, weighted);
    }

    private static int materialContributionMultiplier(
            ChallengeObjectiveType objectiveType,
            ChallengeObjectiveContext context
    ) {
        if (objectiveType == null || context == null) {
            return 1;
        }
        String materialKey = context.materialKey();
        if (materialKey == null || materialKey.isBlank()) {
            return 1;
        }
        String[] tokens = materialKey.trim().toUpperCase(Locale.ROOT).split("\\|");
        if (objectiveType == ChallengeObjectiveType.BLOCK_MINE) {
            int multiplier = 1;
            for (String token : tokens) {
                String key = token == null ? null : token.trim();
                if (key == null || key.isBlank()) {
                    continue;
                }
                if (BLOCK_MINE_X3.contains(key)) {
                    multiplier = Math.max(multiplier, 3);
                } else if (BLOCK_MINE_X2.contains(key)) {
                    multiplier = Math.max(multiplier, 2);
                }
            }
            return multiplier;
        }
        if (objectiveType == ChallengeObjectiveType.RESOURCE_CONTRIBUTION) {
            int multiplier = 1;
            for (String token : tokens) {
                String key = token == null ? null : token.trim();
                if (key == null || key.isBlank()) {
                    continue;
                }
                if (RESOURCE_X3.contains(key)) {
                    multiplier = Math.max(multiplier, 3);
                } else if (RESOURCE_X2.contains(key)) {
                    multiplier = Math.max(multiplier, 2);
                }
            }
            return multiplier;
        }
        if (objectiveType == ChallengeObjectiveType.VAULT_DELIVERY) {
            return 1;
        }
        if (objectiveType == ChallengeObjectiveType.ITEM_CRAFT) {
            String key = tokens.length == 0 ? materialKey.trim().toUpperCase(Locale.ROOT) : tokens[0].trim();
            if (ITEM_CRAFT_X20.contains(key)) {
                return 20;
            }
            if (ITEM_CRAFT_X5.contains(key)) {
                return 5;
            }
            if (ITEM_CRAFT_X2.contains(key)) {
                return 2;
            }
            return 1;
        }
        return 1;
    }

    private static boolean matchesSpecificFocus(ChallengeInstance instance, ChallengeObjectiveContext context) {
        if (instance == null || !instance.specificVariant()) {
            return true;
        }
        if (context == null || instance.focusKey() == null || instance.focusKey().isBlank()) {
            return false;
        }
        return switch (instance.focusType()) {
            case MATERIAL -> matchesMaterialFocus(instance.focusKey(), context.materialKey());
            case ENTITY_TYPE -> sameToken(instance.focusKey(), context.entityTypeKey());
            case NONE -> false;
        };
    }

    private static boolean matchesMaterialFocus(String focusKey, String materialKey) {
        if (focusKey == null || focusKey.isBlank() || materialKey == null || materialKey.isBlank()) {
            return false;
        }
        if (sameToken(focusKey, materialKey)) {
            return true;
        }
        if (materialKey.indexOf('|') < 0) {
            return false;
        }
        for (String token : materialKey.split("\\|")) {
            if (sameToken(focusKey, token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesInstanceContext(ChallengeInstance instance, ChallengeObjectiveContext context) {
        if (instance == null) {
            return false;
        }
        String expectedDimension = instance.dimensionKey();
        if (expectedDimension == null || expectedDimension.isBlank()) {
            return true;
        }
        if (context == null) {
            return false;
        }
        if (expectedDimension != null && !expectedDimension.isBlank() && !sameToken(expectedDimension, context.dimensionKey())) {
            return false;
        }
        return true;
    }

    private static boolean objectiveMatches(ChallengeInstance instance, ChallengeObjectiveType incomingType) {
        if (instance == null || incomingType == null) {
            return false;
        }
        ChallengeObjectiveType instanceType = instance.objectiveType();
        if (instanceType == incomingType) {
            return true;
        }
        if (instanceType != ChallengeObjectiveType.TEAM_PHASE_QUEST) {
            return false;
        }
        return TEAM_PHASE_GATHER_OBJECTIVES.contains(incomingType)
                || TEAM_PHASE_BUILD_OBJECTIVES.contains(incomingType)
                || TEAM_PHASE_BOSS_OBJECTIVES.contains(incomingType);
    }

    private static int adjustTeamPhaseAcceptedAmount(
            ChallengeInstance instance,
            int currentProgress,
            ChallengeObjectiveType incomingType,
            int acceptedAmount
    ) {
        if (instance == null || acceptedAmount <= 0 || incomingType == null) {
            return 0;
        }
        TeamPhaseQuestCatalog.Quest quest = TeamPhaseQuestCatalog.byId(instance.challengeId());
        if (quest == null) {
            return acceptedAmount;
        }
        int safeProgress = Math.max(0, currentProgress);
        int phase1End = Math.max(1, quest.gatherTarget());
        int phase2End = phase1End + Math.max(1, quest.buildTarget());
        int phase3End = phase2End + Math.max(1, quest.bossTarget());
        if (safeProgress >= phase3End) {
            return 0;
        }
        if (safeProgress < phase1End) {
            if (!TEAM_PHASE_GATHER_OBJECTIVES.contains(incomingType)) {
                return 0;
            }
            return Math.min(acceptedAmount, phase1End - safeProgress);
        }
        if (safeProgress < phase2End) {
            if (!TEAM_PHASE_BUILD_OBJECTIVES.contains(incomingType)) {
                return 0;
            }
            return Math.min(acceptedAmount, phase2End - safeProgress);
        }
        if (!TEAM_PHASE_BOSS_OBJECTIVES.contains(incomingType)) {
            return 0;
        }
        return Math.min(acceptedAmount, phase3End - safeProgress);
    }

    private static int adjustSecretQuestAcceptedAmount(
            ChallengeInstance instance,
            int currentProgress,
            ChallengeObjectiveContext context,
            int acceptedAmount
    ) {
        if (instance == null || context == null || acceptedAmount <= 0) {
            return 0;
        }
        if (!SecretQuestCatalog.hasScript(instance.challengeId())) {
            return Math.min(1, acceptedAmount);
        }
        int safeProgress = Math.max(0, currentProgress);
        int stageCount = SecretQuestCatalog.stageCount(instance.challengeId());
        if (stageCount <= 0 || safeProgress >= stageCount) {
            return 0;
        }
        String triggerKey = secretTriggerKey(context);
        if (!SecretQuestCatalog.matchesCurrentStage(instance.challengeId(), safeProgress, triggerKey)) {
            return 0;
        }
        return 1;
    }

    private static String secretTriggerKey(ChallengeObjectiveContext context) {
        if (context == null || context.dedupeKey() == null || context.dedupeKey().isBlank()) {
            return null;
        }
        String dedupeKey = context.dedupeKey().trim().toLowerCase(Locale.ROOT);
        String prefix = "secret-trigger:";
        if (!dedupeKey.startsWith(prefix) || dedupeKey.length() <= prefix.length()) {
            return null;
        }
        return dedupeKey.substring(prefix.length());
    }

    private static boolean sameToken(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private boolean tryConsumeVaultDelivery(ChallengeInstance instance, int townId) {
        if (instance == null || townId <= 0 || instance.objectiveType() != ChallengeObjectiveType.VAULT_DELIVERY) {
            return true;
        }
        Material material = resolveVaultDeliveryMaterial(instance.focusKey());
        if (material == null || material.isAir()) {
            return false;
        }
        try {
            return cityItemVaultService.consumeMaterial(
                    townId,
                    material,
                    Math.max(1, instance.target()),
                    null,
                    "challenge:" + instance.instanceId() + ":" + material.name()
            ).join();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("[challenges] impossibile consumare materiali vault per "
                    + instance.instanceId() + ": " + exception.getMessage());
            return false;
        }
    }

    private static Material resolveVaultDeliveryMaterial(String focusKey) {
        if (focusKey == null || focusKey.isBlank()) {
            return null;
        }
        return Material.matchMaterial(focusKey.trim().toUpperCase(Locale.ROOT));
    }

    private void handleCompletion(
            ChallengeInstance instance,
            TownChallengeProgress progress,
            String townName,
            UUID playerId
    ) {
        ChallengeDefinition definition = definitions.get(instance.challengeId());
        if (definition == null) {
            return;
        }
        ChallengeRewardBundle bundle = rewardBundles.getOrDefault(
                definition.rewardBundleId(),
                ChallengeRewardBundle.empty(definition.rewardBundleId())
        );

        if (instance.mode().race()) {
            boolean won = store.tryInsertWinner(instance.instanceId(), progress.townId(), playerId, Instant.now());
            if (!won) {
                return;
            }
            ChallengeInstance completed = new ChallengeInstance(
                    instance.instanceId(),
                    instance.townId(),
                    instance.cycleKey(),
                    instance.cycleType(),
                    instance.windowKey(),
                    instance.challengeId(),
                    instance.challengeName(),
                    instance.objectiveType(),
                    instance.category(),
                    instance.baseTarget(),
                    instance.target(),
                    instance.excellenceTarget(),
                    instance.fairnessMultiplier(),
                    instance.activeContributors7d(),
                    instance.sizeSnapshot(),
                    instance.onlineSnapshot(),
                    instance.targetFormulaVersion(),
                    instance.variantType(),
                    instance.focusType(),
                    instance.focusKey(),
                    instance.focusLabel(),
                    instance.biomeKey(),
                    instance.dimensionKey(),
                    instance.signatureKey(),
                    instance.mode(),
                    ChallengeInstanceStatus.COMPLETED,
                    progress.townId(),
                    playerId,
                    instance.cycleStartAt(),
                    instance.cycleEndAt(),
                    instance.startedAt(),
                    Instant.now()
            );
            instances.put(completed.instanceId(), completed);
            store.upsertInstance(completed);
            announceRaceCompleted(completed, townName);
            showCompletionTitle(progress.townId(), completed.challengeName(), true);
            if (settings.raceRewards().top1Enabled()) {
                applyRewards(
                        completed,
                        progress.townId(),
                        townName,
                        playerId,
                        applySeasonalSecretRewardBoost(completed, bundle.winner()),
                        "race_first_completion"
                );
            }
            return;
        }

        showCompletionTitle(progress.townId(), instance.challengeName(), false);
        if (instance.mode() == ChallengeMode.DAILY_STANDARD) {
            applyDailyFirstCompletionBonus(instance, progress.townId(), townName, playerId);
        }

        if (instance.mode().weeklyStandard()) {
            applyWeeklyFirstCompletionBonus(instance, progress.townId(), townName, playerId);
        }

        if (instance.mode().weeklyStandard() && completedWeeklySet(progress.townId(), instance.cycleKey())) {
            streakService.markWeeklyCompleted(progress.townId(), instance.cycleKey(), previousWeeklyKey);
            onWeeklyPackageCompleted(progress.townId(), instance.cycleKey(), playerId);
        }
        if (instance.mode().monthlyEvent()) {
            announceMonthlyEventCompleted(instance, townName);
        }
        if (instance.mode().seasonCodex()) {
            applySeasonCodexPoints(instance, progress.townId(), townName, playerId);
        }
        ChallengeRewardSpec completionReward = applySeasonalSecretRewardBoost(instance, bundle.completion());
        applyRewards(instance, progress.townId(), townName, playerId, completionReward, "completion");
        applyPersonalCompletionRewards(instance, progress.townId(), townName, completionReward);
        awardM1SealMilestone(instance, progress.townId());
    }

    private void awardM1SealMilestone(ChallengeInstance instance, int townId) {
        if (atlasService.enabled() && !atlasService.interimSealFallbackEnabled()) {
            return;
        }
        if (townId <= 0 || instance == null) {
            return;
        }
        if (!(instance.mode().weeklyStandard()
                || instance.mode() == ChallengeMode.MONTHLY_STANDARD
                || instance.mode().monthlyEvent()
                || instance.mode().monthlyLedger()
                || instance.mode().seasonCodex())) {
            return;
        }
        String milestoneKey = "m1:" + instance.cycleType().name().toLowerCase(Locale.ROOT) + ':' + instance.instanceId();
        cityLevelService.grantSealMilestone(townId, milestoneKey);
    }

    public boolean atlasEnabled() {
        return atlasService.enabled();
    }

    public boolean storyModeEnabled() {
        return storyModeService.enabled();
    }

    public CityAtlasService.AtlasSealSnapshot atlasSeals(int townId) {
        return atlasService.sealsSnapshot(townId);
    }

    public List<CityStoryModeService.StoryModeChapterSnapshot> storyModeChapterSnapshots(int townId, UUID actorId) {
        return storyModeService.chapterSnapshots(townId, actorId);
    }

    public CityStoryModeService.StoryModeChapterSnapshot storyModeChapterSnapshot(int townId, String chapterId, UUID actorId) {
        return storyModeService.chapterSnapshot(townId, chapterId, actorId);
    }

    public int storyModeMaxPage(int pageSize) {
        return storyModeService.maxPage(pageSize);
    }

    public CityStoryModeService.StoryModeDiagnostics storyModeDiagnostics(int townId) {
        return storyModeService.diagnostics(townId);
    }

    public int questBatchCapacityForTownPreview(int townId) {
        return questBatchCapacityForTown(townId);
    }

    public List<CityAtlasService.AtlasChapterSnapshot> atlasChapterSnapshots(int townId) {
        return atlasService.chapterSnapshots(townId);
    }

    public CompletableFuture<CityAtlasService.ClaimResult> claimAtlasTierReward(
            Player actor,
            int townId,
            String familyId,
            AtlasTier tier
    ) {
        CompletableFuture<CityAtlasService.ClaimResult> future = new CompletableFuture<>();
        ioExecutor.execute(() -> {
            try {
                future.complete(atlasService.claimTierReward(actor, townId, familyId, tier));
            } catch (Exception exception) {
                future.complete(new CityAtlasService.ClaimResult(false, normalizeError(exception)));
            }
        });
        return future;
    }

    private void handleExcellence(
            ChallengeInstance instance,
            TownChallengeProgress progress,
            String townName,
            UUID playerId
    ) {
        ChallengeDefinition definition = definitions.get(instance.challengeId());
        if (definition == null || instance.mode().race()) {
            return;
        }
        ChallengeRewardBundle bundle = rewardBundles.getOrDefault(
                definition.rewardBundleId(),
                ChallengeRewardBundle.empty(definition.rewardBundleId())
        );
        applyRewards(
                instance,
                progress.townId(),
                townName,
                playerId,
                applySeasonalSecretRewardBoost(instance, bundle.excellence()),
                "excellence"
        );
    }

    private boolean completedWeeklySet(int townId, String cycleKey) {
        if (townId <= 0 || cycleKey == null || cycleKey.isBlank()) {
            return false;
        }
        boolean seenWeekly = false;
        for (ChallengeInstance instance : instances.values()) {
            if (!instance.active() || instance.mode() != ChallengeMode.WEEKLY_STANDARD || !cycleKey.equals(instance.cycleKey())) {
                continue;
            }
            if (!instance.globalInstance() && (instance.townId() == null || instance.townId() != townId)) {
                continue;
            }
            seenWeekly = true;
            Map<Integer, TownChallengeProgress> byTown = progressByInstance.getOrDefault(instance.instanceId(), Map.of());
            TownChallengeProgress progress = byTown.get(townId);
            if (progress == null || !progress.baseCompleted(instance.target())) {
                return false;
            }
        }
        return seenWeekly;
    }

    public WeeklyFirstCompletionStatus weeklyFirstCompletionStatus(int townId) {
        if (townId <= 0) {
            return new WeeklyFirstCompletionStatus(false, false);
        }
        CityChallengeSettings.M6Settings m6 = settings.m6();
        boolean enabled = m6 != null
                && m6.firstCompletion() != null
                && m6.firstCompletion().weekly() != null
                && m6.firstCompletion().weekly().enabled();
        String weeklyKey = (currentWeeklyKey == null || currentWeeklyKey.isBlank() || "-".equals(currentWeeklyKey))
                ? LocalDate.now(settings.timezone()).format(WEEK_KEY)
                : currentWeeklyKey;
        return new WeeklyFirstCompletionStatus(enabled, weeklyFirstCompletionClaimed(townId, weeklyKey));
    }

    private boolean weeklyFirstCompletionClaimed(int townId, String weeklyCycleKey) {
        if (townId <= 0 || weeklyCycleKey == null || weeklyCycleKey.isBlank()) {
            return false;
        }
        return rewardLedgerKeys.contains(weeklyFirstCompletionGrantKey(townId, weeklyCycleKey));
    }

    private String dailyFirstCompletionGrantKey(int townId, String dailyCycleKey, int order) {
        return "reward:daily-first:" + dailyCycleKey + ":town:" + townId + ":slot:" + order;
    }

    private int dailyStandardCompletionOrder(int townId, String dailyCycleKey, String instanceId) {
        if (townId <= 0 || dailyCycleKey == null || dailyCycleKey.isBlank() || instanceId == null || instanceId.isBlank()) {
            return -1;
        }
        List<ChallengeInstance> completed = instances.values().stream()
                .filter(instance -> instance.mode() == ChallengeMode.DAILY_STANDARD)
                .filter(instance -> instance.status() == ChallengeInstanceStatus.COMPLETED)
                .filter(instance -> dailyCycleKey.equals(instance.cycleKey()))
                .filter(instance -> instance.townId() != null && instance.townId() == townId)
                .sorted(Comparator
                        .comparing((ChallengeInstance entry) -> entry.endedAt() == null ? Instant.EPOCH : entry.endedAt())
                        .thenComparing(ChallengeInstance::instanceId))
                .toList();
        for (int index = 0; index < completed.size(); index++) {
            if (instanceId.equalsIgnoreCase(completed.get(index).instanceId())) {
                return index + 1;
            }
        }
        return -1;
    }

    private void applyDailyFirstCompletionBonus(
            ChallengeInstance instance,
            int townId,
            String townName,
            UUID playerId
    ) {
        if (instance == null || townId <= 0 || instance.mode() != ChallengeMode.DAILY_STANDARD) {
            return;
        }
        CityChallengeSettings.M6Settings m6 = settings.m6();
        CityChallengeSettings.M6FirstCompletionDailySettings daily = m6 == null || m6.firstCompletion() == null
                ? null
                : m6.firstCompletion().daily();
        if (daily == null || !daily.enabled() || daily.reward() == null || daily.reward().empty()) {
            return;
        }
        String dailyCycleKey = instance.cycleKey();
        if (dailyCycleKey == null || dailyCycleKey.isBlank()) {
            return;
        }
        int order = dailyStandardCompletionOrder(townId, dailyCycleKey, instance.instanceId());
        if (order <= 0 || order > Math.max(1, daily.maxClaims())) {
            dailyFirstRewardSkipCount++;
            return;
        }
        String grantKey = dailyFirstCompletionGrantKey(townId, dailyCycleKey, order);
        if (!reserveGrantKey(grantKey) || !store.markRewardGrant(
                grantKey,
                townId,
                instance.instanceId(),
                ChallengeRewardType.COMMAND_CONSOLE,
                "daily-first-marker",
                Instant.now()
        )) {
            dailyFirstRewardSkipCount++;
            return;
        }
        int before = rewardLedgerKeys.size();
        applyRewards(instance, townId, townName, playerId, daily.reward(), "daily_first_completion");
        if (rewardLedgerKeys.size() > before) {
            dailyFirstRewardGrantCount++;
        } else {
            dailyFirstRewardSkipCount++;
        }
    }

    private String weeklyFirstCompletionGrantKey(int townId, String weeklyCycleKey) {
        return "reward:weekly-first:" + weeklyCycleKey + ":town:" + townId;
    }

    private String weeklyPackageCompletionGrantKey(int townId, String weeklyCycleKey) {
        return "reward:weekly-package-complete:" + weeklyCycleKey + ":town:" + townId;
    }

    private void applyWeeklyFirstCompletionBonus(
            ChallengeInstance instance,
            int townId,
            String townName,
            UUID playerId
    ) {
        if (instance == null || townId <= 0 || !instance.mode().weeklyStandard()) {
            return;
        }
        CityChallengeSettings.M6Settings m6 = settings.m6();
        CityChallengeSettings.M6FirstCompletionWeeklySettings weekly = m6 == null || m6.firstCompletion() == null
                ? null
                : m6.firstCompletion().weekly();
        if (weekly == null || !weekly.enabled() || weekly.reward() == null || weekly.reward().empty()) {
            return;
        }
        String weeklyCycleKey = instance.cycleKey();
        if (weeklyCycleKey == null || weeklyCycleKey.isBlank()) {
            return;
        }
        if (!store.tryInsertWeeklyCompletion(townId, weeklyCycleKey, Instant.now())) {
            weeklyFirstRewardSkipCount++;
            return;
        }
        String grantKey = weeklyFirstCompletionGrantKey(townId, weeklyCycleKey);
        if (!reserveGrantKey(grantKey) || !store.markRewardGrant(
                grantKey,
                townId,
                instance.instanceId(),
                ChallengeRewardType.COMMAND_CONSOLE,
                "weekly-first-marker",
                Instant.now()
        )) {
            weeklyFirstRewardSkipCount++;
            return;
        }
        int before = rewardLedgerKeys.size();
        applyRewards(instance, townId, townName, playerId, weekly.reward(), "weekly_first_completion");
        if (rewardLedgerKeys.size() > before) {
            weeklyFirstRewardGrantCount++;
        } else {
            weeklyFirstRewardSkipCount++;
        }
    }

    private void onWeeklyPackageCompleted(int townId, String weeklyCycleKey, UUID actorId) {
        if (townId <= 0 || weeklyCycleKey == null || weeklyCycleKey.isBlank()) {
            return;
        }
        String packageGrantKey = weeklyPackageCompletionGrantKey(townId, weeklyCycleKey);
        if (!reserveGrantKey(packageGrantKey) || !store.markRewardGrant(
                packageGrantKey,
                townId,
                "weekly-package:" + weeklyCycleKey,
                ChallengeRewardType.COMMAND_CONSOLE,
                "weekly-package-marker",
                Instant.now()
        )) {
            return;
        }
        String seasonKey = seasonKeyNow();
        String key = governanceKey(townId, seasonKey);
        CityChallengeRepository.MilestoneState current = milestonesByTownSeason.getOrDefault(
                key,
                new CityChallengeRepository.MilestoneState(
                        townId,
                        seasonKey,
                        0,
                        false,
                        false,
                        false,
                        false,
                        Instant.now()
                )
        );
        int nextCompleted = current.weeklyCompletedCount() + 1;
        CityChallengeSettings.MilestoneSettings milestoneSettings = settings.milestones();

        boolean m1Done = current.milestone1Done() || nextCompleted >= milestoneSettings.weeklyMilestone1At();
        boolean m2Done = current.milestone2Done() || nextCompleted >= milestoneSettings.weeklyMilestone2At();
        boolean m3Done = current.milestone3Done() || nextCompleted >= milestoneSettings.weeklyMilestone3At();
        boolean finalDone = current.seasonalFinalDone() || nextCompleted >= milestoneSettings.seasonalFinalAt();

        CityChallengeRepository.MilestoneState next = new CityChallengeRepository.MilestoneState(
                townId,
                seasonKey,
                nextCompleted,
                m1Done,
                m2Done,
                m3Done,
                finalDone,
                Instant.now()
        );
        milestonesByTownSeason.put(key, next);
        store.upsertMilestoneState(next);

        if (!current.milestone1Done() && m1Done) {
            applyMilestoneReward(townId, seasonKey, actorId, "weekly_1", milestoneSettings.weeklyMilestone1Reward());
        }
        if (!current.milestone2Done() && m2Done) {
            applyMilestoneReward(townId, seasonKey, actorId, "weekly_2", milestoneSettings.weeklyMilestone2Reward());
        }
        if (!current.milestone3Done() && m3Done) {
            applyMilestoneReward(townId, seasonKey, actorId, "weekly_3", milestoneSettings.weeklyMilestone3Reward());
        }
        if (!current.seasonalFinalDone() && finalDone) {
            applyMilestoneReward(townId, seasonKey, actorId, "season_final", milestoneSettings.seasonalFinalReward());
        }
    }

    private void applyMilestoneReward(
            int townId,
            String seasonKey,
            UUID actorId,
            String milestoneId,
            ChallengeRewardSpec spec
    ) {
        if (spec == null || spec.empty()) {
            return;
        }
        ChallengeRewardSpec effectiveSpec = economyValueService.effectiveRewardSpec(spec, EconomyBalanceCategory.MILESTONE_MONEY_REWARD);
        String townName = townNameById(townId);
        String pseudoInstanceId = "milestone:" + seasonKey + ":" + milestoneId;
        if (effectiveSpec.xpCity() > 0.0D) {
            long scaled = Math.max(1L, Math.round(effectiveSpec.xpCity() * cityLevelService.settings().xpScale()));
            String grantKey = "reward:xp:milestone:" + seasonKey + ":" + milestoneId + ":town:" + townId;
            if (reserveGrantKey(grantKey)
                    && store.markRewardGrant(grantKey, townId, pseudoInstanceId, ChallengeRewardType.XP_CITY,
                    Long.toString(scaled), Instant.now())) {
                cityLevelService.grantXpBonus(townId, scaled, actorId, "milestone-" + milestoneId + ":" + seasonKey);
            }
        }
        if (effectiveSpec.moneyCity() > 0.0D) {
            String grantKey = "reward:money:milestone:" + seasonKey + ":" + milestoneId + ":town:" + townId;
            if (reserveGrantKey(grantKey)
                    && store.markRewardGrant(
                    grantKey,
                    townId,
                    pseudoInstanceId,
                    ChallengeRewardType.MONEY_CITY,
                    Double.toString(effectiveSpec.moneyCity()),
                    Instant.now()
            )) {
                creditTownMoneyReward(townId, actorId, effectiveSpec.moneyCity(), "milestone-" + milestoneId + ":" + seasonKey);
            }
        }
        if (effectiveSpec.vaultItems() != null && !effectiveSpec.vaultItems().isEmpty()) {
            String grantKey = "reward:vault:milestone:" + seasonKey + ":" + milestoneId + ":town:" + townId;
            if (reserveGrantKey(grantKey)
                    && store.markRewardGrant(grantKey, townId, pseudoInstanceId, ChallengeRewardType.ITEM_VAULT,
                    Integer.toString(effectiveSpec.vaultItems().values().stream().mapToInt(Integer::intValue).sum()), Instant.now())) {
                cityItemVaultService.depositRewardItems(townId, effectiveSpec.vaultItems(), "milestone-" + milestoneId + ":" + seasonKey);
            }
        }
        if (effectiveSpec.consoleCommands() != null && !effectiveSpec.consoleCommands().isEmpty()) {
            for (int i = 0; i < effectiveSpec.consoleCommands().size(); i++) {
                String command = effectiveSpec.consoleCommands().get(i);
                if (command == null || command.isBlank()) {
                    continue;
                }
                String commandKey = i < effectiveSpec.commandKeys().size() ? effectiveSpec.commandKeys().get(i) : "";
                String grantKey = "reward:cmd:milestone:" + seasonKey + ":" + milestoneId + ":town:" + townId + ":" + i;
                if (!reserveGrantKey(grantKey)) {
                    continue;
                }
                String rendered = dispatchStructuredBroadcast(commandKey, null, townId, townName, seasonKey, effectiveSpec);
                if (rendered == null || rendered.isBlank()) {
                    rendered = command
                            .replace("{town_id}", Integer.toString(townId))
                            .replace("{town_name}", townName == null ? ("#" + townId) : townName)
                            .replace("{cycle_key}", seasonKey)
                            .replace("{challenge_id}", "milestone_" + milestoneId)
                            .replace("{player_uuid}", actorId == null ? "-" : actorId.toString())
                            .replace("{player_name}", actorId == null ? "-" : actorId.toString());
                }
                if (!store.markRewardGrant(grantKey, townId, pseudoInstanceId, ChallengeRewardType.COMMAND_CONSOLE,
                        rendered, Instant.now())) {
                    continue;
                }
                if (!ChallengeBroadcastFormatter.handlesRewardKey(commandKey)) {
                    dispatchRewardCommand(null, townId, rendered);
                }
            }
        }
    }

    private void applySeasonCodexPoints(
            ChallengeInstance instance,
            int townId,
            String townName,
            UUID actorId
    ) {
        if (instance == null || townId <= 0 || !instance.mode().seasonCodex()) {
            return;
        }
        int points = seasonCodexPointsForMode(instance.mode());
        if (points <= 0) {
            return;
        }
        String grantKey = "reward:codex:sp:" + instance.instanceId() + ":town:" + townId;
        if (!reserveGrantKey(grantKey) || !store.markRewardGrant(
                grantKey,
                townId,
                instance.instanceId(),
                ChallengeRewardType.COMMAND_CONSOLE,
                "points=" + points,
                Instant.now()
        )) {
            return;
        }
        String stateKey = seasonCodexPointsStateKey(instance.cycleKey(), townId);
        int previous = parseIntSafe(store.getRuntimeState(stateKey).orElse("0"), 0);
        int next = Math.max(0, previous + points);
        store.setRuntimeState(stateKey, Integer.toString(next));
        applySeasonCodexMilestones(instance, townId, previous, next, actorId);
        announceTownMiniMessage(
                townId,
                ChallengeBroadcastFormatter.renderCodexProgress(townName, instance.objectiveType(), points, next)
        );
    }

    private void applySeasonCodexMilestones(
            ChallengeInstance instance,
            int townId,
            int previousPoints,
            int nextPoints,
            UUID actorId
    ) {
        if (instance == null || townId <= 0 || nextPoints <= previousPoints) {
            return;
        }
        for (Integer threshold : SEASON_CODEX_MILESTONE_THRESHOLDS) {
            if (threshold == null || threshold <= 0) {
                continue;
            }
            if (previousPoints >= threshold || nextPoints < threshold) {
                continue;
            }
            String grantKey = "reward:codex:milestone:" + instance.cycleKey() + ":town:" + townId + ":sp:" + threshold;
            if (!reserveGrantKey(grantKey) || !store.markRewardGrant(
                    grantKey,
                    townId,
                    instance.instanceId(),
                    ChallengeRewardType.XP_CITY,
                    Integer.toString(threshold),
                    Instant.now()
            )) {
                continue;
            }
            long scaled = Math.max(1L, Math.round((threshold / 2.0D) * cityLevelService.settings().xpScale()));
            cityLevelService.grantXpBonus(
                    townId,
                    scaled,
                    actorId,
                    "season-codex-milestone:" + instance.cycleKey() + ":" + threshold
            );
            announceTownMiniMessage(townId, ChallengeBroadcastFormatter.renderCodexMilestone(threshold));
        }
    }

    private static String seasonCodexPointsStateKey(String cycleKey, int townId) {
        return "season-codex:sp:" + safeText(cycleKey, "-") + ":town:" + townId;
    }

    private static int seasonCodexPointsForMode(ChallengeMode mode) {
        if (mode == null) {
            return 0;
        }
        return switch (mode) {
            case SEASON_CODEX_ACT_I -> 5;
            case SEASON_CODEX_ACT_II, SEASON_CODEX_ACT_III -> 10;
            case SEASON_CODEX_ELITE -> 20;
            case SEASON_CODEX_HIDDEN_RELIC -> 25;
            default -> 0;
        };
    }

    private static int parseIntSafe(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void finalizeMonthlyEventInstance(ChallengeInstance instance) {
        if (!instance.globalInstance()) {
            return;
        }
        Map<Integer, TownChallengeProgress> progress = progressByInstance.getOrDefault(instance.instanceId(), Map.of());
        List<MonthlyEventService.LeaderboardEntry> leaderboard = monthlyEventService.leaderboardFor(instance, progress);
        lastEventLeaderboardSize = leaderboard.size();
        if (leaderboard.isEmpty()) {
            return;
        }
        CityChallengeSettings.EventRewardsSettings rewards = settings.eventRewards();
        if (rewards == null) {
            return;
        }
        if (rewards.top1Enabled() && !rewards.top1Reward().empty() && leaderboard.size() >= 1) {
            applyEventReward(instance, leaderboard.get(0), rewards.top1Reward(), "event_top_1");
        }
        if (rewards.top2Enabled() && !rewards.top2Reward().empty() && leaderboard.size() >= 2) {
            applyEventReward(instance, leaderboard.get(1), rewards.top2Reward(), "event_top_2");
        }
        if (rewards.top3Enabled() && !rewards.top3Reward().empty() && leaderboard.size() >= 3) {
            applyEventReward(instance, leaderboard.get(2), rewards.top3Reward(), "event_top_3");
        }
        if (rewards.thresholdEnabled() && !rewards.thresholdReward().empty()) {
            for (MonthlyEventService.LeaderboardEntry entry : leaderboard) {
                if (entry.progress() < rewards.thresholdTargetProgress()) {
                    continue;
                }
                applyEventReward(instance, entry, rewards.thresholdReward(), "event_threshold");
            }
        }
    }

    private void finalizeRaceInstance(ChallengeInstance instance) {
        if (instance != null && instance.mode().seasonalRace()) {
            return;
        }
        Map<Integer, TownChallengeProgress> progress = progressByInstance.getOrDefault(instance.instanceId(), Map.of());
        List<MonthlyEventService.LeaderboardEntry> leaderboard = competitiveFinalizeService.leaderboard(instance, progress);
        if (leaderboard.isEmpty()) {
            return;
        }
        CityChallengeSettings.EventRewardsSettings rewards = settings.raceRewards();
        if (rewards == null) {
            return;
        }
        if (instance.mode().m8Race()) {
            if (instance.winnerTownId() == null && rewards.top1Enabled() && !rewards.top1Reward().empty()) {
                applyEventReward(instance, leaderboard.getFirst(), rewards.top1Reward(), "race_top_1");
            }
            return;
        }
        if (instance.winnerTownId() == null && rewards.top1Enabled() && !rewards.top1Reward().empty()) {
            applyEventReward(instance, leaderboard.getFirst(), rewards.top1Reward(), "race_top_1");
        }
        if (rewards.top2Enabled() && !rewards.top2Reward().empty() && leaderboard.size() >= 2) {
            applyEventReward(instance, leaderboard.get(1), rewards.top2Reward(), "race_top_2");
        }
        if (rewards.top3Enabled() && !rewards.top3Reward().empty() && leaderboard.size() >= 3) {
            applyEventReward(instance, leaderboard.get(2), rewards.top3Reward(), "race_top_3");
        }
        if (rewards.thresholdEnabled() && !rewards.thresholdReward().empty()) {
            int thresholdTarget = Math.max(instance.target(), rewards.thresholdTargetProgress());
            for (MonthlyEventService.LeaderboardEntry entry : leaderboard) {
                if (entry.progress() < thresholdTarget) {
                    continue;
                }
                applyEventReward(instance, entry, rewards.thresholdReward(), "race_threshold");
            }
        }
    }

    private void applyEventReward(
            ChallengeInstance instance,
            MonthlyEventService.LeaderboardEntry entry,
            ChallengeRewardSpec spec,
            String reasonKind
    ) {
        String townName = townNameById(entry.townId());
        int before = rewardLedgerKeys.size();
        applyRewards(instance, entry.townId(), townName, null, spec, reasonKind);
        int delta = rewardLedgerKeys.size() - before;
        if (delta > 0) {
            eventRewardGrantCount += delta;
        }
    }

    private void applyRewards(
            ChallengeInstance instance,
            int townId,
            String townName,
            UUID playerId,
            ChallengeRewardSpec spec,
            String reasonKind
    ) {
        if (spec == null || spec.empty()) {
            return;
        }
        ChallengeRewardSpec effectiveSpec = applyCycleRewardTuning(instance == null ? null : instance.mode(), spec, reasonKind);
        effectiveSpec = economyValueService.effectiveRewardSpec(effectiveSpec, rewardCategoryForReason(reasonKind));
        ChallengeDefinition definition = instance == null ? null : definitions.get(instance.challengeId());
        String family = definition == null ? "generic" : definition.objectiveFamily();
        double effectiveXp = effectiveCityXp(instance, effectiveSpec, reasonKind);
        if (effectiveXp > 0.0D && rewardPolicyService.allow(
                ChallengeRewardPolicyService.RewardClass.XP_CITY,
                instance == null ? null : instance.mode(),
                family,
                instance == null ? null : instance.objectiveType())) {
            long scaled = Math.max(1L, Math.round(effectiveXp * cityLevelService.settings().xpScale()));
            String grantKey = "reward:xp:" + reasonKind + ':' + instance.instanceId() + ":town:" + townId;
            if (reserveGrantKey(grantKey)
                    && store.markRewardGrant(grantKey, townId, instance.instanceId(), ChallengeRewardType.XP_CITY,
                    Long.toString(scaled), Instant.now())) {
                cityLevelService.grantXpBonus(townId, scaled, playerId, "challenge-" + reasonKind + ':' + instance.instanceId());
                recordCycleReward(instance, townId, reasonKind, ChallengeRewardType.XP_CITY, scaled);
            }
            if (instance.mode().weeklyStandard() && !"weekly_first_completion".equalsIgnoreCase(reasonKind)) {
                double bonusRatio = streakService.weeklyBonusRatioForTown(townId);
                long bonusScaled = Math.max(0L, Math.round(scaled * bonusRatio));
                if (bonusScaled > 0L) {
                    String bonusGrantKey = "reward:xp:streak-bonus:" + reasonKind + ':' + instance.instanceId() + ":town:" + townId;
                    if (reserveGrantKey(bonusGrantKey)
                            && store.markRewardGrant(
                            bonusGrantKey,
                            townId,
                            instance.instanceId(),
                            ChallengeRewardType.XP_CITY,
                            Long.toString(bonusScaled),
                            Instant.now()
                    )) {
                        cityLevelService.grantXpBonus(
                                townId,
                                bonusScaled,
                                playerId,
                                "challenge-streak-bonus:" + instance.instanceId()
                        );
                        recordCycleReward(instance, townId, "excellence".equals(reasonKind) ? reasonKind : "weekly_streak_bonus", ChallengeRewardType.XP_CITY, bonusScaled);
                    }
                }
            }
        }
        if (effectiveSpec.moneyCity() > 0.0D && rewardPolicyService.allow(
                ChallengeRewardPolicyService.RewardClass.MONEY_CITY,
                instance == null ? null : instance.mode(),
                family,
                instance == null ? null : instance.objectiveType())) {
            String grantKey = "reward:money:" + reasonKind + ':' + instance.instanceId() + ":town:" + townId;
            if (reserveGrantKey(grantKey)
                    && store.markRewardGrant(
                    grantKey,
                    townId,
                    instance.instanceId(),
                    ChallengeRewardType.MONEY_CITY,
                    Double.toString(effectiveSpec.moneyCity()),
                    Instant.now()
            )) {
                creditTownMoneyReward(townId, playerId, effectiveSpec.moneyCity(), "challenge-" + reasonKind + ':' + instance.instanceId());
                recordCycleReward(instance, townId, reasonKind, ChallengeRewardType.MONEY_CITY, Math.round(effectiveSpec.moneyCity()));
            }
        }
        if (effectiveSpec.consoleCommands() != null
                && !effectiveSpec.consoleCommands().isEmpty()
                && rewardPolicyService.allow(
                ChallengeRewardPolicyService.RewardClass.COMMAND_CONSOLE,
                instance == null ? null : instance.mode(),
                family,
                instance == null ? null : instance.objectiveType())) {
            for (int i = 0; i < effectiveSpec.consoleCommands().size(); i++) {
                String command = effectiveSpec.consoleCommands().get(i);
                if (command == null || command.isBlank()) {
                    continue;
                }
                String commandKey = i < effectiveSpec.commandKeys().size() ? effectiveSpec.commandKeys().get(i) : "";
                String grantKey = "reward:cmd:" + reasonKind + ':' + instance.instanceId() + ":town:" + townId + ':' + i;
                if (!reserveGrantKey(grantKey)) {
                    continue;
                }
                String formattedBroadcast = dispatchStructuredBroadcast(commandKey, instance, townId, townName, instance.cycleKey(), effectiveSpec);
                ChallengeCommandTemplateEngine.RenderResult rendered = formattedBroadcast == null || formattedBroadcast.isBlank()
                        ? templateEngine.render(
                                command,
                                instance,
                                townId,
                                townName,
                                playerId,
                                0
                        )
                        : ChallengeCommandTemplateEngine.RenderResult.success(formattedBroadcast);
                String payload = rendered.success() ? rendered.command() : "SKIPPED[" + rendered.reasonCode() + "]::" + command;
                if (!store.markRewardGrant(
                        grantKey,
                        townId,
                        instance.instanceId(),
                        ChallengeRewardType.COMMAND_CONSOLE,
                        payload,
                        Instant.now()
                )) {
                    continue;
                }
                if (!rendered.success()) {
                    addLong(templateDroppedByReason, rendered.reasonCode(), 1L);
                    plugin.getLogger().warning("[challenges] reward command skipped. grantKey="
                            + grantKey + ", reason=" + rendered.reasonCode() + ", template=" + command);
                    continue;
                }
                recordCycleReward(instance, townId, reasonKind, ChallengeRewardType.COMMAND_CONSOLE, 0L);
                if (!ChallengeBroadcastFormatter.handlesRewardKey(commandKey)) {
                    dispatchRewardCommand(instance, townId, rendered.command());
                }
            }
        }
        if (effectiveSpec.vaultItems() != null
                && !effectiveSpec.vaultItems().isEmpty()
                && rewardPolicyService.allow(
                ChallengeRewardPolicyService.RewardClass.MATERIAL,
                instance == null ? null : instance.mode(),
                family,
                instance == null ? null : instance.objectiveType())) {
            int totalAmount = effectiveSpec.vaultItems().values().stream().mapToInt(Integer::intValue).sum();
            String grantKey = "reward:vault:" + reasonKind + ':' + instance.instanceId() + ":town:" + townId;
            if (reserveGrantKey(grantKey)
                    && store.markRewardGrant(grantKey, townId, instance.instanceId(), ChallengeRewardType.ITEM_VAULT,
                    Integer.toString(totalAmount), Instant.now())) {
                cityItemVaultService.depositRewardItems(townId, effectiveSpec.vaultItems(),
                        "challenge-" + reasonKind + ':' + instance.instanceId());
                recordCycleReward(instance, townId, reasonKind, ChallengeRewardType.ITEM_VAULT, totalAmount);
            }
        }
    }

    private void applyPersonalCompletionRewards(
            ChallengeInstance instance,
            int townId,
            String townName,
            ChallengeRewardSpec completionReward
    ) {
        if (instance == null || townId <= 0 || completionReward == null || !completionReward.hasPersonalRewards()) {
            return;
        }
        Map<UUID, Integer> contributions = playerContributionCapStore.contributionsForTownInstance(instance.instanceId(), townId);
        if (contributions.isEmpty()) {
            return;
        }
        long xpScale = cityLevelService.settings().xpScale();
        for (Map.Entry<UUID, Integer> entry : contributions.entrySet()) {
            UUID playerId = entry.getKey();
            int contribution = Math.max(0, entry.getValue());
            if (!personalRewardService.eligible(contribution, instance.target())) {
                personalRewardSkipCount++;
                continue;
            }
            if (completionReward.personalXpCityBonus() > 0.0D) {
                if (!rewardPolicyService.allow(
                        ChallengeRewardPolicyService.RewardClass.XP_CITY,
                        instance.mode(),
                        definitionFamily(instance),
                        instance.objectiveType())) {
                    personalRewardSkipCount++;
                    continue;
                }
                long scaled = Math.max(1L, Math.round(completionReward.personalXpCityBonus() * xpScale));
                String grantKey = "reward:xp:personal:completion:" + instance.instanceId() + ":town:" + townId + ":player:" + playerId;
                if (reserveGrantKey(grantKey)
                        && store.markRewardGrant(grantKey, townId, instance.instanceId(), ChallengeRewardType.XP_CITY,
                        Long.toString(scaled), Instant.now())) {
                    store.markPersonalRewardGrant(
                        grantKey,
                        instance.instanceId(),
                        townId,
                        playerId,
                        "personal_light",
                        ChallengeRewardType.XP_CITY,
                        Long.toString(scaled),
                        Instant.now()
                    );
                    cityLevelService.grantXpBonus(townId, scaled, playerId, "challenge-personal:" + instance.instanceId());
                    personalRewardGrantCount++;
                    recordCycleReward(instance, townId, "personal_completion", ChallengeRewardType.XP_CITY, scaled);
                }
            }

        }
    }

    private void dispatchRewardCommand(ChallengeInstance instance, int townId, String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return;
        }
        String trimmed = commandLine.trim();
        if (tryHandleBroadcastCommand(instance, townId, trimmed)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin,
                () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), trimmed));
    }

    private boolean tryHandleBroadcastCommand(ChallengeInstance instance, int townId, String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return false;
        }
        String trimmed = commandLine.trim();
        if (!trimmed.regionMatches(true, 0, "broadcast ", 0, "broadcast ".length())) {
            return false;
        }
        String miniMessage = trimmed.substring("broadcast ".length()).trim();
        if (miniMessage.isBlank()) {
            return true;
        }
        if (instance != null && (instance.mode().race() || instance.mode().monthlyEvent())) {
            announceGlobalMiniMessage(miniMessage);
            return true;
        }
        if (townId > 0) {
            announceTownMiniMessage(townId, miniMessage);
            return true;
        }
        announceGlobalMiniMessage(miniMessage);
        return true;
    }

    private void announceRaceStarted(ChallengeInstance instance) {
        if (instance == null) {
            return;
        }
        announceGlobalMiniMessage(ChallengeBroadcastFormatter.renderRaceStarted(instance, rewardPreview(instance)));
    }

    private void announceMonthlyEventStarted(ChallengeMode mode, String cycleKey) {
        announceGlobalMiniMessage(ChallengeBroadcastFormatter.renderMonthlyEventStarted(mode, cycleKey));
    }

    private void announceRaceCompleted(ChallengeInstance instance, String townName) {
        if (instance == null) {
            return;
        }
        announceGlobalMiniMessage(ChallengeBroadcastFormatter.renderRaceCompleted(instance, townName));
    }

    private void announceMonthlyEventCompleted(ChallengeInstance instance, String townName) {
        if (instance == null) {
            return;
        }
        announceGlobalMiniMessage(ChallengeBroadcastFormatter.renderMonthlyEventCompleted(instance, townName));
    }

    private String dispatchStructuredBroadcast(
            String commandKey,
            ChallengeInstance instance,
            int townId,
            String townName,
            String cycleKey,
            ChallengeRewardSpec rewardSpec
    ) {
        if (!ChallengeBroadcastFormatter.handlesRewardKey(commandKey)) {
            return "";
        }
        String message = ChallengeBroadcastFormatter.renderReward(commandKey, instance, townName, cycleKey, rewardSpec);
        if (message == null || message.isBlank()) {
            return "";
        }
        boolean global = commandKey.startsWith("race_")
                || commandKey.startsWith("event_")
                || (instance != null && (instance.mode().race() || instance.mode().monthlyEvent()));
        if (global) {
            announceGlobalMiniMessage(message);
        } else if (townId > 0) {
            announceTownMiniMessage(townId, message);
        } else {
            announceGlobalMiniMessage(message);
        }
        return message;
    }

    private void announceGlobalMiniMessage(String miniMessage) {
        if (miniMessage == null || miniMessage.isBlank()) {
            return;
        }
        net.kyori.adventure.text.Component component = MiniMessageHelper.parse(miniMessage);
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(component);
            }
        });
    }

    private void announceTownMiniMessage(int townId, String miniMessage) {
        if (townId <= 0 || miniMessage == null || miniMessage.isBlank()) {
            return;
        }
        net.kyori.adventure.text.Component component = MiniMessageHelper.parse(miniMessage);
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (UUID memberId : huskTownsApiHook.getTownMembers(townId).keySet()) {
                Player online = Bukkit.getPlayer(memberId);
                if (online != null && online.isOnline()) {
                    online.sendMessage(component);
                }
            }
        });
    }

    private void showCompletionTitle(int townId, String challengeName, boolean race) {
        if (townId <= 0) {
            return;
        }
        Component title = configUtils.msg(
                race ? "city.challenges.notifications.race_completed.title" : "city.challenges.notifications.completed.title",
                race ? "<gold><bold>RACE VINTA</bold></gold>" : "<green><bold>SFIDA COMPLETATA</bold></green>"
        );
        Component subtitle = configUtils.msg(
                race ? "city.challenges.notifications.race_completed.subtitle" : "city.challenges.notifications.completed.subtitle",
                "<yellow>{challenge}</yellow>",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed(
                        "challenge",
                        safeText(challengeName, "Sfida citta")
                )
        );
        Bukkit.getScheduler().runTask(plugin, () -> {
            List<Player> players = huskTownsApiHook.getTownMembers(townId).keySet().stream()
                    .map(Bukkit::getPlayer)
                    .filter(player -> player != null && player.isOnline())
                    .toList();
            TimedTitleHelper.show(players, title, subtitle);
        });
    }

    private static String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String definitionFamily(ChallengeInstance instance) {
        if (instance == null || instance.challengeId() == null || instance.challengeId().isBlank()) {
            return "generic";
        }
        ChallengeDefinition definition = definitions.get(instance.challengeId());
        if (definition == null || definition.objectiveFamily() == null || definition.objectiveFamily().isBlank()) {
            return "generic";
        }
        return definition.objectiveFamily();
    }

    private boolean reserveGrantKey(String grantKey) {
        boolean added = rewardLedgerKeys.add(grantKey);
        if (!added) {
            duplicateGrantAttempts++;
        }
        return added;
    }

    private void recordCycleReward(
            ChallengeInstance instance,
            int townId,
            String reasonKind,
            ChallengeRewardType rewardType,
            long amount
    ) {
        if (instance == null || townId <= 0 || instance.cycleKey() == null || instance.cycleKey().isBlank()) {
            return;
        }
        String key = cycleMetricKey(townId, instance.cycleType(), instance.cycleKey());
        addInt(cycleRewardsGranted, key, 1);
        if (rewardType == ChallengeRewardType.XP_CITY && amount > 0L) {
            addLong(cycleXpGrantedScaled, key, amount);
            String normalizedReason = reasonKind == null ? "-" : reasonKind.toLowerCase(Locale.ROOT);
            if (normalizedReason.contains("excellence")) {
                addLong(cycleExcellenceXpGrantedScaled, key, amount);
            }
        }
    }

    private static String cycleMetricKey(int townId, ChallengeCycleType cycleType, String cycleKey) {
        String safeCycle = cycleKey == null || cycleKey.isBlank() ? "-" : cycleKey;
        String safeType = cycleType == null ? "-" : cycleType.name();
        return townId + "|" + safeType + "|" + safeCycle;
    }

    private static <K> void addInt(Map<K, Integer> map, K key, Integer delta) {
        if (map == null || key == null || delta == null || delta <= 0) {
            return;
        }
        Integer current = map.get(key);
        int safeCurrent = current == null ? 0 : Math.max(0, current);
        map.put(key, safeCurrent + delta);
    }

    private static <K> void addLong(Map<K, Long> map, K key, Long delta) {
        if (map == null || key == null || delta == null || delta <= 0L) {
            return;
        }
        Long current = map.get(key);
        long safeCurrent = current == null ? 0L : Math.max(0L, current);
        map.put(key, safeCurrent + delta);
    }

    private void persistCycleRecapForInstance(ChallengeInstance instance) {
        if (instance == null || instance.cycleKey() == null || instance.cycleKey().isBlank()) {
            return;
        }
        if (instance.mode().race()) {
            return;
        }
        List<Integer> candidateTownIds = new ArrayList<>();
        if (instance.townId() != null && instance.townId() > 0) {
            candidateTownIds.add(instance.townId());
        }
        Map<Integer, TownChallengeProgress> progress = progressByInstance.getOrDefault(instance.instanceId(), Map.of());
        for (Integer townId : progress.keySet()) {
            if (townId != null && townId > 0 && !candidateTownIds.contains(townId)) {
                candidateTownIds.add(townId);
            }
        }
        if (candidateTownIds.isEmpty()) {
            return;
        }
        for (Integer townId : candidateTownIds) {
            persistCycleRecapForTown(instance.cycleType(), instance.cycleKey(), townId);
        }
    }

    private void persistCycleRecapForTown(ChallengeCycleType cycleType, String cycleKey, int townId) {
        if (cycleType == null || cycleKey == null || cycleKey.isBlank() || townId <= 0) {
            return;
        }
        List<ChallengeInstance> relevant = instances.values().stream()
                .filter(value -> value != null && value.cycleType() == cycleType && cycleKey.equals(value.cycleKey()))
                .filter(value -> value.globalInstance() || (value.townId() != null && value.townId() == townId))
                .toList();
        if (relevant.isEmpty()) {
            return;
        }
        int total = relevant.size();
        int completed = 0;
        for (ChallengeInstance challenge : relevant) {
            TownChallengeProgress progress = progressByInstance
                    .getOrDefault(challenge.instanceId(), Map.of())
                    .get(townId);
            if (progress != null && progress.baseCompleted(challenge.target())) {
                completed++;
            }
        }
        String key = cycleMetricKey(townId, cycleType, cycleKey);
        long xpScaled = Math.max(0L, cycleXpGrantedScaled.getOrDefault(key, 0L));
        long excellenceScaled = Math.max(0L, cycleExcellenceXpGrantedScaled.getOrDefault(key, 0L));
        int rewardsGranted = Math.max(0, cycleRewardsGranted.getOrDefault(key, 0));

        int leaderboardPosition = 0;
        if (cycleType == ChallengeCycleType.MONTHLY) {
            ChallengeInstance eventInstance = instances.values().stream()
                    .filter(ChallengeInstance::active)
                    .filter(value -> value.mode().monthlyEvent() && cycleKey.equals(value.cycleKey()))
                    .findFirst()
                    .orElse(null);
            if (eventInstance != null) {
                List<MonthlyEventService.LeaderboardEntry> leaderboard = monthlyEventService.leaderboardFor(
                        eventInstance,
                        progressByInstance.getOrDefault(eventInstance.instanceId(), Map.of())
                );
                for (int i = 0; i < leaderboard.size(); i++) {
                    if (leaderboard.get(i).townId() == townId) {
                        leaderboardPosition = i + 1;
                        break;
                    }
                }
            }
        }

        ContributorBreakdownSnapshot top = contributorBreakdownForCycle(cycleType, cycleKey, townId);
        UUID topPlayer = top.topContributors().isEmpty() ? null : top.topContributors().getFirst().playerId();
        int topContribution = top.topContributors().isEmpty() ? 0 : top.topContributors().getFirst().contribution();

        CycleRecapSnapshot recap = new CycleRecapSnapshot(
                0L,
                townId,
                cycleType,
                cycleKey,
                completed,
                total,
                xpScaled,
                excellenceScaled,
                0,
                leaderboardPosition,
                rewardsGranted,
                topPlayer,
                topContribution,
                Instant.now()
        );
        store.appendCycleRecap(recap);
        store.pruneRecapHistory(townId, 90);
    }

    private ContributorBreakdownSnapshot contributorBreakdownForCycle(ChallengeCycleType cycleType, String cycleKey, int townId) {
        Map<UUID, Integer> totals = new HashMap<>();
        for (ChallengeInstance instance : instances.values()) {
            if (instance == null || instance.cycleType() != cycleType || !cycleKey.equals(instance.cycleKey())) {
                continue;
            }
            if (!instance.globalInstance() && (instance.townId() == null || instance.townId() != townId)) {
                continue;
            }
            Map<UUID, Integer> perInstance = playerContributionCapStore.contributionsForTownInstance(instance.instanceId(), townId);
            for (Map.Entry<UUID, Integer> entry : perInstance.entrySet()) {
                addInt(totals, entry.getKey(), Math.max(0, entry.getValue()));
            }
        }
        int total = totals.values().stream().mapToInt(Integer::intValue).sum();
        List<ContributorBreakdownSnapshot.Entry> top = totals.entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<UUID, Integer> entry) -> entry.getValue()).reversed()
                        .thenComparing(entry -> entry.getKey().toString()))
                .limit(3)
                .map(entry -> new ContributorBreakdownSnapshot.Entry(entry.getKey(), entry.getValue()))
                .toList();
        int topValue = top.isEmpty() ? 0 : top.getFirst().contribution();
        double carry = total <= 0 ? 0.0D : ((double) topValue / (double) total);
        return new ContributorBreakdownSnapshot(cycleType.name() + ":" + cycleKey, townId, total, carry, top);
    }

    private ChallengeSnapshot toSnapshot(
            ChallengeInstance instance,
            int townId,
            UUID viewerPlayerId,
            Map<Integer, String> townNames,
            Instant now
    ) {
        TownChallengeProgress progress = progressByInstance
                .getOrDefault(instance.instanceId(), Map.of())
                .getOrDefault(townId, new TownChallengeProgress(instance.instanceId(), townId, 0, null, null, Instant.EPOCH));
        int playerContribution = viewerPlayerId == null ? 0 : playerContributionCapStore.get(instance.instanceId(), townId, viewerPlayerId);
        int requiredContribution = personalRewardService.requiredContribution(instance.target());
        boolean personalEligible = playerContribution >= requiredContribution;
        long remaining = 0L;
        if (instance.cycleEndAt() != null && instance.cycleEndAt().isAfter(now)) {
            remaining = instance.cycleEndAt().getEpochSecond() - now.getEpochSecond();
        }
        return new ChallengeSnapshot(
                instance.instanceId(),
                instance.challengeId(),
                instance.challengeName(),
                instance.cycleKey(),
                instance.cycleType(),
                instance.windowKey(),
                instance.objectiveType(),
                instance.category(),
                instance.baseTarget(),
                instance.target(),
                instance.excellenceTarget(),
                instance.fairnessMultiplier(),
                instance.activeContributors7d(),
                instance.variantType(),
                instance.focusType(),
                instance.focusKey(),
                resolvedFocusLabel(instance),
                instance.biomeKey(),
                instance.dimensionKey(),
                progress.progress(),
                progress.baseCompleted(instance.target()),
                progress.excellenceCompleted(instance.excellenceTarget()),
                playerContribution,
                personalEligible,
                instance.mode(),
                instance.status(),
                instance.winnerTownId(),
                instance.winnerTownId() == null ? "-" : townNames.getOrDefault(instance.winnerTownId(), "#" + instance.winnerTownId()),
                Math.max(0L, remaining)
        );
    }

    private static String resolvedFocusLabel(ChallengeInstance instance) {
        if (instance == null || instance.focusKey() == null || instance.focusKey().isBlank()) {
            return "-";
        }
        Material focusMaterial = materialBackedFocus(instance.objectiveType(), instance.focusKey());
        if (focusMaterial != null) {
            return ChallengeLoreFormatter.materialLabel(focusMaterial);
        }
        if (instance.focusLabel() != null && !instance.focusLabel().isBlank()) {
            return instance.focusLabel();
        }
        return ChallengeLoreFormatter.keyLabel(instance.focusKey());
    }

    private static String effectiveFocusLabel(ChallengeObjectiveType objectiveType, String focusKey, String fallbackLabel) {
        Material material = materialBackedFocus(objectiveType, focusKey);
        if (material != null) {
            return ChallengeLoreFormatter.materialLabel(material);
        }
        if (fallbackLabel != null && !fallbackLabel.isBlank()) {
            return fallbackLabel;
        }
        return ChallengeLoreFormatter.keyLabel(focusKey);
    }

    private static Material materialBackedFocus(ChallengeObjectiveType objectiveType, String focusKey) {
        if (objectiveType != ChallengeObjectiveType.VAULT_DELIVERY || focusKey == null || focusKey.isBlank()) {
            return null;
        }
        return Material.matchMaterial(focusKey.trim().toUpperCase(Locale.ROOT));
    }

    private static ChallengeFocusOption resolveFocus(List<ChallengeFocusOption> focusPool, String instanceSeed) {
        if (focusPool == null || focusPool.isEmpty()) {
            return null;
        }
        int index = Math.floorMod(("focus:" + (instanceSeed == null ? "" : instanceSeed)).hashCode(), focusPool.size());
        return focusPool.get(index);
    }

    private static List<ChallengeFocusOption> filterAllowedFocusPool(
            ChallengeDefinition definition,
            String dimensionKey,
            String biomeKey
    ) {
        if (definition == null || definition.focusPool().isEmpty()) {
            return List.of();
        }
        List<ChallengeFocusOption> filtered = new ArrayList<>();
        for (ChallengeFocusOption option : definition.focusPool()) {
            if (option == null || option.key() == null || option.key().isBlank()) {
                continue;
            }
            if (isAllowedFocusForContext(definition.objectiveType(), option.key(), dimensionKey, biomeKey)) {
                filtered.add(option);
            }
        }
        return filtered;
    }

    private static boolean isAllowedFocusForContext(
            ChallengeObjectiveType objectiveType,
            String focusKey,
            String dimensionKey,
            String biomeKey
    ) {
        if (objectiveType == null || focusKey == null || focusKey.isBlank()) {
            return true;
        }
        String normalizedFocus = focusKey.trim().toUpperCase(Locale.ROOT);
        String normalizedDimension = dimensionKey == null ? "" : dimensionKey.trim().toUpperCase(Locale.ROOT);
        String normalizedBiome = biomeKey == null ? "" : biomeKey.trim().toUpperCase(Locale.ROOT);
        return switch (objectiveType) {
            case RARE_MOB_KILL -> switch (normalizedFocus) {
                case "PIGLIN_BRUTE" -> normalizedDimension.contains("NETHER");
                case "SHULKER" -> normalizedDimension.contains("END");
                case "BREEZE" -> normalizedDimension.contains("OVERWORLD");
                case "ELDER_GUARDIAN" -> normalizedDimension.contains("OVERWORLD")
                        && (normalizedBiome.contains("OCEAN") || normalizedBiome.isBlank());
                case "WARDEN" -> normalizedDimension.contains("OVERWORLD");
                default -> true;
            };
            case MOB_KILL -> switch (normalizedFocus) {
                case "BLAZE" -> normalizedDimension.isBlank() || normalizedDimension.contains("NETHER");
                case "WITHER_SKELETON" -> normalizedDimension.isBlank() || normalizedDimension.contains("NETHER");
                case "ENDERMAN" -> true;
                default -> normalizedDimension.isBlank()
                        || normalizedDimension.contains("OVERWORLD")
                        || normalizedDimension.contains("NORMAL");
            };
            default -> true;
        };
    }

    private Map<Integer, String> indexTownNames() {
        Map<Integer, String> names = new HashMap<>();
        for (Town town : huskTownsApiHook.getTowns()) {
            names.put(town.getId(), town.getName());
        }
        return names;
    }

    private String townNameById(int townId) {
        return huskTownsApiHook.getTownById(townId)
                .map(Town::getName)
                .orElse("#" + townId);
    }

    private void creditTownMoneyReward(int townId, UUID actorId, double amount, String reason) {
        if (townId <= 0 || amount <= 0.0D) {
            return;
        }
        Optional<Town> town = huskTownsApiHook.getTownById(townId);
        if (town.isEmpty()) {
            plugin.getLogger().warning("[challenges] impossibile accreditare money reward, town mancante id=" + townId
                    + " reason=" + reason);
            return;
        }
        UUID effectiveActor = actorId != null ? actorId : resolveRewardActor(town.get()).orElse(town.get().getMayor());
        BigDecimal delta = BigDecimal.valueOf(amount);
        try {
            huskTownsApiHook.editTown(effectiveActor, townId, mutableTown ->
                    mutableTown.setMoney(mutableTown.getMoney().add(delta)));
            economyValueService.recordRewardEmission(amount);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("[challenges] accredito money reward fallito townId=" + townId
                    + " amount=" + amount + " reason=" + reason + " error=" + exception.getMessage());
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

    private boolean isTownEligibleForMode(int townId, ChallengeMode mode) {
        if (townId <= 0 || mode == null) {
            return false;
        }
        PrincipalStage required = requiredStageForMode(mode);
        if (required == PrincipalStage.AVAMPOSTO) {
            return true;
        }
        try {
            PrincipalStage current = cityLevelService.statusForTown(townId, false).principalStage();
            if (current == null) {
                return false;
            }
            return current.ordinal() >= required.ordinal();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static PrincipalStage requiredStageForMode(ChallengeMode mode) {
        if (mode == null) {
            return PrincipalStage.AVAMPOSTO;
        }
        return switch (mode) {
            case DAILY_SPRINT_1830, DAILY_SPRINT_2130 -> PrincipalStage.VILLAGGIO;
            case WEEKLY_CLASH -> PrincipalStage.CITTADINA;
            case MONTHLY_CROWN -> PrincipalStage.CITTA;
            default -> PrincipalStage.AVAMPOSTO;
        };
    }

    private ChallengeInstance activeEventInstance() {
        return activeEventInstance(-1);
    }

    private ChallengeInstance activeEventInstance(int townId) {
        return instances.values().stream()
                .filter(ChallengeInstance::active)
                .filter(instance -> instance.mode().monthlyEvent())
                .filter(instance -> {
                    if (townId <= 0) {
                        return true;
                    }
                    if (instance.globalInstance()) {
                        return true;
                    }
                    return instance.townId() != null && instance.townId() == townId;
                })
                .findFirst()
                .orElse(null);
    }

    private boolean isRaceVisible(ChallengeInstance instance, Instant now) {
        if (instance == null || !instance.mode().race()) {
            return false;
        }
        if (instance.active()) {
            Instant visibleFrom = raceVisibleFrom(instance);
            return visibleFrom == null || !now.isBefore(visibleFrom);
        }
        if (instance.status() != ChallengeInstanceStatus.COMPLETED && instance.status() != ChallengeInstanceStatus.EXPIRED) {
            return false;
        }
        String currentKey = currentVisibleRaceCycleKey(instance.mode());
        return currentKey != null && currentKey.equalsIgnoreCase(instance.cycleKey());
    }

    private Instant raceVisibleFrom(ChallengeInstance instance) {
        if (instance == null || instance.cycleStartAt() == null) {
            return null;
        }
        if (instance.mode().dailyRace()) {
            return instance.cycleStartAt().minus(java.time.Duration.ofMinutes(DAILY_REVEAL_MINUTES));
        }
        return instance.cycleStartAt();
    }

    private String currentVisibleRaceCycleKey(ChallengeMode mode) {
        if (mode == null) {
            return null;
        }
        ZoneId zone = settings.timezone();
        LocalDate today = LocalDate.now(zone);
        return switch (mode) {
            case DAILY_SPRINT_1830, DAILY_SPRINT_2130, DAILY_RACE_1600, DAILY_RACE_2100 -> today.format(DAY_KEY);
            case WEEKLY_CLASH -> today.format(WEEK_KEY);
            case MONTHLY_CROWN -> today.format(MONTH_KEY) + "-crown";
            case SEASONAL_RACE -> resolveSeasonWindow(LocalDateTime.now(zone)).cycleKey();
            default -> null;
        };
    }

    private EventLeaderboardSnapshot monthlyEventSnapshot(int townId) {
        ChallengeInstance eventInstance = activeEventInstance(townId);
        if (eventInstance == null) {
            return EventLeaderboardSnapshot.EMPTY;
        }
        Map<Integer, TownChallengeProgress> progress = progressByInstance.getOrDefault(eventInstance.instanceId(), Map.of());
        List<MonthlyEventService.LeaderboardEntry> leaderboard = monthlyEventService.leaderboardFor(eventInstance, progress);
        if (leaderboard.isEmpty()) {
            int threshold = eventInstance.mode() == ChallengeMode.MONTHLY_CROWN
                    ? 0
                    : settings.eventRewards().thresholdTargetProgress();
            return new EventLeaderboardSnapshot(
                    true,
                    eventInstance.windowKey(),
                    eventInstance.challengeName(),
                    0,
                    0,
                    0,
                    0,
                    threshold,
                    false,
                    Math.max(0L, eventInstance.cycleEndAt() == null ? 0L : eventInstance.cycleEndAt().getEpochSecond() - Instant.now().getEpochSecond())
            );
        }
        int position = 0;
        int townProgress = 0;
        int leaderProgress = leaderboard.getFirst().progress();
        for (int i = 0; i < leaderboard.size(); i++) {
            MonthlyEventService.LeaderboardEntry entry = leaderboard.get(i);
            if (entry.townId() == townId) {
                position = i + 1;
                townProgress = entry.progress();
                break;
            }
        }
        int thresholdTarget = eventInstance.mode() == ChallengeMode.MONTHLY_CROWN
                ? 0
                : settings.eventRewards().thresholdTargetProgress();
        return new EventLeaderboardSnapshot(
                true,
                eventInstance.windowKey(),
                eventInstance.challengeName(),
                position,
                leaderboard.size(),
                townProgress,
                leaderProgress,
                thresholdTarget,
                thresholdTarget > 0 && townProgress >= thresholdTarget,
                Math.max(0L, eventInstance.cycleEndAt() == null ? 0L : eventInstance.cycleEndAt().getEpochSecond() - Instant.now().getEpochSecond())
        );
    }

    private List<ChallengeDefinition> definitionsForMode(ChallengeMode mode) {
        List<ChallengeDefinition> generatedRaw = proceduralGenerator.definitionsForMode(mode);
        List<ChallengeDefinition> generatedAllowed = generatedRaw.stream()
                .filter(ChallengeDefinition::enabled)
                .filter(definition -> taxonomyService.isAllowed(definition, mode))
                .sorted(Comparator.comparing(ChallengeDefinition::id))
                .toList();
        if (!generatedAllowed.isEmpty()) {
            recordModeDefinitionStats(mode, generatedRaw.size(), generatedAllowed.size(), 0, 0,
                    generatedAllowed.size(), "procedural");
            return generatedAllowed;
        }

        List<ChallengeDefinition> staticAllowed = definitions.values().stream()
                .filter(ChallengeDefinition::enabled)
                .filter(definition -> definition.cyclesSupported().contains(mode))
                .filter(definition -> taxonomyService.isAllowed(definition, mode))
                .sorted(Comparator.comparing(ChallengeDefinition::id))
                .toList();
        if (!staticAllowed.isEmpty()) {
            recordModeDefinitionStats(mode, generatedRaw.size(), generatedAllowed.size(), staticAllowed.size(), 0,
                    staticAllowed.size(), "static-fallback");
            return staticAllowed;
        }

        List<ChallengeDefinition> fallback = derivedFallbackDefinitionsForMode(mode);
        String reason = fallback.isEmpty() ? "empty:no-candidates" : fallbackReasonForMode(mode);
        recordModeDefinitionStats(mode, generatedRaw.size(), generatedAllowed.size(), staticAllowed.size(), fallback.size(),
                fallback.size(), reason);
        return fallback;
    }

    private void recordModeDefinitionStats(
            ChallengeMode mode,
            int proceduralRaw,
            int proceduralAllowed,
            int staticAllowed,
            int fallbackCount,
            int finalCount,
            String reason
    ) {
        if (mode == null) {
            return;
        }
        modeDefinitionStats.put(mode, new ModeDefinitionStats(
                Math.max(0, proceduralRaw),
                Math.max(0, proceduralAllowed),
                Math.max(0, staticAllowed),
                Math.max(0, fallbackCount),
                Math.max(0, finalCount),
                safeText(reason, "-")
        ));
    }

    private List<ChallengeDefinition> derivedFallbackDefinitionsForMode(ChallengeMode mode) {
        List<ChallengeDefinition> fallback = switch (mode) {
            case MONTHLY_LEDGER_CONTRACT -> exactGeneratedDefinitionsForMode(ChallengeMode.WEEKLY_STANDARD);
            case MONTHLY_LEDGER_GRAND, MONTHLY_LEDGER_MYSTERY -> preferNonEmpty(
                    exactGeneratedDefinitionsForMode(ChallengeMode.MONTHLY_LEDGER_CONTRACT),
                    exactGeneratedDefinitionsForMode(ChallengeMode.WEEKLY_STANDARD)
            );
            case SEASON_CODEX_ACT_I, SEASON_CODEX_ACT_II, SEASON_CODEX_ACT_III ->
                    preferNonEmpty(
                            exactGeneratedDefinitionsForMode(ChallengeMode.MONTHLY_LEDGER_CONTRACT),
                            exactGeneratedDefinitionsForMode(ChallengeMode.WEEKLY_STANDARD)
                    );
            case SEASON_CODEX_ELITE -> preferNonEmpty(
                    exactGeneratedDefinitionsForMode(ChallengeMode.MONTHLY_LEDGER_GRAND),
                    exactGeneratedDefinitionsForMode(ChallengeMode.MONTHLY_LEDGER_CONTRACT),
                    exactGeneratedDefinitionsForMode(ChallengeMode.WEEKLY_STANDARD)
            );
            case SEASON_CODEX_HIDDEN_RELIC -> preferNonEmpty(
                    exactGeneratedDefinitionsForMode(ChallengeMode.MONTHLY_LEDGER_MYSTERY),
                    exactGeneratedDefinitionsForMode(ChallengeMode.MONTHLY_LEDGER_GRAND),
                    exactGeneratedDefinitionsForMode(ChallengeMode.MONTHLY_LEDGER_CONTRACT),
                    exactGeneratedDefinitionsForMode(ChallengeMode.WEEKLY_STANDARD)
            );
            default -> List.of();
        };
        return fallback.stream()
                .filter(ChallengeDefinition::enabled)
                .filter(definition -> taxonomyService.isAllowed(definition, mode))
                .sorted(Comparator.comparing(ChallengeDefinition::id))
                .toList();
    }

    @SafeVarargs
    private final List<ChallengeDefinition> preferNonEmpty(List<ChallengeDefinition>... candidates) {
        if (candidates == null) {
            return List.of();
        }
        for (List<ChallengeDefinition> current : candidates) {
            if (current != null && !current.isEmpty()) {
                return current;
            }
        }
        return List.of();
    }

    private List<ChallengeDefinition> exactGeneratedDefinitionsForMode(ChallengeMode mode) {
        if (mode == null) {
            return List.of();
        }
        return proceduralGenerator.definitionsForMode(mode).stream()
                .filter(ChallengeDefinition::enabled)
                .filter(definition -> taxonomyService.isAllowed(definition, mode))
                .sorted(Comparator.comparing(ChallengeDefinition::id))
                .toList();
    }

    private String fallbackReasonForMode(ChallengeMode mode) {
        return switch (mode) {
            case MONTHLY_LEDGER_CONTRACT -> "fallback:weekly-standard-for-monthly-contract";
            case MONTHLY_LEDGER_GRAND -> "fallback:monthly-contract-for-grand";
            case MONTHLY_LEDGER_MYSTERY -> "fallback:monthly-contract-for-mystery";
            case SEASON_CODEX_ACT_I, SEASON_CODEX_ACT_II, SEASON_CODEX_ACT_III ->
                    "fallback:monthly-contract-for-seasonal-act";
            case SEASON_CODEX_ELITE -> "fallback:monthly-grand-or-contract-for-seasonal-elite";
            case SEASON_CODEX_HIDDEN_RELIC -> "fallback:monthly-mystery-grand-contract-for-hidden-relic";
            default -> "fallback:none";
        };
    }

    private Map<String, Integer> questCandidateCountSummary() {
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("WEEKLY", aggregateCandidateCount(ChallengeMode.WEEKLY_STANDARD));
        summary.put("MONTHLY", aggregateCandidateCount(
                ChallengeMode.MONTHLY_LEDGER_GRAND,
                ChallengeMode.MONTHLY_LEDGER_CONTRACT,
                ChallengeMode.MONTHLY_LEDGER_MYSTERY
        ));
        summary.put("SEASONAL", aggregateCandidateCount(
                ChallengeMode.SEASON_CODEX_ACT_I,
                ChallengeMode.SEASON_CODEX_ACT_II,
                ChallengeMode.SEASON_CODEX_ACT_III,
                ChallengeMode.SEASON_CODEX_ELITE,
                ChallengeMode.SEASON_CODEX_HIDDEN_RELIC
        ));
        summary.put("EVENTS", aggregateCandidateCount(
                ChallengeMode.DAILY_SPRINT_1830,
                ChallengeMode.DAILY_SPRINT_2130,
                ChallengeMode.WEEKLY_CLASH,
                ChallengeMode.MONTHLY_CROWN
        ));
        return summary;
    }

    private Map<String, String> questGenerationReasonSummary() {
        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("WEEKLY", aggregateGenerationReason(ChallengeMode.WEEKLY_STANDARD));
        summary.put("MONTHLY", aggregateGenerationReason(
                ChallengeMode.MONTHLY_LEDGER_GRAND,
                ChallengeMode.MONTHLY_LEDGER_CONTRACT,
                ChallengeMode.MONTHLY_LEDGER_MYSTERY
        ));
        summary.put("SEASONAL", aggregateGenerationReason(
                ChallengeMode.SEASON_CODEX_ACT_I,
                ChallengeMode.SEASON_CODEX_ACT_II,
                ChallengeMode.SEASON_CODEX_ACT_III,
                ChallengeMode.SEASON_CODEX_ELITE,
                ChallengeMode.SEASON_CODEX_HIDDEN_RELIC
        ));
        summary.put("EVENTS", aggregateGenerationReason(
                ChallengeMode.DAILY_SPRINT_1830,
                ChallengeMode.DAILY_SPRINT_2130,
                ChallengeMode.WEEKLY_CLASH,
                ChallengeMode.MONTHLY_CROWN
        ));
        return summary;
    }

    private int aggregateCandidateCount(ChallengeMode... modes) {
        int total = 0;
        if (modes == null) {
            return total;
        }
        for (ChallengeMode mode : modes) {
            ensureModeDefinitionStats(mode);
            ModeDefinitionStats stats = modeDefinitionStats.get(mode);
            if (stats != null) {
                total += Math.max(0, stats.finalCount());
            }
        }
        return total;
    }

    private String aggregateGenerationReason(ChallengeMode... modes) {
        if (modes == null || modes.length == 0) {
            return "-";
        }
        List<String> parts = new ArrayList<>();
        for (ChallengeMode mode : modes) {
            ensureModeDefinitionStats(mode);
            ModeDefinitionStats stats = modeDefinitionStats.get(mode);
            if (stats == null) {
                parts.add(mode.name() + "=not-sampled");
                continue;
            }
            parts.add(mode.name() + '=' + safeText(stats.reason(), "-"));
        }
        return String.join(";", parts);
    }

    private void ensureModeDefinitionStats(ChallengeMode mode) {
        if (mode == null || modeDefinitionStats.containsKey(mode)) {
            return;
        }
        definitionsForMode(mode);
    }

    private List<CycleProgressSnapshot> cycleProgress(List<ChallengeSnapshot> snapshots) {
        List<CycleProgressSnapshot> result = new ArrayList<>();
        for (ChallengeCycleType type : ChallengeCycleType.values()) {
            int activeCount = 0;
            int completedCount = 0;
            int totalProgress = 0;
            int totalTarget = 0;
            long remaining = 0L;
            for (ChallengeSnapshot snapshot : snapshots) {
                if (snapshot.cycleType() != type) {
                    continue;
                }
                activeCount++;
                if (snapshot.townCompleted()) {
                    completedCount++;
                }
                totalProgress += Math.max(0, Math.min(snapshot.target(), snapshot.townProgress()));
                totalTarget += Math.max(0, snapshot.target());
                remaining = Math.max(remaining, snapshot.secondsRemaining());
            }
            result.add(new CycleProgressSnapshot(type, activeCount, completedCount, totalProgress, totalTarget, remaining));
        }
        return List.copyOf(result);
    }

    private List<CycleTopContributorSnapshot> topContributors(int townId) {
        Map<String, Integer> raw = playerContributionCapStore.snapshot();
        Map<ChallengeCycleType, Map<UUID, Integer>> grouped = new HashMap<>();
        for (Map.Entry<String, Integer> entry : raw.entrySet()) {
            String[] split = entry.getKey().split("\\|", 3);
            if (split.length != 3) {
                continue;
            }
            String instanceId = split[0];
            int contributedTownId;
            try {
                contributedTownId = Integer.parseInt(split[1]);
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (contributedTownId != townId) {
                continue;
            }
            ChallengeInstance instance = instances.get(instanceId);
            if (instance == null || !isCurrentCycle(instance)) {
                continue;
            }
            UUID playerId;
            try {
                playerId = UUID.fromString(split[2]);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            grouped.computeIfAbsent(instance.cycleType(), ignored -> new HashMap<>())
                    .compute(playerId, (ignored, existing) -> Integer.valueOf(Math.max(0, existing == null ? 0 : existing)
                            + Math.max(0, entry.getValue())));
        }

        List<CycleTopContributorSnapshot> result = new ArrayList<>();
        for (ChallengeCycleType type : ChallengeCycleType.values()) {
            Map<UUID, Integer> byPlayer = grouped.getOrDefault(type, Map.of());
            int total = byPlayer.values().stream().mapToInt(value -> Math.max(0, value)).sum();
            List<CycleTopContributorSnapshot.Entry> top = byPlayer.entrySet().stream()
                    .sorted(Comparator
                            .comparingInt((Map.Entry<UUID, Integer> e) -> e.getValue()).reversed()
                            .thenComparing(e -> e.getKey().toString()))
                    .limit(3)
                    .map(e -> new CycleTopContributorSnapshot.Entry(e.getKey(), e.getValue()))
                    .toList();
            int topValue = top.isEmpty() ? 0 : Math.max(0, top.getFirst().contribution());
            double carryRatio = total <= 0 ? 0.0D : ((double) topValue / (double) total);
            result.add(new CycleTopContributorSnapshot(type, top, carryRatio));
        }
        return List.copyOf(result);
    }

    private record KpiSnapshot(
            double completionRate,
            double abandonmentRate,
            double antiAbuseHitRate,
            double winnerDistribution,
            double duplicateGrantRate,
            double contributionSpread,
            double topCarryRatio,
            double rerollUsageRate,
            double vetoUsageRate
    ) {
    }

    private KpiSnapshot kpiSnapshot() {
        List<CycleRecapSnapshot> recaps = store.loadAllRecaps(500);
        int totalChallenges = recaps.stream().mapToInt(CycleRecapSnapshot::totalChallenges).sum();
        int completedChallenges = recaps.stream().mapToInt(CycleRecapSnapshot::completedChallenges).sum();
        double completionRate = ratio(completedChallenges, totalChallenges);
        double abandonmentRate = totalChallenges <= 0 ? 0.0D : Math.max(0.0D, 1.0D - completionRate);

        long antiTotal = Math.max(0L, acceptedObjectiveHits) + Math.max(0L, deniedObjectiveHits);
        double antiAbuseHitRate = antiTotal <= 0L ? 0.0D : ((double) Math.max(0L, deniedObjectiveHits) / (double) antiTotal);

        Map<Integer, Integer> winnerCount = new HashMap<>();
        for (CycleRecapSnapshot recap : recaps) {
            if (recap.leaderboardPosition() == 1) {
                addInt(winnerCount, recap.townId(), 1);
            }
        }
        int totalWins = winnerCount.values().stream().mapToInt(Integer::intValue).sum();
        int maxWins = winnerCount.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        double winnerDistribution = ratio(maxWins, totalWins);

        double duplicateGrantRate = ratio(Math.max(0L, duplicateGrantAttempts), Math.max(0L, rewardLedgerKeys.size()) + Math.max(0L, duplicateGrantAttempts));

        List<Integer> contribValues = playerContributionCapStore.snapshot().values().stream()
                .map(value -> Math.max(0, value))
                .toList();
        double contributionSpread = normalizedStdDev(contribValues);
        double topCarryRatio = topContributorsAcrossActiveCycles();
        double rerollUsageRate = rerollUsageRate();
        double vetoUsageRate = vetoUsageRate();
        return new KpiSnapshot(
                completionRate,
                abandonmentRate,
                antiAbuseHitRate,
                winnerDistribution,
                duplicateGrantRate,
                contributionSpread,
                topCarryRatio,
                rerollUsageRate,
                vetoUsageRate
        );
    }

    private double topContributorsAcrossActiveCycles() {
        Map<String, Integer> values = playerContributionCapStore.snapshot();
        if (values.isEmpty()) {
            return 0.0D;
        }
        int total = values.values().stream().mapToInt(value -> Math.max(0, value)).sum();
        int max = values.values().stream().mapToInt(value -> Math.max(0, value)).max().orElse(0);
        return ratio(max, total);
    }

    private double rerollUsageRate() {
        if (governanceByTownSeason.isEmpty()) {
            return 0.0D;
        }
        long used = governanceByTownSeason.values().stream()
                .mapToLong(state -> Math.max(0, state.rerollUsed()))
                .sum();
        long max = Math.max(1, settings.governance().rerollWeeklyMaxPerCycle()) * (long) governanceByTownSeason.size();
        return ratio(used, max);
    }

    private double vetoUsageRate() {
        if (governanceByTownSeason.isEmpty()) {
            return 0.0D;
        }
        long used = governanceByTownSeason.values().stream()
                .filter(state -> state.vetoCategory() != null)
                .count();
        return ratio(used, governanceByTownSeason.size());
    }

    private static double ratio(long numerator, long denominator) {
        if (denominator <= 0L || numerator <= 0L) {
            return 0.0D;
        }
        return Math.min(1.0D, (double) numerator / (double) denominator);
    }

    private static double normalizedStdDev(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return 0.0D;
        }
        double mean = values.stream().mapToDouble(Integer::doubleValue).average().orElse(0.0D);
        if (mean <= 0.0D) {
            return 0.0D;
        }
        double variance = values.stream()
                .mapToDouble(value -> {
                    double delta = value - mean;
                    return delta * delta;
                })
                .average()
                .orElse(0.0D);
        double stdDev = Math.sqrt(Math.max(0.0D, variance));
        return Math.min(1.0D, stdDev / mean);
    }

    private boolean isCurrentCycle(ChallengeInstance instance) {
        if (instance == null) {
            return false;
        }
        return switch (instance.cycleType()) {
            case DAILY -> instance.cycleKey().equals(currentDailyKey);
            case WEEKLY -> instance.cycleKey().equals(currentWeeklyKey);
            case MONTHLY -> instance.cycleKey().equals(currentMonthlyKey);
            case SEASONAL -> instance.cycleKey().equals(currentSeasonalKey);
        };
    }

    private ChallengeRewardPreview rewardPreview(ChallengeInstance instance) {
        if (instance == null) {
            return ChallengeRewardPreview.EMPTY;
        }
        ChallengeDefinition definition = definitions.get(instance.challengeId());
        if (definition == null) {
            return ChallengeRewardPreview.EMPTY;
        }
        ChallengeRewardBundle bundle = rewardBundles.getOrDefault(
                definition.rewardBundleId(),
                ChallengeRewardBundle.empty(definition.rewardBundleId())
        );
        ChallengeRewardSpec spec = instance.mode().race() ? bundle.winner() : bundle.completion();
        spec = applySeasonalSecretRewardBoost(instance, spec);
        spec = applyCycleRewardTuning(instance.mode(), spec, instance.mode().race() ? "preview_race" : "preview_completion");
        spec = economyValueService.effectiveRewardSpec(
                spec,
                instance.mode().race() ? EconomyBalanceCategory.RACE_REWARD : EconomyBalanceCategory.CHALLENGE_MONEY_REWARD
        );
        if (spec == null || spec.empty()) {
            return ChallengeRewardPreview.EMPTY;
        }
        boolean allowCommands = rewardPolicyService.allow(
                ChallengeRewardPolicyService.RewardClass.COMMAND_CONSOLE,
                instance.mode(),
                definition.objectiveFamily(),
                instance.objectiveType()
        );
        boolean allowMoney = rewardPolicyService.allow(
                ChallengeRewardPolicyService.RewardClass.MONEY_CITY,
                instance.mode(),
                definition.objectiveFamily(),
                instance.objectiveType()
        );
        boolean allowMaterials = rewardPolicyService.allow(
                ChallengeRewardPolicyService.RewardClass.MATERIAL,
                instance.mode(),
                definition.objectiveFamily(),
                instance.objectiveType()
        );
        int commandCount = !allowCommands || spec.consoleCommands() == null ? 0 : (int) spec.consoleCommands().stream()
                .filter(command -> command != null && !command.isBlank())
                .count();
        List<String> commandKeys = !allowCommands || spec.commandKeys() == null
                ? List.of()
                : spec.commandKeys().stream()
                .filter(key -> key != null && !key.isBlank())
                .collect(Collectors.toList());
        int vaultTypes = !allowMaterials || spec.vaultItems() == null ? 0 : spec.vaultItems().size();
        int vaultAmount = !allowMaterials || spec.vaultItems() == null
                ? 0
                : spec.vaultItems().values().stream().mapToInt(Integer::intValue).sum();
        List<ChallengeRewardPreview.RewardItem> rewardItems = !allowMaterials || spec.vaultItems() == null
                ? List.of()
                : spec.vaultItems().entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0)
                .sorted(Comparator.comparing(e -> e.getKey().name()))
                .map(entry -> new ChallengeRewardPreview.RewardItem(entry.getKey(), entry.getValue()))
                .toList();
        double effectiveXp = effectiveCityXp(instance, spec, instance.mode().race() ? "winner" : "completion");
        return new ChallengeRewardPreview(
                Math.max(0.0D, effectiveXp),
                allowMoney ? Math.max(0.0D, spec.moneyCity()) : 0.0D,
                Math.max(0.0D, spec.personalXpCityBonus()),
                commandCount,
                List.copyOf(commandKeys),
                List.copyOf(rewardItems),
                vaultTypes,
                Math.max(0, vaultAmount)
        );
    }

    private ChallengeRewardSpec applySeasonalSecretRewardBoost(ChallengeInstance instance, ChallengeRewardSpec spec) {
        if (instance == null || spec == null || spec.empty()) {
            return spec;
        }
        if (instance.mode() != ChallengeMode.SEASON_CODEX_HIDDEN_RELIC) {
            return spec;
        }
        Map<Material, Integer> boostedItems = new HashMap<>();
        if (spec.vaultItems() != null) {
            for (Map.Entry<Material, Integer> entry : spec.vaultItems().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) {
                    continue;
                }
                boostedItems.put(entry.getKey(), Math.max(1, entry.getValue() * SEASONAL_SECRET_ITEM_MULTIPLIER));
            }
        }
        return new ChallengeRewardSpec(
                spec.xpCity() * SEASONAL_SECRET_XP_MULTIPLIER,
                spec.moneyCity() * SEASONAL_SECRET_MONEY_MULTIPLIER,
                spec.consoleCommands(),
                spec.commandKeys(),
                boostedItems,
                spec.personalXpCityBonus() * SEASONAL_SECRET_XP_MULTIPLIER
        );
    }

    private int seasonalActTotalCount() {
        return settings.seasonalActICount() + settings.seasonalActIICount() + settings.seasonalActIIICount();
    }

    private ChallengeRewardSpec applyCycleRewardTuning(ChallengeMode mode, ChallengeRewardSpec spec, String reasonKind) {
        if (mode == null || spec == null || spec.empty()) {
            return spec;
        }
        boolean competitive = isCompetitiveRewardReason(reasonKind);
        double multiplier = rewardMultiplierForMode(mode, competitive);
        if (multiplier == 1.0D) {
            return spec;
        }
        return new ChallengeRewardSpec(
                scaleRewardValue(spec.xpCity(), multiplier),
                scaleRewardValue(spec.moneyCity(), multiplier),
                spec.consoleCommands(),
                spec.commandKeys(),
                scaleRewardItems(spec.vaultItems(), multiplier),
                scaleRewardValue(spec.personalXpCityBonus(), multiplier)
        );
    }

    private static boolean isCompetitiveRewardReason(String reasonKind) {
        if (reasonKind == null || reasonKind.isBlank()) {
            return false;
        }
        String normalized = reasonKind.toLowerCase(Locale.ROOT);
        return normalized.startsWith("race_")
                || normalized.startsWith("event_")
                || normalized.contains("winner")
                || normalized.contains("preview_race");
    }

    private static double rewardMultiplierForMode(ChallengeMode mode, boolean competitive) {
        if (mode == null) {
            return 1.0D;
        }
        if (competitive) {
            return switch (mode) {
                case DAILY_SPRINT_1830, DAILY_SPRINT_2130, DAILY_RACE_1600, DAILY_RACE_2100 -> 3.0D;
                case WEEKLY_CLASH -> 5.0D;
                case MONTHLY_CROWN, MONTHLY_EVENT_A, MONTHLY_EVENT_B -> 15.0D;
                case SEASONAL_RACE -> 30.0D;
                default -> 1.0D;
            };
        }
        return switch (mode) {
            case DAILY_STANDARD -> 1.0D;
            case WEEKLY_STANDARD -> 5.0D;
            case MONTHLY_STANDARD, MONTHLY_LEDGER_CONTRACT, MONTHLY_LEDGER_MYSTERY,
                    MONTHLY_LEDGER_GRAND, MONTHLY_EVENT_A, MONTHLY_EVENT_B, MONTHLY_CROWN -> 15.0D;
            case SEASON_CODEX_ACT_I, SEASON_CODEX_ACT_II, SEASON_CODEX_ACT_III,
                    SEASON_CODEX_ELITE, SEASON_CODEX_HIDDEN_RELIC, SEASONAL_RACE -> 30.0D;
            default -> 1.0D;
        };
    }

    private static double scaleRewardValue(double value, double multiplier) {
        if (value <= 0.0D || multiplier == 1.0D) {
            return value;
        }
        return value * multiplier;
    }

    private Map<Material, Integer> scaleRewardItems(Map<Material, Integer> items, double multiplier) {
        if (items == null || items.isEmpty() || multiplier == 1.0D) {
            return items;
        }
        Map<Material, Integer> scaled = new HashMap<>();
        for (Map.Entry<Material, Integer> entry : items.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            int amount = Math.max(1, (int) Math.round(entry.getValue() * multiplier));
            scaled.put(entry.getKey(), amount);
        }
        return scaled;
    }

    private EconomyBalanceCategory rewardCategoryForReason(String reasonKind) {
        if (reasonKind == null || reasonKind.isBlank()) {
            return EconomyBalanceCategory.CHALLENGE_MONEY_REWARD;
        }
        String normalized = reasonKind.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("race_")) {
            return EconomyBalanceCategory.RACE_REWARD;
        }
        if (normalized.startsWith("milestone")) {
            return EconomyBalanceCategory.MILESTONE_MONEY_REWARD;
        }
        return EconomyBalanceCategory.CHALLENGE_MONEY_REWARD;
    }

    private void refreshDailyBucketTargets(String cycleKey) {
        Map<String, Integer> totals = new HashMap<>();
        for (ChallengeInstance instance : instances.values()) {
            if (!instance.active() || instance.cycleType() != ChallengeCycleType.DAILY) {
                continue;
            }
            if (cycleKey != null && !cycleKey.equals(instance.cycleKey())) {
                continue;
            }
            if (instance.townId() == null) {
                continue;
            }
            String bucket = antiAbuseService.bucketForCategory(instance.category());
            addInt(totals, instance.townId() + "|" + bucket, Math.max(1, instance.target()));
        }
        dailyTargetByBucket.clear();
        dailyTargetByBucket.putAll(totals);
    }

    private void refreshEventDiagnostics() {
        ChallengeInstance active = activeEventInstance();
        if (active == null) {
            lastEventLeaderboardSize = 0;
            return;
        }
        Map<Integer, TownChallengeProgress> progress = progressByInstance.getOrDefault(active.instanceId(), Map.of());
        lastEventLeaderboardSize = monthlyEventService.leaderboardFor(active, progress).size();
    }

    private List<ChallengeDefinition> weightedPickDeterministic(
            List<ChallengeDefinition> candidates,
            int count,
            String seedKey
    ) {
        if (candidates.isEmpty() || count <= 0) {
            return List.of();
        }
        java.util.Random random = new java.util.Random(seedKey.hashCode());
        List<ChallengeDefinition> pool = new ArrayList<>(candidates);
        List<ChallengeDefinition> selected = new ArrayList<>();
        while (!pool.isEmpty() && selected.size() < count) {
            Map<ChallengeDefinition, Double> weighted = new HashMap<>();
            double totalWeight = 0.0D;
            for (ChallengeDefinition definition : pool) {
                double jitter = seededWeightJitter(seedKey, safeText(definition.id(), definition.displayName()));
                double finalWeight = Math.max(1.0D, definition.weight() * jitter);
                weighted.put(definition, finalWeight);
                totalWeight += finalWeight;
            }
            double pick = random.nextDouble(Math.max(1.0D, totalWeight));
            double cursor = 0.0D;
            ChallengeDefinition chosen = pool.getFirst();
            for (ChallengeDefinition definition : pool) {
                cursor += weighted.getOrDefault(definition, 1.0D);
                if (pick < cursor) {
                    chosen = definition;
                    break;
                }
            }
            selected.add(chosen);
            pool.remove(chosen);
        }
        return selected;
    }

    private List<ChallengeDefinition> pickCurationDeterministic(
            List<ChallengeDefinition> candidates,
            int count,
            ChallengeMode mode,
            String cycleKey,
            int townId
    ) {
        return pickCurationDeterministic(candidates, count, mode, cycleKey, townId, null);
    }

    private List<ChallengeDefinition> pickCurationDeterministic(
            List<ChallengeDefinition> candidates,
            int count,
            ChallengeMode mode,
            String cycleKey,
            int townId,
            List<ChallengeDefinition> initialSeasonalHistory
    ) {
        if (candidates.isEmpty() || count <= 0) {
            return List.of();
        }
        lastBoardFallbackReason = "-";
        List<CityChallengeRepository.SelectionHistoryEntry> historySnapshot;
        synchronized (selectionHistory) {
            historySnapshot = List.copyOf(selectionHistory);
        }
        BoardSignalResolver.SignalContext signalContext = boardSignalResolver.resolve(
                townId,
                mode,
                cycleKey,
                historySnapshot
        );
        String generationSeed = generationSeed(generationSeedScope(mode, cycleKey, townId));
        List<ChallengeDefinition> pool = new ArrayList<>(candidates);
        if (pool.isEmpty()) {
            return List.of();
        }
        List<ChallengeDefinition> selected = new ArrayList<>();
        List<ChallengeDefinition> seasonalHistory;
        if (mode.seasonCodex()) {
            seasonalHistory = initialSeasonalHistory != null
                    ? List.copyOf(initialSeasonalHistory)
                    : existingSeasonalDefinitionsForTown(townId, cycleKey);
        } else {
            seasonalHistory = List.of();
        }
        CityChallengeSettings.BundleConstraints constraints = bundleConstraintsFor(mode);
        List<String> slots = constraints.slots();
        int slotIndex = 0;
        while (selected.size() < count && !pool.isEmpty()) {
            String wantedSlot = slots.get(Math.min(slotIndex, slots.size() - 1));
            List<ChallengeDefinition> slotCandidates = pool.stream()
                    .filter(def -> wantedSlot.equals(bundleSlotForMode(def, mode)))
                    .filter(def -> !violatesCurationCooldown(def, townId, mode, cycleKey))
                    .toList();
            List<ChallengeDefinition> cooldownCandidates = pool.stream()
                    .filter(def -> !violatesCurationCooldown(def, townId, mode, cycleKey))
                    .toList();

            WeightedPickResult picked = pickWithRulesAndSignals(
                    slotCandidates,
                    mode,
                    cycleKey,
                    townId,
                    slotIndex,
                    signalContext,
                    seasonalHistory,
                    selected,
                    count,
                    "curation:" + generationSeed,
                    "-"
            );
            if (picked == null) {
                picked = pickWithRulesAndSignals(
                        cooldownCandidates,
                        mode,
                        cycleKey,
                        townId,
                        slotIndex,
                        signalContext,
                        seasonalHistory,
                        selected,
                        count,
                        "curation-fallback:" + generationSeed,
                        "slot-or-rules-fallback"
                );
            }
            if (picked == null) {
                picked = pickWithRulesAndSignals(
                        pool,
                        mode,
                        cycleKey,
                        townId,
                        slotIndex,
                        signalContext,
                        seasonalHistory,
                        selected,
                        count,
                        "curation-degraded:" + generationSeed,
                        "cooldown-degraded"
                );
            }
            ChallengeDefinition chosen = picked == null ? null : picked.definition();
            if (chosen == null && !pool.isEmpty()) {
                List<ChallengeDefinition> forcePool = pool;
                if (mode.seasonCodex()) {
                    final List<ChallengeDefinition> seasonalHistorySnapshot = seasonalHistory;
                    List<ChallengeDefinition> constrained = pool.stream()
                            .filter(candidate -> passesSeasonalHardDiversity(mode, candidate, seasonalHistorySnapshot, selected))
                            .toList();
                    if (!constrained.isEmpty()) {
                        forcePool = constrained;
                    }
                }
                chosen = weightedPickDeterministic(
                        forcePool,
                        1,
                        "curation-force:" + mode.name() + ":" + cycleKey + ":town:" + townId + ":slot:" + slotIndex + ':' + generationSeed
                ).getFirst();
                lastBoardFallbackReason = "force-no-rules";
                addLong(runtimeDroppedByReason, "board-fallback:force-no-rules", 1L);
            }
            if (chosen == null) {
                break;
            }
            selected.add(chosen);
            pool.remove(chosen);
            if (mode.seasonCodex()) {
                seasonalHistory = new ArrayList<>(seasonalHistory);
                seasonalHistory.add(chosen);
                reorderPoolForSeasonalVariety(pool, mode, seasonalHistory, selected);
            }
            slotIndex++;
        }
        return selected;
    }

    private String generationSeedScope(ChallengeMode mode, String cycleKey, int townId) {
        String safeMode = mode == null ? "-" : mode.name().toLowerCase(Locale.ROOT);
        String safeCycle = cycleKey == null || cycleKey.isBlank() ? "-" : cycleKey;
        String scope = safeMode + ':' + safeCycle + ":town:" + townId;
        if (regenerateSalt != null && !regenerateSalt.isBlank()) {
            scope = scope + ":reroll:" + regenerateSalt;
        }
        return scope;
    }

    private String generationSeed(String scope) {
        String normalizedScope = scope == null || scope.isBlank() ? "-" : scope;
        String key = GENERATION_SEED_PREFIX + normalizedScope;
        Optional<String> existing = store.getRuntimeState(key);
        if (existing.isPresent() && !existing.get().isBlank()) {
            return existing.get();
        }
        String generated = UUID.randomUUID().toString();
        store.setRuntimeState(key, generated);
        return generated;
    }

    private record WeightedPickResult(
            ChallengeDefinition definition,
            BoardSignalResolver.SignalScore signalScore,
            String fallbackReason
    ) {
    }

    private WeightedPickResult pickWithRulesAndSignals(
            List<ChallengeDefinition> input,
            ChallengeMode mode,
            String cycleKey,
            int townId,
            int slotIndex,
            BoardSignalResolver.SignalContext signalContext,
            List<ChallengeDefinition> seasonalHistory,
            List<ChallengeDefinition> selected,
            int boardTargetCount,
            String seedPrefix,
            String fallbackReason
    ) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        BoardHardRuleEngine.Context ruleContext = new BoardHardRuleEngine.Context(
                mode,
                boardTargetCount,
                signalContext.previousCycleFamilies(),
                signalContext.resourceContributionSelectionsLastWindow(),
                input
        );
        List<ChallengeDefinition> allowed = new ArrayList<>();
        Map<ChallengeDefinition, BoardSignalResolver.SignalScore> scoreByCandidate = new HashMap<>();
        for (ChallengeDefinition candidate : input) {
            if (!passesSeasonalHardDiversity(mode, candidate, seasonalHistory, selected)) {
                addLong(runtimeDroppedByReason, "seasonal-diversity-hard-cap", 1L);
                continue;
            }
            BoardHardRuleEngine.Decision decision = boardHardRuleEngine.evaluate(candidate, ruleContext, selected);
            if (!decision.allowed()) {
                addLong(boardHardRuleHits, decision.reasonCode(), 1L);
                continue;
            }
            allowed.add(candidate);
            scoreByCandidate.put(candidate, boardSignalResolver.score(candidate, signalContext));
        }
        if (allowed.isEmpty()) {
            return null;
        }
        ChallengeDefinition chosen = weightedPickBySignals(
                allowed,
                scoreByCandidate,
                mode,
                seasonalHistory,
                selected,
                signalContext.season(),
                seedPrefix + ":" + mode.name() + ":" + cycleKey + ":town:" + townId + ":slot:" + slotIndex
        );
        if (chosen == null) {
            return null;
        }
        BoardSignalResolver.SignalScore score = scoreByCandidate.getOrDefault(
                chosen,
                new BoardSignalResolver.SignalScore(0.0D, 0.0D, 0.0D, false, false, false)
        );
        trackSignalSelection(score);
        if (!"-".equals(fallbackReason)) {
            lastBoardFallbackReason = fallbackReason;
            addLong(runtimeDroppedByReason, "board-fallback:" + fallbackReason, 1L);
        }
        return new WeightedPickResult(chosen, score, fallbackReason);
    }

    private boolean passesSeasonalHardDiversity(
            ChallengeMode mode,
            ChallengeDefinition candidate,
            List<ChallengeDefinition> seasonalHistory,
            List<ChallengeDefinition> selected
    ) {
        if (mode == null || candidate == null || !mode.seasonCodex()) {
            return true;
        }
        List<ChallengeDefinition> reference = new ArrayList<>();
        if (seasonalHistory != null && !seasonalHistory.isEmpty()) {
            reference.addAll(seasonalHistory);
        }
        if (selected != null && !selected.isEmpty()) {
            reference.addAll(selected);
        }
        for (ChallengeDefinition current : reference) {
            if (current == null) {
                continue;
            }
            if (safeText(current.id(), "").equalsIgnoreCase(safeText(candidate.id(), ""))) {
                return false;
            }
        }
        long sameFamily = reference.stream()
                .filter(current -> current != null && safeText(current.objectiveFamily(), "").equalsIgnoreCase(safeText(candidate.objectiveFamily(), "")))
                .count();
        if (sameFamily >= SEASONAL_FAMILY_HARD_CAP) {
            return false;
        }
        long sameObjective = reference.stream()
                .filter(current -> current != null && current.objectiveType() == candidate.objectiveType())
                .count();
        if (mode == ChallengeMode.SEASON_CODEX_HIDDEN_RELIC && candidate.objectiveType() == ChallengeObjectiveType.SECRET_QUEST) {
            return true;
        }
        if (mode == ChallengeMode.SEASON_CODEX_HIDDEN_RELIC) {
            return sameObjective < SEASONAL_OBJECTIVE_HARD_CAP;
        }
        if (wouldExceedSeasonalSecretCap(candidate, reference)) {
            return false;
        }
        return sameObjective < SEASONAL_OBJECTIVE_HARD_CAP;
    }

    private boolean wouldExceedSeasonalSecretCap(ChallengeDefinition candidate, List<ChallengeDefinition> reference) {
        if (candidate == null || !isSeasonalSecretDefinition(candidate)) {
            return false;
        }
        int current = seasonalSecretCount(reference);
        int reservedHiddenRelics = Math.max(0, settings.seasonalHiddenRelicCount());
        int total = current + 1 + reservedHiddenRelics;
        return total > Math.max(reservedHiddenRelics, settings.seasonalSecretHardCap());
    }

    private int seasonalSecretCount(List<ChallengeDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ChallengeDefinition definition : definitions) {
            if (isSeasonalSecretDefinition(definition)) {
                count++;
            }
        }
        return count;
    }

    private boolean isSeasonalSecretDefinition(ChallengeDefinition definition) {
        if (definition == null) {
            return false;
        }
        if (definition.objectiveType() == ChallengeObjectiveType.SECRET_QUEST) {
            return true;
        }
        String id = safeText(definition.id(), "").toLowerCase(Locale.ROOT);
        return id.startsWith("secret_");
    }

    private ChallengeDefinition weightedPickBySignals(
            List<ChallengeDefinition> candidates,
            Map<ChallengeDefinition, BoardSignalResolver.SignalScore> scoreByCandidate,
            ChallengeMode mode,
            List<ChallengeDefinition> seasonalHistory,
            List<ChallengeDefinition> selected,
            ChallengeSeasonResolver.Season season,
            String seedKey
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        java.util.Random random = new java.util.Random(seedKey.hashCode());
        BoardSignalResolver.EffectiveWeights weights = boardSignalResolver.effectiveWeights(season);
        Map<ChallengeDefinition, Double> weighted = new HashMap<>();
        double total = 0.0D;
        for (ChallengeDefinition candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            BoardSignalResolver.SignalScore score = scoreByCandidate.getOrDefault(
                    candidate,
                    new BoardSignalResolver.SignalScore(0.0D, 0.0D, 0.0D, false, false, false)
            );
            double baseWeight = Math.max(1.0D, candidate.weight());
            double boost = 1.0D
                    + (weights.seasonThemeWeight() * score.seasonTheme())
                    + (weights.atlasLowestWeight() * score.atlasLowest())
                    + (weights.cityAffinity14dWeight() * score.cityAffinity14d());
            double diversityFactor = seasonalVarietyFactor(mode, candidate, seasonalHistory, selected);
            double jitter = seededWeightJitter(seedKey, safeText(candidate.id(), candidate.displayName()));
            double finalWeight = Math.max(
                    1.0D,
                    baseWeight
                            * Math.max(0.01D, boost)
                            * Math.max(SEASONAL_VARIETY_MIN_FACTOR, diversityFactor)
                            * jitter
            );
            weighted.put(candidate, finalWeight);
            total += finalWeight;
        }
        if (weighted.isEmpty()) {
            return null;
        }
        double pick = random.nextDouble(Math.max(1.0D, total));
        double cursor = 0.0D;
        ChallengeDefinition fallback = candidates.getFirst();
        for (ChallengeDefinition candidate : candidates) {
            Double candidateWeight = weighted.get(candidate);
            if (candidateWeight == null) {
                continue;
            }
            cursor += candidateWeight;
            if (pick <= cursor) {
                return candidate;
            }
            fallback = candidate;
        }
        return fallback;
    }

    private double seededWeightJitter(String seedKey, String candidateId) {
        String normalizedSeed = safeText(seedKey, "-");
        String normalizedCandidate = safeText(candidateId, "-");
        java.util.Random random = new java.util.Random((normalizedSeed + "|" + normalizedCandidate).hashCode());
        return 0.35D + (random.nextDouble() * 1.65D);
    }

    private double seasonalVarietyFactor(
            ChallengeMode mode,
            ChallengeDefinition candidate,
            List<ChallengeDefinition> seasonalHistory,
            List<ChallengeDefinition> selected
    ) {
        if (mode == null || candidate == null || !mode.seasonCodex()) {
            return 1.0D;
        }
        List<ChallengeDefinition> reference = new ArrayList<>();
        if (seasonalHistory != null && !seasonalHistory.isEmpty()) {
            reference.addAll(seasonalHistory);
        }
        if (selected != null && !selected.isEmpty()) {
            reference.addAll(selected);
        }
        if (reference.isEmpty()) {
            return 1.0D;
        }
        long sameId = reference.stream()
                .filter(current -> current != null && safeText(current.id(), "").equalsIgnoreCase(safeText(candidate.id(), "")))
                .count();
        long sameFamily = reference.stream()
                .filter(current -> current != null && safeText(current.objectiveFamily(), "").equalsIgnoreCase(safeText(candidate.objectiveFamily(), "")))
                .count();
        long sameObjective = reference.stream()
                .filter(current -> current != null && current.objectiveType() == candidate.objectiveType())
                .count();
        long sameSlot = reference.stream()
                .filter(current -> current != null && bundleSlotForMode(current, mode).equalsIgnoreCase(bundleSlotForMode(candidate, mode)))
                .count();
        double factor = 1.0D;
        if (sameId > 0) {
            factor *= Math.pow(SEASONAL_VARIETY_ID_DECAY, sameId);
        }
        if (sameFamily > 0) {
            factor *= Math.pow(SEASONAL_VARIETY_FAMILY_DECAY, sameFamily);
        }
        if (sameObjective > 0) {
            factor *= Math.pow(SEASONAL_VARIETY_OBJECTIVE_DECAY, sameObjective);
        }
        if (sameSlot > 0) {
            factor *= Math.pow(SEASONAL_VARIETY_SLOT_DECAY, sameSlot);
        }
        return Math.max(SEASONAL_VARIETY_MIN_FACTOR, factor);
    }

    private void reorderPoolForSeasonalVariety(
            List<ChallengeDefinition> pool,
            ChallengeMode mode,
            List<ChallengeDefinition> seasonalHistory,
            List<ChallengeDefinition> selected
    ) {
        if (pool == null || pool.size() <= 1) {
            return;
        }
        pool.sort(Comparator
                .comparingDouble((ChallengeDefinition candidate) ->
                        -seasonalVarietyFactor(mode, candidate, seasonalHistory, selected))
                .thenComparing(ChallengeDefinition::id));
    }

    private List<ChallengeDefinition> existingSeasonalDefinitionsForTown(int townId, String cycleKey) {
        if (townId <= 0 || cycleKey == null || cycleKey.isBlank()) {
            return List.of();
        }
        return instances.values().stream()
                .filter(ChallengeInstance::active)
                .filter(instance -> instance.cycleType() == ChallengeCycleType.SEASONAL)
                .filter(instance -> cycleKey.equals(instance.cycleKey()))
                .filter(instance -> instance.townId() != null && instance.townId() == townId)
                .map(instance -> definitions.get(instance.challengeId()))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(ChallengeDefinition::id))
                .toList();
    }

    private List<ChallengeDefinition> existingSeasonalActDefinitionsForTown(int townId, String cycleKey) {
        if (townId <= 0 || cycleKey == null || cycleKey.isBlank()) {
            return List.of();
        }
        Set<ChallengeMode> actModes = Set.of(
                ChallengeMode.SEASON_CODEX_ACT_I,
                ChallengeMode.SEASON_CODEX_ACT_II,
                ChallengeMode.SEASON_CODEX_ACT_III
        );
        return instances.values().stream()
                .filter(ChallengeInstance::active)
                .filter(instance -> instance.cycleType() == ChallengeCycleType.SEASONAL)
                .filter(instance -> cycleKey.equals(instance.cycleKey()))
                .filter(instance -> instance.townId() != null && instance.townId() == townId)
                .filter(instance -> actModes.contains(instance.mode()))
                .map(instance -> definitions.get(instance.challengeId()))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(ChallengeDefinition::id))
                .toList();
    }

    private List<ChallengeDefinition> seasonalActCandidates() {
        Map<String, ChallengeDefinition> merged = new LinkedHashMap<>();
        List<ChallengeDefinition> sources = new ArrayList<>();
        sources.addAll(exactGeneratedDefinitionsForMode(ChallengeMode.DAILY_STANDARD));
        sources.addAll(exactGeneratedDefinitionsForMode(ChallengeMode.WEEKLY_STANDARD));
        sources.addAll(exactGeneratedDefinitionsForMode(ChallengeMode.MONTHLY_STANDARD));
        for (ChallengeDefinition definition : sources) {
            ChallengeDefinition rebound = rebindDefinitionToMode(definition, ChallengeMode.SEASON_CODEX_ACT_I);
            if (rebound == null) {
                continue;
            }
            if (rebound.objectiveType() == ChallengeObjectiveType.SECRET_QUEST
                    || rebound.objectiveType() == ChallengeObjectiveType.TEAM_PHASE_QUEST) {
                continue;
            }
            merged.putIfAbsent(rebound.id(), rebound);
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(ChallengeDefinition::id))
                .toList();
    }

    private List<ChallengeDefinition> seasonalEliteCandidates() {
        Map<String, ChallengeDefinition> merged = new LinkedHashMap<>();
        List<ChallengeDefinition> sources = new ArrayList<>();
        sources.addAll(exactGeneratedDefinitionsForMode(ChallengeMode.WEEKLY_STANDARD));
        sources.addAll(exactGeneratedDefinitionsForMode(ChallengeMode.MONTHLY_STANDARD));
        for (ChallengeDefinition definition : sources) {
            ChallengeDefinition rebound = rebindDefinitionToMode(definition, ChallengeMode.SEASON_CODEX_ELITE);
            if (rebound == null) {
                continue;
            }
            if (rebound.objectiveType() == ChallengeObjectiveType.SECRET_QUEST) {
                continue;
            }
            if (!isSeasonalEliteObjective(rebound.objectiveType())) {
                continue;
            }
            merged.putIfAbsent(rebound.id(), rebound);
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(ChallengeDefinition::id))
                .toList();
    }

    private List<ChallengeDefinition> seasonalHiddenRelicCandidates() {
        Map<String, ChallengeDefinition> preferred = new LinkedHashMap<>();
        for (ChallengeDefinition definition : exactGeneratedDefinitionsForMode(ChallengeMode.MONTHLY_STANDARD)) {
            if (definition == null) {
                continue;
            }
            if (SecretQuestCatalog.hasScript(definition.id())) {
                ChallengeDefinition rebound = rebindDefinitionToMode(definition, ChallengeMode.SEASON_CODEX_HIDDEN_RELIC);
                if (rebound != null) {
                    preferred.putIfAbsent(rebound.id(), rebound);
                }
            }
        }
        if (preferred.size() >= settings.seasonalHiddenRelicCount()) {
            return preferred.values().stream()
                    .sorted(Comparator.comparing(ChallengeDefinition::id))
                    .toList();
        }
        for (ChallengeDefinition definition : exactGeneratedDefinitionsForMode(ChallengeMode.MONTHLY_STANDARD)) {
            if (definition == null || definition.id() == null || !definition.id().startsWith("secret_")) {
                continue;
            }
            ChallengeDefinition rebound = rebindDefinitionToMode(definition, ChallengeMode.SEASON_CODEX_HIDDEN_RELIC);
            if (rebound != null) {
                preferred.putIfAbsent(rebound.id(), rebound);
            }
        }
        if (!preferred.isEmpty()) {
            return preferred.values().stream()
                    .sorted(Comparator.comparing(ChallengeDefinition::id))
                    .toList();
        }
        return exactGeneratedDefinitionsForMode(ChallengeMode.MONTHLY_STANDARD).stream()
                .filter(definition -> definition.objectiveType() == ChallengeObjectiveType.SECRET_QUEST)
                .map(definition -> rebindDefinitionToMode(definition, ChallengeMode.SEASON_CODEX_HIDDEN_RELIC))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(ChallengeDefinition::id))
                .toList();
    }

    private ChallengeDefinition rebindDefinitionToMode(ChallengeDefinition definition, ChallengeMode mode) {
        if (definition == null || mode == null) {
            return null;
        }
        if (!taxonomyService.isAllowed(definition.objectiveType(), mode)) {
            return null;
        }
        return new ChallengeDefinition(
                definition.id(),
                definition.displayName(),
                definition.objectiveType(),
                definition.category(),
                definition.bundleSlot(),
                definition.difficultyTag(),
                definition.objectiveFamily(),
                definition.target(),
                definition.targetMin(),
                definition.targetMax(),
                definition.targetStep(),
                definition.weight(),
                Set.of(mode),
                definition.variantMode(),
                definition.specificChance(),
                Map.of(mode, definition.resolveSpecificChance(mode)),
                definition.focusType(),
                definition.focusPool(),
                definition.rewardBundleId(),
                definition.enabled()
        );
    }

    private boolean isSeasonalEliteObjective(ChallengeObjectiveType objectiveType) {
        if (objectiveType == null) {
            return false;
        }
        return switch (objectiveType) {
            case BOSS_KILL,
                    RAID_WIN,
                    RARE_MOB_KILL,
                    TRIAL_VAULT_OPEN,
                    DIMENSION_TRAVEL,
                    ARCHAEOLOGY_BRUSH,
                    NETHER_ACTIVITY,
                    OCEAN_ACTIVITY,
                    STRUCTURE_DISCOVERY,
                    PLACE_BLOCK,
                    CONSTRUCTION,
                    RESOURCE_CONTRIBUTION,
                    ECONOMY_ADVANCED,
                    ITEM_CRAFT,
                    REDSTONE_AUTOMATION,
                    VILLAGER_TRADE -> true;
            default -> false;
        };
    }

    private void trackSignalSelection(BoardSignalResolver.SignalScore score) {
        if (score == null) {
            return;
        }
        if (score.seasonHit()) {
            addLong(boardSignalHits, "season-theme", 1L);
        }
        if (score.atlasLowHit()) {
            addLong(boardSignalHits, "atlas-lowest", 1L);
        }
        if (score.affinityHit()) {
            addLong(boardSignalHits, "city-affinity-14d", 1L);
        }
    }

    private CityChallengeSettings.BundleConstraints bundleConstraintsFor(ChallengeMode mode) {
        CityChallengeSettings.CurationSettings curation = settings.curation();
        if (mode.race()) {
            return curation.raceBundle();
        }
        return switch (mode.cycleType()) {
            case DAILY -> curation.dailyBundle();
            case WEEKLY -> curation.weeklyBundle();
            case MONTHLY -> curation.monthlyBundle();
            case SEASONAL -> curation.monthlyBundle();
        };
    }

    private static String bundleSlotForMode(ChallengeDefinition definition, ChallengeMode mode) {
        if (definition == null) {
            return "";
        }
        if (definition.objectiveType() == ChallengeObjectiveType.TRIAL_VAULT_OPEN && mode != null) {
            return switch (mode.cycleType()) {
                case DAILY -> "off_routine";
                case WEEKLY -> "combat_exploration";
                case MONTHLY -> "epic";
                case SEASONAL -> "epic";
            };
        }
        if (mode != null && mode.race()) {
            return switch (definition.objectiveType()) {
                case MOB_KILL, RARE_MOB_KILL -> "combat";
                case BLOCK_MINE -> "mining";
                case CROP_HARVEST, FISH_CATCH -> "farming";
                default -> definition.bundleSlot();
            };
        }
        return definition.bundleSlot();
    }

    private boolean violatesCurationCooldown(
            ChallengeDefinition definition,
            int townId,
            ChallengeMode mode,
            String cycleKey
    ) {
        CityChallengeSettings.CurationSettings curation = settings.curation();
        int defWindow = Math.max(0, curation.definitionCooldownCycles());
        int categoryWindow = Math.max(0, curation.categoryCooldownCycles());
        int focusWindow = Math.max(0, curation.focusCooldownCycles());
        int familyWindow = Math.max(0, curation.familyCooldownCycles());
        int maxWindow = Math.max(Math.max(defWindow, categoryWindow), Math.max(focusWindow, familyWindow));
        if (maxWindow <= 0) {
            return false;
        }

        Map<String, Integer> cycleRankByKey = new HashMap<>();
        int nextRank = 0;
        synchronized (selectionHistory) {
            for (CityChallengeRepository.SelectionHistoryEntry entry : selectionHistory) {
                if (entry == null || entry.mode() == null) {
                    continue;
                }
                if (entry.townId() == null || entry.townId() != townId) {
                    continue;
                }
                if (entry.mode().cycleType() != mode.cycleType()) {
                    continue;
                }
                if (entry.cycleKey() == null || entry.cycleKey().equalsIgnoreCase(cycleKey)) {
                    continue;
                }
                Integer rank = cycleRankByKey.get(entry.cycleKey());
                if (rank == null) {
                    nextRank++;
                    rank = nextRank;
                    cycleRankByKey.put(entry.cycleKey(), rank);
                }
                if (rank > maxWindow) {
                    continue;
                }
                if (defWindow > 0 && rank <= defWindow && definition.id().equalsIgnoreCase(entry.challengeId())) {
                    return true;
                }
                if (categoryWindow > 0 && rank <= categoryWindow && definition.category() == entry.category()) {
                    return true;
                }
                if (familyWindow > 0 && rank <= familyWindow
                        && definition.objectiveFamily().equalsIgnoreCase(entry.objectiveFamily() == null ? "" : entry.objectiveFamily())) {
                    return true;
                }
                if (focusWindow > 0 && rank <= focusWindow && focusMatches(definition, entry.focusKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean focusMatches(ChallengeDefinition definition, String focusKey) {
        if (focusKey == null || focusKey.isBlank() || definition == null) {
            return false;
        }
        if (definition.focusPool() == null || definition.focusPool().isEmpty()) {
            return false;
        }
        for (ChallengeFocusOption option : definition.focusPool()) {
            if (option != null && option.key() != null && option.key().equalsIgnoreCase(focusKey)) {
                return true;
            }
        }
        return false;
    }

    private void recordSelectionHistory(ChallengeInstance instance, ChallengeDefinition definition) {
        if (instance == null || definition == null) {
            return;
        }
        CityChallengeRepository.SelectionHistoryEntry entry = new CityChallengeRepository.SelectionHistoryEntry(
                instance.townId(),
                instance.mode(),
                instance.cycleKey(),
                definition.id(),
                definition.category(),
                definition.objectiveFamily(),
                instance.focusKey(),
                instance.signatureKey(),
                Instant.now()
        );
        synchronized (selectionHistory) {
            selectionHistory.add(0, entry);
            if (selectionHistory.size() > 10_000) {
                selectionHistory.remove(selectionHistory.size() - 1);
            }
        }
        store.appendSelectionHistory(entry);
    }

    private double effectiveCityXp(ChallengeInstance instance, ChallengeRewardSpec spec, String reasonKind) {
        if (instance == null || spec == null) {
            return 0.0D;
        }
        if (!rewardPolicyService.allow(
                ChallengeRewardPolicyService.RewardClass.XP_CITY,
                instance.mode(),
                definitionFamily(instance),
                instance.objectiveType())) {
            return 0.0D;
        }
        if (!taxonomyService.cityXpEnabled(instance.objectiveType())) {
            return 0.0D;
        }
        String normalizedReason = reasonKind == null ? "" : reasonKind.toLowerCase(Locale.ROOT);
        CityChallengeSettings.XpRangesSettings ranges = settings.xpRanges();
        if (normalizedReason.contains("excellence")) {
            double baseXp = completionXpFor(instance, ranges);
            return Math.max(ranges.excellenceMin(), baseXp * ranges.excellenceRatio());
        }
        if (instance.mode().dailyRace() && isRaceFirstCompletionReason(reasonKind)) {
            return deterministicRangeInclusive(
                    instance.instanceId() + ":xp:daily-race-winner",
                    ranges.dailyRaceWinner().min(),
                    ranges.dailyRaceWinner().max()
            );
        }
        if (instance.mode() == ChallengeMode.WEEKLY_CLASH && isRaceFirstCompletionReason(reasonKind)) {
            return deterministicRangeInclusive(
                    instance.instanceId() + ":xp:weekly-clash-winner",
                    ranges.weeklyCompletion().min(),
                    ranges.weeklyCompletion().max()
            );
        }
        if (instance.mode() == ChallengeMode.MONTHLY_CROWN && isRaceFirstCompletionReason(reasonKind)) {
            return deterministicRangeInclusive(
                    instance.instanceId() + ":xp:monthly-crown-winner",
                    ranges.monthlyEventCompletion().min(),
                    ranges.monthlyEventCompletion().max()
            );
        }
        if (instance.mode().seasonalRace() && isRaceFirstCompletionReason(reasonKind)) {
            return Math.max(0.0D, spec.xpCity());
        }
        if ("completion".equalsIgnoreCase(reasonKind)) {
            return completionXpFor(instance, ranges);
        }
        return Math.max(0.0D, spec.xpCity());
    }

    private static double completionXpFor(ChallengeInstance instance, CityChallengeSettings.XpRangesSettings ranges) {
        if (SecretQuestCatalog.hasScript(instance.challengeId())) {
            return deterministicRangeInclusive(
                    instance.instanceId() + ":xp:secret-quest-completion",
                    320,
                    460
            );
        }
        if (instance.mode() == ChallengeMode.DAILY_STANDARD) {
            return deterministicRangeInclusive(
                    instance.instanceId() + ":xp:daily-completion",
                    ranges.dailyCompletion().min(),
                    ranges.dailyCompletion().max()
            );
        }
        if (instance.mode().weeklyStandard()) {
            return deterministicRangeInclusive(
                    instance.instanceId() + ":xp:weekly-completion",
                    ranges.weeklyCompletion().min(),
                    ranges.weeklyCompletion().max()
            );
        }
        if (instance.mode() == ChallengeMode.MONTHLY_STANDARD
                || instance.mode() == ChallengeMode.MONTHLY_LEDGER_CONTRACT
                || instance.mode() == ChallengeMode.MONTHLY_LEDGER_MYSTERY) {
            return deterministicRangeInclusive(
                    instance.instanceId() + ":xp:monthly-completion",
                    ranges.monthlyCompletion().min(),
                    ranges.monthlyCompletion().max()
            );
        }
        if (instance.mode() == ChallengeMode.MONTHLY_LEDGER_GRAND || instance.mode().monthlyEvent()) {
            return deterministicRangeInclusive(
                    instance.instanceId() + ":xp:monthly-grand-completion",
                    ranges.monthlyEventCompletion().min(),
                    ranges.monthlyEventCompletion().max()
            );
        }
        if (instance.mode().seasonCodex()) {
            double base = deterministicRangeInclusive(
                    instance.instanceId() + ":xp:season-codex-completion",
                    Math.max(ranges.monthlyCompletion().min(), ranges.weeklyCompletion().min()),
                    Math.max(ranges.monthlyCompletion().max(), ranges.monthlyEventCompletion().max())
            );
            return Math.max(ranges.monthlyCompletion().min(), base * SEASONAL_XP_MULTIPLIER);
        }
        if (instance.mode().seasonalRace()) {
            return 0.0D;
        }
        return 0.0D;
    }

    private static boolean isRaceFirstCompletionReason(String reasonKind) {
        return "winner".equalsIgnoreCase(reasonKind) || "race_first_completion".equalsIgnoreCase(reasonKind);
    }

    private static double deterministicRangeInclusive(String seed, int min, int max) {
        int safeMin = Math.min(min, max);
        int safeMax = Math.max(min, max);
        int span = Math.max(1, (safeMax - safeMin) + 1);
        int offset = Math.floorMod((seed == null ? "" : seed).hashCode(), span);
        return safeMin + offset;
    }

    private boolean isMonthlyPlaytimeObjective(
            ChallengeDefinition definition,
            ChallengeCycleType cycleType,
            Integer townId
    ) {
        return definition != null
                && definition.objectiveType() == ChallengeObjectiveType.PLAYTIME_MINUTES
                && cycleType == ChallengeCycleType.MONTHLY
                && townId != null
                && townId > 0;
    }

    private int resolveMonthlyPlaytimeTarget(String instanceId, int townId) {
        int members = Math.max(1, huskTownsApiHook.getTownMembers(townId).size());
        int baseMinutes = members * 90;
        int min = (int) Math.floor(baseMinutes * 0.9D);
        int max = (int) Math.ceil(baseMinutes * 1.1D);
        int step = 10;
        int minStepped = Math.max(step, (min / step) * step);
        int maxStepped = Math.max(minStepped, (max / step) * step);
        int buckets = Math.max(1, ((maxStepped - minStepped) / step) + 1);
        int offset = Math.floorMod((instanceId + ":monthly-playtime:" + townId).hashCode(), buckets);
        return minStepped + (offset * step);
    }

    private int resolveTrialVaultTarget(String instanceId, ChallengeCycleType cycleType) {
        int min;
        int max;
        switch (cycleType) {
            case DAILY -> {
                min = 1;
                max = 2;
            }
            case WEEKLY -> {
                min = 3;
                max = 10;
            }
            case MONTHLY -> {
                min = 10;
                max = 20;
            }
            default -> {
                min = 1;
                max = 2;
            }
        }
        int span = Math.max(1, (max - min) + 1);
        int offset = Math.floorMod((instanceId + ":trial-vault:" + cycleType.name()).hashCode(), span);
        return min + offset;
    }

    public static String blockKey(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private static int clampDelta(long delta) {
        if (delta <= 0L) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, delta);
    }

    private static String normalizeError(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }

    private record SeasonWindow(
            String cycleKey,
            String seasonName,
            String seasonLabel,
            LocalDate startDate,
            LocalDate endDateExclusive
    ) {
    }

    public CompletableFuture<String> setSeasonalVetoCategory(int townId, int category) {
        CompletableFuture<String> future = new CompletableFuture<>();
        ioExecutor.execute(() -> {
            try {
                if (townId <= 0) {
                    future.complete("invalid-town");
                    return;
                }
                CityChallengeSettings.GovernanceSettings governance = settings.governance();
                if (!governance.seasonalVetoEnabled()) {
                    future.complete("veto-disabled");
                    return;
                }
                if (category <= 0) {
                    future.complete("invalid-category");
                    return;
                }
                String seasonKey = seasonKeyNow();
                String key = governanceKey(townId, seasonKey);
                CityChallengeRepository.GovernanceState state = governanceByTownSeason.get(key);
                if (state != null && state.vetoCategory() != null && governance.seasonalVetoImmutable()) {
                    future.complete("veto-already-set");
                    return;
                }
                CityChallengeRepository.GovernanceState next = new CityChallengeRepository.GovernanceState(
                        townId,
                        seasonKey,
                        category,
                        state == null ? 0 : state.rerollUsed(),
                        Instant.now()
                );
                governanceByTownSeason.put(key, next);
                store.upsertGovernanceState(next);
                future.complete("-");
            } catch (Exception exception) {
                future.complete(normalizeError(exception));
            }
        });
        return future;
    }

    public CompletableFuture<String> rerollWeeklyPackage(int townId) {
        CompletableFuture<String> future = new CompletableFuture<>();
        ioExecutor.execute(() -> {
            try {
                if (townId <= 0) {
                    future.complete("invalid-town");
                    return;
                }
                CityChallengeSettings.GovernanceSettings governance = settings.governance();
                if (!governance.rerollWeeklyEnabled()) {
                    future.complete("reroll-disabled");
                    return;
                }
                String seasonKey = seasonKeyNow();
                String key = governanceKey(townId, seasonKey);
                CityChallengeRepository.GovernanceState state = governanceByTownSeason.getOrDefault(
                        key,
                        new CityChallengeRepository.GovernanceState(townId, seasonKey, null, 0, Instant.now())
                );
                if (state.rerollUsed() >= governance.rerollWeeklyMaxPerCycle()) {
                    future.complete("reroll-limit-reached");
                    return;
                }
                int removed = 0;
                for (Map.Entry<String, ChallengeInstance> entry : new ArrayList<>(instances.entrySet())) {
                    ChallengeInstance instance = entry.getValue();
                    if (instance == null || !instance.active()) {
                        continue;
                    }
                    if (instance.mode() != ChallengeMode.WEEKLY_STANDARD) {
                        continue;
                    }
                    if (!currentWeeklyKey.equals(instance.cycleKey())) {
                        continue;
                    }
                    if (instance.townId() == null || instance.townId() != townId) {
                        continue;
                    }
                    instances.remove(entry.getKey());
                    progressByInstance.remove(entry.getKey());
                    removed++;
                }
                if (removed <= 0) {
                    future.complete("no-weekly-package");
                    return;
                }
                CityChallengeRepository.GovernanceState next = new CityChallengeRepository.GovernanceState(
                        townId,
                        seasonKey,
                        state.vetoCategory(),
                        state.rerollUsed() + 1,
                        Instant.now()
                );
                governanceByTownSeason.put(key, next);
                store.upsertGovernanceState(next);

                LocalDate weekStartDate = LocalDate.now(settings.timezone()).with(WeekFields.ISO.dayOfWeek(), 1);
                LocalDateTime cycleStart = weekStartDate.atTime(settings.weeklyRollover());
                LocalDateTime cycleEnd = weekStartDate.plusWeeks(1).atTime(settings.weeklyRollover());
                ensureInstancesForTown(
                        townId,
                        currentWeeklyKey,
                        ChallengeCycleType.WEEKLY,
                        "weekly-standard",
                        ChallengeMode.WEEKLY_STANDARD,
                        cycleStart.atZone(settings.timezone()).toInstant(),
                        cycleEnd.atZone(settings.timezone()).toInstant(),
                        weeklyStandardTargetCount()
                );
                future.complete("-");
            } catch (Exception exception) {
                future.complete(normalizeError(exception));
            }
        });
        return future;
    }

    public Integer vetoCategoryForTownSeason(int townId, String seasonKey) {
        CityChallengeRepository.GovernanceState state = governanceByTownSeason.get(governanceKey(townId, seasonKey));
        return state == null ? null : state.vetoCategory();
    }

    private String governanceKey(int townId, String seasonKey) {
        return townId + "|" + seasonKey;
    }

    private String seasonKeyNow() {
        return ChallengeSeasonResolver.seasonKey(LocalDate.now(settings.timezone()));
    }
}
