package it.patric.cittaexp.playersgui;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class HuskTownsRoleLadderService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private final File rolesFile;
    private final Logger logger;
    private volatile RoleLadderSnapshot cached = null;
    private volatile long loadedAtMillis = 0L;

    public HuskTownsRoleLadderService(File rolesFile, Logger logger) {
        this.rolesFile = rolesFile;
        this.logger = logger;
    }

    public synchronized RoleLadderSnapshot snapshot() {
        long now = System.currentTimeMillis();
        if (cached != null && now - loadedAtMillis <= CACHE_TTL.toMillis()) {
            return cached;
        }
        RoleLadderSnapshot loaded = loadFromDisk();
        this.cached = loaded;
        this.loadedAtMillis = now;
        return loaded;
    }

    private RoleLadderSnapshot loadFromDisk() {
        if (!rolesFile.exists()) {
            logger.warning("[players-gui] roles.yml non trovato, uso fallback ruoli default.");
            return RoleLadderSnapshot.defaultSnapshot();
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(rolesFile);
        ConfigurationSection namesSection = yaml.getConfigurationSection("names");
        ConfigurationSection rolesSection = yaml.getConfigurationSection("roles");
        if (namesSection == null || rolesSection == null) {
            logger.warning("[players-gui] roles.yml invalido (names/roles mancanti), uso fallback ruoli default.");
            return RoleLadderSnapshot.defaultSnapshot();
        }

        Map<Integer, String> names = new LinkedHashMap<>();
        for (String key : namesSection.getKeys(false)) {
            try {
                int weight = Integer.parseInt(key);
                names.put(weight, namesSection.getString(key, "Role " + weight));
            } catch (NumberFormatException ignored) {
                logger.warning("[players-gui] weight non numerico in names: " + key);
            }
        }
        if (names.isEmpty()) {
            logger.warning("[players-gui] roles.yml senza nomi validi, uso fallback ruoli default.");
            return RoleLadderSnapshot.defaultSnapshot();
        }

        List<Integer> weights = new ArrayList<>();
        for (String key : rolesSection.getKeys(false)) {
            try {
                int weight = Integer.parseInt(key);
                if (names.containsKey(weight)) {
                    weights.add(weight);
                }
            } catch (NumberFormatException ignored) {
                logger.warning("[players-gui] weight non numerico in roles: " + key);
            }
        }
        if (weights.isEmpty()) {
            logger.warning("[players-gui] roles.yml senza ladder valida, uso fallback ruoli default.");
            return RoleLadderSnapshot.defaultSnapshot();
        }
        weights.sort(Comparator.naturalOrder());
        return new RoleLadderSnapshot(weights, names);
    }

    public static final class RoleLadderSnapshot {

        private final List<Integer> sortedWeights;
        private final Map<Integer, String> weightNames;

        private RoleLadderSnapshot(List<Integer> sortedWeights, Map<Integer, String> weightNames) {
            this.sortedWeights = List.copyOf(sortedWeights);
            this.weightNames = Map.copyOf(weightNames);
        }

        private static RoleLadderSnapshot defaultSnapshot() {
            Map<Integer, String> names = new LinkedHashMap<>();
            names.put(1, "Resident");
            names.put(2, "Trustee");
            names.put(3, "Mayor");
            return new RoleLadderSnapshot(List.of(1, 2, 3), names);
        }

        public String roleName(int weight) {
            return weightNames.getOrDefault(weight, "Ruolo " + weight);
        }

        public int mayorWeight() {
            return sortedWeights.stream().max(Integer::compareTo).orElse(3);
        }

        public OptionalInt higherThan(int current) {
            return sortedWeights.stream().filter(w -> w > current).min(Integer::compareTo).isPresent()
                    ? OptionalInt.of(sortedWeights.stream().filter(w -> w > current).min(Integer::compareTo).orElseThrow())
                    : OptionalInt.empty();
        }

        public OptionalInt lowerThan(int current) {
            return sortedWeights.stream().filter(w -> w < current).max(Integer::compareTo).isPresent()
                    ? OptionalInt.of(sortedWeights.stream().filter(w -> w < current).max(Integer::compareTo).orElseThrow())
                    : OptionalInt.empty();
        }
    }
}
