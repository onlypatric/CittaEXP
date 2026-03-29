package it.patric.cittaexp.challenges;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class ChallengeContextResolver {

    private static final Map<String, String> DIMENSION_LABELS = Map.of(
            "MINECRAFT:OVERWORLD", "Overworld",
            "MINECRAFT:THE_NETHER", "Nether",
            "MINECRAFT:NETHER", "Nether",
            "MINECRAFT:THE_END", "End"
    );
    private static final Map<String, String> BIOME_FAMILY_LABELS = Map.ofEntries(
            Map.entry("PLAINS", "Pianure"),
            Map.entry("FOREST", "Foreste"),
            Map.entry("TAIGA", "Taiga"),
            Map.entry("JUNGLE", "Giungla"),
            Map.entry("SAVANNA", "Savana"),
            Map.entry("DESERT", "Deserto"),
            Map.entry("BADLANDS", "Badlands"),
            Map.entry("SWAMP", "Palude"),
            Map.entry("SNOW", "Biomi innevati"),
            Map.entry("MOUNTAIN", "Montagne"),
            Map.entry("CAVES", "Grotte"),
            Map.entry("RIVER", "Fiumi"),
            Map.entry("OCEAN", "Oceani"),
            Map.entry("BEACH", "Spiagge"),
            Map.entry("MEADOW", "Praterie alpine"),
            Map.entry("MUSHROOM", "Campi di funghi"),
            Map.entry("NETHER", "Nether"),
            Map.entry("END", "End")
    );
    private static final Map<String, String> BIOME_EXACT_TO_FAMILY = Map.ofEntries(
            Map.entry("PLAINS", "PLAINS"),
            Map.entry("SUNFLOWER_PLAINS", "PLAINS"),
            Map.entry("FOREST", "FOREST"),
            Map.entry("FLOWER_FOREST", "FOREST"),
            Map.entry("BIRCH_FOREST", "FOREST"),
            Map.entry("OLD_GROWTH_BIRCH_FOREST", "FOREST"),
            Map.entry("DARK_FOREST", "FOREST"),
            Map.entry("WINDSWEPT_FOREST", "FOREST"),
            Map.entry("TAIGA", "TAIGA"),
            Map.entry("OLD_GROWTH_PINE_TAIGA", "TAIGA"),
            Map.entry("OLD_GROWTH_SPRUCE_TAIGA", "TAIGA"),
            Map.entry("SNOWY_TAIGA", "TAIGA"),
            Map.entry("JUNGLE", "JUNGLE"),
            Map.entry("SPARSE_JUNGLE", "JUNGLE"),
            Map.entry("BAMBOO_JUNGLE", "JUNGLE"),
            Map.entry("SAVANNA", "SAVANNA"),
            Map.entry("SAVANNA_PLATEAU", "SAVANNA"),
            Map.entry("WINDSWEPT_SAVANNA", "SAVANNA"),
            Map.entry("DESERT", "DESERT"),
            Map.entry("BADLANDS", "BADLANDS"),
            Map.entry("ERODED_BADLANDS", "BADLANDS"),
            Map.entry("WOODED_BADLANDS", "BADLANDS"),
            Map.entry("SWAMP", "SWAMP"),
            Map.entry("MANGROVE_SWAMP", "SWAMP"),
            Map.entry("SNOWY_PLAINS", "SNOW"),
            Map.entry("ICE_SPIKES", "SNOW"),
            Map.entry("SNOWY_BEACH", "SNOW"),
            Map.entry("SNOWY_SLOPES", "SNOW"),
            Map.entry("GROVE", "SNOW"),
            Map.entry("JAGGED_PEAKS", "MOUNTAIN"),
            Map.entry("FROZEN_PEAKS", "MOUNTAIN"),
            Map.entry("STONY_PEAKS", "MOUNTAIN"),
            Map.entry("WINDSWEPT_HILLS", "MOUNTAIN"),
            Map.entry("WINDSWEPT_GRAVELLY_HILLS", "MOUNTAIN"),
            Map.entry("STONY_SHORE", "MOUNTAIN"),
            Map.entry("LUSH_CAVES", "CAVES"),
            Map.entry("DRIPSTONE_CAVES", "CAVES"),
            Map.entry("DEEP_DARK", "CAVES"),
            Map.entry("RIVER", "RIVER"),
            Map.entry("FROZEN_RIVER", "RIVER"),
            Map.entry("OCEAN", "OCEAN"),
            Map.entry("DEEP_OCEAN", "OCEAN"),
            Map.entry("COLD_OCEAN", "OCEAN"),
            Map.entry("DEEP_COLD_OCEAN", "OCEAN"),
            Map.entry("LUKEWARM_OCEAN", "OCEAN"),
            Map.entry("DEEP_LUKEWARM_OCEAN", "OCEAN"),
            Map.entry("WARM_OCEAN", "OCEAN"),
            Map.entry("FROZEN_OCEAN", "OCEAN"),
            Map.entry("DEEP_FROZEN_OCEAN", "OCEAN"),
            Map.entry("BEACH", "BEACH"),
            Map.entry("MEADOW", "MEADOW"),
            Map.entry("CHERRY_GROVE", "MEADOW"),
            Map.entry("MUSHROOM_FIELDS", "MUSHROOM"),
            Map.entry("NETHER_WASTES", "NETHER"),
            Map.entry("BASALT_DELTAS", "NETHER"),
            Map.entry("SOUL_SAND_VALLEY", "NETHER"),
            Map.entry("CRIMSON_FOREST", "NETHER"),
            Map.entry("WARPED_FOREST", "NETHER"),
            Map.entry("THE_END", "END"),
            Map.entry("END_MIDLANDS", "END"),
            Map.entry("END_HIGHLANDS", "END"),
            Map.entry("SMALL_END_ISLANDS", "END"),
            Map.entry("END_BARRENS", "END")
    );
    private static final Map<String, Set<String>> BIOME_CONTAINS_TO_FAMILY = Map.ofEntries(
            Map.entry("JUNGLE", Set.of("JUNGLE", "BAMBOO", "RAINFOREST")),
            Map.entry("DESERT", Set.of("DESERT", "DUNE", "OASIS")),
            Map.entry("SAVANNA", Set.of("SAVANNA", "STEPPE", "BAOBAB")),
            Map.entry("BADLANDS", Set.of("BADLAND", "MESA", "CANYON")),
            Map.entry("SWAMP", Set.of("SWAMP", "MARSH", "BOG", "FEN", "MANGROVE")),
            Map.entry("TAIGA", Set.of("TAIGA", "PINE", "SPRUCE", "CONIFER", "REDWOOD")),
            Map.entry("SNOW", Set.of("SNOW", "FROZEN", "ICY", "GLACIER", "TUNDRA")),
            Map.entry("RIVER", Set.of("RIVER", "STREAM")),
            Map.entry("OCEAN", Set.of("OCEAN", "SEA")),
            Map.entry("BEACH", Set.of("BEACH", "SHORE", "COAST")),
            Map.entry("MOUNTAIN", Set.of("MOUNTAIN", "PEAK", "CLIFF", "HIGHLAND")),
            Map.entry("CAVES", Set.of("CAVE", "CAVERN", "UNDERGROUND", "DEEP_DARK", "DRIPSTONE", "LUSH")),
            Map.entry("MUSHROOM", Set.of("MUSHROOM", "FUNGI")),
            Map.entry("NETHER", Set.of("NETHER", "CRIMSON", "WARPED", "SOUL_SAND", "BASALT")),
            Map.entry("END", Set.of("END", "VOID")),
            Map.entry("MEADOW", Set.of("MEADOW", "GROVE", "CHERRY")),
            Map.entry("FOREST", Set.of("FOREST", "WOODLAND", "WOODS")),
            Map.entry("PLAINS", Set.of("PLAINS", "FIELD", "GRASSLAND"))
    );

    private static volatile Map<String, Set<String>> customExactByFamily = Map.of();
    private static volatile Map<String, Set<String>> customContainsByFamily = Map.of();
    private static volatile Map<String, String> customLabelByFamily = Map.of();

    public String biomeKey(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        try {
            Biome biome = location.getWorld().getBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            return biome == null ? null : biome.toString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public String dimensionKey(Location location) {
        if (location == null) {
            return null;
        }
        return dimensionKey(location.getWorld());
    }

    public String biomeKey(Player player) {
        return player == null ? null : biomeKey(player.getLocation());
    }

    public String dimensionKey(Player player) {
        return player == null ? null : dimensionKey(player.getWorld());
    }

    public String dimensionKey(World world) {
        if (world == null) {
            return null;
        }
        try {
            return world.getKey().toString().toUpperCase(Locale.ROOT);
        } catch (RuntimeException ignored) {
            return world.getName().toUpperCase(Locale.ROOT);
        }
    }

    public static String biomeLabel(String biomeKey) {
        if (biomeKey == null || biomeKey.isBlank()) {
            return null;
        }
        String normalized = normalizeToken(biomeKey);
        if (normalized == null) {
            return null;
        }
        String family = biomeFamilyKey(normalized);
        if (family != null) {
            String custom = customLabelByFamily.get(family);
            if (custom != null && !custom.isBlank()) {
                return custom;
            }
            return BIOME_FAMILY_LABELS.getOrDefault(family, ChallengeLoreFormatter.keyLabel(family));
        }
        return ChallengeLoreFormatter.keyLabel(stripNamespace(normalized));
    }

    public static boolean biomeMatches(String expectedBiomeOrFamily, String actualBiome) {
        if (expectedBiomeOrFamily == null || expectedBiomeOrFamily.isBlank()
                || actualBiome == null || actualBiome.isBlank()) {
            return false;
        }
        String expected = normalizeToken(expectedBiomeOrFamily);
        String actual = normalizeToken(actualBiome);
        if (expected == null || actual == null) {
            return false;
        }
        if (expected.equals(actual) || stripNamespace(expected).equals(stripNamespace(actual))) {
            return true;
        }
        String expectedFamily = biomeFamilyKey(expected);
        String actualFamily = biomeFamilyKey(actual);
        return expectedFamily != null && expectedFamily.equals(actualFamily);
    }

    public static String biomeFamilyKey(String biomeKey) {
        String normalized = normalizeToken(biomeKey);
        if (normalized == null) {
            return null;
        }
        String compact = stripNamespace(normalized);
        if (BIOME_FAMILY_LABELS.containsKey(compact)) {
            return compact;
        }
        String exact = BIOME_EXACT_TO_FAMILY.get(compact);
        if (exact != null) {
            return exact;
        }
        String customExact = matchFromMap(customExactByFamily, compact, true);
        if (customExact != null) {
            return customExact;
        }
        String customContains = matchFromMap(customContainsByFamily, compact, false);
        if (customContains != null) {
            return customContains;
        }
        return matchFromMap(BIOME_CONTAINS_TO_FAMILY, compact, false);
    }

    public static void configureBiomeFamilies(ConfigurationSection section) {
        if (section == null) {
            customExactByFamily = Map.of();
            customContainsByFamily = Map.of();
            customLabelByFamily = Map.of();
            return;
        }
        Map<String, Set<String>> exact = new ConcurrentHashMap<>();
        Map<String, Set<String>> contains = new ConcurrentHashMap<>();
        Map<String, String> labels = new ConcurrentHashMap<>();
        for (String rawFamily : section.getKeys(false)) {
            String family = normalizeToken(rawFamily);
            if (family == null) {
                continue;
            }
            String familyKey = stripNamespace(family);
            ConfigurationSection row = section.getConfigurationSection(rawFamily);
            if (row == null) {
                continue;
            }
            String displayName = row.getString("displayName");
            if (displayName != null && !displayName.isBlank()) {
                labels.put(familyKey, displayName.trim());
            }
            Set<String> exactValues = row.getStringList("exact").stream()
                    .map(ChallengeContextResolver::normalizeToken)
                    .filter(value -> value != null && !value.isBlank())
                    .map(ChallengeContextResolver::stripNamespace)
                    .collect(java.util.stream.Collectors.toSet());
            if (!exactValues.isEmpty()) {
                exact.put(familyKey, Set.copyOf(exactValues));
            }
            Set<String> containsValues = row.getStringList("contains").stream()
                    .map(ChallengeContextResolver::normalizeToken)
                    .filter(value -> value != null && !value.isBlank())
                    .map(ChallengeContextResolver::stripNamespace)
                    .collect(java.util.stream.Collectors.toSet());
            if (!containsValues.isEmpty()) {
                contains.put(familyKey, Set.copyOf(containsValues));
            }
        }
        customExactByFamily = Map.copyOf(exact);
        customContainsByFamily = Map.copyOf(contains);
        customLabelByFamily = Map.copyOf(labels);
    }

    public static String dimensionLabel(String dimensionKey) {
        if (dimensionKey == null || dimensionKey.isBlank()) {
            return null;
        }
        String normalized = normalizeToken(dimensionKey);
        if (normalized == null) {
            return null;
        }
        String label = DIMENSION_LABELS.get(normalized);
        if (label != null) {
            return label;
        }
        String compact = stripNamespace(normalized);
        if ("OVERWORLD".equals(compact) || "NORMAL".equals(compact)) {
            return "Overworld";
        }
        if ("NETHER".equals(compact) || "THE_NETHER".equals(compact)) {
            return "Nether";
        }
        if ("THE_END".equals(compact) || "END".equals(compact)) {
            return "End";
        }
        return ChallengeLoreFormatter.keyLabel(compact);
    }

    public static String normalizeToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return token.trim().toUpperCase(Locale.ROOT);
    }

    private static String matchFromMap(Map<String, Set<String>> map, String token, boolean exact) {
        if (map == null || map.isEmpty() || token == null || token.isBlank()) {
            return null;
        }
        for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
            if (entry == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            for (String needle : entry.getValue()) {
                if (needle == null || needle.isBlank()) {
                    continue;
                }
                if ((exact && token.equals(needle)) || (!exact && token.contains(needle))) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private static String stripNamespace(String token) {
        if (token == null || token.isBlank()) {
            return token;
        }
        int index = token.indexOf(':');
        return index >= 0 && index + 1 < token.length() ? token.substring(index + 1) : token;
    }
}
