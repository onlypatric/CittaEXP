package it.patric.cittaexp.war;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class CityWarPayoutPolicy {

    enum Outcome {
        ATTACKER_WIN,
        DEFENDER_WIN,
        TIME_OUT
    }

    record SettlementPreview(
            boolean enabled,
            BigDecimal grossPayout,
            BigDecimal feeAmount,
            BigDecimal netPayout,
            BigDecimal attackerAdjustment,
            BigDecimal defenderAdjustment
    ) {
    }

    private CityWarPayoutPolicy() {
    }

    static SettlementPreview resolve(
            CityWarSettings.PayoutSettings settings,
            Outcome outcome,
            BigDecimal wager
    ) {
        BigDecimal safeWager = money(wager);
        if (settings == null || !settings.enabled() || safeWager.compareTo(BigDecimal.ZERO) <= 0 || outcome == null) {
            return zero();
        }
        BigDecimal feePercent = BigDecimal.valueOf(Math.max(0.0D, Math.min(1.0D, settings.feePercent())));
        BigDecimal totalPot = money(safeWager.multiply(BigDecimal.valueOf(2L)));
        return switch (outcome) {
            case ATTACKER_WIN -> winner(totalPot, feePercent, true);
            case DEFENDER_WIN -> winner(totalPot, feePercent, false);
            case TIME_OUT -> settings.applyFeeOnTimeout() ? timeout(safeWager, feePercent) : zero();
        };
    }

    private static SettlementPreview winner(BigDecimal grossPayout, BigDecimal feePercent, boolean attackerWins) {
        BigDecimal fee = money(grossPayout.multiply(feePercent));
        BigDecimal net = money(grossPayout.subtract(fee));
        BigDecimal adjustment = fee.negate();
        return new SettlementPreview(
                true,
                grossPayout,
                fee,
                net,
                attackerWins ? adjustment : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                attackerWins ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : adjustment
        );
    }

    private static SettlementPreview timeout(BigDecimal grossPerTown, BigDecimal feePercent) {
        BigDecimal fee = money(grossPerTown.multiply(feePercent));
        BigDecimal net = money(grossPerTown.subtract(fee));
        BigDecimal adjustment = fee.negate();
        return new SettlementPreview(true, grossPerTown, fee, net, adjustment, adjustment);
    }

    private static SettlementPreview zero() {
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return new SettlementPreview(false, zero, zero, zero, zero, zero);
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
