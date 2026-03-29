package it.patric.cittaexp.challenges;

import it.patric.cittaexp.levels.PrincipalStage;
import it.patric.cittaexp.utils.YamlResourceBackfill;
import java.io.File;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class CityChallengeSettings {

    private final boolean enabled;
    private final ZoneId timezone;
    private final int schedulerTickSeconds;
    private final int playtimeTickMinutes;
    private final int dailyStandardCount;
    private final int weeklyStandardCount;
    private final int monthlyStandardCount;
    private final int seasonalRaceCount;
    private final int seasonalActICount;
    private final int seasonalActIICount;
    private final int seasonalActIIICount;
    private final int seasonalEliteCount;
    private final int seasonalHiddenRelicCount;
    private final int seasonalSecretHardCap;
    private final int eventsQuestCount;
    private final int monthlyEventCount;
    private final List<LocalTime> raceWindows;
    private final LocalTime weeklyRollover;
    private final LocalTime monthlyRollover;
    private final LocalTime seasonalRollover;
    private final AntiAbuseSettings antiAbuse;
    private final TemplateFormatterSettings templateFormatter;
    private final DifficultyResolverSettings difficultyResolver;
    private final SpecificTargetScalingSettings specificTargetScaling;
    private final TargetScalingSettings targetScaling;
    private final XpRangesSettings xpRanges;
    private final FairnessSettings fairness;
    private final DualThresholdSettings dualThreshold;
    private final PersonalRewardSettings personalRewards;
    private final StreakSettings streak;
    private final MonthlyEventSettings monthlyEvents;
    private final EventRewardsSettings eventRewards;
    private final EventRewardsSettings raceRewards;
    private final CurationSettings curation;
    private final BoardGeneratorSettings boardGenerator;
    private final GovernanceSettings governance;
    private final M6Settings m6;
    private final MilestoneSettings milestones;
    private final Map<ChallengeObjectiveType, TaxonomyPolicySettings> taxonomyPolicies;
    private final Map<String, ChallengeDefinition> definitions;
    private final Map<String, ChallengeRewardBundle> rewardBundles;

    private CityChallengeSettings(
            boolean enabled,
            ZoneId timezone,
            int schedulerTickSeconds,
            int playtimeTickMinutes,
            int dailyStandardCount,
            int weeklyStandardCount,
            int monthlyStandardCount,
            int seasonalRaceCount,
            int seasonalActICount,
            int seasonalActIICount,
            int seasonalActIIICount,
            int seasonalEliteCount,
            int seasonalHiddenRelicCount,
            int seasonalSecretHardCap,
            int eventsQuestCount,
            int monthlyEventCount,
            List<LocalTime> raceWindows,
            LocalTime weeklyRollover,
            LocalTime monthlyRollover,
            LocalTime seasonalRollover,
            AntiAbuseSettings antiAbuse,
            TemplateFormatterSettings templateFormatter,
            DifficultyResolverSettings difficultyResolver,
            SpecificTargetScalingSettings specificTargetScaling,
            TargetScalingSettings targetScaling,
            XpRangesSettings xpRanges,
            FairnessSettings fairness,
            DualThresholdSettings dualThreshold,
            PersonalRewardSettings personalRewards,
            StreakSettings streak,
            MonthlyEventSettings monthlyEvents,
            EventRewardsSettings eventRewards,
            EventRewardsSettings raceRewards,
            CurationSettings curation,
            BoardGeneratorSettings boardGenerator,
            GovernanceSettings governance,
            M6Settings m6,
            MilestoneSettings milestones,
            Map<ChallengeObjectiveType, TaxonomyPolicySettings> taxonomyPolicies,
            Map<String, ChallengeDefinition> definitions,
            Map<String, ChallengeRewardBundle> rewardBundles
    ) {
        this.enabled = enabled;
        this.timezone = timezone;
        this.schedulerTickSeconds = schedulerTickSeconds;
        this.playtimeTickMinutes = playtimeTickMinutes;
        this.dailyStandardCount = dailyStandardCount;
        this.weeklyStandardCount = weeklyStandardCount;
        this.monthlyStandardCount = monthlyStandardCount;
        this.seasonalRaceCount = seasonalRaceCount;
        this.seasonalActICount = seasonalActICount;
        this.seasonalActIICount = seasonalActIICount;
        this.seasonalActIIICount = seasonalActIIICount;
        this.seasonalEliteCount = seasonalEliteCount;
        this.seasonalHiddenRelicCount = seasonalHiddenRelicCount;
        this.seasonalSecretHardCap = seasonalSecretHardCap;
        this.eventsQuestCount = eventsQuestCount;
        this.monthlyEventCount = monthlyEventCount;
        this.raceWindows = raceWindows;
        this.weeklyRollover = weeklyRollover;
        this.monthlyRollover = monthlyRollover;
        this.seasonalRollover = seasonalRollover;
        this.antiAbuse = antiAbuse;
        this.templateFormatter = templateFormatter;
        this.difficultyResolver = difficultyResolver;
        this.specificTargetScaling = specificTargetScaling;
        this.targetScaling = targetScaling;
        this.xpRanges = xpRanges;
        this.fairness = fairness;
        this.dualThreshold = dualThreshold;
        this.personalRewards = personalRewards;
        this.streak = streak;
        this.monthlyEvents = monthlyEvents;
        this.eventRewards = eventRewards;
        this.raceRewards = raceRewards;
        this.curation = curation;
        this.boardGenerator = boardGenerator;
        this.governance = governance;
        this.m6 = m6 == null ? M6Settings.defaults() : m6;
        this.milestones = milestones;
        this.taxonomyPolicies = taxonomyPolicies == null ? Map.of() : Map.copyOf(taxonomyPolicies);
        this.definitions = definitions;
        this.rewardBundles = rewardBundles;
    }

    public static CityChallengeSettings load(Plugin plugin) {
        YamlResourceBackfill.ensure(plugin, "challenges.yml");
        YamlResourceBackfill.ensure(plugin, "challenge-rewards.yml");

        File challengesFile = new File(plugin.getDataFolder(), "challenges.yml");
        File rewardsFile = new File(plugin.getDataFolder(), "challenge-rewards.yml");

        YamlConfiguration challenges = YamlConfiguration.loadConfiguration(challengesFile);
        YamlConfiguration rewards = YamlConfiguration.loadConfiguration(rewardsFile);

        boolean enabled = challenges.getBoolean("enabled", true);
        ZoneId timezone = parseZone(challenges.getString("timezone", "Europe/Rome"));
        int schedulerTickSeconds = Math.max(5, challenges.getInt("scheduler.tickSeconds", 20));
        int playtimeTickMinutes = Math.max(1, challenges.getInt("playtime.tickMinutes", 1));
        int dailyStandardCount = Math.max(1, challenges.getInt("daily.standardCount", 8));
        int weeklyStandardCount = Math.max(1, challenges.getInt("weekly.standardCount", 18));
        int monthlyStandardCount = Math.max(1, challenges.getInt("monthly.standardCount", 18));
        int seasonalActICount = Math.max(0, challenges.getInt("seasonal.acts.actI", 6));
        int seasonalActIICount = Math.max(0, challenges.getInt("seasonal.acts.actII", 6));
        int seasonalActIIICount = Math.max(0, challenges.getInt("seasonal.acts.actIII", 5));
        int seasonalEliteCount = Math.max(0, challenges.getInt("seasonal.acts.elite", 0));
        int seasonalHiddenRelicCount = Math.max(0, challenges.getInt("seasonal.acts.hiddenRelic", 3));
        int seasonalSecretHardCap = Math.max(
                seasonalHiddenRelicCount,
                challenges.getInt("seasonal.secretHardCap", 8)
        );
        int seasonalRaceCount = Math.max(
                1,
                seasonalActICount + seasonalActIICount + seasonalActIIICount + seasonalEliteCount + seasonalHiddenRelicCount
        );
        int eventsQuestCount = Math.max(1, challenges.getInt("events.questCount", 26));
        int monthlyEventCount = Math.max(1, challenges.getInt("monthlyEvents.countPerWindow", 1));

        List<LocalTime> raceWindows = new ArrayList<>();
        for (String raw : challenges.getStringList("daily.raceWindows")) {
            LocalTime parsed = parseLocalTime(raw);
            if (parsed != null) {
                raceWindows.add(parsed);
            }
        }
        raceWindows.sort(LocalTime::compareTo);
        if (raceWindows.size() < 2) {
            raceWindows = List.of(LocalTime.of(18, 30), LocalTime.of(21, 30));
        } else if (raceWindows.size() > 2) {
            raceWindows = List.copyOf(raceWindows.subList(0, 2));
        }

        LocalTime weeklyRollover = parseLocalTime(challenges.getString("weekly.rolloverAt", "00:10"));
        if (weeklyRollover == null) {
            weeklyRollover = LocalTime.of(0, 10);
        }
        LocalTime monthlyRollover = parseLocalTime(challenges.getString("monthly.rolloverAt", "00:15"));
        if (monthlyRollover == null) {
            monthlyRollover = LocalTime.of(0, 15);
        }
        LocalTime seasonalRollover = parseLocalTime(challenges.getString("seasonal.rolloverAt", "00:20"));
        if (seasonalRollover == null) {
            seasonalRollover = LocalTime.of(0, 20);
        }

        AntiAbuseSettings antiAbuse = parseAntiAbuse(challenges.getConfigurationSection("antiAbuse"));
        TemplateFormatterSettings templateFormatter = parseTemplateFormatter(challenges.getConfigurationSection("templateFormatter"));
        DifficultyResolverSettings difficultyResolver = parseDifficultyResolver(challenges.getConfigurationSection("difficultyResolver"));
        SpecificTargetScalingSettings specificTargetScaling = parseSpecificTargetScaling(
                challenges.getConfigurationSection("specificVariants.targetScaling")
        );
        TargetScalingSettings targetScaling = parseTargetScaling(challenges.getConfigurationSection("scaling"));
        XpRangesSettings xpRanges = parseXpRanges(challenges.getConfigurationSection("xpRangesByCycle"));
        FairnessSettings fairness = parseFairness(challenges.getConfigurationSection("fairness"));
        DualThresholdSettings dualThreshold = parseDualThreshold(challenges.getConfigurationSection("dualThreshold"));
        PersonalRewardSettings personalRewards = parsePersonalRewards(challenges.getConfigurationSection("personalRewards"));
        Map<String, ChallengeRewardBundle> bundles = parseRewardBundles(plugin, rewards.getConfigurationSection("bundles"));
        Map<String, ChallengeDefinition> defs = parseDefinitions(
                plugin,
                challenges.getConfigurationSection("definitions"),
                bundles
        );
        StreakSettings streak = parseStreak(challenges.getConfigurationSection("streak"));
        MonthlyEventSettings monthlyEvents = parseMonthlyEvents(challenges.getConfigurationSection("monthlyEvents"));
        EventRewardsSettings eventRewards = parseEventRewards(plugin, challenges.getConfigurationSection("eventRewards"));
        EventRewardsSettings raceRewards = parseRaceRewards(plugin, challenges.getConfigurationSection("raceRewards"));
        CurationSettings curation = parseCuration(challenges.getConfigurationSection("curation"));
        BoardGeneratorSettings boardGenerator = parseBoardGenerator(challenges.getConfigurationSection("boardGenerator"));
        GovernanceSettings governance = parseGovernance(challenges.getConfigurationSection("governance"));
        M6Settings m6 = parseM6(plugin, challenges.getConfigurationSection("m6"));
        MilestoneSettings milestones = parseMilestones(plugin, challenges.getConfigurationSection("milestones"));
        Map<ChallengeObjectiveType, TaxonomyPolicySettings> taxonomyPolicies = parseTaxonomyPolicies(
                plugin,
                challenges.getConfigurationSection("taxonomyPolicies")
        );

        return new CityChallengeSettings(
                enabled,
                timezone,
                schedulerTickSeconds,
                playtimeTickMinutes,
                dailyStandardCount,
                weeklyStandardCount,
                monthlyStandardCount,
                seasonalRaceCount,
                seasonalActICount,
                seasonalActIICount,
                seasonalActIIICount,
                seasonalEliteCount,
                seasonalHiddenRelicCount,
                seasonalSecretHardCap,
                eventsQuestCount,
                monthlyEventCount,
                List.copyOf(raceWindows),
                weeklyRollover,
                monthlyRollover,
                seasonalRollover,
                antiAbuse,
                templateFormatter,
                difficultyResolver,
                specificTargetScaling,
                targetScaling,
                xpRanges,
                fairness,
                dualThreshold,
                personalRewards,
                streak,
                monthlyEvents,
                eventRewards,
                raceRewards,
                curation,
                boardGenerator,
                governance,
                m6,
                milestones,
                taxonomyPolicies,
                defs,
                bundles
        );
    }

    private static FairnessSettings parseFairness(ConfigurationSection section) {
        if (section == null) {
            return FairnessSettings.defaults();
        }
        int windowDays = Math.max(1, section.getInt("activeContributorsWindowDays", 7));
        ConfigurationSection bracketsSection = section.getConfigurationSection("brackets");
        List<FairnessBracket> brackets = new ArrayList<>();
        if (bracketsSection != null) {
            for (String key : bracketsSection.getKeys(false)) {
                ConfigurationSection row = bracketsSection.getConfigurationSection(key);
                if (row == null) {
                    continue;
                }
                int min = Math.max(1, row.getInt("min", 1));
                int max = Math.max(min, row.getInt("max", min));
                double multiplier = sanitizeMultiplier(row.getDouble("multiplier", 1.0D), 1.0D);
                brackets.add(new FairnessBracket(min, max, multiplier));
            }
        }
        if (brackets.isEmpty()) {
            brackets = FairnessSettings.defaultBrackets();
        }
        brackets = brackets.stream()
                .sorted(java.util.Comparator.comparingInt(FairnessBracket::minContributors))
                .toList();
        return new FairnessSettings(windowDays, List.copyOf(brackets));
    }

    private static DualThresholdSettings parseDualThreshold(ConfigurationSection section) {
        if (section == null) {
            return DualThresholdSettings.defaults();
        }
        double excellenceMultiplier = Math.max(1.0D, section.getDouble("excellenceMultiplier", 1.40D));
        return new DualThresholdSettings(excellenceMultiplier);
    }

    private static PersonalRewardSettings parsePersonalRewards(ConfigurationSection section) {
        if (section == null) {
            return PersonalRewardSettings.defaults();
        }
        double minRatio = section.getDouble("minContributionRatio", 0.05D);
        if (Double.isNaN(minRatio) || minRatio < 0.0D) {
            minRatio = 0.05D;
        }
        int absolute = Math.max(0, section.getInt("thresholdContributionAbsolute", 0));
        return new PersonalRewardSettings(minRatio, absolute);
    }

    private static CurationSettings parseCuration(ConfigurationSection section) {
        if (section == null) {
            return CurationSettings.defaults();
        }
        int defWindow = Math.max(0, section.getInt("cooldown.definitionCycles", 2));
        int categoryWindow = Math.max(0, section.getInt("cooldown.categoryCycles", 2));
        int focusWindow = Math.max(0, section.getInt("cooldown.focusCycles", 2));
        int familyWindow = Math.max(0, section.getInt("cooldown.familyCycles", 2));
        BundleConstraints daily = parseBundleConstraints(section.getConfigurationSection("bundle.daily"),
                BundleConstraints.defaultsDaily());
        BundleConstraints race = parseBundleConstraints(section.getConfigurationSection("bundle.race"),
                BundleConstraints.defaultsRace());
        BundleConstraints weekly = parseBundleConstraints(section.getConfigurationSection("bundle.weekly"),
                BundleConstraints.defaultsWeekly());
        BundleConstraints monthly = parseBundleConstraints(section.getConfigurationSection("bundle.monthly"),
                BundleConstraints.defaultsMonthly());
        return new CurationSettings(defWindow, categoryWindow, focusWindow, familyWindow, daily, race, weekly, monthly);
    }

    private static GovernanceSettings parseGovernance(ConfigurationSection section) {
        if (section == null) {
            return GovernanceSettings.defaults();
        }
        boolean rerollEnabled = section.getBoolean("rerollWeekly.enabled", true);
        int rerollMaxPerCycle = Math.max(0, section.getInt("rerollWeekly.maxPerCycle", 1));
        boolean vetoEnabled = section.getBoolean("seasonalVeto.enabled", true);
        int vetoCount = Math.max(0, section.getInt("seasonalVeto.maxCategories", 1));
        boolean vetoImmutable = section.getBoolean("seasonalVeto.immutablePerSeason", true);
        return new GovernanceSettings(rerollEnabled, rerollMaxPerCycle, vetoEnabled, vetoCount, vetoImmutable);
    }

    private static BoardGeneratorSettings parseBoardGenerator(ConfigurationSection section) {
        if (section == null) {
            return BoardGeneratorSettings.defaults();
        }
        BoardTargetFormulaSettings targetFormula = parseBoardTargetFormula(
                section.getConfigurationSection("targetFormula")
        );
        BoardWeightsSettings weights = parseBoardWeights(section.getConfigurationSection("weights"));
        BoardHardRulesSettings hardRules = parseBoardHardRules(section.getConfigurationSection("hardRules"));
        Map<ChallengeSeasonResolver.Season, SeasonThemeSettings> seasonThemes = parseSeasonThemes(
                section.getConfigurationSection("seasonThemes")
        );
        return new BoardGeneratorSettings(targetFormula, weights, hardRules, seasonThemes);
    }

    private static BoardTargetFormulaSettings parseBoardTargetFormula(ConfigurationSection section) {
        if (section == null) {
            return BoardTargetFormulaSettings.defaults();
        }
        Map<PrincipalStage, Double> stageMultipliers = new EnumMap<>(PrincipalStage.class);
        ConfigurationSection stageSection = section.getConfigurationSection("stageMultipliers");
        for (PrincipalStage stage : PrincipalStage.values()) {
            double fallback = BoardTargetFormulaSettings.defaults().stageMultipliers().getOrDefault(stage, 1.0D);
            double configured = stageSection == null
                    ? fallback
                    : stageSection.getDouble(stage.name(), fallback);
            stageMultipliers.put(stage, sanitizeMultiplier(configured, fallback));
        }

        double daily = sanitizeMultiplier(section.getDouble("cycleMultipliers.daily", 1.0D), 1.0D);
        double race = sanitizeMultiplier(section.getDouble("cycleMultipliers.race", 3.0D), 3.0D);
        double weekly = sanitizeMultiplier(section.getDouble("cycleMultipliers.weekly", 7.0D), 7.0D);
        double monthlyFallback = section.contains("cycleMultipliers.monthly")
                ? section.getDouble("cycleMultipliers.monthly", 25.0D)
                : section.getDouble("cycleMultipliers.monthlyGrand",
                section.getDouble("cycleMultipliers.monthlySide", 25.0D));
        double monthlySide = sanitizeMultiplier(monthlyFallback, 25.0D);
        double monthlyGrand = sanitizeMultiplier(monthlyFallback, 25.0D);
        double seasonal = sanitizeMultiplier(section.getDouble("cycleMultipliers.seasonal", 50.0D), 50.0D);

        Map<ChallengeObjectiveType, ObjectiveExceptionSettings> exceptions = new EnumMap<>(ChallengeObjectiveType.class);
        ConfigurationSection exceptionsSection = section.getConfigurationSection("exceptions");
        for (ChallengeObjectiveType type : ChallengeObjectiveType.values()) {
            ObjectiveExceptionSettings defaults = BoardTargetFormulaSettings.defaults().exceptions().get(type);
            if (exceptionsSection == null) {
                if (defaults != null) {
                    exceptions.put(type, defaults);
                }
                continue;
            }
            ConfigurationSection row = exceptionsSection.getConfigurationSection(type.id());
            if (row == null) {
                row = exceptionsSection.getConfigurationSection(type.name().toLowerCase(Locale.ROOT));
            }
            if (row == null) {
                if (defaults != null) {
                    exceptions.put(type, defaults);
                }
                continue;
            }
            TargetBracketSettings dailyBracket = parseTargetBracket(
                    row.getConfigurationSection("daily"),
                    defaults == null ? null : defaults.daily()
            );
            TargetBracketSettings weeklyBracket = parseTargetBracket(
                    row.getConfigurationSection("weekly"),
                    defaults == null ? null : defaults.weekly()
            );
            TargetBracketSettings monthlySideBracket = parseTargetBracket(
                    row.getConfigurationSection("monthlySide"),
                    defaults == null ? null : defaults.monthlySide()
            );
            TargetBracketSettings monthlyGrandBracket = parseTargetBracket(
                    row.getConfigurationSection("monthlyGrand"),
                    defaults == null ? null : defaults.monthlyGrand()
            );
            TargetBracketSettings raceBracket = parseTargetBracket(
                    row.getConfigurationSection("race"),
                    defaults == null ? null : defaults.race()
            );
            ObjectiveExceptionSettings parsed = new ObjectiveExceptionSettings(
                    dailyBracket,
                    weeklyBracket,
                    monthlySideBracket,
                    monthlyGrandBracket,
                    raceBracket
            );
            if (parsed.hasAny()) {
                exceptions.put(type, parsed);
            }
        }
        return new BoardTargetFormulaSettings(
                Map.copyOf(stageMultipliers),
                daily,
                race,
                weekly,
                monthlySide,
                monthlyGrand,
                seasonal,
                Map.copyOf(exceptions)
        );
    }

    private static TargetBracketSettings parseTargetBracket(
            ConfigurationSection section,
            TargetBracketSettings fallback
    ) {
        if (section == null) {
            return fallback;
        }
        int fallbackMin = fallback == null ? 1 : fallback.min();
        int fallbackMax = fallback == null ? fallbackMin : fallback.max();
        int fallbackStep = fallback == null ? 1 : fallback.step();
        int min = Math.max(1, section.getInt("min", fallbackMin));
        int max = Math.max(min, section.getInt("max", fallbackMax));
        int step = Math.max(1, section.getInt("step", fallbackStep));
        return new TargetBracketSettings(min, max, step);
    }

    private static BoardWeightsSettings parseBoardWeights(ConfigurationSection section) {
        if (section == null) {
            return BoardWeightsSettings.defaults();
        }
        return new BoardWeightsSettings(
                sanitizeRatio(section.getDouble("seasonThemeWeight", 0.50D), 0.50D),
                sanitizeRatio(section.getDouble("atlasLowestWeight", 0.30D), 0.30D),
                sanitizeRatio(section.getDouble("cityAffinity14dWeight", 0.20D), 0.20D)
        );
    }

    private static BoardHardRulesSettings parseBoardHardRules(ConfigurationSection section) {
        if (section == null) {
            return BoardHardRulesSettings.defaults();
        }
        return new BoardHardRulesSettings(
                section.getBoolean("noConsecutiveFamily", true),
                Math.max(0, section.getInt("maxPassivePerBoard", 1)),
                Math.max(0, section.getInt("minSkillfulPerBoard", 1)),
                section.getBoolean("banDailyPlaytimeXpPair", true),
                section.getBoolean("excludeEconomyFromRace", true),
                Math.max(1, section.getInt("resourceContributionWindowDays", 7)),
                Math.max(1, section.getInt("resourceContributionMaxInWindow", 1))
        );
    }

    private static Map<ChallengeSeasonResolver.Season, SeasonThemeSettings> parseSeasonThemes(ConfigurationSection section) {
        Map<ChallengeSeasonResolver.Season, SeasonThemeSettings> result = new EnumMap<>(ChallengeSeasonResolver.Season.class);
        if (section == null) {
            return Map.copyOf(result);
        }
        for (String key : section.getKeys(false)) {
            ChallengeSeasonResolver.Season season;
            try {
                season = ChallengeSeasonResolver.Season.valueOf(key.trim().toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                continue;
            }
            ConfigurationSection row = section.getConfigurationSection(key);
            if (row == null) {
                continue;
            }
            Set<AtlasChapter> chapters = parseAtlasChapters(row.getStringList("preferredChapters"));
            Set<String> families = normalizeLowerSet(row.getStringList("preferredFamilies"));
            ConfigurationSection weights = row.getConfigurationSection("weights");
            Double seasonWeight = weights == null ? null : sanitizeRatio(weights.getDouble("seasonTheme", 0.0D), 0.0D);
            Double atlasWeight = weights == null ? null : sanitizeRatio(weights.getDouble("atlasLowest", 0.0D), 0.0D);
            Double affinityWeight = weights == null ? null : sanitizeRatio(weights.getDouble("cityAffinity14d", 0.0D), 0.0D);
            // Distinguish explicit 0 from not-configured.
            if (weights != null && !weights.contains("seasonTheme")) {
                seasonWeight = null;
            }
            if (weights != null && !weights.contains("atlasLowest")) {
                atlasWeight = null;
            }
            if (weights != null && !weights.contains("cityAffinity14d")) {
                affinityWeight = null;
            }
            result.put(season, new SeasonThemeSettings(
                    Set.copyOf(chapters),
                    Set.copyOf(families),
                    seasonWeight,
                    atlasWeight,
                    affinityWeight
            ));
        }
        return Map.copyOf(result);
    }

    private static Set<AtlasChapter> parseAtlasChapters(List<String> values) {
        EnumSet<AtlasChapter> result = EnumSet.noneOf(AtlasChapter.class);
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                result.add(AtlasChapter.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private static MilestoneSettings parseMilestones(Plugin plugin, ConfigurationSection section) {
        if (section == null) {
            return MilestoneSettings.defaults(plugin);
        }
        int m1 = Math.max(1, section.getInt("weekly.m1At", 1));
        int m2 = Math.max(m1, section.getInt("weekly.m2At", 2));
        int m3 = Math.max(m2, section.getInt("weekly.m3At", 3));
        int seasonalFinal = Math.max(m3, section.getInt("seasonal.finalAt", 6));
        ChallengeRewardSpec weekly1 = parseRewardSpec(plugin, section.getConfigurationSection("rewards.weekly1"));
        ChallengeRewardSpec weekly2 = parseRewardSpec(plugin, section.getConfigurationSection("rewards.weekly2"));
        ChallengeRewardSpec weekly3 = parseRewardSpec(plugin, section.getConfigurationSection("rewards.weekly3"));
        ChallengeRewardSpec finale = parseRewardSpec(plugin, section.getConfigurationSection("rewards.seasonFinal"));
        return new MilestoneSettings(m1, m2, m3, seasonalFinal, weekly1, weekly2, weekly3, finale);
    }

    private static M6Settings parseM6(Plugin plugin, ConfigurationSection section) {
        if (section == null) {
            return M6Settings.defaults();
        }
        ConfigurationSection boardLocks = section.getConfigurationSection("boardLocks");
        int dailyLocked = boardLocks == null ? 8 : Math.max(1, boardLocks.getInt("dailyStandard", 8));
        int weeklyLocked = boardLocks == null ? 18 : Math.max(1, boardLocks.getInt("weeklyStandard", 18));
        M6BoardLocksSettings locks = new M6BoardLocksSettings(dailyLocked, weeklyLocked);

        ConfigurationSection firstCompletion = section.getConfigurationSection("firstCompletion");
        ConfigurationSection dailySection = firstCompletion == null ? null : firstCompletion.getConfigurationSection("daily");
        ConfigurationSection weeklySection = firstCompletion == null ? null : firstCompletion.getConfigurationSection("weekly");
        boolean dailyEnabled = dailySection == null || dailySection.getBoolean("enabled", true);
        int dailyMaxClaims = dailySection == null ? 2 : Math.max(1, dailySection.getInt("maxClaims", 2));
        ChallengeRewardSpec dailyReward = dailySection == null
                ? M6FirstCompletionDailySettings.defaultReward()
                : parseRewardSpec(plugin, dailySection.getConfigurationSection("reward"));
        if (dailyReward.empty()) {
            dailyReward = M6FirstCompletionDailySettings.defaultReward();
        }
        boolean weeklyEnabled = weeklySection == null || weeklySection.getBoolean("enabled", true);
        ChallengeRewardSpec weeklyReward = weeklySection == null
                ? M6FirstCompletionWeeklySettings.defaultReward()
                : parseRewardSpec(plugin, weeklySection.getConfigurationSection("reward"));
        if (weeklyReward.empty()) {
            weeklyReward = M6FirstCompletionWeeklySettings.defaultReward();
        }
        M6FirstCompletionDailySettings daily = new M6FirstCompletionDailySettings(dailyEnabled, dailyMaxClaims, dailyReward);
        M6FirstCompletionWeeklySettings weekly = new M6FirstCompletionWeeklySettings(weeklyEnabled, weeklyReward);
        return new M6Settings(locks, new M6FirstCompletionSettings(daily, weekly));
    }

    private static BundleConstraints parseBundleConstraints(ConfigurationSection section, BundleConstraints fallback) {
        if (section == null) {
            return fallback;
        }
        String a = normalizeBundleToken(section.getString("slotA", fallback.slotA()), fallback.slotA());
        String b = normalizeBundleToken(section.getString("slotB", fallback.slotB()), fallback.slotB());
        String c = normalizeBundleToken(section.getString("slotC", fallback.slotC()), fallback.slotC());
        return new BundleConstraints(a, b, c);
    }

    private static String normalizeBundleToken(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static TemplateFormatterSettings parseTemplateFormatter(ConfigurationSection section) {
        if (section == null) {
            return TemplateFormatterSettings.defaults();
        }
        ConfigurationSection random = section.getConfigurationSection("randomRange");
        if (random == null) {
            return TemplateFormatterSettings.defaults();
        }
        boolean enabled = random.getBoolean("enabled", true);
        long minAllowed = random.getLong("minAllowed", 0L);
        long maxAllowed = random.getLong("maxAllowed", 1_000_000L);
        if (minAllowed > maxAllowed) {
            long swap = minAllowed;
            minAllowed = maxAllowed;
            maxAllowed = swap;
        }
        return new TemplateFormatterSettings(enabled, minAllowed, maxAllowed);
    }

    private static DifficultyResolverSettings parseDifficultyResolver(ConfigurationSection section) {
        if (section == null) {
            return DifficultyResolverSettings.defaults();
        }
        ConfigurationSection thresholds = section.getConfigurationSection("thresholds");
        DifficultyThreshold daily = parseThreshold(thresholds == null ? null : thresholds.getConfigurationSection("daily"), 250, 500);
        DifficultyThreshold weekly = parseThreshold(thresholds == null ? null : thresholds.getConfigurationSection("weekly"), 900, 1800);
        DifficultyThreshold monthly = parseThreshold(thresholds == null ? null : thresholds.getConfigurationSection("monthly"), 3000, 6000);

        ConfigurationSection multipliers = section.getConfigurationSection("multipliers");
        double easy = sanitizeRatio(multipliers == null ? 1.0D : multipliers.getDouble("easy", 1.0D), 1.0D);
        double normal = sanitizeRatio(multipliers == null ? 1.25D : multipliers.getDouble("normal", 1.25D), 1.25D);
        double hard = sanitizeRatio(multipliers == null ? 1.5D : multipliers.getDouble("hard", 1.5D), 1.5D);
        return new DifficultyResolverSettings(daily, weekly, monthly, easy, normal, hard);
    }

    private static SpecificTargetScalingSettings parseSpecificTargetScaling(ConfigurationSection section) {
        if (section == null) {
            return SpecificTargetScalingSettings.defaults();
        }
        double common = sanitizeMultiplier(section.getDouble("common", 1.0D), 1.0D);
        double uncommon = sanitizeMultiplier(section.getDouble("uncommon", 0.8D), 0.8D);
        double rare = sanitizeMultiplier(section.getDouble("rare", 0.6D), 0.6D);
        return new SpecificTargetScalingSettings(common, uncommon, rare);
    }

    private static TargetScalingSettings parseTargetScaling(ConfigurationSection section) {
        if (section == null) {
            return TargetScalingSettings.defaults();
        }
        List<CitySizeBracket> brackets = new ArrayList<>();
        ConfigurationSection bracketSection = section.getConfigurationSection("cityMembersBrackets");
        if (bracketSection != null) {
            for (String key : bracketSection.getKeys(false)) {
                ConfigurationSection row = bracketSection.getConfigurationSection(key);
                if (row == null) {
                    continue;
                }
                int min = Math.max(1, row.getInt("min", 1));
                int max = Math.max(min, row.getInt("max", min));
                double multiplier = sanitizeMultiplier(row.getDouble("multiplier", 1.0D), 1.0D);
                brackets.add(new CitySizeBracket(min, max, multiplier));
            }
        }
        if (brackets.isEmpty()) {
            brackets = TargetScalingSettings.defaultCityBrackets();
        }
        brackets = brackets.stream()
                .sorted(java.util.Comparator.comparingInt(CitySizeBracket::minMembers))
                .toList();

        ConfigurationSection onlineSection = section.getConfigurationSection("serverWideOnline");
        ServerWideOnlineScaling online = onlineSection == null
                ? ServerWideOnlineScaling.defaults()
                : new ServerWideOnlineScaling(
                        onlineSection.getBoolean("enabled", true),
                        Math.max(0.01D, onlineSection.getDouble("minFactor", 0.85D)),
                        Math.max(0.01D, onlineSection.getDouble("maxFactor", 2.00D)),
                        Math.max(0.0D, onlineSection.getDouble("baseFactor", 0.70D)),
                        Math.max(1.0D, onlineSection.getDouble("playersDivisor", 30.0D))
                );
        return new TargetScalingSettings(List.copyOf(brackets), online);
    }

    private static XpRangesSettings parseXpRanges(ConfigurationSection section) {
        if (section == null) {
            return XpRangesSettings.defaults();
        }
        XpRange dailyCompletion = parseXpRange(section.getConfigurationSection("dailyCompletion"), 12, 28);
        XpRange dailyRaceWinner = parseXpRange(section.getConfigurationSection("dailyRaceWinner"), 35, 60);
        XpRange weeklyCompletion = parseXpRange(section.getConfigurationSection("weeklyCompletion"), 30, 85);
        XpRange monthlyCompletion = parseXpRange(section.getConfigurationSection("monthlyCompletion"), 90, 140);
        XpRange monthlyEventCompletion = parseXpRange(section.getConfigurationSection("monthlyEventCompletion"), 110, 190);
        double excellenceRatio = Math.max(0.0D, section.getDouble("excellenceRatio", 0.35D));
        int excellenceMin = Math.max(1, section.getInt("excellenceMin", 6));
        return new XpRangesSettings(
                dailyCompletion,
                dailyRaceWinner,
                weeklyCompletion,
                monthlyCompletion,
                monthlyEventCompletion,
                excellenceRatio,
                excellenceMin
        );
    }

    private static XpRange parseXpRange(ConfigurationSection section, int fallbackMin, int fallbackMax) {
        if (section == null) {
            return new XpRange(fallbackMin, fallbackMax);
        }
        int min = Math.max(0, section.getInt("min", fallbackMin));
        int max = Math.max(min, section.getInt("max", fallbackMax));
        return new XpRange(min, max);
    }

    private static DifficultyThreshold parseThreshold(ConfigurationSection section, int normalFallback, int hardFallback) {
        if (section == null) {
            return new DifficultyThreshold(normalFallback, hardFallback);
        }
        int normal = Math.max(1, section.getInt("normalTarget", normalFallback));
        int hard = Math.max(normal, section.getInt("hardTarget", hardFallback));
        return new DifficultyThreshold(normal, hard);
    }

    private static StreakSettings parseStreak(ConfigurationSection section) {
        if (section == null) {
            return StreakSettings.defaults();
        }
        boolean enabled = section.getBoolean("enabled", true);
        Map<Integer, Double> ladder = new LinkedHashMap<>();
        ConfigurationSection ladderSection = section.getConfigurationSection("bonusLadder");
        if (ladderSection != null) {
            for (String key : ladderSection.getKeys(false)) {
                try {
                    int week = Integer.parseInt(key.trim());
                    if (week <= 0) {
                        continue;
                    }
                    ladder.put(week, Math.max(0.0D, ladderSection.getDouble(key, 0.0D)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (ladder.isEmpty()) {
            ladder = StreakSettings.defaultLadder();
        }
        return new StreakSettings(enabled, Map.copyOf(ladder));
    }

    private static MonthlyEventSettings parseMonthlyEvents(ConfigurationSection section) {
        if (section == null) {
            return MonthlyEventSettings.defaults();
        }
        boolean enabled = section.getBoolean("enabled", true);
        int countPerWindow = Math.max(1, section.getInt("countPerWindow", 1));
        LocalTime eventAStart = parseLocalTime(section.getString("windowA.startAt", "00:15"));
        if (eventAStart == null) {
            eventAStart = LocalTime.of(0, 15);
        }
        LocalTime eventBStart = parseLocalTime(section.getString("windowB.startAt", "00:15"));
        if (eventBStart == null) {
            eventBStart = LocalTime.of(0, 15);
        }
        return new MonthlyEventSettings(enabled, countPerWindow, eventAStart, eventBStart);
    }

    private static EventRewardsSettings parseEventRewards(Plugin plugin, ConfigurationSection section) {
        if (section == null) {
            return EventRewardsSettings.defaults();
        }
        boolean top1Enabled = section.getBoolean("top1.enabled", true);
        boolean top2Enabled = section.getBoolean("top2.enabled", true);
        boolean top3Enabled = section.getBoolean("top3.enabled", true);
        boolean thresholdEnabled = section.getBoolean("threshold.enabled", true);
        int thresholdTarget = Math.max(1, section.getInt("threshold.targetProgress", 1));
        ChallengeRewardSpec top1Reward = parseRewardSpec(plugin, section.getConfigurationSection("top1.reward"));
        ChallengeRewardSpec top2Reward = parseRewardSpec(plugin, section.getConfigurationSection("top2.reward"));
        ChallengeRewardSpec top3Reward = parseRewardSpec(plugin, section.getConfigurationSection("top3.reward"));
        ChallengeRewardSpec thresholdReward = parseRewardSpec(plugin, section.getConfigurationSection("threshold.reward"));
        return new EventRewardsSettings(
                top1Enabled,
                top2Enabled,
                top3Enabled,
                thresholdEnabled,
                thresholdTarget,
                top1Reward,
                top2Reward,
                top3Reward,
                thresholdReward
        );
    }

    private static EventRewardsSettings parseRaceRewards(Plugin plugin, ConfigurationSection section) {
        if (section == null) {
            EventRewardsSettings defaults = EventRewardsSettings.raceDefaults();
            validateRaceRewards(defaults);
            return defaults;
        }
        EventRewardsSettings settings = parseEventRewards(plugin, section);
        validateRaceRewards(settings);
        return settings;
    }

    private static void validateRaceRewards(EventRewardsSettings settings) {
        validateRaceRewardSpec("raceRewards.top1", settings.top1Enabled(), settings.top1Reward());
        validateRaceRewardSpec("raceRewards.top2", settings.top2Enabled(), settings.top2Reward());
        validateRaceRewardSpec("raceRewards.top3", settings.top3Enabled(), settings.top3Reward());
        validateRaceRewardSpec("raceRewards.threshold", settings.thresholdEnabled(), settings.thresholdReward());
    }

    private static void validateRaceRewardSpec(String path, boolean enabled, ChallengeRewardSpec spec) {
        if (!enabled) {
            return;
        }
        if (spec == null || spec.moneyCity() <= 0.0D) {
            throw new IllegalStateException("[challenges] " + path + " deve avere moneyCity > 0");
        }
    }

    private static AntiAbuseSettings parseAntiAbuse(ConfigurationSection section) {
        if (section == null) {
            return AntiAbuseSettings.defaults();
        }
        int throttleMs = Math.max(0, section.getInt("throttleMs", 500));
        double playerContributionCapRatio = sanitizeRatio(section.getDouble("playerContributionCapRatio", 0.35D), 0.35D);
        int afkIdleSeconds = Math.max(30, section.getInt("afkIdleSeconds", 120));
        int placedBlockTtlHours = Math.max(1, section.getInt("placedBlockTtlHours", 24));
        int placedBlockMaxEntries = Math.max(1000, section.getInt("placedBlockMaxEntries", 250000));
        boolean dailyTownCategoryCapEnabled = section.getBoolean("dailyTownCategoryCapEnabled", true);
        double dailyTownCategoryCapRatio = sanitizeRatio(section.getDouble("dailyTownCategoryCapRatio", 2.0D), 2.0D);
        boolean denyOnGuardError = section.getBoolean("denyOnGuardError", true);

        List<String> denyReasons = section.getStringList("denyMobSpawnReasons");
        java.util.Set<String> normalizedReasons = new java.util.LinkedHashSet<>();
        for (String value : denyReasons) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalizedReasons.add(value.trim().toUpperCase(Locale.ROOT));
        }
        if (normalizedReasons.isEmpty()) {
            normalizedReasons = java.util.Set.of("SPAWNER", "SPAWN_EGG", "CUSTOM");
        }

        List<String> rawSampled = section.getStringList("sampledObjectives");
        Set<ChallengeObjectiveType> sampledObjectives = parseSampledObjectives(rawSampled);
        if (sampledObjectives.isEmpty()) {
            sampledObjectives = AntiAbuseSettings.defaults().sampledObjectives();
        }

        List<String> structureLootPrefixes = normalizeLowerList(
                section.getStringList("structureLoot.allowLootTablePrefixes")
        );
        if (structureLootPrefixes.isEmpty()) {
            structureLootPrefixes = List.copyOf(AntiAbuseSettings.defaults().structureLootTableAllowPrefixes());
        }

        Set<String> structureAllowlist = normalizeLowerSet(
                section.getStringList("exploration.allowedStructures")
        );
        if (structureAllowlist.isEmpty()) {
            structureAllowlist = Set.copyOf(AntiAbuseSettings.defaults().explorationStructureAllowlist());
        }
        boolean explorationAdvancementFallbackEnabled = section.getBoolean("exploration.advancementFallbackEnabled", false);

        SuspicionSettings suspicion = parseSuspicionSettings(section.getConfigurationSection("suspicion"));
        Map<String, ObjectiveFamilyProfile> familyProfiles = parseObjectiveFamilyProfiles(
                section.getConfigurationSection("profiles"),
                throttleMs,
                playerContributionCapRatio
        );
        return new AntiAbuseSettings(
                throttleMs,
                playerContributionCapRatio,
                afkIdleSeconds,
                java.util.Set.copyOf(normalizedReasons),
                placedBlockTtlHours,
                placedBlockMaxEntries,
                dailyTownCategoryCapEnabled,
                dailyTownCategoryCapRatio,
                denyOnGuardError,
                Set.copyOf(sampledObjectives),
                List.copyOf(structureLootPrefixes),
                Set.copyOf(structureAllowlist),
                explorationAdvancementFallbackEnabled,
                suspicion,
                Map.copyOf(familyProfiles)
        );
    }

    private static Set<ChallengeObjectiveType> parseSampledObjectives(List<String> values) {
        EnumSet<ChallengeObjectiveType> result = EnumSet.noneOf(ChallengeObjectiveType.class);
        if (values == null) {
            return result;
        }
        for (String value : values) {
            ChallengeObjectiveType type = ChallengeObjectiveType.fromId(value);
            if (type != null) {
                result.add(type);
            }
        }
        return result;
    }

    private static List<String> normalizeLowerList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            result.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(result);
    }

    private static Set<String> normalizeLowerSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            result.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(result);
    }

    private static SuspicionSettings parseSuspicionSettings(ConfigurationSection section) {
        if (section == null) {
            return SuspicionSettings.defaults();
        }
        return new SuspicionSettings(
                section.getBoolean("enabled", true),
                Math.max(1, section.getInt("warningThreshold", 8)),
                Math.max(1, section.getInt("clampThreshold", 15)),
                Math.max(1, section.getInt("reviewThreshold", 25)),
                Math.max(1, section.getInt("decayIntervalMinutes", 30)),
                Math.max(1, section.getInt("decayAmount", 1))
        );
    }

    private static Map<String, ObjectiveFamilyProfile> parseObjectiveFamilyProfiles(
            ConfigurationSection section,
            int fallbackThrottleMs,
            double fallbackCapRatio
    ) {
        Map<String, ObjectiveFamilyProfile> defaults = ObjectiveFamilyProfile.defaultsBalanced(
                fallbackThrottleMs,
                fallbackCapRatio
        );
        if (section == null) {
            return defaults;
        }
        Map<String, ObjectiveFamilyProfile> result = new HashMap<>(defaults);
        for (String key : section.getKeys(false)) {
            String family = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
            if (family.isBlank()) {
                continue;
            }
            ConfigurationSection row = section.getConfigurationSection(key);
            if (row == null) {
                continue;
            }
            ObjectiveFamilyProfile fallback = defaults.getOrDefault(family, ObjectiveFamilyProfile.defaultProfile(fallbackThrottleMs, fallbackCapRatio));
            ObjectiveFamilyProfile parsed = new ObjectiveFamilyProfile(
                    Math.max(0, row.getInt("throttleMs", fallback.throttleMs())),
                    sanitizeRatio(row.getDouble("softCapRatio", fallback.softCapRatio()), fallback.softCapRatio()),
                    sanitizeRatio(row.getDouble("hardCapRatio", fallback.hardCapRatio()), fallback.hardCapRatio()),
                    sanitizeRatio(row.getDouble("clampRatio", fallback.clampRatio()), fallback.clampRatio()),
                    normalizeUpperSet(row.getStringList("denySources"), fallback.denySources()),
                    Math.max(0, row.getInt("suspicionOnThrottle", fallback.suspicionOnThrottle())),
                    Math.max(0, row.getInt("suspicionOnDenySource", fallback.suspicionOnDenySource())),
                    Math.max(0, row.getInt("suspicionOnCap", fallback.suspicionOnCap())),
                    Math.max(0, row.getInt("suspicionOnSoftClamp", fallback.suspicionOnSoftClamp())),
                    Math.max(0, row.getInt("suspicionOnDedupe", fallback.suspicionOnDedupe()))
            );
            result.put(family, parsed);
        }
        return Map.copyOf(result);
    }

    private static Set<String> normalizeUpperSet(List<String> values, Set<String> fallback) {
        if (values == null || values.isEmpty()) {
            return fallback == null ? Set.of() : Set.copyOf(fallback);
        }
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            result.add(value.trim().toUpperCase(Locale.ROOT));
        }
        if (result.isEmpty()) {
            return fallback == null ? Set.of() : Set.copyOf(fallback);
        }
        return Set.copyOf(result);
    }

    private static double sanitizeRatio(double value, double fallback) {
        if (Double.isNaN(value) || value <= 0.0D) {
            return fallback;
        }
        return value;
    }

    private static double sanitizeMultiplier(double value, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0D) {
            return fallback;
        }
        return value;
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

    private static Map<String, ChallengeRewardBundle> parseRewardBundles(Plugin plugin, ConfigurationSection root) {
        Map<String, ChallengeRewardBundle> result = new LinkedHashMap<>();
        if (root == null) {
            return result;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            ChallengeRewardSpec completion = parseRewardSpec(plugin, section.getConfigurationSection("completion"));
            ChallengeRewardSpec winner = parseRewardSpec(plugin, section.getConfigurationSection("winner"));
            ChallengeRewardSpec excellence = parseRewardSpec(plugin, section.getConfigurationSection("excellence"));
            if (!winner.empty() && winner.moneyCity() <= 0.0D) {
                throw new IllegalStateException("[challenges] bundles." + key + ".winner deve avere moneyCity > 0");
            }
            result.put(key, new ChallengeRewardBundle(key, completion, winner, excellence));
        }
        return result;
    }

    private static ChallengeRewardSpec parseRewardSpec(Plugin plugin, ConfigurationSection section) {
        if (section == null) {
            return ChallengeRewardSpec.EMPTY;
        }
        ConfigurationSection city = section.getConfigurationSection("city");
        double xpCity = Math.max(0.0D, section.getDouble("xpCity",
                city == null ? 0.0D : city.getDouble("xpCity", 0.0D)));
        double moneyCity = Math.max(0.0D, section.getDouble("moneyCity",
                city == null ? 0.0D : city.getDouble("moneyCity", 0.0D)));
        List<String> commands = section.isList("commands")
                ? section.getStringList("commands")
                : (city == null ? List.of() : city.getStringList("commands"));
        if (commands == null) {
            commands = List.of();
        }
        List<String> cleanCommands = commands.stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toList());
        List<String> commandKeysRaw = section.isList("commandKeys")
                ? section.getStringList("commandKeys")
                : (city == null ? List.of() : city.getStringList("commandKeys"));
        List<String> commandKeys = new ArrayList<>();
        for (int i = 0; i < cleanCommands.size(); i++) {
            String fallbackKey = "command_" + (i + 1);
            String key = i < commandKeysRaw.size() ? commandKeysRaw.get(i) : fallbackKey;
            if (key == null || key.isBlank()) {
                key = fallbackKey;
            }
            commandKeys.add(key.trim());
        }
        Map<Material, Integer> items = new EnumMap<>(Material.class);
        ConfigurationSection itemsSection = section.getConfigurationSection("vaultItems");
        if (itemsSection == null && city != null) {
            itemsSection = city.getConfigurationSection("vaultItems");
        }
        if (itemsSection != null) {
            for (String rawMaterial : itemsSection.getKeys(false)) {
                Material material = Material.matchMaterial(rawMaterial.toUpperCase(Locale.ROOT));
                if (material == null || !material.isItem()) {
                    plugin.getLogger().warning("[challenges] materiale reward non valido: " + rawMaterial);
                    continue;
                }
                int amount = Math.max(0, itemsSection.getInt(rawMaterial, 0));
                if (amount > 0) {
                    items.put(material, amount);
                }
            }
        }
        ConfigurationSection personal = section.getConfigurationSection("personal");
        double personalXp = personal == null ? 0.0D : Math.max(0.0D, personal.getDouble("xpCityBonus", 0.0D));
        return new ChallengeRewardSpec(
                xpCity,
                moneyCity,
                List.copyOf(cleanCommands),
                List.copyOf(commandKeys),
                Map.copyOf(items),
                personalXp
        );
    }

    private static Map<String, ChallengeDefinition> parseDefinitions(
            Plugin plugin,
            ConfigurationSection root,
            Map<String, ChallengeRewardBundle> bundles
    ) {
        Map<String, ChallengeDefinition> result = new LinkedHashMap<>();
        if (root == null) {
            return result;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            ChallengeObjectiveType objectiveType = ChallengeObjectiveType.fromId(section.getString("objective", ""));
            if (objectiveType == null) {
                plugin.getLogger().warning("[challenges] objective non valido per def '" + key + "'");
                continue;
            }
            String rewardBundleId = section.getString("rewardBundle", "");
            if (!rewardBundleId.isBlank() && !bundles.containsKey(rewardBundleId)) {
                plugin.getLogger().warning("[challenges] rewardBundle non trovato per def '" + key + "': " + rewardBundleId);
            }
            Set<ChallengeMode> supported = parseSupportedModes(section);
            if (supported.isEmpty()) {
                supported = EnumSet.of(ChallengeMode.DAILY_STANDARD);
            }
            int target = Math.max(1, section.getInt("target", 1));
            int targetMin = target;
            int targetMax = target;
            int targetStep = 1;
            ConfigurationSection targetRange = section.getConfigurationSection("targetRange");
            if (targetRange != null) {
                targetMin = Math.max(1, targetRange.getInt("min", target));
                targetMax = Math.max(targetMin, targetRange.getInt("max", targetMin));
                targetStep = Math.max(1, targetRange.getInt("step", 1));
            } else if (objectiveType == ChallengeObjectiveType.TRANSPORT_DISTANCE) {
                // Default transport challenge range: 250..1000 with +50 progression.
                targetMin = 250;
                targetMax = 1000;
                targetStep = 50;
            }
            String bundleSlot = section.getString("bundleSlot", defaultBundleSlot(objectiveType));
            String difficultyTag = section.getString("difficultyTag", defaultDifficultyTag(objectiveType));
            String objectiveFamily = section.getString("objectiveFamily", defaultObjectiveFamily(objectiveType));
            VariantSettings variantSettings = parseVariantSettings(section, objectiveType);
            ChallengeDefinition definition = new ChallengeDefinition(
                    key,
                    section.getString("name", key),
                    objectiveType,
                    Math.max(1, section.getInt("category", 1)),
                    bundleSlot,
                    difficultyTag,
                    objectiveFamily,
                    target,
                    targetMin,
                    targetMax,
                    targetStep,
                    Math.max(1, section.getInt("weight", 1)),
                    Set.copyOf(supported),
                    variantSettings.mode(),
                    variantSettings.specificChance(),
                    variantSettings.specificChanceByCycle(),
                    variantSettings.focusType(),
                    variantSettings.focusPool(),
                    rewardBundleId,
                    section.getBoolean("enabled", true)
            );
            result.put(key, definition);
        }
        return result;
    }

    private static Map<ChallengeObjectiveType, TaxonomyPolicySettings> parseTaxonomyPolicies(
            Plugin plugin,
            ConfigurationSection section
    ) {
        Map<ChallengeObjectiveType, TaxonomyPolicySettings> result = new EnumMap<>(ChallengeObjectiveType.class);
        if (section == null) {
            return Map.of();
        }
        for (String key : section.getKeys(false)) {
            ChallengeObjectiveType objectiveType = ChallengeObjectiveType.fromId(key);
            ConfigurationSection row = section.getConfigurationSection(key);
            if (row == null) {
                continue;
            }
            if (objectiveType == null) {
                objectiveType = ChallengeObjectiveType.fromId(row.getString("objective", ""));
            }
            if (objectiveType == null) {
                plugin.getLogger().warning("[challenges] taxonomyPolicies objective non valido: " + key);
                continue;
            }
            ChallengeProgressionRole progressionRole = ChallengeProgressionRole.fromString(
                    row.getString("progressionRole", "")
            );
            Set<ChallengeMode> allowedModes = null;
            if (row.isList("allowedModes")) {
                EnumSet<ChallengeMode> parsed = EnumSet.noneOf(ChallengeMode.class);
                for (String rawMode : row.getStringList("allowedModes")) {
                    ChallengeMode mode = parseModeToken(rawMode);
                    if (mode != null) {
                        parsed.add(mode);
                    }
                }
                allowedModes = parsed.isEmpty() ? Set.of() : Set.copyOf(parsed);
            }
            Boolean raceEligible = row.contains("raceEligible") ? Boolean.valueOf(row.getBoolean("raceEligible")) : null;
            Boolean cityXpEnabled = row.contains("cityXpEnabled") ? Boolean.valueOf(row.getBoolean("cityXpEnabled")) : null;
            result.put(
                    objectiveType,
                    new TaxonomyPolicySettings(progressionRole, allowedModes, raceEligible, cityXpEnabled)
            );
        }
        return Map.copyOf(result);
    }

    private static String defaultBundleSlot(ChallengeObjectiveType type) {
        if (type == null) {
            return "support";
        }
        return switch (type) {
            case BLOCK_MINE, CROP_HARVEST, FISH_CATCH, RESOURCE_CONTRIBUTION, VAULT_DELIVERY, FOOD_CRAFT, BREW_POTION, ITEM_CRAFT -> "production";
            case MOB_KILL, RARE_MOB_KILL, BOSS_KILL, RAID_WIN, DIMENSION_TRAVEL, STRUCTURE_DISCOVERY, ARCHAEOLOGY_BRUSH, TRIAL_VAULT_OPEN, NETHER_ACTIVITY, OCEAN_ACTIVITY, SECRET_QUEST, TEAM_PHASE_QUEST -> "combat_exploration";
            case VILLAGER_TRADE, ECONOMY_ADVANCED, PLAYTIME_MINUTES, TRANSPORT_DISTANCE, ANIMAL_INTERACTION -> "social_economy";
            case CONSTRUCTION, REDSTONE_AUTOMATION, XP_PICKUP, STRUCTURE_LOOT, FARMING_ECOSYSTEM, PLACE_BLOCK -> "support";
        };
    }

    private static String defaultDifficultyTag(ChallengeObjectiveType type) {
        if (type == null) {
            return "medium";
        }
        return switch (type) {
            case BOSS_KILL, RAID_WIN, RARE_MOB_KILL, STRUCTURE_DISCOVERY, ARCHAEOLOGY_BRUSH, TRIAL_VAULT_OPEN, SECRET_QUEST, TEAM_PHASE_QUEST -> "epic";
            case NETHER_ACTIVITY, OCEAN_ACTIVITY, PLACE_BLOCK -> "medium";
            case PLAYTIME_MINUTES, RESOURCE_CONTRIBUTION, VAULT_DELIVERY, XP_PICKUP, FISH_CATCH, FOOD_CRAFT, BREW_POTION -> "easy";
            case ITEM_CRAFT -> "medium";
            default -> "medium";
        };
    }

    private static String defaultObjectiveFamily(ChallengeObjectiveType type) {
        if (type == null) {
            return "generic";
        }
        return switch (type) {
            case BLOCK_MINE, RESOURCE_CONTRIBUTION, VAULT_DELIVERY -> "mining";
            case MOB_KILL, RARE_MOB_KILL, BOSS_KILL, RAID_WIN, NETHER_ACTIVITY, OCEAN_ACTIVITY -> "combat";
            case CROP_HARVEST, FARMING_ECOSYSTEM, ANIMAL_INTERACTION, FOOD_CRAFT, BREW_POTION -> "farming";
            case ITEM_CRAFT -> "builder";
            case STRUCTURE_DISCOVERY, DIMENSION_TRAVEL, STRUCTURE_LOOT, TRIAL_VAULT_OPEN, TRANSPORT_DISTANCE, ARCHAEOLOGY_BRUSH, SECRET_QUEST -> "exploration";
            case TEAM_PHASE_QUEST -> "seasonal_team";
            case VILLAGER_TRADE, ECONOMY_ADVANCED -> "economy";
            case REDSTONE_AUTOMATION, CONSTRUCTION, PLACE_BLOCK -> "builder";
            case FISH_CATCH -> "fishing";
            case PLAYTIME_MINUTES -> "playtime";
            case XP_PICKUP -> "utility";
        };
    }

    private static VariantSettings parseVariantSettings(
            ConfigurationSection section,
            ChallengeObjectiveType objectiveType
    ) {
        ConfigurationSection variant = section.getConfigurationSection("variant");
        if (variant == null) {
            return new VariantSettings(
                    ChallengeVariantMode.GLOBAL_ONLY,
                    0.50D,
                    Map.of(),
                    ChallengeFocusType.NONE,
                    List.of()
            );
        }
        ChallengeVariantMode mode = ChallengeVariantMode.fromString(variant.getString("mode", "GLOBAL_ONLY"));
        double specificChance = clampChance(variant.getDouble("specificChance", 0.50D));
        Map<ChallengeMode, Double> byCycle = parseSpecificChanceByCycle(variant.getConfigurationSection("specificChanceByCycle"));
        ChallengeFocusType focusType = ChallengeFocusType.fromString(variant.getString("focusType", "NONE"));
        List<ChallengeFocusOption> focusPool = parseFocusPool(
                variant.getConfigurationSection("focusPool"),
                variant.getMapList("focusPool"),
                objectiveType
        );
        if (focusType == ChallengeFocusType.NONE || focusPool.isEmpty()) {
            return new VariantSettings(mode == ChallengeVariantMode.SPECIFIC_ONLY ? ChallengeVariantMode.GLOBAL_ONLY : mode,
                    specificChance, byCycle, ChallengeFocusType.NONE, List.of());
        }
        return new VariantSettings(mode, specificChance, byCycle, focusType, focusPool);
    }

    private static Map<ChallengeMode, Double> parseSpecificChanceByCycle(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<ChallengeMode, Double> result = new EnumMap<>(ChallengeMode.class);
        for (String key : section.getKeys(false)) {
            ChallengeMode mode = parseModeToken(key);
            if (mode == null) {
                continue;
            }
            result.put(mode, clampChance(section.getDouble(key, 0.50D)));
        }
        return Map.copyOf(result);
    }

    private static List<ChallengeFocusOption> parseFocusPool(
            ConfigurationSection section,
            List<Map<?, ?>> listFallback,
            ChallengeObjectiveType objectiveType
    ) {
        List<ChallengeFocusOption> result = new ArrayList<>();
        if (listFallback != null && !listFallback.isEmpty()) {
            for (Map<?, ?> row : listFallback) {
                if (row == null || row.isEmpty()) {
                    continue;
                }
                Object keyObj = row.get("key");
                String key = keyObj == null ? "" : keyObj.toString();
                Object labelObj = row.get("label");
                String label = labelObj == null ? null : labelObj.toString();
                Object rarityObj = row.get("rarity");
                ChallengeFocusRarity rarity = ChallengeFocusRarity.fromString(rarityObj == null ? null : rarityObj.toString());
                ChallengeFocusOption option = new ChallengeFocusOption(key, label, rarity);
                if (!option.key().isBlank()) {
                    result.add(option);
                }
            }
        } else if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection row = section.getConfigurationSection(key);
                if (row == null) {
                    continue;
                }
                ChallengeFocusOption option = new ChallengeFocusOption(
                        row.getString("key", ""),
                        row.getString("label", null),
                        ChallengeFocusRarity.fromString(row.getString("rarity", "COMMON"))
                );
                if (!option.key().isBlank()) {
                    result.add(option);
                }
            }
        }
        if (!result.isEmpty()) {
            return List.copyOf(result);
        }
        return defaultFocusPoolFor(objectiveType);
    }

    private static List<ChallengeFocusOption> defaultFocusPoolFor(ChallengeObjectiveType type) {
        if (type == null) {
            return List.of();
        }
        return switch (type) {
            case BLOCK_MINE -> List.of(
                    new ChallengeFocusOption("COAL_ORE", "Minerale di carbone", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("IRON_ORE", "Minerale di ferro", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("COPPER_ORE", "Minerale di rame", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("REDSTONE_ORE", "Minerale di redstone", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("LAPIS_ORE", "Minerale di lapislazzuli", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("GOLD_ORE", "Minerale d'oro", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("DIAMOND_ORE", "Minerale di diamante", ChallengeFocusRarity.RARE),
                    new ChallengeFocusOption("EMERALD_ORE", "Minerale di smeraldo", ChallengeFocusRarity.RARE),
                    new ChallengeFocusOption("ANCIENT_DEBRIS", "Ancient Debris", ChallengeFocusRarity.RARE)
            );
            case MOB_KILL -> List.of(
                    new ChallengeFocusOption("ZOMBIE", "Zombie", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("SKELETON", "Scheletro", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("CREEPER", "Creeper", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("SPIDER", "Ragno", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("ENDERMAN", "Enderman", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("WITCH", "Strega", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("BLAZE", "Blaze", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("WITHER_SKELETON", "Wither Skeleton", ChallengeFocusRarity.RARE)
            );
            case RARE_MOB_KILL -> List.of(
                    new ChallengeFocusOption("BREEZE", "Breeze", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("PIGLIN_BRUTE", "Piglin Brute", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("SHULKER", "Shulker", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("ELDER_GUARDIAN", "Elder Guardian", ChallengeFocusRarity.RARE),
                    new ChallengeFocusOption("WARDEN", "Warden", ChallengeFocusRarity.RARE)
            );
            case CROP_HARVEST -> List.of(
                    new ChallengeFocusOption("WHEAT", "Grano", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("CARROTS", "Carote", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("POTATOES", "Patate", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("BEETROOTS", "Barbabietole", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("NETHER_WART", "Verruche del Nether", ChallengeFocusRarity.UNCOMMON)
            );
            case FISH_CATCH -> List.of(
                    new ChallengeFocusOption("COD", "Merluzzo", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("SALMON", "Salmone", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("PUFFERFISH", "Pesce palla", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("TROPICAL_FISH", "Pesce tropicale", ChallengeFocusRarity.UNCOMMON)
            );
            case FOOD_CRAFT -> List.of(
                    new ChallengeFocusOption("COOKED_BEEF", "Bistecca cotta", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("BREAD", "Pane", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("BAKED_POTATO", "Patata al forno", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("COOKED_CHICKEN", "Pollo cotto", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("COOKED_MUTTON", "Montone cotto", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("GOLDEN_CARROT", "Carota dorata", ChallengeFocusRarity.UNCOMMON)
            );
            case BREW_POTION -> List.of(
                    new ChallengeFocusOption("POTION_TYPE_HEALING", "Pozioni di cura", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("POTION_TYPE_STRENGTH", "Pozioni di forza", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("POTION_TYPE_SWIFTNESS", "Pozioni di velocita", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("POTION_TYPE_REGENERATION", "Pozioni di rigenerazione", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("POTION_SPLASH", "Pozioni splash", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("POTION_LEVEL_II", "Pozioni livello II", ChallengeFocusRarity.RARE)
            );
            case ITEM_CRAFT -> List.of(
                    new ChallengeFocusOption("PISTON", "Pistoni", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("STICKY_PISTON", "Pistoni appiccicosi", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("REPEATER", "Repeater", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("COMPARATOR", "Comparator", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("DIAMOND_CHESTPLATE", "Corazze di diamante", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("BEACON", "Beacon", ChallengeFocusRarity.RARE)
            );
            case ANIMAL_INTERACTION -> List.of(
                    new ChallengeFocusOption("COW", "Mucca", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("SHEEP", "Pecora", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("PIG", "Maiale", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("CHICKEN", "Gallina", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("HORSE", "Cavallo", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("WOLF", "Lupo", ChallengeFocusRarity.UNCOMMON)
            );
            case CONSTRUCTION -> List.of(
                    new ChallengeFocusOption("STONE_BRICKS", "Mattoni di pietra", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("BRICKS", "Mattoni", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("OAK_PLANKS", "Assi di quercia", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("DEEPSLATE_BRICKS", "Mattoni di deepslate", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("QUARTZ_BLOCK", "Blocco di quarzo", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("POLISHED_BLACKSTONE_BRICKS", "Mattoni di blackstone", ChallengeFocusRarity.UNCOMMON)
            );
            case REDSTONE_AUTOMATION -> List.of(
                    new ChallengeFocusOption("REDSTONE", "Redstone", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("REDSTONE_TORCH", "Torcia di redstone", ChallengeFocusRarity.COMMON),
                    new ChallengeFocusOption("REPEATER", "Repeater", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("COMPARATOR", "Comparator", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("OBSERVER", "Observer", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("PISTON", "Pistone", ChallengeFocusRarity.UNCOMMON),
                    new ChallengeFocusOption("HOPPER", "Hopper", ChallengeFocusRarity.UNCOMMON)
            );
            default -> List.of();
        };
    }

    private static ChallengeMode parseModeToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DAILY", "DAILY_STANDARD" -> ChallengeMode.DAILY_STANDARD;
            case "DAILY_SPRINT_1830", "SPRINT_1830", "DAILY_RACE_1830" -> ChallengeMode.DAILY_SPRINT_1830;
            case "DAILY_SPRINT_2130", "SPRINT_2130", "DAILY_RACE_2130" -> ChallengeMode.DAILY_SPRINT_2130;
            case "DAILY_RACE", "RACE", "RACE_1600", "DAILY_RACE_1600" -> ChallengeMode.DAILY_RACE_1600;
            case "RACE_2100", "DAILY_RACE_2100" -> ChallengeMode.DAILY_RACE_2100;
            case "WEEKLY_CLASH", "CLASH_WEEKLY" -> ChallengeMode.WEEKLY_CLASH;
            case "MONTHLY_CROWN", "CROWN_MONTHLY" -> ChallengeMode.MONTHLY_CROWN;
            case "MONTHLY_LEDGER_GRAND", "LEDGER_GRAND", "MONTHLY_GRAND" -> ChallengeMode.MONTHLY_LEDGER_GRAND;
            case "MONTHLY_LEDGER_CONTRACT", "LEDGER_CONTRACT", "MONTHLY_CONTRACT" -> ChallengeMode.MONTHLY_LEDGER_CONTRACT;
            case "MONTHLY_LEDGER_MYSTERY", "LEDGER_MYSTERY", "MONTHLY_MYSTERY" -> ChallengeMode.MONTHLY_LEDGER_MYSTERY;
            case "SEASON_CODEX_ACT_I", "CODEX_ACT_I" -> ChallengeMode.SEASON_CODEX_ACT_I;
            case "SEASON_CODEX_ACT_II", "CODEX_ACT_II" -> ChallengeMode.SEASON_CODEX_ACT_II;
            case "SEASON_CODEX_ACT_III", "CODEX_ACT_III" -> ChallengeMode.SEASON_CODEX_ACT_III;
            case "SEASON_CODEX_ELITE", "CODEX_ELITE" -> ChallengeMode.SEASON_CODEX_ELITE;
            case "SEASON_CODEX_HIDDEN_RELIC", "CODEX_HIDDEN_RELIC", "CODEX_RELIC" -> ChallengeMode.SEASON_CODEX_HIDDEN_RELIC;
            case "SEASONAL", "SEASONAL_RACE", "RACE_SEASONAL" -> ChallengeMode.SEASONAL_RACE;
            case "WEEKLY", "WEEKLY_STANDARD" -> ChallengeMode.WEEKLY_STANDARD;
            case "MONTHLY", "MONTHLY_STANDARD" -> ChallengeMode.MONTHLY_STANDARD;
            case "MONTHLY_EVENT_A", "EVENT_A" -> ChallengeMode.MONTHLY_EVENT_A;
            case "MONTHLY_EVENT_B", "EVENT_B" -> ChallengeMode.MONTHLY_EVENT_B;
            default -> {
                try {
                    yield ChallengeMode.valueOf(normalized);
                } catch (IllegalArgumentException ignored) {
                    yield null;
                }
            }
        };
    }

    private static Set<ChallengeMode> parseSupportedModes(ConfigurationSection section) {
        EnumSet<ChallengeMode> supported = EnumSet.noneOf(ChallengeMode.class);
        List<String> configured = section.getStringList("cycles");
        if (configured.isEmpty()) {
            configured = section.getStringList("modes");
        }
        if (!configured.isEmpty()) {
            for (String raw : configured) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String normalized = raw.trim().toUpperCase(Locale.ROOT);
                switch (normalized) {
                    case "DAILY" -> supported.add(ChallengeMode.DAILY_STANDARD);
                    case "DAILY_SPRINT_1830", "SPRINT_1830", "DAILY_RACE_1830" -> supported.add(ChallengeMode.DAILY_SPRINT_1830);
                    case "DAILY_SPRINT_2130", "SPRINT_2130", "DAILY_RACE_2130" -> supported.add(ChallengeMode.DAILY_SPRINT_2130);
                    case "DAILY_RACE", "RACE", "RACE_1600", "DAILY_RACE_1600" -> supported.add(ChallengeMode.DAILY_RACE_1600);
                    case "RACE_2100", "DAILY_RACE_2100" -> supported.add(ChallengeMode.DAILY_RACE_2100);
                    case "WEEKLY_CLASH", "CLASH_WEEKLY" -> supported.add(ChallengeMode.WEEKLY_CLASH);
                    case "MONTHLY_CROWN", "CROWN_MONTHLY" -> supported.add(ChallengeMode.MONTHLY_CROWN);
                    case "MONTHLY_LEDGER_GRAND", "LEDGER_GRAND", "MONTHLY_GRAND" -> supported.add(ChallengeMode.MONTHLY_LEDGER_GRAND);
                    case "MONTHLY_LEDGER_CONTRACT", "LEDGER_CONTRACT", "MONTHLY_CONTRACT" -> supported.add(ChallengeMode.MONTHLY_LEDGER_CONTRACT);
                    case "MONTHLY_LEDGER_MYSTERY", "LEDGER_MYSTERY", "MONTHLY_MYSTERY" -> supported.add(ChallengeMode.MONTHLY_LEDGER_MYSTERY);
                    case "SEASON_CODEX_ACT_I", "CODEX_ACT_I" -> supported.add(ChallengeMode.SEASON_CODEX_ACT_I);
                    case "SEASON_CODEX_ACT_II", "CODEX_ACT_II" -> supported.add(ChallengeMode.SEASON_CODEX_ACT_II);
                    case "SEASON_CODEX_ACT_III", "CODEX_ACT_III" -> supported.add(ChallengeMode.SEASON_CODEX_ACT_III);
                    case "SEASON_CODEX_ELITE", "CODEX_ELITE" -> supported.add(ChallengeMode.SEASON_CODEX_ELITE);
                    case "SEASON_CODEX_HIDDEN_RELIC", "CODEX_HIDDEN_RELIC", "CODEX_RELIC" -> supported.add(ChallengeMode.SEASON_CODEX_HIDDEN_RELIC);
                    case "SEASONAL", "SEASONAL_RACE", "RACE_SEASONAL" -> supported.add(ChallengeMode.SEASONAL_RACE);
                    case "WEEKLY", "WEEKLY_STANDARD" -> supported.add(ChallengeMode.WEEKLY_STANDARD);
                    case "MONTHLY", "MONTHLY_STANDARD" -> supported.add(ChallengeMode.MONTHLY_STANDARD);
                    case "MONTHLY_EVENT_A", "EVENT_A" -> supported.add(ChallengeMode.MONTHLY_EVENT_A);
                    case "MONTHLY_EVENT_B", "EVENT_B" -> supported.add(ChallengeMode.MONTHLY_EVENT_B);
                    default -> {
                        try {
                            supported.add(ChallengeMode.valueOf(normalized));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }
            return supported;
        }

        if (section.getBoolean("standardEnabled", true)) {
            supported.add(ChallengeMode.DAILY_STANDARD);
        }
        if (section.getBoolean("raceEnabled", false)) {
            supported.add(ChallengeMode.DAILY_RACE_1600);
            supported.add(ChallengeMode.DAILY_RACE_2100);
        }
        if (section.getBoolean("seasonalRaceEnabled", false)) {
            supported.add(ChallengeMode.SEASONAL_RACE);
        }
        if (section.getBoolean("weeklyEnabled", false)) {
            supported.add(ChallengeMode.WEEKLY_STANDARD);
        }
        if (section.getBoolean("monthlyEnabled", false)) {
            supported.add(ChallengeMode.MONTHLY_STANDARD);
        }
        if (section.getBoolean("monthlyEventAEnabled", false)) {
            supported.add(ChallengeMode.MONTHLY_EVENT_A);
        }
        if (section.getBoolean("monthlyEventBEnabled", false)) {
            supported.add(ChallengeMode.MONTHLY_EVENT_B);
        }
        return supported;
    }

    private static ZoneId parseZone(String raw) {
        try {
            return ZoneId.of(raw);
        } catch (Exception ignored) {
            return ZoneId.of("Europe/Rome");
        }
    }

    private static LocalTime parseLocalTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] split = raw.split(":");
        if (split.length != 2) {
            return null;
        }
        try {
            int hour = Integer.parseInt(split[0].trim());
            int minute = Integer.parseInt(split[1].trim());
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return null;
            }
            return LocalTime.of(hour, minute);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public ZoneId timezone() {
        return timezone;
    }

    public int schedulerTickSeconds() {
        return schedulerTickSeconds;
    }

    public int playtimeTickMinutes() {
        return playtimeTickMinutes;
    }

    public int dailyStandardCount() {
        return dailyStandardCount;
    }

    public int weeklyStandardCount() {
        return weeklyStandardCount;
    }

    public int monthlyStandardCount() {
        return monthlyStandardCount;
    }

    public int seasonalRaceCount() {
        return seasonalRaceCount;
    }

    public int seasonalActICount() {
        return seasonalActICount;
    }

    public int seasonalActIICount() {
        return seasonalActIICount;
    }

    public int seasonalActIIICount() {
        return seasonalActIIICount;
    }

    public int seasonalEliteCount() {
        return seasonalEliteCount;
    }

    public int seasonalHiddenRelicCount() {
        return seasonalHiddenRelicCount;
    }

    public int seasonalSecretHardCap() {
        return seasonalSecretHardCap;
    }

    public int eventsQuestCount() {
        return eventsQuestCount;
    }

    public int monthlyEventCount() {
        return monthlyEventCount;
    }

    public List<LocalTime> raceWindows() {
        return raceWindows;
    }

    public LocalTime weeklyRollover() {
        return weeklyRollover;
    }

    public LocalTime monthlyRollover() {
        return monthlyRollover;
    }

    public LocalTime seasonalRollover() {
        return seasonalRollover;
    }

    public Map<String, ChallengeDefinition> definitions() {
        return definitions;
    }

    public AntiAbuseSettings antiAbuse() {
        return antiAbuse;
    }

    public Map<String, ChallengeRewardBundle> rewardBundles() {
        return rewardBundles;
    }

    public TemplateFormatterSettings templateFormatter() {
        return templateFormatter;
    }

    public DifficultyResolverSettings difficultyResolver() {
        return difficultyResolver;
    }

    public SpecificTargetScalingSettings specificTargetScaling() {
        return specificTargetScaling;
    }

    public TargetScalingSettings targetScaling() {
        return targetScaling;
    }

    public XpRangesSettings xpRanges() {
        return xpRanges;
    }

    public FairnessSettings fairness() {
        return fairness;
    }

    public DualThresholdSettings dualThreshold() {
        return dualThreshold;
    }

    public PersonalRewardSettings personalRewards() {
        return personalRewards;
    }

    public StreakSettings streak() {
        return streak;
    }

    public MonthlyEventSettings monthlyEvents() {
        return monthlyEvents;
    }

    public EventRewardsSettings eventRewards() {
        return eventRewards;
    }

    public EventRewardsSettings raceRewards() {
        return raceRewards;
    }

    public CurationSettings curation() {
        return curation;
    }

    public BoardGeneratorSettings boardGenerator() {
        return boardGenerator;
    }

    public GovernanceSettings governance() {
        return governance;
    }

    public M6Settings m6() {
        return m6;
    }

    public MilestoneSettings milestones() {
        return milestones;
    }

    public Map<ChallengeObjectiveType, TaxonomyPolicySettings> taxonomyPolicies() {
        return taxonomyPolicies;
    }

    public ChallengeRewardBundle rewardBundle(String id) {
        if (id == null || id.isBlank()) {
            return ChallengeRewardBundle.empty("-");
        }
        return rewardBundles.getOrDefault(id, ChallengeRewardBundle.empty(id));
    }

    private record VariantSettings(
            ChallengeVariantMode mode,
            double specificChance,
            Map<ChallengeMode, Double> specificChanceByCycle,
            ChallengeFocusType focusType,
            List<ChallengeFocusOption> focusPool
    ) {
    }

    public record AntiAbuseSettings(
            int throttleMs,
            double playerContributionCapRatio,
            int afkIdleSeconds,
            Set<String> denyMobSpawnReasons,
            int placedBlockTtlHours,
            int placedBlockMaxEntries,
            boolean dailyTownCategoryCapEnabled,
            double dailyTownCategoryCapRatio,
            boolean denyOnGuardError,
            Set<ChallengeObjectiveType> sampledObjectives,
            List<String> structureLootTableAllowPrefixes,
            Set<String> explorationStructureAllowlist,
            boolean explorationAdvancementFallbackEnabled,
            SuspicionSettings suspicion,
            Map<String, ObjectiveFamilyProfile> familyProfiles
    ) {
        static AntiAbuseSettings defaults() {
            return new AntiAbuseSettings(
                    500,
                    0.35D,
                    120,
                    Set.of("SPAWNER", "SPAWN_EGG", "CUSTOM"),
                    24,
                    250000,
                    true,
                    2.0D,
                    true,
                    Set.of(
                            ChallengeObjectiveType.PLAYTIME_MINUTES,
                            ChallengeObjectiveType.TRANSPORT_DISTANCE,
                            ChallengeObjectiveType.VILLAGER_TRADE,
                            ChallengeObjectiveType.ECONOMY_ADVANCED
                    ),
                    List.of("minecraft:chests/"),
                    Set.of(
                            "minecraft:fortress",
                            "minecraft:bastion_remnant",
                            "minecraft:end_city",
                            "minecraft:ancient_city",
                            "minecraft:jungle_pyramid",
                            "minecraft:desert_pyramid",
                            "minecraft:stronghold",
                            "minecraft:mansion",
                            "minecraft:monument",
                            "minecraft:trial_chambers",
                            "minecraft:trail_ruins",
                            "minecraft:shipwreck",
                            "minecraft:ruined_portal",
                            "minecraft:village_plains",
                            "minecraft:village_desert",
                            "minecraft:village_savanna",
                            "minecraft:village_snowy",
                            "minecraft:village_taiga"
                    ),
                    false,
                    SuspicionSettings.defaults(),
                    ObjectiveFamilyProfile.defaultsBalanced(500, 0.35D)
            );
        }

        public boolean isSampledAllowed(ChallengeObjectiveType type) {
            return type != null && sampledObjectives != null && sampledObjectives.contains(type);
        }

        public ObjectiveFamilyProfile profileForFamily(String family) {
            if (familyProfiles == null || familyProfiles.isEmpty()) {
                return ObjectiveFamilyProfile.defaultProfile(throttleMs, playerContributionCapRatio);
            }
            if (family == null || family.isBlank()) {
                return familyProfiles.getOrDefault("social_coop", ObjectiveFamilyProfile.defaultProfile(throttleMs, playerContributionCapRatio));
            }
            return familyProfiles.getOrDefault(
                    family.trim().toLowerCase(Locale.ROOT),
                    ObjectiveFamilyProfile.defaultProfile(throttleMs, playerContributionCapRatio)
            );
        }
    }

    public record SuspicionSettings(
            boolean enabled,
            int warningThreshold,
            int clampThreshold,
            int reviewThreshold,
            int decayIntervalMinutes,
            int decayAmount
    ) {
        static SuspicionSettings defaults() {
            return new SuspicionSettings(true, 8, 15, 25, 30, 1);
        }
    }

    public record ObjectiveFamilyProfile(
            int throttleMs,
            double softCapRatio,
            double hardCapRatio,
            double clampRatio,
            Set<String> denySources,
            int suspicionOnThrottle,
            int suspicionOnDenySource,
            int suspicionOnCap,
            int suspicionOnSoftClamp,
            int suspicionOnDedupe
    ) {
        static ObjectiveFamilyProfile defaultProfile(int throttleMs, double capRatio) {
            double safeHard = Math.max(0.1D, capRatio);
            double safeSoft = Math.max(0.05D, Math.min(safeHard, safeHard * 0.8D));
            return new ObjectiveFamilyProfile(
                    Math.max(0, throttleMs),
                    safeSoft,
                    safeHard,
                    0.50D,
                    Set.of("SPAWNER", "SPAWN_EGG", "CUSTOM"),
                    1,
                    2,
                    2,
                    1,
                    1
            );
        }

        static Map<String, ObjectiveFamilyProfile> defaultsBalanced(int throttleMs, double capRatio) {
            Map<String, ObjectiveFamilyProfile> map = new LinkedHashMap<>();
            map.put("resource_grind", new ObjectiveFamilyProfile(
                    Math.max(100, throttleMs),
                    Math.max(0.10D, capRatio * 0.75D),
                    Math.max(0.12D, capRatio),
                    0.50D,
                    Set.of("SPAWNER", "SPAWN_EGG", "CUSTOM"),
                    1,
                    2,
                    2,
                    1,
                    1
            ));
            map.put("mob_combat", new ObjectiveFamilyProfile(
                    Math.max(250, throttleMs),
                    Math.max(0.10D, capRatio * 0.80D),
                    Math.max(0.12D, capRatio),
                    0.60D,
                    Set.of("SPAWNER", "SPAWN_EGG", "CUSTOM"),
                    1,
                    2,
                    2,
                    1,
                    1
            ));
            map.put("structure_loot", new ObjectiveFamilyProfile(
                    Math.max(350, throttleMs),
                    Math.max(0.08D, capRatio * 0.70D),
                    Math.max(0.10D, capRatio * 0.90D),
                    0.50D,
                    Set.of(),
                    1,
                    1,
                    2,
                    1,
                    2
            ));
            map.put("sampled_passive", new ObjectiveFamilyProfile(
                    Math.max(500, throttleMs),
                    Math.max(0.08D, capRatio * 0.65D),
                    Math.max(0.10D, capRatio * 0.85D),
                    0.50D,
                    Set.of(),
                    1,
                    1,
                    2,
                    1,
                    1
            ));
            map.put("social_coop", new ObjectiveFamilyProfile(
                    Math.max(150, throttleMs),
                    Math.max(0.10D, capRatio * 0.90D),
                    Math.max(0.12D, capRatio),
                    0.70D,
                    Set.of(),
                    1,
                    1,
                    1,
                    1,
                    1
            ));
            return Map.copyOf(map);
        }
    }

    public record TemplateFormatterSettings(
            boolean enabled,
            long randomRangeMinAllowed,
            long randomRangeMaxAllowed
    ) {
        static TemplateFormatterSettings defaults() {
            return new TemplateFormatterSettings(true, 0L, 1_000_000L);
        }
    }

    public record DifficultyResolverSettings(
            DifficultyThreshold daily,
            DifficultyThreshold weekly,
            DifficultyThreshold monthly,
            double easyMultiplier,
            double normalMultiplier,
            double hardMultiplier
    ) {
        static DifficultyResolverSettings defaults() {
            return new DifficultyResolverSettings(
                    new DifficultyThreshold(250, 500),
                    new DifficultyThreshold(900, 1800),
                    new DifficultyThreshold(3000, 6000),
                    1.0D,
                    1.25D,
                    1.5D
            );
        }
    }

    public record SpecificTargetScalingSettings(
            double commonMultiplier,
            double uncommonMultiplier,
            double rareMultiplier
    ) {
        static SpecificTargetScalingSettings defaults() {
            return new SpecificTargetScalingSettings(1.0D, 0.8D, 0.6D);
        }

        public double multiplierFor(ChallengeFocusRarity rarity) {
            if (rarity == null) {
                return commonMultiplier;
            }
            return switch (rarity) {
                case COMMON -> commonMultiplier;
                case UNCOMMON -> uncommonMultiplier;
                case RARE -> rareMultiplier;
            };
        }
    }

    public record DifficultyThreshold(
            int normalTarget,
            int hardTarget
    ) {
    }

    public record CitySizeBracket(
            int minMembers,
            int maxMembers,
            double multiplier
    ) {
    }

    public record ServerWideOnlineScaling(
            boolean enabled,
            double minFactor,
            double maxFactor,
            double baseFactor,
            double playersDivisor
    ) {
        static ServerWideOnlineScaling defaults() {
            return new ServerWideOnlineScaling(true, 0.85D, 2.00D, 0.70D, 30.0D);
        }
    }

    public record TargetScalingSettings(
            List<CitySizeBracket> cityMembersBrackets,
            ServerWideOnlineScaling serverWideOnline
    ) {
        static TargetScalingSettings defaults() {
            return new TargetScalingSettings(
                    List.copyOf(defaultCityBrackets()),
                    ServerWideOnlineScaling.defaults()
            );
        }

        static List<CitySizeBracket> defaultCityBrackets() {
            return List.of(
                    new CitySizeBracket(1, 2, 0.60D),
                    new CitySizeBracket(3, 5, 0.80D),
                    new CitySizeBracket(6, 9, 1.00D),
                    new CitySizeBracket(10, 13, 1.20D),
                    new CitySizeBracket(14, 19, 1.45D),
                    new CitySizeBracket(20, Integer.MAX_VALUE, 1.70D)
            );
        }
    }

    public record XpRange(
            int min,
            int max
    ) {
    }

    public record XpRangesSettings(
            XpRange dailyCompletion,
            XpRange dailyRaceWinner,
            XpRange weeklyCompletion,
            XpRange monthlyCompletion,
            XpRange monthlyEventCompletion,
            double excellenceRatio,
            int excellenceMin
    ) {
        static XpRangesSettings defaults() {
            return new XpRangesSettings(
                    new XpRange(12, 28),
                    new XpRange(35, 60),
                    new XpRange(30, 85),
                    new XpRange(90, 140),
                    new XpRange(110, 190),
                    0.35D,
                    6
            );
        }
    }

    public record FairnessBracket(
            int minContributors,
            int maxContributors,
            double multiplier
    ) {
    }

    public record FairnessSettings(
            int activeContributorsWindowDays,
            List<FairnessBracket> brackets
    ) {
        static FairnessSettings defaults() {
            return new FairnessSettings(7, List.copyOf(defaultBrackets()));
        }

        static List<FairnessBracket> defaultBrackets() {
            return List.of(
                    new FairnessBracket(1, 5, 0.70D),
                    new FairnessBracket(6, 10, 1.00D),
                    new FairnessBracket(11, 20, 1.35D),
                    new FairnessBracket(21, Integer.MAX_VALUE, 1.65D)
            );
        }
    }

    public record DualThresholdSettings(
            double excellenceMultiplier
    ) {
        static DualThresholdSettings defaults() {
            return new DualThresholdSettings(1.40D);
        }
    }

    public record PersonalRewardSettings(
            double minContributionRatio,
            int thresholdContributionAbsolute
    ) {
        static PersonalRewardSettings defaults() {
            return new PersonalRewardSettings(0.05D, 0);
        }
    }

    public record StreakSettings(
            boolean enabled,
            Map<Integer, Double> bonusLadder
    ) {
        static StreakSettings defaults() {
            return new StreakSettings(true, Map.copyOf(defaultLadder()));
        }

        static Map<Integer, Double> defaultLadder() {
            Map<Integer, Double> ladder = new LinkedHashMap<>();
            ladder.put(1, 0.0D);
            ladder.put(2, 0.05D);
            ladder.put(3, 0.10D);
            ladder.put(4, 0.20D);
            return ladder;
        }

        public double bonusForStreak(int streakCount) {
            if (!enabled || streakCount <= 0 || bonusLadder == null || bonusLadder.isEmpty()) {
                return 0.0D;
            }
            int bestKey = 1;
            double bestValue = 0.0D;
            for (Map.Entry<Integer, Double> entry : bonusLadder.entrySet()) {
                int key = entry.getKey() == null ? 0 : entry.getKey();
                if (key <= 0 || key > streakCount) {
                    continue;
                }
                if (key >= bestKey) {
                    bestKey = key;
                    bestValue = Math.max(0.0D, entry.getValue() == null ? 0.0D : entry.getValue());
                }
            }
            return bestValue;
        }
    }

    public record MonthlyEventSettings(
            boolean enabled,
            int countPerWindow,
            LocalTime windowAStartAt,
            LocalTime windowBStartAt
    ) {
        static MonthlyEventSettings defaults() {
            return new MonthlyEventSettings(true, 1, LocalTime.of(0, 15), LocalTime.of(0, 15));
        }
    }

    public record EventRewardsSettings(
            boolean top1Enabled,
            boolean top2Enabled,
            boolean top3Enabled,
            boolean thresholdEnabled,
            int thresholdTargetProgress,
            ChallengeRewardSpec top1Reward,
            ChallengeRewardSpec top2Reward,
            ChallengeRewardSpec top3Reward,
            ChallengeRewardSpec thresholdReward
    ) {
        static EventRewardsSettings defaults() {
            return new EventRewardsSettings(
                    true,
                    true,
                    true,
                    true,
                    1,
                    ChallengeRewardSpec.EMPTY,
                    ChallengeRewardSpec.EMPTY,
                    ChallengeRewardSpec.EMPTY,
                    ChallengeRewardSpec.EMPTY
            );
        }

        static EventRewardsSettings raceDefaults() {
            return new EventRewardsSettings(
                    true,
                    true,
                    true,
                    true,
                    1,
                    ChallengeRewardSpec.EMPTY,
                    ChallengeRewardSpec.EMPTY,
                    ChallengeRewardSpec.EMPTY,
                    ChallengeRewardSpec.EMPTY
            );
        }
    }

    public record BundleConstraints(
            String slotA,
            String slotB,
            String slotC
    ) {
        static BundleConstraints defaultsDaily() {
            return new BundleConstraints("easy", "medium", "off_routine");
        }

        static BundleConstraints defaultsRace() {
            return new BundleConstraints("combat", "mining", "farming");
        }

        static BundleConstraints defaultsWeekly() {
            return new BundleConstraints("production", "combat_exploration", "social_economy");
        }

        static BundleConstraints defaultsMonthly() {
            return new BundleConstraints("epic", "support", "support");
        }

        public List<String> slots() {
            return List.of(slotA, slotB, slotC);
        }
    }

    public record CurationSettings(
            int definitionCooldownCycles,
            int categoryCooldownCycles,
            int focusCooldownCycles,
            int familyCooldownCycles,
            BundleConstraints dailyBundle,
            BundleConstraints raceBundle,
            BundleConstraints weeklyBundle,
            BundleConstraints monthlyBundle
    ) {
        static CurationSettings defaults() {
            return new CurationSettings(
                    2,
                    2,
                    2,
                    2,
                    BundleConstraints.defaultsDaily(),
                    BundleConstraints.defaultsRace(),
                    BundleConstraints.defaultsWeekly(),
                    BundleConstraints.defaultsMonthly()
            );
        }
    }

    public record TargetBracketSettings(
            int min,
            int max,
            int step
    ) {
    }

    public record ObjectiveExceptionSettings(
            TargetBracketSettings daily,
            TargetBracketSettings weekly,
            TargetBracketSettings monthlySide,
            TargetBracketSettings monthlyGrand,
            TargetBracketSettings race
    ) {
        public TargetBracketSettings bracketFor(ChallengeMode mode) {
            if (mode == null) {
                return null;
            }
            return switch (mode) {
                case DAILY_STANDARD -> daily;
                case WEEKLY_STANDARD -> weekly;
                case MONTHLY_STANDARD -> monthlySide;
                case MONTHLY_LEDGER_CONTRACT, MONTHLY_LEDGER_MYSTERY -> monthlySide;
                case MONTHLY_LEDGER_GRAND -> monthlyGrand;
                case MONTHLY_EVENT_A, MONTHLY_EVENT_B, MONTHLY_CROWN -> monthlyGrand;
                case DAILY_SPRINT_1830, DAILY_SPRINT_2130, DAILY_RACE_1600, DAILY_RACE_2100, WEEKLY_CLASH,
                        SEASONAL_RACE -> race;
                case SEASON_CODEX_ACT_I, SEASON_CODEX_ACT_II, SEASON_CODEX_ACT_III,
                        SEASON_CODEX_ELITE, SEASON_CODEX_HIDDEN_RELIC -> monthlyGrand;
            };
        }

        public boolean hasAny() {
            return daily != null || weekly != null || monthlySide != null || monthlyGrand != null || race != null;
        }
    }

    public record BoardTargetFormulaSettings(
            Map<PrincipalStage, Double> stageMultipliers,
            double dailyMultiplier,
            double raceMultiplier,
            double weeklyMultiplier,
            double monthlySideMultiplier,
            double monthlyGrandMultiplier,
            double seasonalMultiplier,
            Map<ChallengeObjectiveType, ObjectiveExceptionSettings> exceptions
    ) {
        static BoardTargetFormulaSettings defaults() {
            Map<PrincipalStage, Double> stage = new EnumMap<>(PrincipalStage.class);
            stage.put(PrincipalStage.AVAMPOSTO, 1.00D);
            stage.put(PrincipalStage.BORGO, 1.35D);
            stage.put(PrincipalStage.VILLAGGIO, 1.75D);
            stage.put(PrincipalStage.CITTADINA, 2.25D);
            stage.put(PrincipalStage.CITTA, 2.80D);
            stage.put(PrincipalStage.REGNO, 3.50D);

            Map<ChallengeObjectiveType, ObjectiveExceptionSettings> exceptions = new EnumMap<>(ChallengeObjectiveType.class);
            exceptions.put(
                    ChallengeObjectiveType.STRUCTURE_DISCOVERY,
                    new ObjectiveExceptionSettings(
                            new TargetBracketSettings(1, 1, 1),
                            new TargetBracketSettings(1, 2, 1),
                            new TargetBracketSettings(2, 4, 1),
                            new TargetBracketSettings(3, 6, 1),
                            new TargetBracketSettings(1, 1, 1)
                    )
            );
            exceptions.put(
                    ChallengeObjectiveType.TRIAL_VAULT_OPEN,
                    new ObjectiveExceptionSettings(
                            new TargetBracketSettings(1, 1, 1),
                            new TargetBracketSettings(1, 3, 1),
                            new TargetBracketSettings(2, 6, 1),
                            new TargetBracketSettings(3, 8, 1),
                            new TargetBracketSettings(1, 2, 1)
                    )
            );
            exceptions.put(
                    ChallengeObjectiveType.RAID_WIN,
                    new ObjectiveExceptionSettings(
                            new TargetBracketSettings(1, 1, 1),
                            new TargetBracketSettings(1, 2, 1),
                            new TargetBracketSettings(1, 3, 1),
                            new TargetBracketSettings(2, 4, 1),
                            new TargetBracketSettings(1, 1, 1)
                    )
            );
            exceptions.put(
                    ChallengeObjectiveType.BOSS_KILL,
                    new ObjectiveExceptionSettings(
                            new TargetBracketSettings(1, 1, 1),
                            new TargetBracketSettings(1, 1, 1),
                            new TargetBracketSettings(1, 2, 1),
                            new TargetBracketSettings(2, 3, 1),
                            new TargetBracketSettings(1, 1, 1)
                    )
            );
            return new BoardTargetFormulaSettings(
                    Map.copyOf(stage),
                    1.0D,
                    3.0D,
                    7.0D,
                    25.0D,
                    25.0D,
                    50.0D,
                    Map.copyOf(exceptions)
            );
        }

        public double multiplierForMode(ChallengeMode mode) {
            if (mode == null) {
                return 1.0D;
            }
            return switch (mode) {
                case DAILY_STANDARD -> dailyMultiplier;
                case DAILY_SPRINT_1830, DAILY_SPRINT_2130, DAILY_RACE_1600, DAILY_RACE_2100 -> raceMultiplier;
                case WEEKLY_STANDARD, WEEKLY_CLASH -> weeklyMultiplier;
                case MONTHLY_STANDARD -> monthlySideMultiplier;
                case MONTHLY_LEDGER_CONTRACT, MONTHLY_LEDGER_MYSTERY -> monthlySideMultiplier;
                case MONTHLY_LEDGER_GRAND, MONTHLY_EVENT_A, MONTHLY_EVENT_B, MONTHLY_CROWN -> monthlyGrandMultiplier;
                case SEASONAL_RACE, SEASON_CODEX_ACT_I, SEASON_CODEX_ACT_II, SEASON_CODEX_ACT_III,
                        SEASON_CODEX_ELITE, SEASON_CODEX_HIDDEN_RELIC -> seasonalMultiplier;
            };
        }
    }

    public record BoardWeightsSettings(
            double seasonThemeWeight,
            double atlasLowestWeight,
            double cityAffinity14dWeight
    ) {
        static BoardWeightsSettings defaults() {
            return new BoardWeightsSettings(0.50D, 0.30D, 0.20D);
        }
    }

    public record BoardHardRulesSettings(
            boolean noConsecutiveFamily,
            int maxPassivePerBoard,
            int minSkillfulPerBoard,
            boolean banDailyPlaytimeXpPair,
            boolean excludeEconomyFromRace,
            int resourceContributionWindowDays,
            int resourceContributionMaxInWindow
    ) {
        static BoardHardRulesSettings defaults() {
            return new BoardHardRulesSettings(
                    true,
                    1,
                    1,
                    true,
                    true,
                    7,
                    1
            );
        }
    }

    public record SeasonThemeSettings(
            Set<AtlasChapter> preferredChapters,
            Set<String> preferredFamilies,
            Double seasonThemeWeightOverride,
            Double atlasLowestWeightOverride,
            Double cityAffinityWeightOverride
    ) {
    }

    public record BoardGeneratorSettings(
            BoardTargetFormulaSettings targetFormula,
            BoardWeightsSettings weights,
            BoardHardRulesSettings hardRules,
            Map<ChallengeSeasonResolver.Season, SeasonThemeSettings> seasonThemes
    ) {
        static BoardGeneratorSettings defaults() {
            return new BoardGeneratorSettings(
                    BoardTargetFormulaSettings.defaults(),
                    BoardWeightsSettings.defaults(),
                    BoardHardRulesSettings.defaults(),
                    Map.of()
            );
        }
    }

    public record GovernanceSettings(
            boolean rerollWeeklyEnabled,
            int rerollWeeklyMaxPerCycle,
            boolean seasonalVetoEnabled,
            int seasonalVetoMaxCategories,
            boolean seasonalVetoImmutable
    ) {
        static GovernanceSettings defaults() {
            return new GovernanceSettings(true, 1, true, 1, true);
        }
    }

    public record M6BoardLocksSettings(
            int dailyStandardCount,
            int weeklyStandardCount
    ) {
        static M6BoardLocksSettings defaults() {
            return new M6BoardLocksSettings(8, 18);
        }
    }

    public record M6FirstCompletionDailySettings(
            boolean enabled,
            int maxClaims,
            ChallengeRewardSpec reward
    ) {
        static ChallengeRewardSpec defaultReward() {
            return new ChallengeRewardSpec(
                    10.0D,
                    0.0D,
                    List.of(),
                    List.of(),
                    Map.of(Material.IRON_INGOT, 8),
                    0.0D
            );
        }

        static M6FirstCompletionDailySettings defaults() {
            return new M6FirstCompletionDailySettings(true, 2, defaultReward());
        }
    }

    public record M6FirstCompletionWeeklySettings(
            boolean enabled,
            ChallengeRewardSpec reward
    ) {
        static ChallengeRewardSpec defaultReward() {
            return new ChallengeRewardSpec(
                    14.0D,
                    0.0D,
                    List.of(),
                    List.of(),
                    Map.of(),
                    0.0D
            );
        }

        static M6FirstCompletionWeeklySettings defaults() {
            return new M6FirstCompletionWeeklySettings(true, defaultReward());
        }
    }

    public record M6FirstCompletionSettings(
            M6FirstCompletionDailySettings daily,
            M6FirstCompletionWeeklySettings weekly
    ) {
        static M6FirstCompletionSettings defaults() {
            return new M6FirstCompletionSettings(
                    M6FirstCompletionDailySettings.defaults(),
                    M6FirstCompletionWeeklySettings.defaults()
            );
        }
    }

    public record M6Settings(
            M6BoardLocksSettings boardLocks,
            M6FirstCompletionSettings firstCompletion
    ) {
        static M6Settings defaults() {
            return new M6Settings(
                    M6BoardLocksSettings.defaults(),
                    M6FirstCompletionSettings.defaults()
            );
        }
    }

    public record TaxonomyPolicySettings(
            ChallengeProgressionRole progressionRole,
            Set<ChallengeMode> allowedModes,
            Boolean raceEligible,
            Boolean cityXpEnabled
    ) {
    }

    public record MilestoneSettings(
            int weeklyMilestone1At,
            int weeklyMilestone2At,
            int weeklyMilestone3At,
            int seasonalFinalAt,
            ChallengeRewardSpec weeklyMilestone1Reward,
            ChallengeRewardSpec weeklyMilestone2Reward,
            ChallengeRewardSpec weeklyMilestone3Reward,
            ChallengeRewardSpec seasonalFinalReward
    ) {
        static MilestoneSettings defaults(Plugin plugin) {
            return new MilestoneSettings(
                    1, 2, 3, 6,
                    parseRewardSpec(plugin, null),
                    parseRewardSpec(plugin, null),
                    parseRewardSpec(plugin, null),
                    parseRewardSpec(plugin, null)
            );
        }
    }
}
