package it.patric.cittaexp.custommobs;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class MobTraitRegistry {

    public record LoadResult(MobTraitRegistry registry, int loadedCount, int skippedCount, List<String> warnings) {
    }

    private final Map<String, MobTraitSpec> traits;

    public MobTraitRegistry(Map<String, MobTraitSpec> traits) {
        this.traits = Map.copyOf(traits);
    }

    public static LoadResult load(File directory, MobAbilityRegistry abilityRegistry) {
        if (!directory.exists() || !directory.isDirectory()) {
            return new LoadResult(new MobTraitRegistry(Map.of()), 0, 0, List.of());
        }
        File[] files = directory.listFiles((dir, name) -> {
            String normalized = name.toLowerCase(Locale.ROOT);
            return normalized.endsWith(".yml") && !normalized.equals("pack-manifest.yml");
        });
        if (files == null || files.length == 0) {
            return new LoadResult(new MobTraitRegistry(Map.of()), 0, 0, List.of());
        }
        Arrays.sort(files);
        Map<String, MobTraitSpec> loaded = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        int skipped = 0;
        for (File file : files) {
            try {
                MobTraitSpec spec = parseTrait(YamlConfiguration.loadConfiguration(file), abilityRegistry);
                if (loaded.containsKey(spec.id())) {
                    skipped++;
                    warnings.add(file.getName() + ": duplicate trait id '" + spec.id() + "'");
                    continue;
                }
                loaded.put(spec.id(), spec);
            } catch (Exception exception) {
                skipped++;
                warnings.add(file.getName() + ": " + exception.getMessage());
            }
        }
        return new LoadResult(new MobTraitRegistry(loaded), loaded.size(), skipped, List.copyOf(warnings));
    }

    private static MobTraitSpec parseTrait(YamlConfiguration yaml, MobAbilityRegistry abilityRegistry) {
        String id = CustomMobParsingSupport.requireString(yaml, "id");
        int weight = Math.max(1, yaml.getInt("weight", 1));
        String namePrefix = yaml.getString("namePrefix", "");
        String nameSuffix = yaml.getString("nameSuffix", "");
        Map<String, Double> attributes = CustomMobParsingSupport.parseAttributes(yaml.getConfigurationSection("stats"));
        Map<org.bukkit.inventory.EquipmentSlot, CustomMobEquipmentItem> equipment = CustomMobParsingSupport.parseEquipment(yaml.getConfigurationSection("equipment"));
        java.util.Set<String> tags = CustomMobParsingSupport.parseNormalizedStrings(yaml.getStringList("tags"));
        if (tags.isEmpty()) {
            tags = CustomMobParsingSupport.parseNormalizedStrings(yaml.getStringList("tagsAdd"));
        }
        List<String> addAbilities = List.copyOf(yaml.getStringList("addAbilities"));
        List<String> removeAbilities = List.copyOf(yaml.getStringList("removeAbilities"));
        CustomMobParsingSupport.requireKnownAbilities(addAbilities, abilityRegistry, "trait addAbilities");
        CustomMobParsingSupport.requireKnownAbilities(removeAbilities, abilityRegistry, "trait removeAbilities");
        CustomMobCatalogMetadata metadata = CustomMobParsingSupport.parseCatalogMetadata(
                yaml,
                "shared",
                java.util.Set.of("traits"),
                tags
        );
        return new MobTraitSpec(id, weight, namePrefix, nameSuffix, attributes, equipment, tags, addAbilities, removeAbilities, metadata);
    }

    public Optional<MobTraitSpec> get(String id) {
        return Optional.ofNullable(traits.get(id));
    }

    public Map<String, MobTraitSpec> all() {
        return traits;
    }

    public List<MobTraitSpec> findByTag(String tag) {
        String normalized = normalizeLookup(tag);
        if (normalized.isBlank()) {
            return List.of();
        }
        return traits.values().stream()
                .filter(spec -> spec.tags().contains(normalized))
                .toList();
    }

    public List<MobTraitSpec> findByPool(String poolId) {
        String normalized = normalizeLookup(poolId);
        if (normalized.isBlank()) {
            return List.of();
        }
        return traits.values().stream()
                .filter(spec -> spec.metadata().matchesPool(normalized))
                .toList();
    }

    public List<MobTraitSpec> findByArc(String arcId) {
        String normalized = normalizeLookup(arcId);
        if (normalized.isBlank()) {
            return List.of();
        }
        return traits.values().stream()
                .filter(spec -> spec.metadata().matchesArc(normalized))
                .toList();
    }

    public List<MobTraitSpec> findByTheme(String themeId) {
        String normalized = normalizeLookup(themeId);
        if (normalized.isBlank()) {
            return List.of();
        }
        return traits.values().stream()
                .filter(spec -> spec.metadata().matchesTheme(normalized))
                .toList();
    }

    public List<MobTraitSpec> findByPack(String packId) {
        String normalized = normalizeLookup(packId);
        if (normalized.isBlank()) {
            return List.of();
        }
        return traits.values().stream()
                .filter(spec -> spec.metadata().packId().equals(normalized))
                .toList();
    }

    private static String normalizeLookup(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }
}
