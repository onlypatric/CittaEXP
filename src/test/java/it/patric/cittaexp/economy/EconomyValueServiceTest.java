package it.patric.cittaexp.economy;

import it.patric.cittaexp.challenges.ChallengeRewardSpec;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class EconomyValueServiceTest {

    @Test
    void scalesMoneyAndEconomicItemsFromSnapshot() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true);
        yaml.set("diamondBaselineCoins", 80.0D);
        yaml.set("recalcIntervalSeconds", 300);
        yaml.set("observation.minActiveTowns", 3);
        yaml.set("observation.targetAverageTownTreasury", 18_000.0D);
        yaml.set("observation.targetRecentRewardEmission", 3_500.0D);
        yaml.set("observation.treasuryWeight", 0.7D);
        yaml.set("observation.emissionWeight", 0.3D);
        yaml.set("observation.emissionHalfLifeHours", 24.0D);
        yaml.set("drift.dailyGrowthPercent", 0.35D);
        yaml.set("curves.costExponent", 1.0D);
        yaml.set("curves.rewardExponent", 0.75D);
        yaml.set("curves.minCostMultiplier", 0.85D);
        yaml.set("curves.maxCostMultiplier", 3.0D);
        yaml.set("curves.minRewardMultiplier", 0.9D);
        yaml.set("curves.maxRewardMultiplier", 2.0D);
        yaml.set("economicRewardMaterials", java.util.List.of("DIAMOND", "GOLD_BLOCK"));
        yaml.set("categoryMultipliers.TOWN_RENAME", 0.9D);
        yaml.set("categoryMultipliers.CHALLENGE_MONEY_REWARD", 1.1D);
        yaml.set("categoryMultipliers.ECONOMIC_ITEM_REWARD", 1.0D);
        EconomyBalanceSettings settings = EconomyBalanceSettings.fromConfiguration(yaml);

        EconomyInflationSnapshot snapshot = new EconomyInflationSnapshot(
                Instant.now(),
                false,
                true,
                0.0D,
                5,
                90_000.0D,
                18_000.0D,
                2_000.0D,
                1.0D,
                1.0D,
                1.0D,
                1.5D,
                1.5D
        );
        AtomicReference<Double> emission = new AtomicReference<>(0.0D);
        EconomyValueService service = new EconomyValueService(settings, () -> snapshot, emission::set);

        assertEquals(135.0D, service.effectiveAmount(EconomyBalanceCategory.TOWN_RENAME, 100.0D));
        assertEquals(165.0D, service.effectiveAmount(EconomyBalanceCategory.CHALLENGE_MONEY_REWARD, 100.0D));

        Map<Material, Integer> items = service.scaleEconomicRewardItems(Map.of(
                Material.DIAMOND, 2,
                Material.BREAD, 8
        ));
        assertEquals(3, items.get(Material.DIAMOND));
        assertEquals(8, items.get(Material.BREAD));

        ChallengeRewardSpec effective = service.effectiveRewardSpec(
                new ChallengeRewardSpec(10.0D, 100.0D, java.util.List.of(), java.util.List.of(), Map.of(Material.GOLD_BLOCK, 2), 0.0D),
                EconomyBalanceCategory.CHALLENGE_MONEY_REWARD
        );
        assertEquals(165.0D, effective.moneyCity());
        assertEquals(3, effective.vaultItems().get(Material.GOLD_BLOCK));

        service.recordRewardEmission(420.0D);
        assertEquals(420.0D, emission.get());
    }
}
