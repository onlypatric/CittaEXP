package it.patric.cittaexp.war;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

final class CityWarXpRewardPolicyTest {

    @Test
    void attackerWinUsesBasePlusWagerBonus() {
        CityWarSettings.XpRewardSettings settings = settings(true, true, true, false, 200.0D, 240.0D, 0.0D, 0.01D, 400.0D, CityWarSettings.XpRewardRounding.CEIL);

        CityWarXpRewardPolicy.RewardPreview preview = CityWarXpRewardPolicy.resolve(settings, CityWarXpRewardPolicy.Outcome.ATTACKER_WIN, BigDecimal.valueOf(8_000L));

        assertTrue(preview.enabled());
        assertEquals(200.0D, preview.baseXp());
        assertEquals(80.0D, preview.wagerBonusXp());
        assertEquals(280.0D, preview.totalXp());
    }

    @Test
    void defenderWinUsesOwnBaseAndCap() {
        CityWarSettings.XpRewardSettings settings = settings(true, true, true, false, 200.0D, 240.0D, 0.0D, 0.05D, 300.0D, CityWarSettings.XpRewardRounding.CEIL);

        CityWarXpRewardPolicy.RewardPreview preview = CityWarXpRewardPolicy.resolve(settings, CityWarXpRewardPolicy.Outcome.DEFENDER_WIN, BigDecimal.valueOf(20_000L));

        assertTrue(preview.enabled());
        assertEquals(240.0D, preview.baseXp());
        assertEquals(300.0D, preview.wagerBonusXp());
        assertEquals(540.0D, preview.totalXp());
    }

    @Test
    void timeoutDoesNotAwardWhenDisabled() {
        CityWarSettings.XpRewardSettings settings = settings(true, true, true, false, 200.0D, 240.0D, 50.0D, 0.01D, 400.0D, CityWarSettings.XpRewardRounding.CEIL);

        CityWarXpRewardPolicy.RewardPreview preview = CityWarXpRewardPolicy.resolve(settings, CityWarXpRewardPolicy.Outcome.TIME_OUT, BigDecimal.valueOf(10_000L));

        assertFalse(preview.enabled());
        assertEquals(0.0D, preview.totalXp());
    }

    @Test
    void halfUpRoundingRoundsToNearestWholeXp() {
        CityWarSettings.XpRewardSettings settings = settings(true, true, true, false, 100.25D, 120.0D, 0.0D, 0.001D, 100.0D, CityWarSettings.XpRewardRounding.HALF_UP);

        CityWarXpRewardPolicy.RewardPreview preview = CityWarXpRewardPolicy.resolve(settings, CityWarXpRewardPolicy.Outcome.ATTACKER_WIN, BigDecimal.valueOf(249L));

        assertTrue(preview.enabled());
        assertEquals(0.249D, preview.wagerBonusXp(), 0.0001D);
        assertEquals(100.0D, preview.totalXp());
    }

    private static CityWarSettings.XpRewardSettings settings(
            boolean enabled,
            boolean attackerWin,
            boolean defenderWin,
            boolean timeout,
            double attackerBase,
            double defenderBase,
            double timeoutBase,
            double wagerMultiplier,
            double wagerCap,
            CityWarSettings.XpRewardRounding rounding
    ) {
        return new CityWarSettings.XpRewardSettings(
                enabled,
                attackerWin,
                defenderWin,
                timeout,
                attackerBase,
                defenderBase,
                timeoutBase,
                wagerMultiplier,
                wagerCap,
                rounding
        );
    }
}
