package it.patric.cittaexp.persistence.runtime;

import it.patric.cittaexp.persistence.config.PersistenceSettings;
import it.patric.cittaexp.persistence.domain.CityMemberRecord;
import it.patric.cittaexp.persistence.domain.CityRecord;
import it.patric.cittaexp.persistence.domain.CityRoleRecord;
import it.patric.cittaexp.persistence.domain.PersistenceWriteOutcome;
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
                false,
                false,
                1200L,
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
                false,
                false,
                2000L,
                now,
                now,
                0
        ));

        PersistenceWriteOutcome stale = service.updateCity(new CityRecord(
                cityId,
                "Brina Nuova",
                "BRI",
                leaderId,
                false,
                false,
                2500L,
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
                false,
                false,
                1000L,
                now,
                now,
                0
        ));
        service.upsertMember(new CityMemberRecord(cityId, playerId, "membro", now, true));
        service.upsertRole(new CityRoleRecord(cityId, "membro", "Membro", 1, "{\"invite\":false}"));

        assertTrue(service.pendingCount() >= 3L);
    }
}
