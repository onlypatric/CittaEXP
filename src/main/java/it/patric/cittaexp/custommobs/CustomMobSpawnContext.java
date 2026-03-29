package it.patric.cittaexp.custommobs;

import java.util.UUID;

public record CustomMobSpawnContext(
        String spawnSource,
        Integer ownerTownId,
        String sessionId,
        String encounterId,
        String waveId,
        Integer waveNumber,
        UUID targetGuardianUuid,
        String defenseTier,
        String debugLabel,
        boolean summoned,
        UUID parentMobUuid,
        CustomMobSpawnAdapter spawnAdapter
) {

    public static CustomMobSpawnContext simple(String spawnSource) {
        return new CustomMobSpawnContext(spawnSource, null, null, null, null, null, null, null, null, false, null, null);
    }

    public CustomMobSpawnContext summonedChild(UUID parentMobUuid) {
        return new CustomMobSpawnContext(
                spawnSource,
                ownerTownId,
                sessionId,
                encounterId,
                waveId,
                waveNumber,
                targetGuardianUuid,
                defenseTier,
                debugLabel,
                true,
                parentMobUuid,
                spawnAdapter
        );
    }
}
