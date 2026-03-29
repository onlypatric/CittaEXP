package it.patric.cittaexp.custommobs;

import org.bukkit.entity.LivingEntity;

@FunctionalInterface
public interface CustomMobSpawnAdapter {
    void afterSpawn(LivingEntity entity, CustomMobSpawnContext context);
}
