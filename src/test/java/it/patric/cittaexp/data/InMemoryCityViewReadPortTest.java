package it.patric.cittaexp.data;

import it.patric.cittaexp.preview.PreviewScenario;
import it.patric.cittaexp.ui.contract.CityViewReadPort;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryCityViewReadPortTest {

    private final InMemoryCityViewReadPort port = new InMemoryCityViewReadPort();

    @Test
    void freezeScenarioExposesRestrictedState() {
        CityViewReadPort.CityViewSnapshot snapshot = port.snapshot(UUID.randomUUID(), PreviewScenario.FREEZE);

        assertTrue(snapshot.frozen());
        assertEquals(CityViewReadPort.CityTier.VILLAGGIO, snapshot.tier());
    }

    @Test
    void capitalScenarioExposesCapitalFlag() {
        CityViewReadPort.CityViewSnapshot snapshot = port.snapshot(UUID.randomUUID(), PreviewScenario.CAPITAL);

        assertTrue(snapshot.capital());
        assertFalse(snapshot.frozen());
        assertEquals(1, snapshot.rank());
    }
}
