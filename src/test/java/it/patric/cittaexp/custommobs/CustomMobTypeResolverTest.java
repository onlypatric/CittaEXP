package it.patric.cittaexp.custommobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class CustomMobTypeResolverTest {

    @Test
    void legacyStyledTokensResolveAgainstRegistryBackedTypes() {
        assumeTrue(registryBackedTypesAvailable(), "Paper registry-backed singleton types are unavailable in the plain unit-test runtime");
        assertEquals("minecraft:max_health", CustomMobTypeResolver.parseAttribute("GENERIC_MAX_HEALTH").getKey().toString());
        assertNotNull(CustomMobTypeResolver.parseSound("ENTITY_WARDEN_ROAR"));
        assertEquals("minecraft:slowness", CustomMobTypeResolver.parsePotionEffectType("SLOWNESS").getKey().toString());
    }

    @Test
    void legacyStyledTokensResolveAgainstJvmSafeTypes() {
        assertEquals(Particle.FLAME, CustomMobTypeResolver.parseParticle("FLAME"));
        assertEquals(EntityType.ZOMBIE, CustomMobTypeResolver.parseEntityType("ZOMBIE"));
        assertEquals(Material.IRON_INGOT, CustomMobTypeResolver.parseMaterial("IRON_INGOT"));
    }

    @Test
    void namespacedTokensResolveAgainstRegistryKeys() {
        assumeTrue(registryBackedTypesAvailable(), "Paper registry-backed singleton types are unavailable in the plain unit-test runtime");
        assertNotNull(CustomMobTypeResolver.parsePotionEffectType("minecraft:wither"));
        assertEquals("minecraft:wither", CustomMobTypeResolver.parsePotionEffectType("minecraft:wither").getKey().toString());
    }

    @Test
    void namespacedTokensResolveAgainstJvmSafeTypes() {
        assertEquals(EntityType.SKELETON, CustomMobTypeResolver.parseEntityType("minecraft:skeleton"));
        assertEquals(Particle.SOUL, CustomMobTypeResolver.parseParticle("minecraft:soul"));
    }

    private static boolean registryBackedTypesAvailable() {
        try {
            return CustomMobTypeResolver.parseAttribute("GENERIC_MAX_HEALTH") != null
                    && CustomMobTypeResolver.parseSound("ENTITY_WARDEN_ROAR") != null
                    && CustomMobTypeResolver.parsePotionEffectType("SLOWNESS") != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
