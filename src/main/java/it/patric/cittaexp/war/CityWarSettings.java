package it.patric.cittaexp.war;

import java.io.File;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class CityWarSettings {

    public enum XpRewardRounding {
        HALF_UP,
        CEIL,
        FLOOR;

        static XpRewardRounding fromConfig(String raw) {
            if (raw == null || raw.isBlank()) {
                return CEIL;
            }
            return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "half_up", "half-up", "round" -> HALF_UP;
                case "floor", "down" -> FLOOR;
                default -> CEIL;
            };
        }
    }

    public record GuiSettings(int pageSize) {
    }

    public record RollbackSettings(boolean enabled, boolean trackBurn, boolean suppressBlockDrops) {
    }

    public record TrophySettings(boolean enabled, Material material, String itemsAdderItemId) {
    }

    public record PayoutSettings(
            boolean enabled,
            double feePercent,
            boolean applyFeeOnTimeout
    ) {
    }

    public record XpRewardSettings(
            boolean enabled,
            boolean awardOnAttackerWin,
            boolean awardOnDefenderWin,
            boolean awardOnTimeout,
            double attackerWinBaseXp,
            double defenderWinBaseXp,
            double timeoutBaseXp,
            double wagerBonusMultiplier,
            double wagerBonusCap,
            XpRewardRounding rounding
    ) {
    }

    private final boolean enabled;
    private final int minCityLevel;
    private final int maxCityLevelDifference;
    private final boolean singleActiveWarGlobal;
    private final GuiSettings gui;
    private final RollbackSettings rollback;
    private final TrophySettings trophy;
    private final PayoutSettings payout;
    private final XpRewardSettings xpRewards;

    private CityWarSettings(
            boolean enabled,
            int minCityLevel,
            int maxCityLevelDifference,
            boolean singleActiveWarGlobal,
            GuiSettings gui,
            RollbackSettings rollback,
            TrophySettings trophy,
            PayoutSettings payout,
            XpRewardSettings xpRewards
    ) {
        this.enabled = enabled;
        this.minCityLevel = minCityLevel;
        this.maxCityLevelDifference = maxCityLevelDifference;
        this.singleActiveWarGlobal = singleActiveWarGlobal;
        this.gui = gui;
        this.rollback = rollback;
        this.trophy = trophy;
        this.payout = payout;
        this.xpRewards = xpRewards;
    }

    public static CityWarSettings load(Plugin plugin) {
        plugin.saveResource("war.yml", false);
        File file = new File(plugin.getDataFolder(), "war.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return fromConfiguration(yaml);
    }

    static CityWarSettings fromConfiguration(YamlConfiguration yaml) {
        boolean enabled = yaml.getBoolean("enabled", true);
        int minCityLevel = Math.max(1, yaml.getInt("minCityLevel", 25));
        int maxCityLevelDifference = Math.max(0, yaml.getInt("maxCityLevelDifference", 50));
        boolean singleActiveWarGlobal = yaml.getBoolean("singleActiveWarGlobal", true);
        GuiSettings gui = new GuiSettings(Math.max(1, yaml.getInt("gui.pageSize", 27)));
        RollbackSettings rollback = new RollbackSettings(
                yaml.getBoolean("rollback.enabled", true),
                yaml.getBoolean("rollback.trackBurn", true),
                yaml.getBoolean("rollback.suppressBlockDrops", true)
        );
        Material trophyMaterial = Material.matchMaterial(yaml.getString("trophy.material", "TOTEM_OF_UNDYING"));
        if (trophyMaterial == null || trophyMaterial.isAir()) {
            trophyMaterial = Material.TOTEM_OF_UNDYING;
        }
        String trophyItemsAdderItemId = yaml.getString("trophy.itemsAdderItemId", "cittaexp_gui:war_win_token");
        TrophySettings trophy = new TrophySettings(
                yaml.getBoolean("trophy.enabled", true),
                trophyMaterial,
                trophyItemsAdderItemId == null ? "" : trophyItemsAdderItemId.trim()
        );
        PayoutSettings payout = new PayoutSettings(
                yaml.getBoolean("payout.enabled", true),
                Math.max(0.0D, Math.min(1.0D, yaml.getDouble("payout.feePercent", 0.10D))),
                yaml.getBoolean("payout.applyFeeOnTimeout", false)
        );
        XpRewardSettings xpRewards = new XpRewardSettings(
                yaml.getBoolean("xpRewards.enabled", true),
                yaml.getBoolean("xpRewards.awardOn.attackerWin", true),
                yaml.getBoolean("xpRewards.awardOn.defenderWin", true),
                yaml.getBoolean("xpRewards.awardOn.timeout", false),
                Math.max(0.0D, yaml.getDouble("xpRewards.baseXp.attackerWin", 200.0D)),
                Math.max(0.0D, yaml.getDouble("xpRewards.baseXp.defenderWin", 240.0D)),
                Math.max(0.0D, yaml.getDouble("xpRewards.baseXp.timeout", 0.0D)),
                Math.max(0.0D, yaml.getDouble("xpRewards.wagerBonusMultiplier", 0.01D)),
                Math.max(0.0D, yaml.getDouble("xpRewards.wagerBonusCap", 400.0D)),
                XpRewardRounding.fromConfig(yaml.getString("xpRewards.rounding", "ceil"))
        );
        return new CityWarSettings(
                enabled,
                minCityLevel,
                maxCityLevelDifference,
                singleActiveWarGlobal,
                gui,
                rollback,
                trophy,
                payout,
                xpRewards
        );
    }

    public boolean enabled() {
        return enabled;
    }

    public int minCityLevel() {
        return minCityLevel;
    }

    public int maxCityLevelDifference() {
        return maxCityLevelDifference;
    }

    public boolean singleActiveWarGlobal() {
        return singleActiveWarGlobal;
    }

    public GuiSettings gui() {
        return gui;
    }

    public RollbackSettings rollback() {
        return rollback;
    }

    public TrophySettings trophy() {
        return trophy;
    }

    public PayoutSettings payout() {
        return payout;
    }

    public XpRewardSettings xpRewards() {
        return xpRewards;
    }
}
