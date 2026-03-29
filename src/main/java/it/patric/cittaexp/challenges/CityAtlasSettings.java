package it.patric.cittaexp.challenges;

import it.patric.cittaexp.levels.PrincipalStage;
import it.patric.cittaexp.utils.YamlResourceBackfill;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class CityAtlasSettings {

    public record ChapterMeta(String displayName, Material icon) {
    }

    private final boolean enabled;
    private final boolean tierIvEnabled;
    private final boolean interimSealFallbackEnabled;
    private final String cutoverFlagKey;
    private final Map<AtlasChapter, ChapterMeta> chapterMeta;
    private final Map<String, AtlasFamilyDefinition> families;
    private final StoryModeConfig storyMode;

    private CityAtlasSettings(
            boolean enabled,
            boolean tierIvEnabled,
            boolean interimSealFallbackEnabled,
            String cutoverFlagKey,
            Map<AtlasChapter, ChapterMeta> chapterMeta,
            Map<String, AtlasFamilyDefinition> families,
            StoryModeConfig storyMode
    ) {
        this.enabled = enabled;
        this.tierIvEnabled = tierIvEnabled;
        this.interimSealFallbackEnabled = interimSealFallbackEnabled;
        this.cutoverFlagKey = cutoverFlagKey;
        this.chapterMeta = chapterMeta;
        this.families = families;
        this.storyMode = storyMode == null ? StoryModeConfig.DISABLED : storyMode;
    }

    public static CityAtlasSettings load(Plugin plugin) {
        YamlResourceBackfill.ensure(plugin, "atlas.yml");
        File file = new File(plugin.getDataFolder(), "atlas.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        boolean enabled = config.getBoolean("enabled", true);
        boolean tierIvEnabled = config.getBoolean("tier4.enabled", false);
        boolean interimFallback = config.getBoolean("seals.interimFallbackEnabled", false);
        String cutoverFlagKey = config.getString("cutover.flagKey", "atlas.m3m4.cutover.v1");

        Map<AtlasChapter, ChapterMeta> chapterMeta = parseChapterMeta(config.getConfigurationSection("chapters"));
        Map<String, ChallengeRewardSpec> rewardProfiles = parseRewardProfiles(
                plugin,
                config.getConfigurationSection("rewardProfiles")
        );
        Map<String, AtlasFamilyDefinition> families = parseFamilies(
                plugin,
                config.getConfigurationSection("families"),
                rewardProfiles,
                tierIvEnabled
        );
        StoryModeConfig storyMode = parseStoryMode(
                plugin,
                config.getConfigurationSection("storyMode"),
                rewardProfiles,
                families
        );

        return new CityAtlasSettings(
                enabled,
                tierIvEnabled,
                interimFallback,
                cutoverFlagKey == null || cutoverFlagKey.isBlank() ? "atlas.m3m4.cutover.v1" : cutoverFlagKey,
                Map.copyOf(chapterMeta),
                Map.copyOf(families),
                storyMode
        );
    }

    private static Map<AtlasChapter, ChapterMeta> parseChapterMeta(ConfigurationSection section) {
        Map<AtlasChapter, ChapterMeta> meta = new EnumMap<>(AtlasChapter.class);
        for (AtlasChapter chapter : AtlasChapter.values()) {
            String path = chapter.name().toLowerCase(Locale.ROOT);
            String displayName = chapter.defaultDisplayName();
            Material icon = chapter.defaultIcon();
            if (section != null) {
                ConfigurationSection row = section.getConfigurationSection(path);
                if (row != null) {
                    String configuredName = row.getString("displayName");
                    if (configuredName != null && !configuredName.isBlank()) {
                        displayName = configuredName;
                    }
                    Material parsed = parseMaterial(row.getString("icon"));
                    if (parsed != null) {
                        icon = parsed;
                    }
                }
            }
            meta.put(chapter, new ChapterMeta(displayName, icon));
        }
        return meta;
    }

    private static Map<String, AtlasFamilyDefinition> parseFamilies(
            Plugin plugin,
            ConfigurationSection section,
            Map<String, ChallengeRewardSpec> rewardProfiles,
            boolean tierIvEnabled
    ) {
        Map<String, AtlasFamilyDefinition> families = new LinkedHashMap<>();
        if (section == null) {
            return families;
        }
        visitNestedSections(section, "", (id, row) -> {
            if (!row.contains("chapter") && !row.contains("objectiveType")) {
                return;
            }
            AtlasChapter chapter = AtlasChapter.fromString(row.getString("chapter"));
            ChallengeObjectiveType objectiveType = ChallengeObjectiveType.fromId(row.getString("objectiveType"));
            if (chapter == null || objectiveType == null) {
                plugin.getLogger().warning("[atlas] famiglia ignorata id=" + id + " (chapter/objective invalidi)");
                return;
            }
            String displayName = row.getString("displayName", toDisplayName(id));
            PrincipalStage minStage = parsePrincipalStage(row.getString("minStage"));
            ChallengeFocusType focusType = parseFocusType(row.getString("focusType"));
            Set<String> focusKeys = parseFocusKeys(row.getStringList("focusKeys"));
            Map<AtlasTier, Integer> targets = parseTargets(row.getConfigurationSection("targets"), tierIvEnabled);

            String defaultProfile = row.getString("defaultRewardProfile", "tier_i");
            ConfigurationSection rewardByTierSection = row.getConfigurationSection("rewardProfileByTier");
            Map<AtlasTier, ChallengeRewardSpec> rewardByTier = new EnumMap<>(AtlasTier.class);
            for (AtlasTier tier : AtlasTier.values()) {
                if (tier == AtlasTier.IV_ELITE && !tierIvEnabled) {
                    continue;
                }
                String key = defaultProfile;
                if (rewardByTierSection != null) {
                    String override = rewardByTierSection.getString(tier.name());
                    if (override != null && !override.isBlank()) {
                        key = override;
                    }
                }
                rewardByTier.put(tier, rewardProfiles.getOrDefault(key, ChallengeRewardSpec.EMPTY));
            }

            boolean enabled = row.getBoolean("enabled", true);
            AtlasFamilyDefinition definition = new AtlasFamilyDefinition(
                    id,
                    displayName,
                    chapter,
                    objectiveType,
                    focusType,
                    focusKeys,
                    minStage,
                    targets,
                    rewardByTier,
                    enabled
            );
            families.put(id, definition);
        });
        return families;
    }

    private static Map<AtlasTier, Integer> parseTargets(ConfigurationSection section, boolean tierIvEnabled) {
        Map<AtlasTier, Integer> map = new EnumMap<>(AtlasTier.class);
        if (section == null) {
            return map;
        }
        for (AtlasTier tier : AtlasTier.values()) {
            if (tier == AtlasTier.IV_ELITE && !tierIvEnabled) {
                continue;
            }
            int value = Math.max(0, section.getInt(tier.name(), 0));
            if (value > 0) {
                map.put(tier, value);
            }
        }
        return map;
    }

    private static Set<String> parseFocusKeys(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalized.add(value.trim().toUpperCase(Locale.ROOT));
        }
        return Set.copyOf(normalized);
    }

    private static ChallengeFocusType parseFocusType(String raw) {
        if (raw == null || raw.isBlank()) {
            return ChallengeFocusType.NONE;
        }
        try {
            return ChallengeFocusType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ChallengeFocusType.NONE;
        }
    }

    private static PrincipalStage parsePrincipalStage(String raw) {
        if (raw == null || raw.isBlank()) {
            return PrincipalStage.AVAMPOSTO;
        }
        try {
            return PrincipalStage.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return PrincipalStage.AVAMPOSTO;
        }
    }

    private static StoryModeConfig parseStoryMode(
            Plugin plugin,
            ConfigurationSection section,
            Map<String, ChallengeRewardSpec> rewardProfiles,
            Map<String, AtlasFamilyDefinition> families
    ) {
        if (section == null) {
            return StoryModeConfig.DISABLED;
        }
        boolean enabled = section.getBoolean("enabled", true);
        int chapterUnlockEveryLevels = Math.max(1, section.getInt("chapterUnlockEveryLevels", 5));
        int completionGatePercent = Math.max(1, Math.min(100, section.getInt("completionGatePercent", 100)));
        Map<String, StoryModeTaskTemplate> templates = parseStoryTemplates(plugin, section.getConfigurationSection("templates"), families);
        ConfigurationSection chapters = section.getConfigurationSection("chapters");
        List<StoryModeChapterDefinition> baseChapters = parseStoryChapters(
                plugin,
                chapters == null ? null : chapters.getConfigurationSection("base"),
                false,
                rewardProfiles,
                templates
        );
        List<StoryModeChapterDefinition> eliteChapters = parseStoryChapters(
                plugin,
                chapters == null ? null : chapters.getConfigurationSection("elite"),
                true,
                rewardProfiles,
                templates
        );
        return new StoryModeConfig(
                enabled,
                chapterUnlockEveryLevels,
                completionGatePercent,
                Map.copyOf(templates),
                List.copyOf(baseChapters),
                List.copyOf(eliteChapters)
        );
    }

    private static Map<String, StoryModeTaskTemplate> parseStoryTemplates(
            Plugin plugin,
            ConfigurationSection section,
            Map<String, AtlasFamilyDefinition> families
    ) {
        Map<String, StoryModeTaskTemplate> templates = new LinkedHashMap<>();
        if (section == null) {
            return templates;
        }
        visitNestedSections(section, "", (id, row) -> {
            if (!row.contains("type") && !row.contains("familyId") && !row.contains("defenseTier")) {
                return;
            }
            StoryModeStepType stepType = StoryModeStepType.fromString(row.getString("type"));
            StoryModeProofType proofType = StoryModeProofType.fromString(row.getString("proofType"));
            String familyId = normalizeId(row.getString("familyId"));
            AtlasTier atlasTier = parseAtlasTier(row.getString("tier"));
            String defenseTier = normalizeTierId(row.getString("defenseTier"));
            AtlasChapter chapter = AtlasChapter.fromString(row.getString("chapter"));
            if (chapter == null && familyId != null) {
                AtlasFamilyDefinition family = families.get(familyId);
                if (family != null) {
                    chapter = family.chapter();
                }
            }
            if ((familyId == null || atlasTier == null) && defenseTier == null) {
                plugin.getLogger().warning("[story-mode] template ignorato id=" + id + " (nessun requisito valido)");
                return;
            }
            templates.put(id, new StoryModeTaskTemplate(
                    id,
                    row.getString("title", toDisplayName(id)),
                    row.getString("description", toDisplayName(id)),
                    stepType,
                    proofType,
                    chapter,
                    familyId,
                    atlasTier,
                    defenseTier,
                    row.getBoolean("enabled", true)
            ));
        });
        return templates;
    }

    private static void visitNestedSections(
            ConfigurationSection section,
            String prefix,
            java.util.function.BiConsumer<String, ConfigurationSection> consumer
    ) {
        if (section == null || consumer == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null) {
                continue;
            }
            String id = prefix == null || prefix.isBlank() ? key : prefix + '.' + key;
            consumer.accept(id, child);
            visitNestedSections(child, id, consumer);
        }
    }

    private static List<StoryModeChapterDefinition> parseStoryChapters(
            Plugin plugin,
            ConfigurationSection section,
            boolean elite,
            Map<String, ChallengeRewardSpec> rewardProfiles,
            Map<String, StoryModeTaskTemplate> templates
    ) {
        List<StoryModeChapterDefinition> chapters = new ArrayList<>();
        if (section == null) {
            return chapters;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection row = section.getConfigurationSection(key);
            if (row == null) {
                continue;
            }
            String id = row.getString("id", key);
            List<String> tasks = normalizeStringList(row.getStringList("tasks")).stream()
                    .map(CityAtlasSettings::normalizeId)
                    .filter(templateId -> templateId != null && templates.containsKey(templateId))
                    .toList();
            String milestone = normalizeId(row.getString("milestone"));
            String proof = normalizeId(row.getString("proof"));
            if (tasks.size() != 3 || milestone == null || !templates.containsKey(milestone)
                    || proof == null || !templates.containsKey(proof)) {
                plugin.getLogger().warning("[story-mode] chapter ignorato id=" + id + " (tasks/milestone/proof non validi)");
                continue;
            }
            String rewardProfile = row.getString("rewardProfile", elite ? "story_elite" : "story_early");
            chapters.add(new StoryModeChapterDefinition(
                    id,
                    Math.max(1, row.getInt("order", chapters.size() + 1)),
                    row.getString("title", toDisplayName(id)),
                    row.getString("act", elite ? "Elite" : "Story Mode"),
                    elite,
                    Math.max(1, row.getInt("minCityLevel", 1)),
                    AtlasChapter.fromString(row.getString("primaryChapter")),
                    parseAtlasChapterList(row.getStringList("taskFamilies")),
                    row.getString("narrativeBeat", ""),
                    row.getString("signatureGoal", ""),
                    row.getString("chapterProof", ""),
                    row.getString("completionFantasy", ""),
                    List.copyOf(tasks),
                    milestone,
                    proof,
                    rewardProfiles.getOrDefault(rewardProfile, ChallengeRewardSpec.EMPTY)
            ));
        }
        chapters.sort(java.util.Comparator.comparingInt(StoryModeChapterDefinition::order));
        return chapters;
    }

    private static AtlasTier parseAtlasTier(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AtlasTier.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String normalizeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    private static String normalizeTierId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private static List<AtlasChapter> parseAtlasChapterList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<AtlasChapter> chapters = new ArrayList<>();
        for (String value : values) {
            AtlasChapter chapter = AtlasChapter.fromString(value);
            if (chapter != null) {
                chapters.add(chapter);
            }
        }
        return List.copyOf(chapters);
    }

    private static Map<String, ChallengeRewardSpec> parseRewardProfiles(Plugin plugin, ConfigurationSection section) {
        Map<String, ChallengeRewardSpec> profiles = new HashMap<>();
        profiles.put("tier_i", new ChallengeRewardSpec(250, 0.0D, List.of(), List.of(), Map.of(), 0.0D));
        profiles.put("tier_ii", new ChallengeRewardSpec(600, 0.0D, List.of(), List.of(), Map.of(), 0.0D));
        profiles.put("tier_iii", new ChallengeRewardSpec(1400, 0.0D, List.of(), List.of(), Map.of(), 0.0D));
        profiles.put("tier_iv", new ChallengeRewardSpec(3000, 0.0D, List.of(), List.of(), Map.of(), 0.0D));

        if (section == null) {
            return profiles;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection row = section.getConfigurationSection(key);
            if (row == null) {
                continue;
            }
            profiles.put(key, parseRewardSpec(plugin, row));
        }
        return profiles;
    }

    private static ChallengeRewardSpec parseRewardSpec(Plugin plugin, ConfigurationSection section) {
        if (section == null) {
            return ChallengeRewardSpec.EMPTY;
        }
        double xpCity = Math.max(0.0D, section.getDouble("xpCity", 0.0D));
        List<String> commands = normalizeStringList(section.getStringList("consoleCommands"));
        List<String> commandKeys = normalizeStringList(section.getStringList("commandKeys"));
        Map<Material, Integer> vaultItems = parseVaultItems(plugin, section.getConfigurationSection("vaultItems"));
        return new ChallengeRewardSpec(xpCity, 0.0D, commands, commandKeys, vaultItems, 0.0D);
    }

    private static Map<Material, Integer> parseVaultItems(Plugin plugin, ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<Material, Integer> items = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Material material = parseMaterial(key);
            if (material == null) {
                plugin.getLogger().warning("[atlas] materiale reward non valido: " + key);
                continue;
            }
            int amount = Math.max(0, section.getInt(key, 0));
            if (amount <= 0) {
                continue;
            }
            items.merge(material, amount, Integer::sum);
        }
        return Map.copyOf(items);
    }

    private static Material parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
    }

    private static List<String> normalizeStringList(List<String> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String value : input) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalized.add(value.trim());
        }
        return List.copyOf(normalized);
    }

    private static String toDisplayName(String id) {
        if (id == null || id.isBlank()) {
            return "Famiglia";
        }
        String token = id;
        int idx = id.lastIndexOf('.');
        if (idx >= 0 && idx + 1 < id.length()) {
            token = id.substring(idx + 1);
        }
        String[] pieces = token.replace('_', ' ').split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String piece : pieces) {
            if (piece.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(piece.charAt(0)));
            if (piece.length() > 1) {
                builder.append(piece.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.length() == 0 ? "Famiglia" : builder.toString();
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean tierIvEnabled() {
        return tierIvEnabled;
    }

    public boolean interimSealFallbackEnabled() {
        return interimSealFallbackEnabled;
    }

    public String cutoverFlagKey() {
        return cutoverFlagKey;
    }

    public Map<AtlasChapter, ChapterMeta> chapterMeta() {
        return chapterMeta;
    }

    public Map<String, AtlasFamilyDefinition> families() {
        return families;
    }

    public StoryModeConfig storyMode() {
        return storyMode;
    }

    public Map<AtlasChapter, List<AtlasFamilyDefinition>> familiesByChapter() {
        Map<AtlasChapter, List<AtlasFamilyDefinition>> grouped = new EnumMap<>(AtlasChapter.class);
        for (AtlasChapter chapter : AtlasChapter.values()) {
            grouped.put(chapter, new ArrayList<>());
        }
        for (AtlasFamilyDefinition definition : families.values()) {
            if (!definition.enabled()) {
                continue;
            }
            grouped.computeIfAbsent(definition.chapter(), ignored -> new ArrayList<>()).add(definition);
        }
        for (Map.Entry<AtlasChapter, List<AtlasFamilyDefinition>> entry : grouped.entrySet()) {
            entry.setValue(List.copyOf(entry.getValue()));
        }
        return grouped;
    }

    public Set<ChallengeObjectiveType> trackedObjectives() {
        EnumSet<ChallengeObjectiveType> types = EnumSet.noneOf(ChallengeObjectiveType.class);
        for (AtlasFamilyDefinition definition : families.values()) {
            if (definition.enabled() && definition.objectiveType() != null) {
                types.add(definition.objectiveType());
            }
        }
        return Set.copyOf(types);
    }
}
