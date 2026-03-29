package it.patric.cittaexp.war;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

final class CityWarPayoutPolicyTest {

    @Test
    void attackerWinAppliesFeeToTotalPot() {
        CityWarSettings.PayoutSettings settings = new CityWarSettings.PayoutSettings(true, 0.10D, false);

        CityWarPayoutPolicy.SettlementPreview preview = CityWarPayoutPolicy.resolve(
                settings,
                CityWarPayoutPolicy.Outcome.ATTACKER_WIN,
                BigDecimal.valueOf(1_000L)
        );

        assertTrue(preview.enabled());
        assertEquals(BigDecimal.valueOf(2000L).setScale(2), preview.grossPayout());
        assertEquals(BigDecimal.valueOf(200L).setScale(2), preview.feeAmount());
        assertEquals(BigDecimal.valueOf(1800L).setScale(2), preview.netPayout());
        assertEquals(BigDecimal.valueOf(-200L).setScale(2), preview.attackerAdjustment());
        assertEquals(BigDecimal.ZERO.setScale(2), preview.defenderAdjustment());
    }

    @Test
    void timeoutOnlyAdjustsWhenEnabled() {
        CityWarSettings.PayoutSettings disabledTimeout = new CityWarSettings.PayoutSettings(true, 0.10D, false);
        CityWarPayoutPolicy.SettlementPreview skipped = CityWarPayoutPolicy.resolve(
                disabledTimeout,
                CityWarPayoutPolicy.Outcome.TIME_OUT,
                BigDecimal.valueOf(1_000L)
        );
        assertFalse(skipped.enabled());

        CityWarSettings.PayoutSettings enabledTimeout = new CityWarSettings.PayoutSettings(true, 0.10D, true);
        CityWarPayoutPolicy.SettlementPreview applied = CityWarPayoutPolicy.resolve(
                enabledTimeout,
                CityWarPayoutPolicy.Outcome.TIME_OUT,
                BigDecimal.valueOf(1_000L)
        );
        assertTrue(applied.enabled());
        assertEquals(BigDecimal.valueOf(100L).setScale(2), applied.feeAmount());
        assertEquals(BigDecimal.valueOf(900L).setScale(2), applied.netPayout());
        assertEquals(BigDecimal.valueOf(-100L).setScale(2), applied.attackerAdjustment());
        assertEquals(BigDecimal.valueOf(-100L).setScale(2), applied.defenderAdjustment());
    }
}
