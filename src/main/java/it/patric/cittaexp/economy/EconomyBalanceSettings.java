package it.patric.cittaexp.economy;

import java.io.File;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class EconomyBalanceSettings {

    public record ObservationSettings(
            int minActiveTowns,
            double targetAverageTownTreasury,
            double targetRecentRewardEmission,
            double treasuryWeight,
            double emissionWeight,
            double emissionHalfLifeHours
    ) {
    }

    public record DriftSettings(
            double dailyGrowthPercent
    ) {
    }

    public record CurveSettings(
            double exponent,
            double minMultiplier,
            double maxMultiplier
    ) {
        public double clamp(double multiplier) {
            return Math.max(minMultiplier, Math.min(maxMultiplier, multiplier));
        }
    }

    private final boolean enabled;
    private final double diamondBaselineCoins;
    private final int recalcIntervalSeconds;
    private final ObservationSettings observation;
    private final DriftSettings drift;
    private final CurveSettings costCurve;
    private final CurveSettings rewardCurve;
    private final Set<Material> economicRewardMaterials;
    private final Map<EconomyBalanceCategory, Double> categoryMultipliers;

    private EconomyBalanceSettings(
            boolean enabled,
            double diamondBaselineCoins,
            int recalcIntervalSeconds,
            ObservationSettings observation,
            DriftSettings drift,
            CurveSettings costCurve,
            CurveSettings rewardCurve,
            Set<Material> economicRewardMaterials,
            Map<EconomyBalanceCategory, Double> categoryMultipliers
    ) {
        this.enabled = enabled;
        this.diamondBaselineCoins = diamondBaselineCoins;
        this.recalcIntervalSeconds = recalcIntervalSeconds;
        this.observation = observation;
        this.drift = drift;
        this.costCurve = costCurve;
        this.rewardCurve = rewardCurve;
        this.economicRewardMaterials = economicRewardMaterials;
        this.categoryMultipliers = categoryMultipliers;
    }

    public static EconomyBalanceSettings load(Plugin plugin) {
        plugin.saveResource("economy-balance.yml", false);
        File file = new File(plugin.getDataFolder(), "economy-balance.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return fromConfiguration(plugin, yaml);
    }

    static EconomyBalanceSettings fromConfiguration(Plugin plugin, YamlConfiguration yaml) {
        return fromConfiguration(yaml, plugin == null ? null : plugin.getLogger());
    }

    static EconomyBalanceSettings fromConfiguration(YamlConfiguration yaml) {
        return fromConfiguration(yaml, null);
    }

    private static EconomyBalanceSettings fromConfiguration(YamlConfiguration yaml, java.util.logging.Logger logger) {
        boolean enabled = yaml.getBoolean("enabled", true);
        double diamondBaselineCoins = Math.max(1.0D, yaml.getDouble("diamondBaselineCoins", 80.0D));
        int recalcIntervalSeconds = Math.max(30, yaml.getInt("recalcIntervalSeconds", 300));

        ConfigurationSection observationSection = yaml.getConfigurationSection("observation");
        ObservationSettings observation = new ObservationSettings(
                Math.max(1, intValue(observationSection, "minActiveTowns", 3)),
                Math.max(1.0D, doubleValue(observationSection, "targetAverageTownTreasury", 18_000.0D)),
                Math.max(1.0D, doubleValue(observationSection, "targetRecentRewardEmission", 3_500.0D)),
                Math.max(0.0D, doubleValue(observationSection, "treasuryWeight", 0.70D)),
                Math.max(0.0D, doubleValue(observationSection, "emissionWeight", 0.30D)),
                Math.max(1.0D, doubleValue(observationSection, "emissionHalfLifeHours", 24.0D))
        );

        ConfigurationSection driftSection = yaml.getConfigurationSection("drift");
        DriftSettings drift = new DriftSettings(
                Math.max(0.0D, doubleValue(driftSection, "dailyGrowthPercent", 0.35D))
        );

        ConfigurationSection curvesSection = yaml.getConfigurationSection("curves");
        CurveSettings costCurve = new CurveSettings(
                Math.max(0.05D, doubleValue(curvesSection, "costExponent", 1.0D)),
                Math.max(0.10D, doubleValue(curvesSection, "minCostMultiplier", 0.85D)),
                Math.max(0.10D, doubleValue(curvesSection, "maxCostMultiplier", 3.0D))
        );
        CurveSettings rewardCurve = new CurveSettings(
                Math.max(0.05D, doubleValue(curvesSection, "rewardExponent", 0.75D)),
                Math.max(0.10D, doubleValue(curvesSection, "minRewardMultiplier", 0.90D)),
                Math.max(0.10D, doubleValue(curvesSection, "maxRewardMultiplier", 2.0D))
        );

        Set<Material> economicRewardMaterials = EnumSet.noneOf(Material.class);
        for (String raw : yaml.getStringList("economicRewardMaterials")) {
            Material material = Material.matchMaterial(raw);
            if (material == null) {
                if (logger != null) {
                    logger.warning("[economy] materiale economico non valido in economy-balance.yml: " + raw);
                }
                continue;
            }
            economicRewardMaterials.add(material);
        }

        Map<EconomyBalanceCategory, Double> categoryMultipliers = new EnumMap<>(EconomyBalanceCategory.class);
        ConfigurationSection multiplierSection = yaml.getConfigurationSection("categoryMultipliers");
        for (EconomyBalanceCategory category : EconomyBalanceCategory.values()) {
            double configured = doubleValue(multiplierSection, category.name(), 1.0D);
            categoryMultipliers.put(category, Math.max(0.05D, configured));
        }

        return new EconomyBalanceSettings(
                enabled,
                diamondBaselineCoins,
                recalcIntervalSeconds,
                observation,
                drift,
                costCurve,
                rewardCurve,
                Set.copyOf(economicRewardMaterials),
                Map.copyOf(categoryMultipliers)
        );
    }

    private static int intValue(ConfigurationSection section, String path, int fallback) {
        return section == null ? fallback : section.getInt(path, fallback);
    }

    private static double doubleValue(ConfigurationSection section, String path, double fallback) {
        return section == null ? fallback : section.getDouble(path, fallback);
    }

    public boolean enabled() {
        return enabled;
    }

    public double diamondBaselineCoins() {
        return diamondBaselineCoins;
    }

    public int recalcIntervalSeconds() {
        return recalcIntervalSeconds;
    }

    public ObservationSettings observation() {
        return observation;
    }

    public DriftSettings drift() {
        return drift;
    }

    public CurveSettings costCurve() {
        return costCurve;
    }

    public CurveSettings rewardCurve() {
        return rewardCurve;
    }

    public boolean economicRewardMaterial(Material material) {
        return material != null && economicRewardMaterials.contains(material);
    }

    public Set<Material> economicRewardMaterials() {
        return economicRewardMaterials;
    }

    public double categoryMultiplier(EconomyBalanceCategory category) {
        return categoryMultipliers.getOrDefault(category, 1.0D);
    }

    public Map<EconomyBalanceCategory, Double> categoryMultipliers() {
        return categoryMultipliers;
    }
}
