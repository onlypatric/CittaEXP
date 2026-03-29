package it.patric.cittaexp.economy;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EconomyBalanceSettingsTest {

    @Test
    void loadsWhitelistAndCategoryMultipliers() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true);
        yaml.set("diamondBaselineCoins", 80.0D);
        yaml.set("recalcIntervalSeconds", 300);
        yaml.set("observation.minActiveTowns", 4);
        yaml.set("observation.targetAverageTownTreasury", 20_000.0D);
        yaml.set("observation.targetRecentRewardEmission", 5_000.0D);
        yaml.set("observation.treasuryWeight", 0.6D);
        yaml.set("observation.emissionWeight", 0.4D);
        yaml.set("observation.emissionHalfLifeHours", 12.0D);
        yaml.set("drift.dailyGrowthPercent", 0.5D);
        yaml.set("curves.costExponent", 1.0D);
        yaml.set("curves.rewardExponent", 0.75D);
        yaml.set("curves.minCostMultiplier", 0.85D);
        yaml.set("curves.maxCostMultiplier", 3.0D);
        yaml.set("curves.minRewardMultiplier", 0.9D);
        yaml.set("curves.maxRewardMultiplier", 2.0D);
        yaml.set("economicRewardMaterials", java.util.List.of("DIAMOND", "GOLD_BLOCK"));
        yaml.set("categoryMultipliers.PLAYER_CITY_CREATE", 1.1D);
        yaml.set("categoryMultipliers.RACE_REWARD", 0.95D);

        EconomyBalanceSettings settings = EconomyBalanceSettings.fromConfiguration(yaml);

        assertEquals(80.0D, settings.diamondBaselineCoins());
        assertEquals(4, settings.observation().minActiveTowns());
        assertTrue(settings.economicRewardMaterial(Material.DIAMOND));
        assertTrue(settings.economicRewardMaterial(Material.GOLD_BLOCK));
        assertEquals(1.1D, settings.categoryMultiplier(EconomyBalanceCategory.PLAYER_CITY_CREATE));
        assertEquals(0.95D, settings.categoryMultiplier(EconomyBalanceCategory.RACE_REWARD));
    }
}
