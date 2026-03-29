package it.patric.cittaexp.economy;

import java.time.Instant;

public record EconomyInflationSnapshot(
        Instant calculatedAt,
        boolean frozen,
        boolean telemetryReliable,
        double manualBiasPercent,
        int townCount,
        double totalTownTreasury,
        double averageTownTreasury,
        double recentRewardEmission,
        double observedIndex,
        double timeDriftIndex,
        double effectiveIndex,
        double costMultiplier,
        double rewardMultiplier
) {
    public static EconomyInflationSnapshot neutral(boolean frozen, double manualBiasPercent) {
        Instant now = Instant.now();
        return new EconomyInflationSnapshot(
                now,
                frozen,
                false,
                manualBiasPercent,
                0,
                0.0D,
                0.0D,
                0.0D,
                1.0D,
                1.0D,
                Math.max(0.1D, 1.0D + (manualBiasPercent / 100.0D)),
                1.0D,
                1.0D
        );
    }
}
