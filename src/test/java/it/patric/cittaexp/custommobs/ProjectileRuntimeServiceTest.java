package it.patric.cittaexp.custommobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectileRuntimeServiceTest {

    @Test
    void trackedProjectilesCanBeCancelledByOwnerAndSession() {
        ProjectileRuntimeService service = new ProjectileRuntimeService();
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();

        ProjectileRuntimeService.RuntimeHandle first = service.create(ownerA, new CustomMobSpawnContext(
                "city_defense",
                7,
                "session-a",
                "encounter-a",
                "session-a:3",
                3,
                null,
                "L3",
                "debug-a",
                false,
                null,
                null
        ));
        ProjectileRuntimeService.RuntimeHandle second = service.create(ownerB, new CustomMobSpawnContext(
                "city_defense",
                9,
                "session-b",
                "encounter-b",
                "session-b:5",
                5,
                null,
                "L5",
                "debug-b",
                false,
                null,
                null
        ));

        assertEquals(2, service.activeCount());

        service.cancelForOwner(ownerA);
        assertEquals(1, service.activeCount());
        assertEquals(second.runtimeId(), service.snapshots().getFirst().runtimeId());

        service.cancelForSession("session-b");
        assertEquals(0, service.activeCount());

        // Idempotence on already-cleared handles.
        first.cancel();
        second.cancel();
        assertTrue(service.snapshots().isEmpty());
    }

    @Test
    void snapshotsPreserveEncounterMetadata() {
        ProjectileRuntimeService service = new ProjectileRuntimeService();
        UUID owner = UUID.randomUUID();

        ProjectileRuntimeService.RuntimeHandle handle = service.create(owner, new CustomMobSpawnContext(
                "city_defense",
                12,
                "session-z",
                "encounter-z",
                "session-z:7",
                7,
                null,
                "L7",
                "debug-z",
                true,
                UUID.randomUUID(),
                null
        ));

        ProjectileRuntimeService.ProjectileRuntimeSnapshot snapshot = service.snapshots().getFirst();
        assertEquals(handle.runtimeId(), snapshot.runtimeId());
        assertEquals(owner, snapshot.ownerEntityId());
        assertEquals("city_defense", snapshot.spawnSource());
        assertEquals(12, snapshot.ownerTownId());
        assertEquals("session-z", snapshot.sessionId());
        assertEquals("encounter-z", snapshot.encounterId());
        assertEquals("session-z:7", snapshot.waveId());
        assertEquals(7, snapshot.waveNumber());
        assertTrue(snapshot.startedAt() != null);
    }
}
