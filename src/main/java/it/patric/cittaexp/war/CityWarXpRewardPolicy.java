package it.patric.cittaexp.war;

import java.math.BigDecimal;

final class CityWarXpRewardPolicy {

    enum Outcome {
        ATTACKER_WIN,
        DEFENDER_WIN,
        TIME_OUT
    }

    record RewardPreview(
            boolean enabled,
            double baseXp,
            double wagerBonusXp,
            double totalXp
    ) {
    }

    private CityWarXpRewardPolicy() {
    }

    static RewardPreview resolve(
            CityWarSettings.XpRewardSettings settings,
            Outcome outcome,
            BigDecimal wager
    ) {
        if (settings == null || !settings.enabled() || outcome == null) {
            return new RewardPreview(false, 0.0D, 0.0D, 0.0D);
        }
        boolean awardEnabled;
        double baseXp;
        switch (outcome) {
            case ATTACKER_WIN -> {
                awardEnabled = settings.awardOnAttackerWin();
                baseXp = settings.attackerWinBaseXp();
            }
            case DEFENDER_WIN -> {
                awardEnabled = settings.awardOnDefenderWin();
                baseXp = settings.defenderWinBaseXp();
            }
            case TIME_OUT -> {
                awardEnabled = settings.awardOnTimeout();
                baseXp = settings.timeoutBaseXp();
            }
            default -> {
                awardEnabled = false;
                baseXp = 0.0D;
            }
        }
        if (!awardEnabled) {
            return new RewardPreview(false, 0.0D, 0.0D, 0.0D);
        }
        double safeWager = wager == null ? 0.0D : Math.max(0.0D, wager.doubleValue());
        double wagerBonus = Math.min(safeWager * Math.max(0.0D, settings.wagerBonusMultiplier()), Math.max(0.0D, settings.wagerBonusCap()));
        double total = Math.max(0.0D, baseXp + wagerBonus);
        total = applyRounding(total, settings.rounding());
        return new RewardPreview(true, Math.max(0.0D, baseXp), Math.max(0.0D, wagerBonus), Math.max(0.0D, total));
    }

    private static double applyRounding(double value, CityWarSettings.XpRewardRounding rounding) {
        if (value <= 0.0D) {
            return 0.0D;
        }
        if (rounding == null) {
            return Math.ceil(value);
        }
        return switch (rounding) {
            case FLOOR -> Math.floor(value);
            case HALF_UP -> Math.round(value);
            case CEIL -> Math.ceil(value);
        };
    }
}
