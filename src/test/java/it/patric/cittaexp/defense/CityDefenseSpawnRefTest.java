package it.patric.cittaexp.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class CityDefenseSpawnRefTest {

    @Test
    void parsesVanillaRef() {
        CityDefenseSpawnRef ref = CityDefenseSpawnRef.parse("zombie");
        assertTrue(ref.isVanilla());
        assertEquals(EntityType.ZOMBIE, ref.vanillaType());
        assertEquals("ZOMBIE", ref.configToken());
    }

    @Test
    void parsesCustomRef() {
        CityDefenseSpawnRef ref = CityDefenseSpawnRef.parse("custom:ember_guard");
        assertTrue(ref.isCustom());
        assertEquals("ember_guard", ref.customMobId());
        assertEquals("custom:ember_guard", ref.configToken());
    }
}
