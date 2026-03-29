package it.patric.cittaexp.war;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CityWarSettingsRoundingTest {

    @Test
    void parsesConfiguredRoundingValues() {
        assertEquals(CityWarSettings.XpRewardRounding.CEIL, CityWarSettings.XpRewardRounding.fromConfig("ceil"));
        assertEquals(CityWarSettings.XpRewardRounding.HALF_UP, CityWarSettings.XpRewardRounding.fromConfig("half_up"));
        assertEquals(CityWarSettings.XpRewardRounding.FLOOR, CityWarSettings.XpRewardRounding.fromConfig("floor"));
        assertEquals(CityWarSettings.XpRewardRounding.CEIL, CityWarSettings.XpRewardRounding.fromConfig(null));
    }
}
