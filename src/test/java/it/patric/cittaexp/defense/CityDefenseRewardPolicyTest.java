package it.patric.cittaexp.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

final class CityDefenseRewardPolicyTest {

    @Test
    void retryAdjustsOnlyMoneyAndBoostedBaseResources() {
        CityDefenseLevelSpec spec = new CityDefenseLevelSpec(
                CityDefenseTier.L5,
                50,
                8,
                1600.0D,
                BigDecimal.valueOf(2500L),
                BigDecimal.valueOf(875L),
                32400L,
                2400L,
                1500,
                Map.of(
                        Material.IRON_INGOT, 100,
                        Material.DIAMOND, 10,
                        Material.BREAD, 40
                ),
                List.of(),
                13,
                5,
                10,
                0.16D,
                List.of(CityDefenseSpawnRef.vanilla(org.bukkit.entity.EntityType.ZOMBIE)),
                List.of(CityDefenseSpawnRef.vanilla(org.bukkit.entity.EntityType.HUSK)),
                CityDefenseSpawnRef.vanilla(org.bukkit.entity.EntityType.RAVAGER)
        );
        CityDefenseSettings.RetryPolicy retryPolicy = new CityDefenseSettings.RetryPolicy(
                CityDefenseTier.L16,
                new CityDefenseSettings.RetryBand(0.20D, 1.50D),
                new CityDefenseSettings.RetryBand(0.35D, 1.35D),
                new CityDefenseSettings.RetryBand(0.50D, 1.20D),
                Set.of(Material.IRON_INGOT, Material.BREAD)
        );

        CityDefenseRewardPolicy.RewardOutcome outcome = CityDefenseRewardPolicy.resolve(
                spec,
                retryPolicy,
                true,
                BigDecimal.valueOf(400.0D),
                Map.of(
                        Material.IRON_INGOT, 100,
                        Material.DIAMOND, 10,
                        Material.BREAD, 40
                )
        );

        assertTrue(outcome.retryAdjusted());
        assertEquals("80.00", outcome.rewardMoney().toPlainString());
        assertEquals(150, outcome.rewardItems().get(Material.IRON_INGOT));
        assertEquals(60, outcome.rewardItems().get(Material.BREAD));
        assertEquals(10, outcome.rewardItems().get(Material.DIAMOND));
    }

    @Test
    void highTierKeepsFullRewardEvenIfAlreadyCompleted() {
        CityDefenseLevelSpec spec = new CityDefenseLevelSpec(
                CityDefenseTier.L18,
                180,
                16,
                24500.0D,
                BigDecimal.valueOf(22000L),
                BigDecimal.valueOf(9900L),
                302400L,
                4050L,
                39200,
                Map.of(Material.DIAMOND, 64, Material.OBSIDIAN, 96),
                List.of(),
                33,
                10,
                30,
                0.48D,
                List.of(CityDefenseSpawnRef.vanilla(org.bukkit.entity.EntityType.ENDERMAN)),
                List.of(CityDefenseSpawnRef.vanilla(org.bukkit.entity.EntityType.WITHER_SKELETON)),
                CityDefenseSpawnRef.vanilla(org.bukkit.entity.EntityType.WARDEN)
        );
        CityDefenseSettings.RetryPolicy retryPolicy = new CityDefenseSettings.RetryPolicy(
                CityDefenseTier.L16,
                new CityDefenseSettings.RetryBand(0.20D, 1.50D),
                new CityDefenseSettings.RetryBand(0.35D, 1.35D),
                new CityDefenseSettings.RetryBand(0.50D, 1.20D),
                Set.of(Material.OBSIDIAN)
        );

        CityDefenseRewardPolicy.RewardOutcome outcome = CityDefenseRewardPolicy.resolve(
                spec,
                retryPolicy,
                true,
                BigDecimal.valueOf(10000.0D),
                Map.of(Material.DIAMOND, 64, Material.OBSIDIAN, 96)
        );

        assertFalse(outcome.retryAdjusted());
        assertEquals("10000.00", outcome.rewardMoney().toPlainString());
        assertEquals(96, outcome.rewardItems().get(Material.OBSIDIAN));
    }
}
