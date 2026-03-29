package it.patric.cittaexp.levels;

import it.patric.cittaexp.defense.CityDefenseTier;
import java.util.ArrayList;
import java.io.File;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class CityLevelSettings {

    private final int xpPerLevel;
    private final double curveExponent;
    private final int levelCap;
    private final int regnoExtraClaimMaxLevel;
    private final int xpScale;
    private final int scanIntervalSeconds;
    private final int taxCheckIntervalSeconds;
    private final String taxTimezone;
    private final int taxRunHour;
    private final int taxRunMinute;
    private final double upgradeBalanceMultiplier;
    private final Set<Material> allowedContainers;
    private final Map<Material, Long> materialXpScaled;
    private final Map<TownStage, StageSpec> stageSpecs;
    private final Map<Integer, LevelSpec> levelSpecs;
    private final SpawnPrivacyPolicy spawnPrivacyPolicy;
    private final StoreSettings storeSettings;
    private final MySqlSettings mySqlSettings;

    public record StageSpec(
            int requiredLevel,
            double upgradeCost,
            int claimCap,
            int memberCap,
            int spawnerCap,
            int shopCap,
            double monthlyTax,
            boolean staffApprovalRequired,
            int huskTownLevel,
            CityDefenseTier requiredDefenseTier
    ) {
    }

    public enum LevelRequirementType {
        XP_REACHED,
        BANK_BALANCE_MIN,
        TOWN_SPAWN_REQUIRED,
        DEFENSE_TIER_COMPLETED,
        STAFF_APPROVAL_REQUIRED
    }

    public record LevelRequirementSpec(
            LevelRequirementType type,
            double amount,
            CityDefenseTier defenseTier,
            boolean required
    ) {
    }

    public record LevelSpec(
            int level,
            boolean confirmationRequired,
            List<LevelRequirementSpec> requirements
    ) {
    }

    public record SpawnPrivacyPolicy(
            boolean enabled,
            TownStage forcePublicFromStage,
            boolean allowBorgoToggle
    ) {
    }

    public record StoreSettings(
            int flushSeconds,
            int flushBatchSize,
            int replayBackoffSeconds,
            int shutdownDrainSeconds,
            int cacheIdleTtlSeconds,
            int cacheEvictIntervalSeconds,
            int resyncSeconds
    ) {
    }

    public record MySqlSettings(
            boolean enabled,
            String host,
            int port,
            String database,
            String username,
            String password,
            String params
    ) {
    }

    private CityLevelSettings(
            int xpPerLevel,
            double curveExponent,
            int levelCap,
            int regnoExtraClaimMaxLevel,
            int xpScale,
            int scanIntervalSeconds,
            int taxCheckIntervalSeconds,
            String taxTimezone,
            int taxRunHour,
            int taxRunMinute,
            double upgradeBalanceMultiplier,
            Set<Material> allowedContainers,
            Map<Material, Long> materialXpScaled,
            Map<TownStage, StageSpec> stageSpecs,
            Map<Integer, LevelSpec> levelSpecs,
            SpawnPrivacyPolicy spawnPrivacyPolicy,
            StoreSettings storeSettings,
            MySqlSettings mySqlSettings
    ) {
        this.xpPerLevel = xpPerLevel;
        this.curveExponent = curveExponent;
        this.levelCap = levelCap;
        this.regnoExtraClaimMaxLevel = regnoExtraClaimMaxLevel;
        this.xpScale = xpScale;
        this.scanIntervalSeconds = scanIntervalSeconds;
        this.taxCheckIntervalSeconds = taxCheckIntervalSeconds;
        this.taxTimezone = taxTimezone;
        this.taxRunHour = taxRunHour;
        this.taxRunMinute = taxRunMinute;
        this.upgradeBalanceMultiplier = upgradeBalanceMultiplier;
        this.allowedContainers = allowedContainers;
        this.materialXpScaled = materialXpScaled;
        this.stageSpecs = stageSpecs;
        this.levelSpecs = levelSpecs;
        this.spawnPrivacyPolicy = spawnPrivacyPolicy;
        this.storeSettings = storeSettings;
        this.mySqlSettings = mySqlSettings;
    }

    public static CityLevelSettings load(Plugin plugin) {
        plugin.saveResource("levels.yml", false);
        plugin.saveResource("materials-xp.yml", false);

        File levelsFile = new File(plugin.getDataFolder(), "levels.yml");
        File materialsFile = new File(plugin.getDataFolder(), "materials-xp.yml");

        YamlConfiguration levels = YamlConfiguration.loadConfiguration(levelsFile);
        YamlConfiguration materials = YamlConfiguration.loadConfiguration(materialsFile);

        int xpPerLevel = Math.max(1, levels.getInt("progression.xpPerLevel", 100));
        double curveExponent = Math.max(0.1D, levels.getDouble("progression.curveExponent", 1.30D));
        int levelCap = Math.max(250, levels.getInt("progression.levelCap", 250));
        int regnoExtraClaimMaxLevel = Math.max(
                250,
                levels.getInt("progression.regnoExtraClaimMaxLevel", 250)
        );
        int xpScale = Math.max(1, levels.getInt("progression.xpScale", 10_000));
        int scanIntervalSeconds = Math.max(5, levels.getInt("scan.intervalSeconds", 120));
        int taxCheckIntervalSeconds = Math.max(10, levels.getInt("tax.checkIntervalSeconds", 60));
        String taxTimezone = levels.getString("tax.timezone", "Europe/Rome");
        int taxRunHour = clamp(levels.getInt("tax.runHour", 0), 0, 23);
        int taxRunMinute = clamp(levels.getInt("tax.runMinute", 5), 0, 59);
        double upgradeBalanceMultiplier = Math.max(1.0D, levels.getDouble("upgrade.requiredBalanceMultiplier", 1.5D));

        Set<Material> containers = EnumSet.noneOf(Material.class);
        for (String raw : levels.getStringList("scan.allowedContainers")) {
            Material material = Material.matchMaterial(raw);
            if (material != null) {
                containers.add(material);
            }
        }
        if (containers.isEmpty()) {
            containers.add(Material.CHEST);
            containers.add(Material.TRAPPED_CHEST);
            containers.add(Material.BARREL);
        }

        Map<Material, Long> materialXpScaled = new EnumMap<>(Material.class);
        ConfigurationSection section = materials.getConfigurationSection("materials");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null) {
                    plugin.getLogger().warning("[levels] materiale non valido in materials-xp.yml: " + key);
                    continue;
                }
                double xp = section.getDouble(key, 0.0D);
                if (xp <= 0.0D) {
                    continue;
                }
                long scaled = Math.round(xp * xpScale);
                if (scaled <= 0L) {
                    continue;
                }
                materialXpScaled.put(material, scaled);
            }
        }

        Map<TownStage, StageSpec> stageSpecs = new EnumMap<>(TownStage.class);
        boolean hasM1StageConfig = levels.isConfigurationSection("stages.AVAMPOSTO_I");
        for (TownStage stage : TownStage.values()) {
            StageSpec spec;
            if (!hasM1StageConfig) {
                int memberCap = stage.principalStage() == PrincipalStage.REGNO
                        ? Math.max(50, stage.memberCap())
                        : Math.max(1, stage.memberCap());
                spec = new StageSpec(
                        stage.requiredLevel(),
                        Math.max(0.0D, stage.upgradeCost()),
                        Math.max(1, stage.claimCap()),
                        memberCap,
                        Math.max(0, stage.spawnerCap()),
                        Math.max(0, stage.shopCap()),
                        Math.max(0.0D, stage.monthlyTax()),
                        stage.staffApprovalRequired(),
                        Math.max(1, stage.huskTownLevel()),
                        null
                );
            } else {
                String base = "stages." + stage.name();
                int configuredMemberCap = levels.getInt(base + ".memberCap", stage.memberCap());
                int memberCap = stage.principalStage() == PrincipalStage.REGNO
                        ? Math.max(50, configuredMemberCap)
                        : Math.max(1, configuredMemberCap);
                CityDefenseTier requiredDefenseTier = parseDefenseTier(levels.getString(base + ".requiredDefenseTier", null));
                spec = new StageSpec(
                        Math.max(1, levels.getInt(base + ".requiredLevel", stage.requiredLevel())),
                        Math.max(0.0D, levels.getDouble(base + ".upgradeCost", stage.upgradeCost())),
                        Math.max(1, levels.getInt(base + ".claimCap", stage.claimCap())),
                        memberCap,
                        Math.max(0, levels.getInt(base + ".spawnerCap", stage.spawnerCap())),
                        Math.max(0, levels.getInt(base + ".shopCap", stage.shopCap())),
                        Math.max(0.0D, levels.getDouble(base + ".monthlyTax", stage.monthlyTax())),
                        levels.getBoolean(base + ".staffApprovalRequired", stage.staffApprovalRequired()),
                        Math.max(1, levels.getInt(base + ".huskTownLevel", stage.huskTownLevel())),
                        requiredDefenseTier
                );
            }
            stageSpecs.put(stage, spec);
        }

        Map<Integer, LevelSpec> levelSpecs = new java.util.HashMap<>();
        ConfigurationSection levelsSection = levels.getConfigurationSection("progression.levels");
        if (levelsSection != null) {
            for (String key : levelsSection.getKeys(false)) {
                int level;
                try {
                    level = Integer.parseInt(key.trim());
                } catch (NumberFormatException ignored) {
                    plugin.getLogger().warning("[levels] livello non valido in progression.levels: " + key);
                    continue;
                }
                if (level < 1 || level > levelCap) {
                    continue;
                }
                String base = "progression.levels." + key;
                boolean confirmationRequired = levels.getBoolean(base + ".confirmationRequired", true);
                List<LevelRequirementSpec> requirements = new ArrayList<>();
                List<?> rawRequirements = levels.getList(base + ".requirements");
                if (rawRequirements != null) {
                    for (Object rawRequirement : rawRequirements) {
                        LevelRequirementSpec requirement = parseRequirement(rawRequirement);
                        if (requirement != null) {
                            requirements.add(requirement);
                        }
                    }
                }
                levelSpecs.put(level, new LevelSpec(level, confirmationRequired, List.copyOf(requirements)));
            }
        }

        TownStage configuredForcePublicStage = TownStage.fromDbValue(
                levels.getString("spawnPrivacyPolicy.forcePublicFromStage", TownStage.BORGO_II.name())
        );
        TownStage effectiveForcePublicStage = configuredForcePublicStage.ordinal() < TownStage.BORGO_II.ordinal()
                ? TownStage.BORGO_II
                : configuredForcePublicStage;
        SpawnPrivacyPolicy spawnPrivacyPolicy = new SpawnPrivacyPolicy(
                levels.getBoolean("spawnPrivacyPolicy.enabled", true),
                effectiveForcePublicStage,
                levels.getBoolean("spawnPrivacyPolicy.allowBorgoToggle", true)
        );

        StoreSettings storeSettings = new StoreSettings(
                Math.max(1, levels.getInt("store.flushSeconds", 3)),
                Math.max(1, levels.getInt("store.flushBatchSize", 128)),
                Math.max(1, levels.getInt("store.replayBackoffSeconds", 10)),
                Math.max(1, levels.getInt("store.shutdownDrainSeconds", 5)),
                Math.max(60, levels.getInt("store.cacheIdleTtlSeconds", 900)),
                Math.max(30, levels.getInt("store.cacheEvictIntervalSeconds", 60)),
                Math.max(30, levels.getInt("store.resyncSeconds", 120))
        );

        MySqlSettings mySqlSettings = new MySqlSettings(
                levels.getBoolean("mysql.enabled", false),
                levels.getString("mysql.host", "127.0.0.1"),
                Math.max(1, levels.getInt("mysql.port", 3306)),
                levels.getString("mysql.database", "cittaexp"),
                levels.getString("mysql.username", "root"),
                levels.getString("mysql.password", ""),
                levels.getString("mysql.params", "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC")
        );

        return new CityLevelSettings(
                xpPerLevel,
                curveExponent,
                levelCap,
                regnoExtraClaimMaxLevel,
                xpScale,
                scanIntervalSeconds,
                taxCheckIntervalSeconds,
                taxTimezone,
                taxRunHour,
                taxRunMinute,
                upgradeBalanceMultiplier,
                containers,
                materialXpScaled,
                stageSpecs,
                Map.copyOf(levelSpecs),
                spawnPrivacyPolicy,
                storeSettings,
                mySqlSettings
        );
    }

    public int xpPerLevel() {
        return xpPerLevel;
    }

    public double curveExponent() {
        return curveExponent;
    }

    public int levelCap() {
        return levelCap;
    }

    public int regnoExtraClaimMaxLevel() {
        return regnoExtraClaimMaxLevel;
    }

    public int xpScale() {
        return xpScale;
    }

    public int scanIntervalSeconds() {
        return scanIntervalSeconds;
    }

    public int taxCheckIntervalSeconds() {
        return taxCheckIntervalSeconds;
    }

    public String taxTimezone() {
        return taxTimezone;
    }

    public int taxRunHour() {
        return taxRunHour;
    }

    public int taxRunMinute() {
        return taxRunMinute;
    }

    public double upgradeBalanceMultiplier() {
        return upgradeBalanceMultiplier;
    }

    public Set<Material> allowedContainers() {
        return allowedContainers;
    }

    public Map<Material, Long> materialXpScaled() {
        return materialXpScaled;
    }

    public StageSpec spec(TownStage stage) {
        return stageSpecs.getOrDefault(stage, new StageSpec(
                stage.requiredLevel(),
                stage.upgradeCost(),
                stage.claimCap(),
                stage.memberCap(),
                stage.spawnerCap(),
                stage.shopCap(),
                stage.monthlyTax(),
                stage.staffApprovalRequired(),
                stage.huskTownLevel(),
                null
        ));
    }

    public SpawnPrivacyPolicy spawnPrivacyPolicy() {
        return spawnPrivacyPolicy;
    }

    public LevelSpec levelSpec(int level) {
        return levelSpecs.getOrDefault(level, new LevelSpec(level, true, List.of()));
    }

    public int storeFlushSeconds() {
        return storeSettings.flushSeconds();
    }

    public int storeFlushBatchSize() {
        return storeSettings.flushBatchSize();
    }

    public int storeReplayBackoffSeconds() {
        return storeSettings.replayBackoffSeconds();
    }

    public int storeShutdownDrainSeconds() {
        return storeSettings.shutdownDrainSeconds();
    }

    public int cacheIdleTtlSeconds() {
        return storeSettings.cacheIdleTtlSeconds();
    }

    public int cacheEvictIntervalSeconds() {
        return storeSettings.cacheEvictIntervalSeconds();
    }

    public int storeResyncSeconds() {
        return storeSettings.resyncSeconds();
    }

    public MySqlSettings mySqlSettings() {
        return mySqlSettings;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static CityDefenseTier parseDefenseTier(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return CityDefenseTier.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static LevelRequirementSpec parseRequirement(Object raw) {
        if (raw instanceof String rawString) {
            LevelRequirementType type = parseRequirementType(rawString);
            if (type == null) {
                return null;
            }
            return new LevelRequirementSpec(type, 0.0D, null, true);
        }
        if (!(raw instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Object typeRaw = rawMap.get("type");
        LevelRequirementType type = parseRequirementType(typeRaw == null ? null : String.valueOf(typeRaw));
        if (type == null) {
            return null;
        }
        double amount = parseDouble(rawMap.get("amount"));
        if (amount <= 0.0D) {
            amount = parseDouble(rawMap.get("value"));
        }
        CityDefenseTier defenseTier = parseDefenseTier(rawMap.get("tier") == null ? null : String.valueOf(rawMap.get("tier")));
        boolean required = rawMap.get("required") == null || Boolean.parseBoolean(String.valueOf(rawMap.get("required")));
        return new LevelRequirementSpec(type, amount, defenseTier, required);
    }

    private static LevelRequirementType parseRequirementType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LevelRequirementType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static double parseDouble(Object raw) {
        if (raw == null) {
            return 0.0D;
        }
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }
}
