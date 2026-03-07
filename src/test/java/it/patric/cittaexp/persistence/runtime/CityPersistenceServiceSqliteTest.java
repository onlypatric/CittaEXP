package it.patric.cittaexp.persistence.runtime;

import it.patric.cittaexp.core.model.CityStatus;
import it.patric.cittaexp.core.model.CityTier;
import it.patric.cittaexp.persistence.config.PersistenceSettings;
import it.patric.cittaexp.persistence.domain.CityInvitationRecord;
import it.patric.cittaexp.persistence.domain.CityMemberRecord;
import it.patric.cittaexp.persistence.domain.CityRecord;
import it.patric.cittaexp.persistence.domain.CityRoleRecord;
import it.patric.cittaexp.persistence.domain.FreezeCaseRecord;
import it.patric.cittaexp.persistence.domain.PersistenceWriteOutcome;
import it.patric.cittaexp.core.model.FreezeReason;
import it.patric.cittaexp.core.model.InvitationStatus;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CityPersistenceServiceSqliteTest {

    @TempDir
    Path tempDir;

    private CityPersistenceService service;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());

        PersistenceSettings settings = new PersistenceSettings(
                PersistenceSettings.PersistenceMode.SQLITE,
                true,
                new PersistenceSettings.MysqlSettings("127.0.0.1", 3306, "cittaexp", "root", "", ""),
                new PersistenceSettings.SqliteSettings("data/test.sqlite"),
                new PersistenceSettings.ReplaySettings(50, 5)
        );

        PersistenceModeManager modeManager = new PersistenceModeManager(settings, Logger.getLogger("test"));
        modeManager.initialize();

        SchemaInstaller schemaInstaller = new SchemaInstaller(plugin, settings, modeManager);
        service = new CityPersistenceService(plugin, settings, modeManager, schemaInstaller, Logger.getLogger("test"));
        service.ensureSchema();
    }

    @Test
    void createAndReadCityWorks() {
        UUID cityId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        long now = System.currentTimeMillis();

        PersistenceWriteOutcome created = service.createCity(new CityRecord(
                cityId,
                "Aurora",
                "AUR",
                leaderId,
                CityTier.BORGO,
                CityStatus.ACTIVE,
                false,
                false,
                1200L,
                1,
                10,
                now,
                now,
                0
        ));

        assertTrue(created.success());

        Optional<CityRecord> loaded = service.findCityById(cityId);
        assertTrue(loaded.isPresent());
        assertEquals("Aurora", loaded.get().name());
        assertEquals("AUR", loaded.get().tag());
    }

    @Test
    void optimisticLockingReturnsConflictOnStaleRevision() {
        UUID cityId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        long now = System.currentTimeMillis();

        service.createCity(new CityRecord(
                cityId,
                "Brina",
                "BRI",
                leaderId,
                CityTier.BORGO,
                CityStatus.ACTIVE,
                false,
                false,
                2000L,
                1,
                10,
                now,
                now,
                0
        ));

        PersistenceWriteOutcome stale = service.updateCity(new CityRecord(
                cityId,
                "Brina Nuova",
                "BRI",
                leaderId,
                CityTier.BORGO,
                CityStatus.ACTIVE,
                false,
                false,
                2500L,
                1,
                10,
                now,
                now + 1000,
                0
        ), 9);

        assertFalse(stale.success());
        assertTrue(stale.conflict());
    }

    @Test
    void fallbackWritesPopulateOutboxQueue() {
        UUID cityId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        long now = System.currentTimeMillis();

        service.createCity(new CityRecord(
                cityId,
                "Cenere",
                "CEN",
                leaderId,
                CityTier.BORGO,
                CityStatus.ACTIVE,
                false,
                false,
                1000L,
                1,
                10,
                now,
                now,
                0
        ));
        service.upsertMember(new CityMemberRecord(cityId, playerId, "membro", now, true));
        service.upsertRole(new CityRoleRecord(cityId, "membro", "Membro", 1, "{\"invite\":false}"));

        assertTrue(service.pendingCount() >= 3L);
    }

    @Test
    void invitationAndFreezeReadWriteWorks() {
        long now = System.currentTimeMillis();
        UUID cityId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        service.createCity(new CityRecord(
                cityId,
                "Fortezza",
                "FOR",
                leaderId,
                CityTier.BORGO,
                CityStatus.ACTIVE,
                false,
                false,
                0L,
                1,
                10,
                now,
                now,
                0
        ));

        CityInvitationRecord invitation = new CityInvitationRecord(
                UUID.randomUUID(),
                cityId,
                UUID.randomUUID(),
                leaderId,
                InvitationStatus.PENDING,
                now,
                now + 1000L,
                now
        );
        FreezeCaseRecord freezeCase = new FreezeCaseRecord(
                UUID.randomUUID(),
                cityId,
                FreezeReason.STAFF_MANUAL,
                "manual",
                true,
                leaderId,
                null,
                now,
                0L
        );

        assertTrue(service.upsertInvitation(invitation).success());
        assertTrue(service.upsertFreezeCase(freezeCase).success());
        assertTrue(service.findInvitation(invitation.invitationId()).isPresent());
        assertTrue(service.findActiveFreezeCase(cityId).isPresent());
    }
}
