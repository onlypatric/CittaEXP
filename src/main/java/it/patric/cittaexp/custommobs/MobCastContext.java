package it.patric.cittaexp.custommobs;

import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

public record MobCastContext(
        LivingEntity caster,
        LivingEntity primaryTarget,
        Entity triggerSource,
        MobTriggerSourceKind triggerSourceKind,
        Location origin,
        CustomMobSpawnContext spawnContext,
        Integer ownerTownId,
        String waveId,
        Integer waveNumber,
        UUID targetGuardianUuid,
        Map<String, Object> runtimeVars
) {
}
