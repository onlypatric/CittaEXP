package it.patric.cittaexp.defense;

import it.patric.cittaexp.custommobs.CustomMobTypeResolver;
import it.patric.cittaexp.custommobs.CustomMobRegistry;
import it.patric.cittaexp.itemsadder.ItemsAdderRewardResolver;
import it.patric.cittaexp.utils.YamlResourceBackfill;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

public final class CityDefenseSettings {

    public record DefenderScaling(double perExtraDefender, double maxMultiplier) {
    }

    public record SpawnSettings(
            int minDistanceFromGuardian,
            int maxDistanceFromGuardian,
            int frontCountMin,
            int frontCountMax,
            int groupSizeMin,
            int groupSizeMax,
            int frontJitterRadius,
            int intraGroupSpacing,
            boolean allowInsideClaims,
            boolean allowOutsideClaims
    ) {
    }

    public record BreachSettings(
            boolean enabled,
            long pathFailSeconds,
            long breakCooldownSeconds,
            int maxBreaksPerMob,
            int maxVerticalDelta,
            int searchDepth,
            Set<EntityType> vanillaBreacherTypes
    ) {
        public boolean isVanillaBreacher(EntityType entityType) {
            return entityType != null && vanillaBreacherTypes.contains(entityType);
        }
    }

    public record RecoverySettings(
            long stuckSampleSeconds,
            double stuckDistanceThreshold,
            long stuckTimeoutSeconds,
            int repositionStepDistance,
            int maxAttemptsPerWave
    ) {
    }

    public record RetryBand(double moneyMultiplier, double baseResourceMultiplier) {
    }

    public record RetryPolicy(
            CityDefenseTier exemptFromTier,
            RetryBand lowBand,
            RetryBand midBand,
            RetryBand highBand,
            Set<Material> boostedMaterials
    ) {
        public boolean retryEligible(CityDefenseTier tier) {
            return tier != null && tier.number() < exemptFromTier.number();
        }

        public boolean retryActive(CityDefenseTier tier, boolean alreadyCompleted) {
            return alreadyCompleted && retryEligible(tier);
        }

        public RetryBand bandFor(CityDefenseTier tier) {
            if (tier == null || tier.number() <= 5) {
                return lowBand;
            }
            if (tier.number() <= 10) {
                return midBand;
            }
            return highBand;
        }

        public boolean boostedMaterial(Material material) {
            return material != null && boostedMaterials.contains(material);
        }
    }

    private record DefaultLevelSpec(
            int minCityLevel,
            int waves,
            double guardianHp,
            BigDecimal startCost,
            BigDecimal rewardMoney,
            long cooldownSeconds,
            long maxDurationSeconds,
            int rewardXp,
            List<CityDefenseSpawnRef> regularMobs,
            List<CityDefenseSpawnRef> eliteMobs,
            CityDefenseSpawnRef bossMob,
            int baseMobCount,
            int waveIncrement,
            int bossSupportCount,
            double eliteChance,
            Map<Material, Integer> rewardItems,
            List<ItemsAdderRewardResolver.RewardSpec> firstClearUniqueRewards
    ) {
    }

    private final boolean enabled;
    private final int globalActiveCap;
    private final int antiCheeseRadius;
    private final long sessionTimeoutSeconds;
    private final long prepSeconds;
    private final long interWaveSeconds;
    private final int maxMobsAlive;
    private final int minSpawnPoints;
    private final int spawnRadiusMin;
    private final int spawnRadiusMax;
    private final SpawnSettings spawnSettings;
    private final BreachSettings breachSettings;
    private final RecoverySettings recoverySettings;
    private final double playerDeathPenaltyPercent;
    private final DefenderScaling defenderScaling;
    private final RetryPolicy retryPolicy;
    private final Map<CityDefenseTier, CityDefenseLevelSpec> levels;

    private CityDefenseSettings(
            boolean enabled,
            int globalActiveCap,
            int antiCheeseRadius,
            long sessionTimeoutSeconds,
            long prepSeconds,
            long interWaveSeconds,
            int maxMobsAlive,
            int minSpawnPoints,
            int spawnRadiusMin,
            int spawnRadiusMax,
            SpawnSettings spawnSettings,
            BreachSettings breachSettings,
            RecoverySettings recoverySettings,
            double playerDeathPenaltyPercent,
            DefenderScaling defenderScaling,
            RetryPolicy retryPolicy,
            Map<CityDefenseTier, CityDefenseLevelSpec> levels
    ) {
        this.enabled = enabled;
        this.globalActiveCap = globalActiveCap;
        this.antiCheeseRadius = antiCheeseRadius;
        this.sessionTimeoutSeconds = sessionTimeoutSeconds;
        this.prepSeconds = prepSeconds;
        this.interWaveSeconds = interWaveSeconds;
        this.maxMobsAlive = maxMobsAlive;
        this.minSpawnPoints = minSpawnPoints;
        this.spawnRadiusMin = spawnRadiusMin;
        this.spawnRadiusMax = spawnRadiusMax;
        this.spawnSettings = spawnSettings;
        this.breachSettings = breachSettings;
        this.recoverySettings = recoverySettings;
        this.playerDeathPenaltyPercent = playerDeathPenaltyPercent;
        this.defenderScaling = defenderScaling;
        this.retryPolicy = retryPolicy;
        this.levels = levels;
    }

    public static CityDefenseSettings load(Plugin plugin) {
        return load(plugin, new CustomMobRegistry(Map.of()));
    }

    public static CityDefenseSettings load(Plugin plugin, CustomMobRegistry customMobRegistry) {
        YamlResourceBackfill.ensure(plugin, "defense.yml");
        File file = new File(plugin.getDataFolder(), "defense.yml");
        return load(file, plugin.getLogger(), customMobRegistry);
    }

    static CityDefenseSettings load(File file, Logger logger, CustomMobRegistry customMobRegistry) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        boolean enabled = cfg.getBoolean("enabled", true);
        int globalCap = Math.max(1, cfg.getInt("globalActiveCap", 2));
        int antiCheeseRadius = Math.max(4, cfg.getInt("antiCheeseRadius", 16));
        long timeout = Math.max(60L, cfg.getLong("timing.sessionTimeoutSeconds", 2_100L));
        long prep = Math.max(0L, cfg.getLong("timing.prepSeconds", 60L));
        long interWave = Math.max(0L, cfg.getLong("timing.interWaveSeconds", 12L));
        int maxMobsAlive = Math.max(10, cfg.getInt("maxMobsAlive", 90));
        int minSpawnPoints = Math.max(1, cfg.getInt("spawn.minPoints", 5));
        int spawnRadiusMin = Math.max(4, cfg.getInt("spawn.radiusMin", 20));
        int spawnRadiusMax = Math.max(spawnRadiusMin + 1, cfg.getInt("spawn.radiusMax", 32));
        SpawnSettings spawnSettings = loadSpawnSettings(cfg, minSpawnPoints, spawnRadiusMin, spawnRadiusMax);
        BreachSettings breachSettings = loadBreachSettings(cfg, logger);
        RecoverySettings recoverySettings = loadRecoverySettings(cfg);
        double playerDeathPenaltyPercent = clamp(cfg.getDouble("playerDeathPenaltyPercent", 0.12D), 0.0D, 1.0D);

        DefenderScaling scaling = new DefenderScaling(
                Math.max(0.0D, cfg.getDouble("defenderScaling.perExtraDefender", 0.22D)),
                Math.max(1.0D, cfg.getDouble("defenderScaling.maxMultiplier", 3.0D))
        );
        RetryPolicy retryPolicy = loadRetryPolicy(cfg, logger);

        Map<CityDefenseTier, CityDefenseLevelSpec> levels = new EnumMap<>(CityDefenseTier.class);
        ConfigurationSection levelsSection = cfg.getConfigurationSection("levels");
        for (CityDefenseTier tier : CityDefenseTier.values()) {
            ConfigurationSection row = levelsSection == null ? null : levelsSection.getConfigurationSection(tier.id());
            levels.put(tier, parseLevelSpec(tier, row, customMobRegistry, logger));
        }
        return new CityDefenseSettings(
                enabled,
                globalCap,
                antiCheeseRadius,
                timeout,
                prep,
                interWave,
                maxMobsAlive,
                minSpawnPoints,
                spawnRadiusMin,
                spawnRadiusMax,
                spawnSettings,
                breachSettings,
                recoverySettings,
                playerDeathPenaltyPercent,
                scaling,
                retryPolicy,
                Map.copyOf(levels)
        );
    }

    private static CityDefenseLevelSpec parseLevelSpec(
            CityDefenseTier tier,
            ConfigurationSection row,
            CustomMobRegistry customMobRegistry,
            Logger logger
    ) {
        DefaultLevelSpec defaults = defaultLevelSpec(tier);

        int minLevel = row == null ? defaults.minCityLevel() : Math.max(1, row.getInt("minCityLevel", defaults.minCityLevel()));
        int waves = row == null ? defaults.waves() : Math.max(1, row.getInt("waves", defaults.waves()));
        double guardianHp = row == null ? defaults.guardianHp() : Math.max(1.0D, row.getDouble("guardianHp", defaults.guardianHp()));
        BigDecimal startCost = BigDecimal.valueOf(row == null ? defaults.startCost().longValue() : Math.max(0L, row.getLong("startCost", defaults.startCost().longValue())));
        BigDecimal rewardMoney = BigDecimal.valueOf(row == null ? defaults.rewardMoney().longValue() : Math.max(0L, row.getLong("rewardMoney", defaults.rewardMoney().longValue())));
        long cooldownSeconds = row == null ? defaults.cooldownSeconds() : Math.max(0L, row.getLong("cooldownSeconds", defaults.cooldownSeconds()));
        long maxDurationSeconds = row == null ? defaults.maxDurationSeconds() : Math.max(60L, row.getLong("maxDurationSeconds", defaults.maxDurationSeconds()));
        int rewardXp = row == null ? defaults.rewardXp() : Math.max(0, row.getInt("rewardXp", defaults.rewardXp()));
        int baseMobs = row == null ? defaults.baseMobCount() : Math.max(1, row.getInt("baseMobCount", defaults.baseMobCount()));
        int waveIncrement = row == null ? defaults.waveIncrement() : Math.max(0, row.getInt("waveIncrement", defaults.waveIncrement()));
        int bossSupportCount = row == null ? defaults.bossSupportCount() : Math.max(0, row.getInt("bossSupportCount", defaults.bossSupportCount()));
        double eliteChance = row == null ? defaults.eliteChance() : clamp(row.getDouble("eliteChance", defaults.eliteChance()), 0.0D, 1.0D);
        List<CityDefenseSpawnRef> regularMobs = parseSpawnRefList(
                row == null ? null : row.getStringList("regularMobs"),
                defaults.regularMobs(),
                customMobRegistry,
                logger,
                tier,
                "regularMobs"
        );
        List<CityDefenseSpawnRef> eliteMobs = parseSpawnRefList(
                row == null ? null : row.getStringList("eliteMobs"),
                defaults.eliteMobs(),
                customMobRegistry,
                logger,
                tier,
                "eliteMobs"
        );
        CityDefenseSpawnRef bossMob = parseSpawnRef(
                row == null ? null : row.getString("bossMob"),
                defaults.bossMob(),
                customMobRegistry,
                logger,
                tier,
                "bossMob"
        );
        Map<Material, Integer> rewardItems = parseItems(row == null ? null : row.getConfigurationSection("rewardItems"), defaults.rewardItems());
        List<ItemsAdderRewardResolver.RewardSpec> firstClearUniqueRewards = parseUniqueRewards(
                row == null ? null : row.getMapList("firstClearUniqueRewards"),
                logger,
                tier
        );

        return new CityDefenseLevelSpec(
                tier,
                minLevel,
                waves,
                guardianHp,
                startCost,
                rewardMoney,
                cooldownSeconds,
                maxDurationSeconds,
                rewardXp,
                rewardItems,
                firstClearUniqueRewards,
                baseMobs,
                waveIncrement,
                bossSupportCount,
                eliteChance,
                regularMobs,
                eliteMobs,
                bossMob
        );
    }

    private static DefaultLevelSpec defaultLevelSpec(CityDefenseTier tier) {
        int number = tier.number();
        int waves = 3 + number;
        double guardianHp = 300.0D + (number * number * 90.0D);
        BigDecimal startCost = BigDecimal.valueOf(20_000L * number * number);
        BigDecimal rewardMoney = defaultRewardMoney(number, startCost);
        long cooldownSeconds = Math.max(7_200L, number * 21_600L);
        long maxDurationSeconds = defaultMaxDurationSeconds(number);
        int rewardXp = 250 * number * number;
        int baseMobCount = 8 + (number * 2);
        int waveIncrement = 3 + (number / 3);
        int bossSupportCount = 5 + (number * 2);
        double eliteChance = clamp(0.08D + (number * 0.02D), 0.08D, 0.65D);
        List<CityDefenseSpawnRef> regularMobs = defaultRegularRefs(number);
        List<CityDefenseSpawnRef> eliteMobs = defaultEliteRefs(number);
        CityDefenseSpawnRef bossMob = CityDefenseSpawnRef.vanilla(defaultBossType(number));
        Map<Material, Integer> rewardItems = defaultRewardItems(number);
        return new DefaultLevelSpec(
                number * 10,
                waves,
                guardianHp,
                startCost,
                rewardMoney,
                cooldownSeconds,
                maxDurationSeconds,
                rewardXp,
                regularMobs,
                eliteMobs,
                bossMob,
                baseMobCount,
                waveIncrement,
                bossSupportCount,
                eliteChance,
                rewardItems,
                List.of()
        );
    }

    private static BigDecimal defaultRewardMoney(int tierNumber, BigDecimal startCost) {
        double ratio;
        if (tierNumber <= 5) {
            ratio = 0.25D + ((tierNumber - 1) * 0.025D);
        } else if (tierNumber <= 10) {
            ratio = 0.30D + ((tierNumber - 6) * 0.025D);
        } else if (tierNumber <= 15) {
            ratio = 0.35D + ((tierNumber - 11) * 0.025D);
        } else if (tierNumber <= 20) {
            ratio = 0.40D + ((tierNumber - 16) * 0.025D);
        } else {
            ratio = 0.50D + ((tierNumber - 21) * 0.0125D);
        }
        return startCost.multiply(BigDecimal.valueOf(ratio)).setScale(0, RoundingMode.HALF_UP);
    }

    private static long defaultMaxDurationSeconds(int tierNumber) {
        if (tierNumber <= 5) {
            return 1_800L + ((long) (tierNumber - 1) * 150L);
        }
        if (tierNumber <= 10) {
            return 2_400L + ((long) (tierNumber - 6) * 150L);
        }
        if (tierNumber <= 15) {
            return 3_000L + ((long) (tierNumber - 11) * 150L);
        }
        if (tierNumber <= 20) {
            return 3_600L + ((long) (tierNumber - 16) * 225L);
        }
        return 4_500L + ((long) (tierNumber - 21) * 225L);
    }

    private static RetryPolicy loadRetryPolicy(YamlConfiguration cfg, Logger logger) {
        ConfigurationSection section = cfg.getConfigurationSection("retry");
        CityDefenseTier exemptFromTier = parseTier(
                section == null ? null : section.getString("exemptFromTier"),
                CityDefenseTier.L16,
                logger,
                "retry.exemptFromTier"
        );
        RetryBand lowBand = parseRetryBand(section == null ? null : section.getConfigurationSection("l1To5"), 0.20D, 1.50D);
        RetryBand midBand = parseRetryBand(section == null ? null : section.getConfigurationSection("l6To10"), 0.35D, 1.35D);
        RetryBand highBand = parseRetryBand(section == null ? null : section.getConfigurationSection("l11To15"), 0.50D, 1.20D);
        Set<Material> boostedMaterials = EnumSet.noneOf(Material.class);
        List<String> configuredMaterials = section == null ? List.of() : section.getStringList("boostedMaterials");
        if (configuredMaterials.isEmpty()) {
            boostedMaterials.addAll(defaultRetryBoostedMaterials());
        } else {
            for (String raw : configuredMaterials) {
                Material material = Material.matchMaterial(raw);
                if (material == null) {
                    if (logger != null) {
                        logger.warning("[defense] materiale retry non valido: " + raw);
                    }
                    continue;
                }
                boostedMaterials.add(material);
            }
            if (boostedMaterials.isEmpty()) {
                boostedMaterials.addAll(defaultRetryBoostedMaterials());
            }
        }
        return new RetryPolicy(exemptFromTier, lowBand, midBand, highBand, Set.copyOf(boostedMaterials));
    }

    private static RetryBand parseRetryBand(ConfigurationSection section, double fallbackMoney, double fallbackItems) {
        return new RetryBand(
                clamp(section == null ? fallbackMoney : section.getDouble("moneyMultiplier", fallbackMoney), 0.0D, 1.0D),
                Math.max(1.0D, section == null ? fallbackItems : section.getDouble("baseResourceMultiplier", fallbackItems))
        );
    }

    private static SpawnSettings loadSpawnSettings(
            YamlConfiguration cfg,
            int legacyMinSpawnPoints,
            int legacySpawnRadiusMin,
            int legacySpawnRadiusMax
    ) {
        ConfigurationSection section = cfg.getConfigurationSection("spawn");
        int minDistance = Math.max(8, section == null ? Math.max(25, legacySpawnRadiusMin) : section.getInt("minDistanceFromGuardian", Math.max(25, legacySpawnRadiusMin)));
        int maxDistance = Math.max(minDistance + 1, section == null ? Math.max(50, legacySpawnRadiusMax) : section.getInt("maxDistanceFromGuardian", Math.max(50, legacySpawnRadiusMax)));
        int frontCountMin = Math.max(1, section == null ? 2 : section.getInt("frontCountMin", 2));
        int frontCountMax = Math.max(frontCountMin, section == null ? Math.max(frontCountMin, Math.min(4, legacyMinSpawnPoints)) : section.getInt("frontCountMax", Math.max(frontCountMin, 4)));
        int groupSizeMin = Math.max(1, section == null ? 3 : section.getInt("groupSizeMin", 3));
        int groupSizeMax = Math.max(groupSizeMin, section == null ? 6 : section.getInt("groupSizeMax", 6));
        int frontJitterRadius = Math.max(0, section == null ? 8 : section.getInt("frontJitterRadius", 8));
        int intraGroupSpacing = Math.max(1, section == null ? 3 : section.getInt("intraGroupSpacing", 3));
        boolean allowInsideClaims = section == null || section.getBoolean("allowInsideClaims", true);
        boolean allowOutsideClaims = section == null || section.getBoolean("allowOutsideClaims", true);
        return new SpawnSettings(
                minDistance,
                maxDistance,
                frontCountMin,
                frontCountMax,
                groupSizeMin,
                groupSizeMax,
                frontJitterRadius,
                intraGroupSpacing,
                allowInsideClaims,
                allowOutsideClaims
        );
    }

    private static BreachSettings loadBreachSettings(YamlConfiguration cfg, Logger logger) {
        ConfigurationSection section = cfg.getConfigurationSection("breach");
        boolean enabled = section != null && section.getBoolean("enabled", true);
        long pathFailSeconds = Math.max(1L, section == null ? 6L : section.getLong("pathFailSeconds", 6L));
        long breakCooldownSeconds = Math.max(1L, section == null ? 4L : section.getLong("breakCooldownSeconds", 4L));
        int maxBreaksPerMob = Math.max(0, section == null ? 8 : section.getInt("maxBreaksPerMob", 8));
        int maxVerticalDelta = Math.max(0, section == null ? 2 : section.getInt("maxVerticalDelta", 2));
        int searchDepth = Math.max(1, section == null ? 1 : section.getInt("searchDepth", 1));
        Set<EntityType> vanillaBreacherTypes = EnumSet.noneOf(EntityType.class);
        List<String> configuredTypes = section == null ? List.of() : section.getStringList("vanillaBreacherTypes");
        for (String raw : configuredTypes) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                EntityType type = EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
                if (type.isAlive() && type != EntityType.PLAYER) {
                    vanillaBreacherTypes.add(type);
                }
            } catch (IllegalArgumentException exception) {
                if (logger != null) {
                    logger.warning("[defense] vanilla breacher type non valido: " + raw);
                }
            }
        }
        return new BreachSettings(
                enabled,
                pathFailSeconds,
                breakCooldownSeconds,
                maxBreaksPerMob,
                maxVerticalDelta,
                searchDepth,
                Set.copyOf(vanillaBreacherTypes)
        );
    }

    private static RecoverySettings loadRecoverySettings(YamlConfiguration cfg) {
        ConfigurationSection section = cfg.getConfigurationSection("recovery");
        return new RecoverySettings(
                Math.max(1L, section == null ? 3L : section.getLong("stuckSampleSeconds", 3L)),
                clamp(section == null ? 2.0D : section.getDouble("stuckDistanceThreshold", 2.0D), 0.1D, 16.0D),
                Math.max(1L, section == null ? 8L : section.getLong("stuckTimeoutSeconds", 8L)),
                Math.max(1, section == null ? 8 : section.getInt("repositionStepDistance", 8)),
                Math.max(0, section == null ? 3 : section.getInt("maxAttemptsPerWave", 3))
        );
    }

    private static CityDefenseTier parseTier(String raw, CityDefenseTier fallback, Logger logger, String path) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return CityDefenseTier.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            if (logger != null) {
                logger.warning("[defense] tier non valido in " + path + ": " + raw + " -> fallback " + fallback.name());
            }
            return fallback;
        }
    }

    private static Set<Material> defaultRetryBoostedMaterials() {
        return EnumSet.of(
                Material.IRON_INGOT,
                Material.GOLD_INGOT,
                Material.COPPER_INGOT,
                Material.REDSTONE,
                Material.COAL,
                Material.LAPIS_LAZULI,
                Material.QUARTZ,
                Material.BREAD,
                Material.ARROW,
                Material.STRING,
                Material.LEATHER,
                Material.PRISMARINE,
                Material.OBSIDIAN
        );
    }

    private static List<CityDefenseSpawnRef> defaultRegularRefs(int tierNumber) {
        if (tierNumber <= 5) {
            return vanillaRefs(List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER));
        }
        if (tierNumber <= 10) {
            return vanillaRefs(List.of(EntityType.PILLAGER, EntityType.CREEPER, EntityType.STRAY, EntityType.ENDERMAN));
        }
        if (tierNumber <= 15) {
            return vanillaRefs(List.of(EntityType.WITHER_SKELETON, EntityType.ENDERMAN, EntityType.PILLAGER, EntityType.WITCH));
        }
        if (tierNumber <= 20) {
            return vanillaRefs(List.of(EntityType.DROWNED, EntityType.STRAY, EntityType.ENDERMAN, EntityType.WITHER_SKELETON));
        }
        return vanillaRefs(List.of(EntityType.WITHER_SKELETON, EntityType.ENDERMAN, EntityType.PILLAGER, EntityType.PIGLIN_BRUTE));
    }

    private static List<CityDefenseSpawnRef> defaultEliteRefs(int tierNumber) {
        if (tierNumber <= 5) {
            return vanillaRefs(List.of(EntityType.HUSK, EntityType.STRAY, EntityType.VINDICATOR));
        }
        if (tierNumber <= 10) {
            return vanillaRefs(List.of(EntityType.WITCH, EntityType.WITHER_SKELETON, EntityType.PIGLIN_BRUTE));
        }
        if (tierNumber <= 15) {
            return vanillaRefs(List.of(EntityType.WITCH, EntityType.PIGLIN_BRUTE, EntityType.EVOKER));
        }
        if (tierNumber <= 20) {
            return vanillaRefs(List.of(EntityType.WITCH, EntityType.PIGLIN_BRUTE, EntityType.WITHER_SKELETON, EntityType.ELDER_GUARDIAN));
        }
        return vanillaRefs(List.of(EntityType.PIGLIN_BRUTE, EntityType.WITCH, EntityType.WITHER_SKELETON, EntityType.EVOKER));
    }

    private static EntityType defaultBossType(int tierNumber) {
        if (tierNumber <= 5) {
            return EntityType.RAVAGER;
        }
        if (tierNumber <= 10) {
            return EntityType.PIGLIN_BRUTE;
        }
        if (tierNumber <= 15) {
            return EntityType.EVOKER;
        }
        if (tierNumber <= 20) {
            return EntityType.WARDEN;
        }
        return EntityType.WARDEN;
    }

    private static Map<Material, Integer> defaultRewardItems(int tierNumber) {
        Map<Material, Integer> items = new LinkedHashMap<>();
        items.put(Material.DIAMOND, 8 + (tierNumber * 4));
        items.put(Material.EMERALD, 16 + (tierNumber * 8));
        if (tierNumber >= 4) {
            items.put(Material.ENDER_PEARL, tierNumber * 8);
        }
        if (tierNumber >= 6) {
            items.put(Material.BLAZE_ROD, tierNumber * 4);
        }
        if (tierNumber >= 10) {
            items.put(Material.NETHERITE_SCRAP, Math.max(1, tierNumber / 2));
        }
        if (tierNumber >= 12) {
            items.put(Material.ECHO_SHARD, Math.max(4, tierNumber * 2));
        }
        if (tierNumber >= 18) {
            items.put(Material.TOTEM_OF_UNDYING, Math.max(1, tierNumber / 6));
        }
        return Map.copyOf(items);
    }

    private static List<CityDefenseSpawnRef> parseSpawnRefList(
            List<String> raw,
            List<CityDefenseSpawnRef> fallback,
            CustomMobRegistry customMobRegistry,
            Logger logger,
            CityDefenseTier tier,
            String fieldName
    ) {
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        ArrayList<CityDefenseSpawnRef> parsed = new ArrayList<>();
        for (String value : raw) {
            CityDefenseSpawnRef ref = parseSpawnRef(value, null, customMobRegistry, logger, tier, fieldName);
            if (ref == null) {
                continue;
            }
            parsed.add(ref);
        }
        if (parsed.isEmpty()) {
            return fallback;
        }
        return List.copyOf(parsed);
    }

    private static CityDefenseSpawnRef parseSpawnRef(
            String raw,
            CityDefenseSpawnRef fallback,
            CustomMobRegistry customMobRegistry,
            Logger logger,
            CityDefenseTier tier,
            String fieldName
    ) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            CityDefenseSpawnRef ref = CityDefenseSpawnRef.parse(raw);
            if (ref.isVanilla()) {
                EntityType type = ref.vanillaType();
                if (type == null || !type.isAlive() || type == EntityType.PLAYER) {
                    throw new IllegalArgumentException("vanilla type is not a valid living mob");
                }
                return ref;
            }
            if (customMobRegistry.get(ref.customMobId()).isEmpty()) {
                throw new IllegalArgumentException("unknown custom mob id");
            }
            return ref;
        } catch (IllegalArgumentException exception) {
            if (logger != null) {
                logger.warning("[defense] invalid spawn ref tier=" + tier.name()
                        + " field=" + fieldName
                        + " value=" + raw
                        + " reason=" + exception.getMessage());
            }
            return fallback;
        }
    }

    private static List<CityDefenseSpawnRef> vanillaRefs(List<EntityType> types) {
        return types.stream()
                .map(CityDefenseSpawnRef::vanilla)
                .toList();
    }

    private static Map<Material, Integer> parseItems(ConfigurationSection section, Map<Material, Integer> fallback) {
        if (section == null) {
            return fallback;
        }
        Map<Material, Integer> parsed = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Material material;
            try {
                material = CustomMobTypeResolver.parseMaterial(key.trim());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            int amount = Math.max(0, section.getInt(key, 0));
            if (material.isAir() || amount <= 0) {
                continue;
            }
            parsed.put(material, amount);
        }
        return parsed.isEmpty() ? fallback : Map.copyOf(parsed);
    }

    private static List<ItemsAdderRewardResolver.RewardSpec> parseUniqueRewards(
            List<Map<?, ?>> raw,
            Logger logger,
            CityDefenseTier tier
    ) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<ItemsAdderRewardResolver.RewardSpec> parsed = new ArrayList<>();
        for (Map<?, ?> row : raw) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            Object idRaw = row.get("item");
            if (idRaw == null) {
                idRaw = row.get("id");
            }
            String itemId = idRaw == null ? "" : idRaw.toString().trim();
            int amount = 1;
            Object amountRaw = row.get("amount");
            if (amountRaw instanceof Number number) {
                amount = Math.max(1, number.intValue());
            } else if (amountRaw != null) {
                try {
                    amount = Math.max(1, Integer.parseInt(amountRaw.toString().trim()));
                } catch (NumberFormatException ignored) {
                    amount = 1;
                }
            }
            if (itemId.isBlank()) {
                if (logger != null) {
                    logger.warning("[defense] firstClearUniqueRewards invalid entry tier=" + tier.name() + " missing item id");
                }
                continue;
            }
            parsed.add(new ItemsAdderRewardResolver.RewardSpec(itemId, amount));
        }
        return parsed.isEmpty() ? List.of() : List.copyOf(parsed);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public boolean enabled() {
        return enabled;
    }

    public int globalActiveCap() {
        return globalActiveCap;
    }

    public int antiCheeseRadius() {
        return antiCheeseRadius;
    }

    public long sessionTimeoutSeconds() {
        return sessionTimeoutSeconds;
    }

    public long prepSeconds() {
        return prepSeconds;
    }

    public long interWaveSeconds() {
        return interWaveSeconds;
    }

    public int maxMobsAlive() {
        return maxMobsAlive;
    }

    public int minSpawnPoints() {
        return minSpawnPoints;
    }

    public int spawnRadiusMin() {
        return spawnRadiusMin;
    }

    public int spawnRadiusMax() {
        return spawnRadiusMax;
    }

    public double playerDeathPenaltyPercent() {
        return playerDeathPenaltyPercent;
    }

    public SpawnSettings spawnSettings() {
        return spawnSettings;
    }

    public BreachSettings breachSettings() {
        return breachSettings;
    }

    public RecoverySettings recoverySettings() {
        return recoverySettings;
    }

    public DefenderScaling defenderScaling() {
        return defenderScaling;
    }

    public RetryPolicy retryPolicy() {
        return retryPolicy;
    }

    public CityDefenseLevelSpec level(CityDefenseTier tier) {
        return levels.get(tier);
    }

    public Map<CityDefenseTier, CityDefenseLevelSpec> levels() {
        return levels;
    }
}
