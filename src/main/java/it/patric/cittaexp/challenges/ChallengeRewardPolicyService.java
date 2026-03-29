package it.patric.cittaexp.challenges;

import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class ChallengeRewardPolicyService {

    public enum RewardClass {
        XP_CITY,
        MONEY_CITY,
        MATERIAL,
        COMMAND_CONSOLE
    }

    private record PolicyKey(String family, ChallengeMode mode, RewardClass rewardClass) {
    }

    private final ChallengeTaxonomyService taxonomyService;
    private final Map<RewardClass, Boolean> defaults = new EnumMap<>(RewardClass.class);
    private final Map<PolicyKey, Boolean> overrides = new HashMap<>();

    public ChallengeRewardPolicyService(Plugin plugin, ChallengeTaxonomyService taxonomyService) {
        this.taxonomyService = taxonomyService;
        defaults.put(RewardClass.XP_CITY, Boolean.TRUE);
        defaults.put(RewardClass.MONEY_CITY, Boolean.TRUE);
        defaults.put(RewardClass.MATERIAL, Boolean.TRUE);
        defaults.put(RewardClass.COMMAND_CONSOLE, Boolean.TRUE);
        loadOverrides(plugin);
    }

    public boolean allow(
            RewardClass rewardClass,
            ChallengeMode mode,
            String objectiveFamily,
            ChallengeObjectiveType objectiveType
    ) {
        if (rewardClass == null) {
            return false;
        }
        if (rewardClass == RewardClass.XP_CITY && (objectiveType == null || !taxonomyService.cityXpEnabled(objectiveType))) {
            return false;
        }
        String family = normalizeFamily(objectiveFamily);
        ChallengeMode safeMode = mode == null ? ChallengeMode.DAILY_STANDARD : mode;
        PolicyKey key = new PolicyKey(family, safeMode, rewardClass);
        Boolean override = overrides.get(key);
        if (override != null) {
            return override;
        }
        return defaults.getOrDefault(rewardClass, Boolean.TRUE);
    }

    private void loadOverrides(Plugin plugin) {
        if (plugin == null) {
            return;
        }
        File file = new File(plugin.getDataFolder(), "challenges.yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection rewardPolicy = yaml.getConfigurationSection("rewardPolicy");
        if (rewardPolicy == null) {
            return;
        }
        ConfigurationSection defaultsSection = rewardPolicy.getConfigurationSection("defaults");
        if (defaultsSection != null) {
            for (RewardClass rewardClass : RewardClass.values()) {
                String key = rewardClass.name().toLowerCase(Locale.ROOT);
                defaults.put(rewardClass, defaultsSection.getBoolean(key, defaults.getOrDefault(rewardClass, Boolean.TRUE)));
            }
        }
        ConfigurationSection matrix = rewardPolicy.getConfigurationSection("matrix");
        if (matrix == null) {
            return;
        }
        for (String familyKey : matrix.getKeys(false)) {
            ConfigurationSection familySection = matrix.getConfigurationSection(familyKey);
            if (familySection == null) {
                continue;
            }
            for (String modeKey : familySection.getKeys(false)) {
                ConfigurationSection modeSection = familySection.getConfigurationSection(modeKey);
                if (modeSection == null) {
                    continue;
                }
                ChallengeMode mode = parseMode(modeKey);
                if (mode == null) {
                    continue;
                }
                for (RewardClass rewardClass : RewardClass.values()) {
                    String rewardKey = rewardClass.name().toLowerCase(Locale.ROOT);
                    if (modeSection.contains(rewardKey)) {
                        overrides.put(
                                new PolicyKey(normalizeFamily(familyKey), mode, rewardClass),
                                modeSection.getBoolean(rewardKey)
                        );
                    }
                }
            }
        }
    }

    private static ChallengeMode parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String token = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return ChallengeMode.valueOf(token);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String normalizeFamily(String family) {
        if (family == null || family.isBlank()) {
            return "generic";
        }
        return family.trim().toLowerCase(Locale.ROOT);
    }
}
