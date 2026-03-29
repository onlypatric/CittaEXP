package it.patric.cittaexp.economy;

import it.patric.cittaexp.challenges.ChallengeRewardSpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;
import org.bukkit.Material;

public final class EconomyValueService {

    private final EconomyBalanceSettings settings;
    private final Supplier<EconomyInflationSnapshot> snapshotSupplier;
    private final DoubleConsumer emissionRecorder;

    public EconomyValueService(
            EconomyBalanceSettings settings,
            EconomyInflationService inflationService
    ) {
        this(
                settings,
                inflationService::snapshot,
                inflationService::recordRewardEmission
        );
    }

    EconomyValueService(
            EconomyBalanceSettings settings,
            Supplier<EconomyInflationSnapshot> snapshotSupplier,
            DoubleConsumer emissionRecorder
    ) {
        this.settings = settings;
        this.snapshotSupplier = snapshotSupplier;
        this.emissionRecorder = emissionRecorder;
    }

    public EconomyInflationSnapshot snapshot() {
        return snapshotSupplier.get();
    }

    public void recordRewardEmission(double amount) {
        emissionRecorder.accept(amount);
    }

    public double multiplier(EconomyBalanceCategory category) {
        EconomyInflationSnapshot snapshot = snapshotSupplier.get();
        double base = category.cost() ? snapshot.costMultiplier() : snapshot.rewardMultiplier();
        double configured = Math.max(0.05D, settings.categoryMultiplier(category));
        double raw = base * configured;
        return category.cost()
                ? settings.costCurve().clamp(raw)
                : settings.rewardCurve().clamp(raw);
    }

    public double effectiveAmount(EconomyBalanceCategory category, double nominal) {
        if (!settings.enabled()) {
            return Math.max(0.0D, nominal);
        }
        return roundMoney(Math.max(0.0D, nominal) * multiplier(category));
    }

    public BigDecimal effectiveMoney(EconomyBalanceCategory category, BigDecimal nominal) {
        if (nominal == null || nominal.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(effectiveAmount(category, nominal.doubleValue())).setScale(2, RoundingMode.HALF_UP);
    }

    public Map<Material, Integer> scaleEconomicRewardItems(Map<Material, Integer> nominalItems) {
        if (nominalItems == null || nominalItems.isEmpty()) {
            return Map.of();
        }
        Map<Material, Integer> scaled = new EnumMap<>(Material.class);
        double multiplier = multiplier(EconomyBalanceCategory.ECONOMIC_ITEM_REWARD);
        for (Map.Entry<Material, Integer> entry : nominalItems.entrySet()) {
            Material material = entry.getKey();
            Integer amount = entry.getValue();
            if (material == null || amount == null || amount <= 0) {
                continue;
            }
            int effective = amount;
            if (settings.economicRewardMaterial(material)) {
                effective = Math.max(1, (int) Math.round(amount * multiplier));
            }
            scaled.put(material, effective);
        }
        return Map.copyOf(scaled);
    }

    public ChallengeRewardSpec effectiveRewardSpec(ChallengeRewardSpec nominal, EconomyBalanceCategory moneyCategory) {
        if (nominal == null || nominal.empty()) {
            return nominal == null ? ChallengeRewardSpec.EMPTY : nominal;
        }
        return new ChallengeRewardSpec(
                nominal.xpCity(),
                effectiveAmount(moneyCategory, nominal.moneyCity()),
                nominal.consoleCommands(),
                nominal.commandKeys(),
                scaleEconomicRewardItems(nominal.vaultItems()),
                nominal.personalXpCityBonus()
        );
    }

    private static double roundMoney(double value) {
        return BigDecimal.valueOf(Math.max(0.0D, value))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
