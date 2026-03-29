package it.patric.cittaexp.defense;

import it.patric.cittaexp.itemsadder.ItemsAdderRewardResolver;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;

public record CityDefenseLevelSpec(
        CityDefenseTier tier,
        int minCityLevel,
        int waves,
        double guardianHp,
        BigDecimal startCost,
        BigDecimal rewardMoney,
        long cooldownSeconds,
        long maxDurationSeconds,
        int rewardXp,
        Map<Material, Integer> rewardItems,
        List<ItemsAdderRewardResolver.RewardSpec> firstClearUniqueRewards,
        int baseMobCount,
        int waveIncrement,
        int bossSupportCount,
        double eliteChance,
        List<CityDefenseSpawnRef> regularMobs,
        List<CityDefenseSpawnRef> eliteMobs,
        CityDefenseSpawnRef bossMob
) {
}
