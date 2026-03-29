package it.patric.cittaexp.challenges;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class ProceduralChallengeGenerator {

    public record ResolvedContext(String biomeKey, String dimensionKey) {
        public static final ResolvedContext NONE = new ResolvedContext(null, null);
    }

    private record TargetBounds(int min, int max, int step, int weight) {
    }

    private record Profile(
            String id,
            String name,
            ChallengeObjectiveType objectiveType,
            int category,
            String bundleSlot,
            String difficultyTag,
            String objectiveFamily,
            String rewardBundleId,
            Set<ChallengeMode> cycles,
            ChallengeVariantMode variantMode,
            double specificChance,
            ChallengeFocusType focusType,
            List<ChallengeFocusOption> focusPool,
            TargetBounds defaultBounds
    ) {
    }

    private final Plugin plugin;
    private final ChallengeTaxonomyService taxonomyService;
    private final Map<ChallengeObjectiveType, TargetBounds> boundsByObjective;
    private final List<Profile> profiles;
    private final double biomeChance;
    private final double dimensionChance;
    private final int signatureTownDays;
    private final int signatureGlobalDays;

    public ProceduralChallengeGenerator(Plugin plugin, ChallengeTaxonomyService taxonomyService) {
        this.plugin = plugin;
        this.taxonomyService = taxonomyService;
        YamlConfiguration yaml = loadYaml(plugin);
        ChallengeContextResolver.configureBiomeFamilies(yaml.getConfigurationSection("procedural.context.biomeFamilies"));
        this.boundsByObjective = loadBounds(yaml.getConfigurationSection("procedural.boundaries"));
        this.biomeChance = clampChance(yaml.getDouble("procedural.context.biomeChance", 0.65D));
        this.dimensionChance = clampChance(yaml.getDouble("procedural.context.dimensionChance", 0.45D));
        this.signatureTownDays = Math.max(1, yaml.getInt("procedural.signature.townDays", 30));
        this.signatureGlobalDays = Math.max(1, yaml.getInt("procedural.signature.globalDays", 7));
        this.profiles = defaultProfiles();
    }

    public int signatureTownDays() {
        return signatureTownDays;
    }

    public int signatureGlobalDays() {
        return signatureGlobalDays;
    }

    public List<ChallengeDefinition> definitionsForMode(ChallengeMode mode) {
        List<ChallengeDefinition> result = new ArrayList<>();
        for (Profile profile : profiles) {
            if (mode == null || !supportsMode(profile, mode)) {
                continue;
            }
            if (taxonomyService != null && !taxonomyService.isAllowed(profile.objectiveType, mode)) {
                continue;
            }
            TargetBounds bounds = usesProfileBounds(profile)
                    ? profile.defaultBounds
                    : boundsByObjective.getOrDefault(profile.objectiveType, profile.defaultBounds);
            result.add(new ChallengeDefinition(
                    profile.id,
                    profile.name,
                    profile.objectiveType,
                    profile.category,
                    profile.bundleSlot,
                    profile.difficultyTag,
                    profile.objectiveFamily,
                    bounds.min,
                    bounds.min,
                    bounds.max,
                    bounds.step,
                    bounds.weight,
                    EnumSet.of(mode),
                    profile.variantMode,
                    profile.specificChance,
                    Map.of(),
                    profile.focusType,
                    profile.focusPool,
                    profile.rewardBundleId,
                    true
            ));
        }
        return result;
    }

    private static boolean supportsMode(Profile profile, ChallengeMode mode) {
        if (profile == null || mode == null) {
            return false;
        }
        if (profile.cycles.contains(mode)) {
            return true;
        }
        ChallengeObjectiveType objective = profile.objectiveType;
        return switch (mode) {
            case DAILY_SPRINT_1830, DAILY_SPRINT_2130 ->
                    profile.cycles.contains(ChallengeMode.DAILY_RACE_1600)
                            || profile.cycles.contains(ChallengeMode.DAILY_RACE_2100);
            case WEEKLY_CLASH -> profile.cycles.contains(ChallengeMode.WEEKLY_STANDARD) && isCompetitiveObjective(objective);
            case MONTHLY_CROWN -> (profile.cycles.contains(ChallengeMode.MONTHLY_EVENT_A)
                    || profile.cycles.contains(ChallengeMode.MONTHLY_EVENT_B)
                    || profile.cycles.contains(ChallengeMode.MONTHLY_STANDARD))
                    && isCompetitiveObjective(objective);
            case MONTHLY_LEDGER_GRAND -> profile.cycles.contains(ChallengeMode.MONTHLY_STANDARD)
                    && isGrandLedgerCandidate(profile);
            case MONTHLY_LEDGER_CONTRACT -> profile.cycles.contains(ChallengeMode.MONTHLY_STANDARD)
                    && !isMysteryLedgerCandidate(profile)
                    && !isGrandLedgerCandidate(profile);
            case MONTHLY_LEDGER_MYSTERY -> profile.cycles.contains(ChallengeMode.MONTHLY_STANDARD)
                    && isMysteryLedgerCandidate(profile);
            case SEASON_CODEX_ACT_I, SEASON_CODEX_ACT_II, SEASON_CODEX_ACT_III ->
                    profile.cycles.contains(ChallengeMode.MONTHLY_STANDARD)
                            && isCodexActCandidate(profile);
            case SEASON_CODEX_ELITE -> profile.cycles.contains(ChallengeMode.MONTHLY_STANDARD)
                    && isCodexEliteCandidate(profile);
            case SEASON_CODEX_HIDDEN_RELIC -> profile.cycles.contains(ChallengeMode.MONTHLY_STANDARD)
                    && isCodexHiddenRelicCandidate(profile);
            default -> false;
        };
    }

    private static boolean isCompetitiveObjective(ChallengeObjectiveType objective) {
        if (objective == null) {
            return false;
        }
        return switch (objective) {
            case MOB_KILL, RARE_MOB_KILL, BLOCK_MINE, CROP_HARVEST, FISH_CATCH, RAID_WIN, TRIAL_VAULT_OPEN -> true;
            default -> false;
        };
    }

    private static boolean isGrandLedgerCandidate(Profile profile) {
        if (profile == null) {
            return false;
        }
        if (isMysteryLedgerCandidate(profile)) {
            return false;
        }
        if ("epic".equalsIgnoreCase(profile.difficultyTag)) {
            return true;
        }
        return switch (profile.objectiveType) {
            case BOSS_KILL, RAID_WIN, TRIAL_VAULT_OPEN, TEAM_PHASE_QUEST, NETHER_ACTIVITY, OCEAN_ACTIVITY -> true;
            default -> false;
        };
    }

    private static boolean isMysteryLedgerCandidate(Profile profile) {
        if (profile == null) {
            return false;
        }
        if (profile.objectiveType == ChallengeObjectiveType.SECRET_QUEST) {
            return true;
        }
        String id = profile.id == null ? "" : profile.id.toLowerCase(Locale.ROOT);
        return id.contains("secret") || id.contains("mystery");
    }

    private static boolean isCodexActCandidate(Profile profile) {
        if (profile == null) {
            return false;
        }
        return switch (profile.objectiveType) {
            case PLAYTIME_MINUTES, XP_PICKUP, ECONOMY_ADVANCED -> false;
            case SECRET_QUEST, TEAM_PHASE_QUEST -> false;
            default -> true;
        };
    }

    private static boolean isCodexEliteCandidate(Profile profile) {
        if (profile == null) {
            return false;
        }
        if ("hard".equalsIgnoreCase(profile.difficultyTag) || "epic".equalsIgnoreCase(profile.difficultyTag)) {
            return true;
        }
        return switch (profile.objectiveType) {
            case BOSS_KILL, RAID_WIN, RARE_MOB_KILL, TRIAL_VAULT_OPEN, TEAM_PHASE_QUEST, SECRET_QUEST -> true;
            default -> false;
        };
    }

    private static boolean isCodexHiddenRelicCandidate(Profile profile) {
        if (profile == null) {
            return false;
        }
        return isMysteryLedgerCandidate(profile);
    }

    private static boolean usesProfileBounds(Profile profile) {
        if (profile == null) {
            return false;
        }
        return profile.objectiveType == ChallengeObjectiveType.TEAM_PHASE_QUEST
                || isMysteryLedgerCandidate(profile);
    }

    public ResolvedContext resolveContext(
            ChallengeObjectiveType objectiveType,
            ChallengeMode mode,
            String seed
    ) {
        if (objectiveType == null || mode == null || mode.race() || !supportsContext(objectiveType)) {
            return ResolvedContext.NONE;
        }
        java.util.Random random = new java.util.Random(("ctx:" + mode.name() + ":" + safe(seed)).hashCode());
        String dimension = null;
        if (random.nextDouble() < dimensionChance) {
            List<String> dimensions = allowedDimensions(objectiveType);
            if (!dimensions.isEmpty()) {
                dimension = dimensions.get(Math.floorMod(random.nextInt(), dimensions.size()));
            }
        }
        return new ResolvedContext(
                null,
                ChallengeContextResolver.normalizeToken(dimension)
        );
    }

    public boolean supportsContext(ChallengeObjectiveType type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case MOB_KILL, RARE_MOB_KILL, BLOCK_MINE, CROP_HARVEST, FISH_CATCH, ANIMAL_INTERACTION,
                    CONSTRUCTION, REDSTONE_AUTOMATION, STRUCTURE_DISCOVERY, STRUCTURE_LOOT, NETHER_ACTIVITY -> true;
            default -> false;
        };
    }

    private static YamlConfiguration loadYaml(Plugin plugin) {
        File file = new File(plugin.getDataFolder(), "challenges.yml");
        if (!file.exists()) {
            plugin.saveResource("challenges.yml", false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private static double clampChance(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0D;
        }
        if (value < 0.0D) {
            return 0.0D;
        }
        if (value > 1.0D) {
            return 1.0D;
        }
        return value;
    }

    private static String safe(String value) {
        return value == null ? "-" : value;
    }

    private static boolean biomeSupportedInDimension(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return true;
        }
        String normalized = dimension.trim().toUpperCase(Locale.ROOT);
        return normalized.contains("OVERWORLD") || normalized.contains("NORMAL");
    }

    private static List<String> allowedDimensions(ChallengeObjectiveType type) {
        return switch (type) {
            case MOB_KILL, RARE_MOB_KILL, STRUCTURE_LOOT, STRUCTURE_DISCOVERY -> List.of(
                    "minecraft:overworld",
                    "minecraft:the_nether",
                    "minecraft:the_end"
            );
            case BLOCK_MINE -> List.of("minecraft:overworld", "minecraft:the_nether");
            case NETHER_ACTIVITY -> List.of("minecraft:the_nether");
            case CROP_HARVEST, FISH_CATCH, ANIMAL_INTERACTION, CONSTRUCTION, REDSTONE_AUTOMATION -> List.of("minecraft:overworld");
            default -> List.of();
        };
    }

    private static List<String> allowedBiomeFamilies(ChallengeObjectiveType type) {
        return switch (type) {
            case MOB_KILL, RARE_MOB_KILL -> List.of(
                    "PLAINS", "FOREST", "DESERT", "JUNGLE", "SAVANNA", "SWAMP", "SNOW", "TAIGA", "BADLANDS"
            );
            case BLOCK_MINE -> List.of("PLAINS", "FOREST", "JUNGLE", "MOUNTAIN", "CAVES");
            case CROP_HARVEST -> List.of("PLAINS", "MEADOW", "SAVANNA");
            case FISH_CATCH -> List.of("RIVER", "OCEAN", "BEACH");
            case ANIMAL_INTERACTION -> List.of("PLAINS", "FOREST", "SAVANNA", "MEADOW");
            case CONSTRUCTION, REDSTONE_AUTOMATION -> List.of("PLAINS", "FOREST", "TAIGA", "DESERT");
            case STRUCTURE_DISCOVERY, STRUCTURE_LOOT -> List.of("DESERT", "JUNGLE", "SWAMP", "TAIGA", "SNOW", "BADLANDS");
            default -> List.of();
        };
    }

    private static Map<ChallengeObjectiveType, TargetBounds> loadBounds(ConfigurationSection section) {
        Map<ChallengeObjectiveType, TargetBounds> result = new EnumMap<>(ChallengeObjectiveType.class);
        if (section == null) {
            return result;
        }
        for (String objectiveId : section.getKeys(false)) {
            ChallengeObjectiveType type = ChallengeObjectiveType.fromId(objectiveId);
            if (type == null) {
                continue;
            }
            ConfigurationSection row = section.getConfigurationSection(objectiveId);
            if (row == null) {
                continue;
            }
            int min = Math.max(1, row.getInt("min", 1));
            int max = Math.max(min, row.getInt("max", min));
            int step = Math.max(1, row.getInt("step", 1));
            int weight = Math.max(1, row.getInt("weight", 8));
            result.put(type, new TargetBounds(min, max, step, weight));
        }
        return result;
    }

    private static List<Profile> defaultProfiles() {
        Set<ChallengeMode> dailyWeekly = EnumSet.of(ChallengeMode.DAILY_STANDARD, ChallengeMode.WEEKLY_STANDARD);
        Set<ChallengeMode> weeklyMonthly = EnumSet.of(ChallengeMode.WEEKLY_STANDARD, ChallengeMode.MONTHLY_STANDARD);
        Set<ChallengeMode> weeklyMonthlyEvents = EnumSet.of(
                ChallengeMode.WEEKLY_STANDARD,
                ChallengeMode.MONTHLY_STANDARD,
                ChallengeMode.MONTHLY_EVENT_A,
                ChallengeMode.MONTHLY_EVENT_B
        );
        Set<ChallengeMode> dailyOnly = EnumSet.of(ChallengeMode.DAILY_STANDARD);
        Set<ChallengeMode> dailyWeeklyRace = EnumSet.of(
                ChallengeMode.DAILY_STANDARD,
                ChallengeMode.WEEKLY_STANDARD,
                ChallengeMode.DAILY_RACE_1600,
                ChallengeMode.DAILY_RACE_2100
        );
        Set<ChallengeMode> dailyRaceOnly = EnumSet.of(
                ChallengeMode.DAILY_RACE_1600,
                ChallengeMode.DAILY_RACE_2100
        );
        Set<ChallengeMode> dailyWeeklyEvent = EnumSet.of(
                ChallengeMode.DAILY_STANDARD,
                ChallengeMode.WEEKLY_STANDARD,
                ChallengeMode.MONTHLY_EVENT_A,
                ChallengeMode.MONTHLY_EVENT_B
        );
        Set<ChallengeMode> allCycles = EnumSet.of(
                ChallengeMode.DAILY_STANDARD,
                ChallengeMode.WEEKLY_STANDARD,
                ChallengeMode.MONTHLY_STANDARD,
                ChallengeMode.MONTHLY_EVENT_A,
                ChallengeMode.MONTHLY_EVENT_B
        );
        List<Profile> result = new ArrayList<>(List.of(
                profile("proc_resources", "Contributo Risorse", ChallengeObjectiveType.RESOURCE_CONTRIBUTION, 1, "production", "easy", "resource_grind",
                        "std_crop", weeklyMonthly, ChallengeVariantMode.GLOBAL_ONLY, 0.0D, ChallengeFocusType.NONE, List.of(),
                        new TargetBounds(220, 520, 20, 9)),
                profile("proc_vault_delivery", "Consegna al Vault", ChallengeObjectiveType.VAULT_DELIVERY, 1, "production", "easy", "resource_grind",
                        "std_crop", weeklyMonthly, ChallengeVariantMode.SPECIFIC_ONLY, 1.0D, ChallengeFocusType.MATERIAL, focusVaultDelivery(),
                        new TargetBounds(8, 24, 4, 11)),
                profile("proc_mining", "Mining", ChallengeObjectiveType.BLOCK_MINE, 2, "easy", "medium", "resource_grind",
                        "std_mine", EnumSet.of(
                                ChallengeMode.DAILY_STANDARD,
                                ChallengeMode.WEEKLY_STANDARD,
                                ChallengeMode.MONTHLY_STANDARD,
                                ChallengeMode.DAILY_RACE_1600,
                                ChallengeMode.DAILY_RACE_2100,
                                ChallengeMode.MONTHLY_EVENT_A,
                                ChallengeMode.MONTHLY_EVENT_B
                        ), ChallengeVariantMode.MIXED, 0.50D, ChallengeFocusType.MATERIAL,
                        focusMine(), new TargetBounds(24, 60, 4, 12)),
                profile("proc_mob_hunt", "Caccia ai Mob", ChallengeObjectiveType.MOB_KILL, 3, "medium", "medium", "mob_combat",
                        "std_mob", EnumSet.of(
                                ChallengeMode.DAILY_STANDARD,
                                ChallengeMode.WEEKLY_STANDARD,
                                ChallengeMode.DAILY_RACE_1600,
                                ChallengeMode.DAILY_RACE_2100,
                                ChallengeMode.MONTHLY_EVENT_A,
                                ChallengeMode.MONTHLY_EVENT_B
                        ),
                        ChallengeVariantMode.MIXED, 0.50D, ChallengeFocusType.ENTITY_TYPE, focusMob(),
                        new TargetBounds(12, 36, 4, 10)),
                profile("proc_daily_pvp", "Caccia al Rivale", ChallengeObjectiveType.MOB_KILL, 3, "combat", "hard", "mob_combat",
                        "race_pvp_legend", dailyRaceOnly,
                        ChallengeVariantMode.SPECIFIC_ONLY, 1.0D, ChallengeFocusType.ENTITY_TYPE, focusPvP(),
                        new TargetBounds(1, 1, 1, 14)),
                profile("proc_rare_mob", "Mob Rari", ChallengeObjectiveType.RARE_MOB_KILL, 3, "off_routine", "hard", "mob_combat",
                        "std_mob", weeklyMonthlyEvents, ChallengeVariantMode.SPECIFIC_ONLY, 1.0D, ChallengeFocusType.ENTITY_TYPE,
                        focusRareMob(), new TargetBounds(1, 3, 1, 5)),
                profile("proc_crop", "Raccolto", ChallengeObjectiveType.CROP_HARVEST, 4, "production", "easy", "resource_grind",
                        "std_crop", EnumSet.of(
                                ChallengeMode.DAILY_STANDARD,
                                ChallengeMode.WEEKLY_STANDARD,
                                ChallengeMode.MONTHLY_STANDARD,
                                ChallengeMode.DAILY_RACE_1600,
                                ChallengeMode.DAILY_RACE_2100,
                                ChallengeMode.MONTHLY_EVENT_A,
                                ChallengeMode.MONTHLY_EVENT_B
                        ), ChallengeVariantMode.MIXED, 0.50D, ChallengeFocusType.MATERIAL, focusCrops(),
                        new TargetBounds(16, 48, 4, 9)),
                profile("proc_fishing", "Pesca", ChallengeObjectiveType.FISH_CATCH, 14, "off_routine", "medium", "social_coop",
                        "std_crop", dailyWeeklyRace, ChallengeVariantMode.MIXED, 0.50D, ChallengeFocusType.MATERIAL, focusFish(),
                        new TargetBounds(4, 16, 2, 7)),
                profile("proc_food_production", "Produzione Cibo", ChallengeObjectiveType.FOOD_CRAFT, 14, "production", "easy", "farming",
                        "std_crop", dailyWeekly, ChallengeVariantMode.MIXED, 0.50D, ChallengeFocusType.MATERIAL, focusFood(),
                        new TargetBounds(24, 72, 8, 8)),
                profile("proc_brew_potion", "Produzione Pozioni", ChallengeObjectiveType.BREW_POTION, 14, "production", "easy", "farming",
                        "std_xp", dailyWeekly, ChallengeVariantMode.MIXED, 0.50D, ChallengeFocusType.MATERIAL, focusBrew(),
                        new TargetBounds(2, 10, 1, 7)),
                profile("proc_craft_master", "Craft Master", ChallengeObjectiveType.ITEM_CRAFT, 17, "production", "medium", "builder",
                        "std_xp", dailyWeekly, ChallengeVariantMode.MIXED, 0.50D, ChallengeFocusType.MATERIAL, focusCraftMaster(),
                        new TargetBounds(4, 24, 2, 8)),
                profile("proc_transport", "Distanza Trasporto", ChallengeObjectiveType.TRANSPORT_DISTANCE, 11, "off_routine", "medium", "sampled_passive",
                        "std_xp", allCycles, ChallengeVariantMode.GLOBAL_ONLY, 0.0D, ChallengeFocusType.NONE, List.of(),
                        new TargetBounds(40, 160, 20, 8)),
                profile("proc_playtime", "Tempo Online", ChallengeObjectiveType.PLAYTIME_MINUTES, 19, "support", "easy", "sampled_passive",
                        "std_playtime", dailyOnly, ChallengeVariantMode.GLOBAL_ONLY, 0.0D, ChallengeFocusType.NONE, List.of(),
                        new TargetBounds(10, 30, 5, 6)),
                profile("proc_xp_pickup", "Raccolta XP", ChallengeObjectiveType.XP_PICKUP, 20, "support", "easy", "resource_grind",
                        "std_xp", dailyWeekly, ChallengeVariantMode.GLOBAL_ONLY, 0.0D, ChallengeFocusType.NONE, List.of(),
                        new TargetBounds(20, 100, 10, 8)),
                profile("proc_structure_discovery", "Esplorazione", ChallengeObjectiveType.STRUCTURE_DISCOVERY, 7, "combat_exploration", "hard", "social_coop",
                        "std_xp", EnumSet.of(
                                ChallengeMode.WEEKLY_STANDARD,
                                ChallengeMode.MONTHLY_STANDARD,
                                ChallengeMode.MONTHLY_EVENT_A,
                                ChallengeMode.MONTHLY_EVENT_B
                        ), ChallengeVariantMode.GLOBAL_ONLY, 0.0D,
                        ChallengeFocusType.NONE, List.of(), new TargetBounds(1, 2, 1, 4)),
                profile("proc_construction", "Costruzione", ChallengeObjectiveType.CONSTRUCTION, 17, "support", "medium", "social_coop",
                        "std_crop", dailyWeeklyEvent, ChallengeVariantMode.MIXED, 0.50D, ChallengeFocusType.MATERIAL, focusConstruction(),
                        new TargetBounds(24, 80, 8, 7)),
                profile("proc_build_monument", "Monumento Cittadino", ChallengeObjectiveType.PLACE_BLOCK, 17, "support", "medium", "builder",
                        "std_xp", EnumSet.of(ChallengeMode.MONTHLY_STANDARD), ChallengeVariantMode.GLOBAL_ONLY, 0.0D,
                        ChallengeFocusType.NONE, List.of(), new TargetBounds(1500, 1500, 100, 4)),
                profile("proc_secret_quest", "Mandato Segreto", ChallengeObjectiveType.SECRET_QUEST, 12, "secret", "epic", "exploration",
                        "secret_epic", EnumSet.of(ChallengeMode.MONTHLY_STANDARD), ChallengeVariantMode.GLOBAL_ONLY, 0.0D,
                        ChallengeFocusType.NONE, List.of(), new TargetBounds(3, 3, 1, 1)),
                profile("proc_redstone", "Automazione Redstone", ChallengeObjectiveType.REDSTONE_AUTOMATION, 18, "support", "medium", "social_coop",
                        "std_crop", dailyWeeklyEvent, ChallengeVariantMode.MIXED, 0.45D, ChallengeFocusType.MATERIAL, focusRedstone(),
                        new TargetBounds(6, 24, 2, 6)),
                profile("proc_animals", "Interazione Animali", ChallengeObjectiveType.ANIMAL_INTERACTION, 15, "social_economy", "medium", "social_coop",
                        "std_crop", dailyWeeklyEvent, ChallengeVariantMode.MIXED, 0.50D, ChallengeFocusType.ENTITY_TYPE, focusAnimals(),
                        new TargetBounds(6, 24, 2, 6)),
                profile("proc_villager_trade", "Scambi Villager", ChallengeObjectiveType.VILLAGER_TRADE, 6, "social_economy", "medium", "social_coop",
                        "std_xp", dailyWeeklyEvent, ChallengeVariantMode.GLOBAL_ONLY, 0.0D, ChallengeFocusType.NONE, List.of(),
                        new TargetBounds(2, 12, 1, 6)),
                profile("proc_dimension", "Viaggio Dimensioni", ChallengeObjectiveType.DIMENSION_TRAVEL, 12, "combat_exploration", "hard", "social_coop",
                        "std_xp", dailyWeeklyEvent, ChallengeVariantMode.GLOBAL_ONLY, 0.0D, ChallengeFocusType.NONE, List.of(),
                        new TargetBounds(1, 4, 1, 4)),
                profile("proc_archaeology", "Archeologia", ChallengeObjectiveType.ARCHAEOLOGY_BRUSH, 8, "off_routine", "medium", "social_coop",
                        "std_xp", dailyWeeklyEvent, ChallengeVariantMode.GLOBAL_ONLY, 0.0D, ChallengeFocusType.NONE, List.of(),
                        new TargetBounds(2, 8, 1, 4)),
                profile("proc_trial_vault", "Volta di Prova", ChallengeObjectiveType.TRIAL_VAULT_OPEN, 7, "off_routine", "hard", "social_coop",
                        "std_xp", EnumSet.of(
                                ChallengeMode.WEEKLY_STANDARD,
                                ChallengeMode.MONTHLY_STANDARD
                        ),
                        ChallengeVariantMode.GLOBAL_ONLY, 0.0D, ChallengeFocusType.NONE, List.of(),
                        new TargetBounds(1, 4, 1, 5)),
                profile("proc_raid_win", "Raid Vittoriosi", ChallengeObjectiveType.RAID_WIN, 5, "combat_exploration", "hard", "combat",
                        "std_xp", EnumSet.of(ChallengeMode.WEEKLY_STANDARD),
                        ChallengeVariantMode.GLOBAL_ONLY, 0.0D, ChallengeFocusType.NONE, List.of(),
                        new TargetBounds(1, 1, 1, 4)),
                profile("proc_nether_activity", "Spedizione Nether", ChallengeObjectiveType.NETHER_ACTIVITY, 5, "combat_exploration", "medium", "combat",
                        "std_xp", weeklyMonthly, ChallengeVariantMode.GLOBAL_ONLY, 0.0D, ChallengeFocusType.NONE, List.of(),
                        new TargetBounds(2, 8, 1, 7)),
                profile("proc_ocean_operation", "Operazione Oceano", ChallengeObjectiveType.OCEAN_ACTIVITY, 7, "combat_exploration", "medium", "combat",
                        "std_xp", weeklyMonthly, ChallengeVariantMode.GLOBAL_ONLY, 0.0D, ChallengeFocusType.NONE, List.of(),
                        new TargetBounds(2, 8, 1, 7)),
                profile("proc_economy", "Economia Avanzata", ChallengeObjectiveType.ECONOMY_ADVANCED, 16, "social_economy", "medium", "sampled_passive",
                        "std_xp", weeklyMonthly, ChallengeVariantMode.GLOBAL_ONLY, 0.0D, ChallengeFocusType.NONE, List.of(),
                        new TargetBounds(5, 30, 1, 5)),
                profile("proc_boss", "Boss", ChallengeObjectiveType.BOSS_KILL, 5, "epic", "hard", "mob_combat",
                        "std_mob", EnumSet.of(ChallengeMode.MONTHLY_STANDARD),
                        ChallengeVariantMode.GLOBAL_ONLY, 0.0D, ChallengeFocusType.NONE, List.of(),
                        new TargetBounds(1, 1, 1, 2))
        ));
        result.addAll(secretProfiles());
        for (TeamPhaseQuestCatalog.Quest quest : TeamPhaseQuestCatalog.quests()) {
            int total = quest.totalTarget();
            result.add(profile(
                    quest.id(),
                    quest.displayName(),
                    ChallengeObjectiveType.TEAM_PHASE_QUEST,
                    30,
                    "epic",
                    "epic",
                    "seasonal_team",
                    "seasonal_team_phase",
                    EnumSet.of(ChallengeMode.SEASONAL_RACE),
                    ChallengeVariantMode.GLOBAL_ONLY,
                    0.0D,
                    ChallengeFocusType.NONE,
                    List.of(),
                    new TargetBounds(total, total, 1, 10)
            ));
        }
        return List.copyOf(result);
    }

    private static List<Profile> secretProfiles() {
        List<Profile> result = new ArrayList<>();
        result.add(profile("secret_black_archive", "L'Archivio Nero", ChallengeObjectiveType.ARCHAEOLOGY_BRUSH, 12, "secret", "epic", "exploration",
                "secret_relic", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_B), ChallengeVariantMode.MIXED, 0.75D, ChallengeFocusType.MATERIAL, List.of(
                                new ChallengeFocusOption("SUSPICIOUS_GRAVEL", "Ghiaia Sospetta", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("SUSPICIOUS_SAND", "Sabbia Sospetta", ChallengeFocusRarity.RARE)
                        ),
                new TargetBounds(192, 320, 32, 1)));

        result.add(profile("secret_first_trial_vaults", "Le Volte della Prima Prova", ChallengeObjectiveType.TRIAL_VAULT_OPEN, 12, "combat_exploration", "epic", "exploration",
                "secret_epic", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_A), ChallengeVariantMode.GLOBAL_ONLY, 0.00D, ChallengeFocusType.NONE, List.of(),
                new TargetBounds(18, 30, 3, 1)));

        result.add(profile("secret_long_ash_caravan", "La Lunga Carovana di Cenere", ChallengeObjectiveType.TRANSPORT_DISTANCE, 12, "off_routine", "epic", "seasonal_team",
                "secret_hunt", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_B), ChallengeVariantMode.GLOBAL_ONLY, 0.00D, ChallengeFocusType.NONE, List.of(),
                new TargetBounds(180000, 300000, 30000, 2)));

        result.add(profile("secret_war_of_broken_banners", "Guerra degli Stendardi Spezzati", ChallengeObjectiveType.RAID_WIN, 12, "combat_exploration", "epic", "combat",
                "secret_hunt", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_A), ChallengeVariantMode.GLOBAL_ONLY, 0.00D, ChallengeFocusType.NONE, List.of(),
                new TargetBounds(8, 14, 2, 1)));

        result.add(profile("secret_salt_crown", "La Corona di Sale", ChallengeObjectiveType.OCEAN_ACTIVITY, 12, "combat_exploration", "epic", "exploration",
                "secret_void", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_B), ChallengeVariantMode.GLOBAL_ONLY, 0.00D, ChallengeFocusType.NONE, List.of(),
                new TargetBounds(144, 240, 24, 2)));

        result.add(profile("secret_below_the_red_sky", "Sotto il Cielo Rosso", ChallengeObjectiveType.NETHER_ACTIVITY, 12, "combat_exploration", "epic", "exploration",
                "secret_void", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_A), ChallengeVariantMode.MIXED, 0.65D, ChallengeFocusType.MATERIAL, List.of(
                                new ChallengeFocusOption("ANCIENT_DEBRIS", "Detriti Antichi", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("BLAZE_ROD", "Verga di Blaze", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("MAGMA_CREAM", "Crema di Magma", ChallengeFocusRarity.UNCOMMON)
                        ),
                new TargetBounds(300, 540, 30, 2)));

        result.add(profile("secret_council_of_embers", "Consiglio delle Braci", ChallengeObjectiveType.RESOURCE_CONTRIBUTION, 12, "social_economy", "epic", "social_coop",
                "secret_council", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_B), ChallengeVariantMode.SPECIFIC_ONLY, 1.00D, ChallengeFocusType.MATERIAL, List.of(
                                new ChallengeFocusOption("DIAMOND", "Diamante", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("EMERALD", "Smeraldo", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("ANCIENT_DEBRIS", "Detriti Antichi", ChallengeFocusRarity.RARE)
                        ),
                new TargetBounds(64000, 120000, 8000, 1)));

        result.add(profile("secret_ledger_of_the_silent_market", "Registro del Mercato Silente", ChallengeObjectiveType.ECONOMY_ADVANCED, 12, "social_economy", "epic", "social_economy",
                "secret_council", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_A), ChallengeVariantMode.GLOBAL_ONLY, 0.00D, ChallengeFocusType.NONE, List.of(),
                new TargetBounds(240000, 400000, 40000, 2)));

        result.add(profile("secret_brokers_of_the_fifth_bell", "Mediatori della Quinta Campana", ChallengeObjectiveType.VILLAGER_TRADE, 12, "social_economy", "hard", "social_economy",
                "secret_council", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_A), ChallengeVariantMode.GLOBAL_ONLY, 0.00D, ChallengeFocusType.NONE, List.of(),
                new TargetBounds(768, 1280, 128, 3)));

        result.add(profile("secret_last_red_circuit", "L'Ultimo Circuito Rosso", ChallengeObjectiveType.REDSTONE_AUTOMATION, 12, "off_routine", "epic", "builder",
                "secret_epic", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_B), ChallengeVariantMode.MIXED, 0.70D, ChallengeFocusType.MATERIAL, List.of(
                                new ChallengeFocusOption("OBSERVER", "Osservatore", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("COMPARATOR", "Comparatore", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("REPEATER", "Ripetitore", ChallengeFocusRarity.UNCOMMON),
                                new ChallengeFocusOption("REDSTONE_BLOCK", "Blocco di Redstone", ChallengeFocusRarity.UNCOMMON)
                        ),
                new TargetBounds(36, 72, 6, 2)));

        result.add(profile("secret_crimson_phials", "Fiale Cremisi", ChallengeObjectiveType.BREW_POTION, 12, "support", "epic", "support",
                "secret_void", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_B), ChallengeVariantMode.MIXED, 0.70D, ChallengeFocusType.MATERIAL, List.of(
                                new ChallengeFocusOption("NETHER_WART", "Verruca del Nether", ChallengeFocusRarity.UNCOMMON),
                                new ChallengeFocusOption("GHAST_TEAR", "Lacrima di Ghast", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("BLAZE_POWDER", "Polvere di Blaze", ChallengeFocusRarity.UNCOMMON),
                                new ChallengeFocusOption("FERMENTED_SPIDER_EYE", "Occhio di Ragno Fermentato", ChallengeFocusRarity.UNCOMMON)
                        ),
                new TargetBounds(320, 512, 64, 2)));

        result.add(profile("secret_forge_of_relics", "Forgia delle Reliquie", ChallengeObjectiveType.ITEM_CRAFT, 12, "epic", "epic", "builder",
                "secret_relic", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_A), ChallengeVariantMode.MIXED, 0.80D, ChallengeFocusType.MATERIAL, List.of(
                                new ChallengeFocusOption("END_CRYSTAL", "Cristallo dell'End", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("BEACON", "Faro", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("LODESTONE", "Magnetite", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("RESPAWN_ANCHOR", "Ancora della Rinascita", ChallengeFocusRarity.RARE)
                        ),
                new TargetBounds(512, 896, 64, 2)));

        result.add(profile("secret_banquet_of_the_eclipse", "Banchetto dell'Eclisse", ChallengeObjectiveType.FOOD_CRAFT, 12, "support", "hard", "seasonal_team",
                "secret_epic", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_B), ChallengeVariantMode.MIXED, 0.65D, ChallengeFocusType.MATERIAL, List.of(
                                new ChallengeFocusOption("GOLDEN_CARROT", "Carota Dorata", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("PUMPKIN_PIE", "Torta di Zucca", ChallengeFocusRarity.UNCOMMON),
                                new ChallengeFocusOption("RABBIT_STEW", "Stufato di Coniglio", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("SUSPICIOUS_STEW", "Stufato Sospetto", ChallengeFocusRarity.RARE)
                        ),
                new TargetBounds(896, 1536, 128, 3)));

        result.add(profile("secret_gatewalkers_oath", "Giuramento dei Varcamondi", ChallengeObjectiveType.DIMENSION_TRAVEL, 12, "combat_exploration", "epic", "exploration",
                "secret_void", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_A, ChallengeMode.MONTHLY_EVENT_B), ChallengeVariantMode.GLOBAL_ONLY, 0.00D, ChallengeFocusType.NONE, List.of(),
                new TargetBounds(96, 180, 12, 2)));

        result.add(profile("secret_maps_of_the_buried_world", "Mappe del Mondo Sepolto", ChallengeObjectiveType.STRUCTURE_DISCOVERY, 12, "secret", "epic", "exploration",
                "secret_relic", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_B), ChallengeVariantMode.GLOBAL_ONLY, 0.00D, ChallengeFocusType.NONE, List.of(),
                new TargetBounds(40, 60, 5, 1)));

        result.add(profile("secret_citadel_of_blackstone", "Cittadella di Pietranera", ChallengeObjectiveType.PLACE_BLOCK, 12, "epic", "epic", "builder",
                "secret_epic", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_A), ChallengeVariantMode.MIXED, 0.75D, ChallengeFocusType.MATERIAL, List.of(
                                new ChallengeFocusOption("OBSIDIAN", "Ossidiana", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("BLACKSTONE", "Pietranera", ChallengeFocusRarity.UNCOMMON),
                                new ChallengeFocusOption("POLISHED_DEEPSLATE", "Ardesia Profonda Levigata", ChallengeFocusRarity.UNCOMMON),
                                new ChallengeFocusOption("COPPER_BLOCK", "Blocco di Rame", ChallengeFocusRarity.UNCOMMON)
                        ),
                new TargetBounds(36000, 60000, 6000, 2)));

        result.add(profile("secret_ash_vein_covenant", "Patto della Vena di Cenere", ChallengeObjectiveType.BLOCK_MINE, 12, "secret", "epic", "resource_grind",
                "secret_relic", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_A), ChallengeVariantMode.MIXED, 0.80D, ChallengeFocusType.MATERIAL, List.of(
                                new ChallengeFocusOption("ANCIENT_DEBRIS", "Detriti Antichi", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("DIAMOND_ORE", "Minerale di Diamante", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("DEEPSLATE_DIAMOND_ORE", "Minerale di Diamante in Ardesia Profonda", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("EMERALD_ORE", "Minerale di Smeraldo", ChallengeFocusRarity.RARE)
                        ),
                new TargetBounds(1536, 3072, 256, 1)));

        result.add(profile("secret_hollow_kings", "I Re Cavi", ChallengeObjectiveType.BOSS_KILL, 12, "epic", "epic", "combat",
                "secret_void", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_A, ChallengeMode.MONTHLY_EVENT_B), ChallengeVariantMode.SPECIFIC_ONLY, 1.00D, ChallengeFocusType.ENTITY_TYPE, List.of(
                                new ChallengeFocusOption("WITHER", "Wither", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("ENDER_DRAGON", "Drago dell'End", ChallengeFocusRarity.RARE)
                        ),
                new TargetBounds(4, 6, 1, 1)));

        result.add(profile("secret_hunters_of_the_omen_court", "Cacciatori della Corte del Presagio", ChallengeObjectiveType.RARE_MOB_KILL, 12, "combat_exploration", "epic", "mob_combat",
                "secret_hunt", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_A), ChallengeVariantMode.SPECIFIC_ONLY, 1.00D, ChallengeFocusType.ENTITY_TYPE, List.of(
                                new ChallengeFocusOption("EVOKER", "Evocatore", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("RAVAGER", "Devastatore", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("PIGLIN_BRUTE", "Piglin Bruto", ChallengeFocusRarity.RARE),
                                new ChallengeFocusOption("ELDER_GUARDIAN", "Guardiano Anziano", ChallengeFocusRarity.RARE)
                        ),
                new TargetBounds(36, 60, 6, 2)));

        result.add(profile("secret_hidden_mandate", "Il Mandato Nascosto", ChallengeObjectiveType.SECRET_QUEST, 12, "secret", "epic", "seasonal_team",
                "secret_epic", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_B), ChallengeVariantMode.GLOBAL_ONLY, 0.00D, ChallengeFocusType.NONE, List.of(),
                new TargetBounds(3, 3, 1, 1)));
        result.add(profile("secret_gate_of_the_undertow", "Porta della Risacca", ChallengeObjectiveType.SECRET_QUEST, 12, "secret", "epic", "exploration",
                "secret_void", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_A, ChallengeMode.MONTHLY_EVENT_B), ChallengeVariantMode.GLOBAL_ONLY, 0.00D, ChallengeFocusType.NONE, List.of(),
                new TargetBounds(3, 3, 1, 1)));
        result.add(profile("secret_court_of_ashes", "Corte delle Ceneri", ChallengeObjectiveType.SECRET_QUEST, 12, "secret", "epic", "exploration",
                "secret_relic", EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_A, ChallengeMode.MONTHLY_EVENT_B), ChallengeVariantMode.GLOBAL_ONLY, 0.00D, ChallengeFocusType.NONE, List.of(),
                new TargetBounds(3, 3, 1, 1)));
        result.addAll(scriptedSecretProfiles());
        return List.copyOf(result);
    }

    private static List<Profile> scriptedSecretProfiles() {
        return List.of(
                scriptedSecretProfile("secret_bell_of_deepwater", "Campana degli Abissi", "secret_void", "exploration"),
                scriptedSecretProfile("secret_embers_of_the_seal", "Braci del Sigillo", "secret_relic", "exploration"),
                scriptedSecretProfile("secret_road_of_tithe", "Strada della Decima", "secret_council", "social_economy"),
                scriptedSecretProfile("secret_threefold_sigil", "Sigillo Triplice", "secret_epic", "seasonal_team"),
                scriptedSecretProfile("secret_sunken_court", "Corte Sommersa", "secret_void", "exploration"),
                scriptedSecretProfile("secret_furnace_of_echoes", "Fornace degli Echi", "secret_relic", "builder"),
                scriptedSecretProfile("secret_hush_of_monoliths", "Silenzio dei Monoliti", "secret_relic", "exploration"),
                scriptedSecretProfile("secret_watchers_tithe", "Decima dei Guardiani", "secret_hunt", "mob_combat"),
                scriptedSecretProfile("secret_lanterns_of_the_fault", "Lanterne della Faglia", "secret_epic", "exploration"),
                scriptedSecretProfile("secret_red_bastion_pact", "Patto del Bastione Rosso", "secret_void", "exploration"),
                scriptedSecretProfile("secret_archive_of_salt", "Archivio del Sale", "secret_relic", "exploration"),
                scriptedSecretProfile("secret_thorn_market", "Mercato delle Spine", "secret_council", "social_economy"),
                scriptedSecretProfile("secret_black_current", "Corrente Nera", "secret_void", "exploration"),
                scriptedSecretProfile("secret_stone_oath", "Giuramento di Pietra", "secret_relic", "builder"),
                scriptedSecretProfile("secret_hall_of_veins", "Sala delle Vene", "secret_relic", "resource_grind"),
                scriptedSecretProfile("secret_ashen_compass", "Bussola di Cenere", "secret_hunt", "exploration"),
                scriptedSecretProfile("secret_cinder_route", "Rotta di Cenere", "secret_void", "seasonal_team"),
                scriptedSecretProfile("secret_shrouded_ledger", "Registro Velato", "secret_council", "social_economy"),
                scriptedSecretProfile("secret_deep_court", "Corte Profonda", "secret_void", "exploration"),
                scriptedSecretProfile("secret_last_toll", "Ultimo Rintocco", "secret_epic", "seasonal_team"),
                scriptedSecretProfile("secret_harrowed_passage", "Passaggio Segnato", "secret_hunt", "exploration"),
                scriptedSecretProfile("secret_iron_vigil", "Veglia di Ferro", "secret_relic", "resource_grind"),
                scriptedSecretProfile("secret_drowned_circuit", "Circuito Sommerso", "secret_void", "support"),
                scriptedSecretProfile("secret_sealed_wayfinder", "Wayfinder Sigillato", "secret_epic", "exploration"),
                scriptedSecretProfile("secret_low_flame_accord", "Accordo di Brace Bassa", "secret_council", "social_economy"),
                scriptedSecretProfile("secret_void_meridian", "Meridiano del Vuoto", "secret_void", "exploration"),
                scriptedSecretProfile("secret_worn_throne", "Trono Consunto", "secret_hunt", "combat"),
                scriptedSecretProfile("secret_tide_bell", "Campana di Marea", "secret_void", "exploration"),
                scriptedSecretProfile("secret_fallow_sigil", "Sigillo di Maggese", "secret_epic", "seasonal_team"),
                scriptedSecretProfile("secret_buried_concord", "Concordato Sepolto", "secret_relic", "exploration")
        );
    }

    private static Profile scriptedSecretProfile(
            String id,
            String name,
            String rewardBundleId,
            String objectiveFamily
    ) {
        return profile(
                id,
                name,
                ChallengeObjectiveType.SECRET_QUEST,
                12,
                "secret",
                "epic",
                objectiveFamily,
                rewardBundleId,
                EnumSet.of(ChallengeMode.MONTHLY_STANDARD, ChallengeMode.MONTHLY_EVENT_A, ChallengeMode.MONTHLY_EVENT_B),
                ChallengeVariantMode.GLOBAL_ONLY,
                0.00D,
                ChallengeFocusType.NONE,
                List.of(),
                new TargetBounds(3, 3, 1, 1)
        );
    }

    private static Profile profile(
            String id,
            String name,
            ChallengeObjectiveType objective,
            int category,
            String slot,
            String difficulty,
            String family,
            String rewardBundleId,
            Set<ChallengeMode> cycles,
            ChallengeVariantMode variantMode,
            double specificChance,
            ChallengeFocusType focusType,
            List<ChallengeFocusOption> focusPool,
            TargetBounds bounds
    ) {
        return new Profile(
                id,
                name,
                objective,
                category,
                slot,
                difficulty,
                family,
                rewardBundleId,
                EnumSet.copyOf(cycles),
                variantMode,
                specificChance,
                focusType,
                List.copyOf(focusPool),
                bounds
        );
    }

    private static List<ChallengeFocusOption> focusMine() {
        return List.of(
                new ChallengeFocusOption("COAL_ORE", "minerale di carbone", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("IRON_ORE", "minerale di ferro", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("COPPER_ORE", "minerale di rame", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("REDSTONE_ORE", "minerale di redstone", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("LAPIS_ORE", "minerale di lapislazzuli", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("GOLD_ORE", "minerale d'oro", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("DIAMOND_ORE", "minerale di diamante", ChallengeFocusRarity.RARE)
        );
    }

    private static List<ChallengeFocusOption> focusMob() {
        return List.of(
                new ChallengeFocusOption("ZOMBIE", "zombie", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("SKELETON", "scheletri", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("SPIDER", "ragni", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("CREEPER", "creeper", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("ENDERMAN", "enderman", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("BLAZE", "blaze", ChallengeFocusRarity.UNCOMMON)
        );
    }

    private static List<ChallengeFocusOption> focusRareMob() {
        return List.of(
                new ChallengeFocusOption("BREEZE", "breeze", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("PIGLIN_BRUTE", "piglin brute", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("SHULKER", "shulker", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("ELDER_GUARDIAN", "elder guardian", ChallengeFocusRarity.RARE),
                new ChallengeFocusOption("WARDEN", "warden", ChallengeFocusRarity.RARE)
        );
    }

    private static List<ChallengeFocusOption> focusPvP() {
        return List.of(
                new ChallengeFocusOption("PLAYER", "player", ChallengeFocusRarity.RARE)
        );
    }

    private static List<ChallengeFocusOption> focusCrops() {
        return List.of(
                new ChallengeFocusOption("WHEAT", "grano", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("CARROTS", "carote", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("POTATOES", "patate", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("BEETROOTS", "barbabietole", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("NETHER_WART", "verruche del nether", ChallengeFocusRarity.UNCOMMON)
        );
    }

    private static List<ChallengeFocusOption> focusVaultDelivery() {
        return List.of(
                new ChallengeFocusOption("COAL", "carbone", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("REDSTONE", "redstone", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("WHEAT", "grano", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("CARROT", "carote", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("POTATO", "patate", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("BREAD", "pane", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("IRON_INGOT", "lingotti di ferro", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("COPPER_INGOT", "lingotti di rame", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("QUARTZ", "quarzo", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("GOLD_INGOT", "lingotti d'oro", ChallengeFocusRarity.RARE)
        );
    }

    private static List<ChallengeFocusOption> focusFish() {
        return List.of(
                new ChallengeFocusOption("COD", "merluzzi", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("SALMON", "salmoni", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("PUFFERFISH", "pesci palla", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("TROPICAL_FISH", "pesci tropicali", ChallengeFocusRarity.UNCOMMON)
        );
    }

    private static List<ChallengeFocusOption> focusFood() {
        return List.of(
                new ChallengeFocusOption("COOKED_BEEF", "bistecche cotte", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("BREAD", "pagnotte di pane", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("BAKED_POTATO", "patate al forno", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("COOKED_CHICKEN", "pollo cotto", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("COOKED_MUTTON", "montone cotto", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("GOLDEN_CARROT", "carote dorate", ChallengeFocusRarity.UNCOMMON)
        );
    }

    private static List<ChallengeFocusOption> focusBrew() {
        return List.of(
                new ChallengeFocusOption("POTION_TYPE_HEALING", "pozioni di cura", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("POTION_TYPE_STRENGTH", "pozioni di forza", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("POTION_TYPE_SWIFTNESS", "pozioni di velocita", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("POTION_TYPE_REGENERATION", "pozioni di rigenerazione", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("POTION_SPLASH", "pozioni splash", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("POTION_LEVEL_II", "pozioni livello II", ChallengeFocusRarity.RARE)
        );
    }

    private static List<ChallengeFocusOption> focusCraftMaster() {
        return List.of(
                new ChallengeFocusOption("PISTON", "pistoni", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("STICKY_PISTON", "pistoni appiccicosi", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("REPEATER", "repeater", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("COMPARATOR", "comparator", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("DIAMOND_CHESTPLATE", "corazze di diamante", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("BEACON", "beacon", ChallengeFocusRarity.RARE)
        );
    }

    private static List<ChallengeFocusOption> focusConstruction() {
        return List.of(
                new ChallengeFocusOption("STONE_BRICKS", "mattoni di pietra", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("BRICKS", "mattoni", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("OAK_PLANKS", "assi di quercia", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("DEEPSLATE_BRICKS", "mattoni di deepslate", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("QUARTZ_BLOCK", "blocchi di quarzo", ChallengeFocusRarity.UNCOMMON)
        );
    }

    private static List<ChallengeFocusOption> focusRedstone() {
        return List.of(
                new ChallengeFocusOption("REDSTONE", "redstone", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("REDSTONE_TORCH", "torce di redstone", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("REPEATER", "repeater", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("COMPARATOR", "comparator", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("OBSERVER", "observer", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("HOPPER", "hopper", ChallengeFocusRarity.UNCOMMON)
        );
    }

    private static List<ChallengeFocusOption> focusAnimals() {
        return List.of(
                new ChallengeFocusOption("COW", "mucche", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("SHEEP", "pecore", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("PIG", "maiali", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("CHICKEN", "galline", ChallengeFocusRarity.COMMON),
                new ChallengeFocusOption("HORSE", "cavalli", ChallengeFocusRarity.UNCOMMON),
                new ChallengeFocusOption("WOLF", "lupi", ChallengeFocusRarity.UNCOMMON)
        );
    }
}
