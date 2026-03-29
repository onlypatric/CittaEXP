package it.patric.cittaexp.defense;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.Material;

final class CityDefenseRewardPolicy {

    record RewardOutcome(
            BigDecimal rewardMoney,
            Map<Material, Integer> rewardItems,
            boolean retryAdjusted
    ) {
    }

    private CityDefenseRewardPolicy() {
    }

    static RewardOutcome resolve(
            CityDefenseLevelSpec spec,
            CityDefenseSettings.RetryPolicy retryPolicy,
            boolean alreadyCompleted,
            BigDecimal effectiveRewardMoney,
            Map<Material, Integer> effectiveRewardItems
    ) {
        BigDecimal safeMoney = effectiveRewardMoney == null ? BigDecimal.ZERO : effectiveRewardMoney.max(BigDecimal.ZERO);
        Map<Material, Integer> safeItems = effectiveRewardItems == null ? Map.of() : Map.copyOf(effectiveRewardItems);
        if (spec == null || retryPolicy == null || !retryPolicy.retryActive(spec.tier(), alreadyCompleted)) {
            return new RewardOutcome(safeMoney.setScale(2, RoundingMode.HALF_UP), safeItems, false);
        }

        CityDefenseSettings.RetryBand band = retryPolicy.bandFor(spec.tier());
        BigDecimal rewardMoney = safeMoney
                .multiply(BigDecimal.valueOf(band.moneyMultiplier()))
                .setScale(2, RoundingMode.HALF_UP);

        Map<Material, Integer> rewardItems = new EnumMap<>(Material.class);
        for (Map.Entry<Material, Integer> entry : safeItems.entrySet()) {
            Material material = entry.getKey();
            Integer amount = entry.getValue();
            if (material == null || amount == null || amount <= 0) {
                continue;
            }
            int effective = amount;
            if (retryPolicy.boostedMaterial(material)) {
                effective = Math.max(1, (int) Math.round(amount * band.baseResourceMultiplier()));
            }
            rewardItems.put(material, effective);
        }
        return new RewardOutcome(rewardMoney, Map.copyOf(rewardItems), true);
    }
}
