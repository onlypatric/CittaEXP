package it.patric.cittaexp.custommobs;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

public final class CustomMobRegistry {

    public record LoadResult(
            CustomMobRegistry registry,
            int loadedCount,
            int skippedCount,
            List<String> warnings
    ) {
    }

    private final Map<String, CustomMobSpec> mobs;

    public CustomMobRegistry(Map<String, CustomMobSpec> mobs) {
        this.mobs = Map.copyOf(mobs);
    }

    public static LoadResult load(File directory, MobAbilityRegistry abilityRegistry) {
        return load(directory, abilityRegistry, new MobTraitRegistry(Map.of()), new MobVariantRegistry(Map.of()));
    }

    public static LoadResult load(
            File directory,
            MobAbilityRegistry abilityRegistry,
            MobTraitRegistry traitRegistry,
            MobVariantRegistry variantRegistry
    ) {
        if (!directory.exists() || !directory.isDirectory()) {
            return new LoadResult(new CustomMobRegistry(Map.of()), 0, 0, List.of());
        }
        File[] files = directory.listFiles((dir, name) -> {
            String normalized = name.toLowerCase(Locale.ROOT);
            return normalized.endsWith(".yml") && !normalized.equals("pack-manifest.yml");
        });
        if (files == null || files.length == 0) {
            return new LoadResult(new CustomMobRegistry(Map.of()), 0, 0, List.of());
        }
        Arrays.sort(files);
        Map<String, CustomMobSpec> loaded = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        int skipped = 0;
        for (File file : files) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                CustomMobSpec spec = parseMob(yaml, abilityRegistry, traitRegistry, variantRegistry);
                if (loaded.containsKey(spec.id())) {
                    skipped++;
                    warnings.add(file.getName() + ": duplicate mob id '" + spec.id() + "'");
                    continue;
                }
                loaded.put(spec.id(), spec);
            } catch (Exception exception) {
                skipped++;
                warnings.add(file.getName() + ": " + exception.getMessage());
            }
        }
        return new LoadResult(new CustomMobRegistry(loaded), loaded.size(), skipped, List.copyOf(warnings));
    }

    private static CustomMobSpec parseMob(
            YamlConfiguration yaml,
            MobAbilityRegistry abilityRegistry,
            MobTraitRegistry traitRegistry,
            MobVariantRegistry variantRegistry
    ) {
        String id = CustomMobParsingSupport.requireString(yaml, "id");
        EntityType entityType = CustomMobTypeResolver.parseEntityType(CustomMobParsingSupport.requireString(yaml, "type"));
        if (!entityType.isSpawnable() || !entityType.isAlive()) {
            throw new IllegalArgumentException("entity type is not a living spawnable type: " + entityType);
        }
        String displayName = yaml.getString("name", "").trim();
        boolean showName = yaml.getBoolean("showName", false);
        boolean boss = yaml.getBoolean("boss", false);
        Map<String, Double> attributes = CustomMobParsingSupport.parseAttributes(yaml.getConfigurationSection("stats"));
        Map<org.bukkit.inventory.EquipmentSlot, CustomMobEquipmentItem> equipment = CustomMobParsingSupport.parseEquipment(yaml.getConfigurationSection("equipment"));
        CustomMobFlags flags = parseFlags(yaml.getConfigurationSection("flags"));
        Set<String> tags = CustomMobParsingSupport.parseNormalizedStrings(yaml.getStringList("tags"));

        List<String> mainAbilities = normalizeAbilityList(yaml.getStringList("mainAbilities"));
        List<String> secondaryAbilities = normalizeAbilityList(yaml.getStringList("secondaryAbilities"));
        List<String> passiveAbilities = normalizeAbilityList(yaml.getStringList("passiveAbilities"));
        List<String> legacyOnSpawn = normalizeAbilityList(yaml.getStringList("abilities.onSpawn"));
        if (!legacyOnSpawn.isEmpty()) {
            mainAbilities = mergeUnique(mainAbilities, legacyOnSpawn);
        }

        CustomMobParsingSupport.requireKnownAbilities(mainAbilities, abilityRegistry, "mob mainAbilities");
        CustomMobParsingSupport.requireKnownAbilities(secondaryAbilities, abilityRegistry, "mob secondaryAbilities");
        CustomMobParsingSupport.requireKnownAbilities(passiveAbilities, abilityRegistry, "mob passiveAbilities");

        List<String> traitIds = normalizeAbilityList(yaml.getStringList("traits"));
        for (String traitId : traitIds) {
            if (traitRegistry.get(traitId).isEmpty()) {
                throw new IllegalArgumentException("unknown trait id referenced by mob: " + traitId);
            }
        }
        List<String> variantIds = normalizeAbilityList(yaml.getStringList("variants"));
        for (String variantId : variantIds) {
            if (variantRegistry.get(variantId).isEmpty()) {
                throw new IllegalArgumentException("unknown variant id referenced by mob: " + variantId);
            }
        }

        List<MobPhaseRule> phaseRules = parsePhaseRules(yaml.getMapList("phaseBindings"), abilityRegistry);
        CustomMobBossbarSpec bossbarSpec = parseBossbarSpec(yaml.getConfigurationSection("bossbar"));
        CustomMobLocalBroadcasts localBroadcasts = parseLocalBroadcasts(yaml.getConfigurationSection("localBroadcasts"));
        CustomMobCatalogMetadata metadata = CustomMobParsingSupport.parseCatalogMetadata(
                yaml,
                inferArcId(id, tags),
                inferPools(boss),
                inferThemes(id, tags)
        );

        return new CustomMobSpec(
                id,
                entityType,
                displayName,
                showName,
                attributes,
                equipment,
                flags,
                tags,
                boss,
                mainAbilities,
                secondaryAbilities,
                passiveAbilities,
                traitIds,
                variantIds,
                phaseRules,
                bossbarSpec,
                localBroadcasts,
                metadata
        );
    }

    private static List<String> normalizeAbilityList(List<String> values) {
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalized.add(value.trim());
        }
        return List.copyOf(normalized);
    }

    private static List<String> mergeUnique(List<String> first, List<String> second) {
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        merged.addAll(first);
        merged.addAll(second);
        return List.copyOf(merged);
    }

    private static CustomMobFlags parseFlags(org.bukkit.configuration.ConfigurationSection section) {
        if (section == null) {
            return CustomMobFlags.DEFAULT;
        }
        return new CustomMobFlags(
                section.getBoolean("silent", false),
                section.getBoolean("collidable", true),
                section.getBoolean("invulnerable", false)
        );
    }

    private static List<MobPhaseRule> parsePhaseRules(List<Map<?, ?>> rawRules, MobAbilityRegistry abilityRegistry) {
        if (rawRules == null || rawRules.isEmpty()) {
            return List.of();
        }
        List<MobPhaseRule> rules = new ArrayList<>();
        for (Map<?, ?> rawRule : rawRules) {
            int phaseNumber = Math.max(1, MobAbilityRegistry.getInt(rawRule, "phase", 1));
            String phaseId = MobAbilityRegistry.requireMapString(rawRule, "id");
            String label = MobAbilityRegistry.requireMapString(rawRule, "label");
            double hpBelowRatio = MobAbilityRegistry.getDouble(rawRule, "hpBelow", 1.0D);
            List<String> addAbilities = normalizeAbilityList(stringList(rawRule.get("addAbilities")));
            List<String> removeAbilities = normalizeAbilityList(stringList(rawRule.get("removeAbilities")));
            CustomMobParsingSupport.requireKnownAbilities(addAbilities, abilityRegistry, "phase addAbilities");
            CustomMobParsingSupport.requireKnownAbilities(removeAbilities, abilityRegistry, "phase removeAbilities");
            List<MobActionSpec> entryActions = new ArrayList<>();
            for (Map<?, ?> actionMap : mapList(rawRule.get("entryActions"))) {
                entryActions.add(MobAbilityRegistry.parseAction(actionMap, true));
            }
            String namePrefix = stringValue(rawRule.get("namePrefix"));
            String nameSuffix = stringValue(rawRule.get("nameSuffix"));
            rules.add(new MobPhaseRule(
                    phaseNumber,
                    phaseId,
                    label,
                    hpBelowRatio,
                    addAbilities,
                    removeAbilities,
                    entryActions,
                    namePrefix,
                    nameSuffix
            ));
        }
        rules.sort(java.util.Comparator.comparingInt(MobPhaseRule::phaseNumber));
        if (rules.isEmpty() || rules.getFirst().phaseNumber() != 1) {
            throw new IllegalArgumentException("phaseBindings must start with phase 1");
        }
        return List.copyOf(rules);
    }

    private static CustomMobBossbarSpec parseBossbarSpec(ConfigurationSection section) {
        if (section == null) {
            return CustomMobBossbarSpec.DISABLED;
        }
        boolean enabled = section.getBoolean("enabled", true);
        String title = section.getString("title", "<dark_red>{boss}</dark_red> <gray>{phase}</gray>");
        BossBar.Color color = CustomMobParsingSupport.parseEnum(
                BossBar.Color.class,
                section.getString("color", "RED"),
                "bossbar color"
        );
        BossBar.Overlay overlay = CustomMobParsingSupport.parseEnum(
                BossBar.Overlay.class,
                section.getString("overlay", "PROGRESS"),
                "bossbar overlay"
        );
        return new CustomMobBossbarSpec(enabled, title, color, overlay);
    }

    private static CustomMobLocalBroadcasts parseLocalBroadcasts(ConfigurationSection section) {
        if (section == null) {
            return CustomMobLocalBroadcasts.EMPTY;
        }
        return new CustomMobLocalBroadcasts(
                section.getString("spawn", ""),
                section.getString("phase", ""),
                section.getString("defeat", "")
        );
    }

    private static String stringValue(Object raw) {
        return raw instanceof String value ? value.trim() : "";
    }

    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object value : list) {
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                result.add(stringValue.trim());
            }
        }
        return List.copyOf(result);
    }

    private static List<Map<?, ?>> mapList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<?, ?>> result = new ArrayList<>();
        for (Object value : list) {
            if (!(value instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("phase entryActions contains non-map entry");
            }
            result.add(map);
        }
        return List.copyOf(result);
    }

    public Optional<CustomMobSpec> get(String id) {
        return Optional.ofNullable(mobs.get(id));
    }

    public Map<String, CustomMobSpec> all() {
        return Collections.unmodifiableMap(mobs);
    }

    public List<CustomMobSpec> findByTag(String tag) {
        String normalized = normalizeLookup(tag);
        if (normalized.isBlank()) {
            return List.of();
        }
        return mobs.values().stream()
                .filter(spec -> spec.tags().contains(normalized))
                .toList();
    }

    public List<CustomMobSpec> findByPool(String poolId) {
        String normalized = normalizeLookup(poolId);
        if (normalized.isBlank()) {
            return List.of();
        }
        return mobs.values().stream()
                .filter(spec -> spec.metadata().matchesPool(normalized))
                .toList();
    }

    public List<CustomMobSpec> findByArc(String arcId) {
        String normalized = normalizeLookup(arcId);
        if (normalized.isBlank()) {
            return List.of();
        }
        return mobs.values().stream()
                .filter(spec -> spec.metadata().matchesArc(normalized))
                .toList();
    }

    public List<CustomMobSpec> findByTheme(String themeId) {
        String normalized = normalizeLookup(themeId);
        if (normalized.isBlank()) {
            return List.of();
        }
        return mobs.values().stream()
                .filter(spec -> spec.metadata().matchesTheme(normalized))
                .toList();
    }

    public List<CustomMobSpec> findByPack(String packId) {
        String normalized = normalizeLookup(packId);
        if (normalized.isBlank()) {
            return List.of();
        }
        return mobs.values().stream()
                .filter(spec -> spec.metadata().packId().equals(normalized))
                .toList();
    }

    private static Set<String> inferPools(boolean boss) {
        return boss ? Set.of("defense", "boss") : Set.of("defense", "regular");
    }

    private static Set<String> inferThemes(String id, Set<String> tags) {
        if (tags != null && !tags.isEmpty()) {
            return tags;
        }
        String inferredArc = inferArcId(id, tags);
        return inferredArc.isBlank() ? Set.of() : Set.of(inferredArc);
    }

    private static String inferArcId(String id, Set<String> tags) {
        if (tags.contains("frontier") || startsWithAny(id, "frontier_", "border_", "bone_", "grave_")) {
            return "frontier";
        }
        if (tags.contains("breach") || tags.contains("citadel") || startsWithAny(id, "ash_", "breach_", "citadel_", "ember_")) {
            return "breach";
        }
        if (tags.contains("omen") || tags.contains("void") || startsWithAny(id, "void_", "omen_", "crown_", "throne_", "abyss_", "last_siege_")) {
            return "omen";
        }
        return CustomMobCatalogMetadata.DEFAULT_ARC_ID;
    }

    private static boolean startsWithAny(String value, String... prefixes) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (String prefix : prefixes) {
            if (normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeLookup(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }
}
