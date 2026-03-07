package it.patric.cittaexp.persistence.runtime;

import it.patric.cittaexp.core.model.CityStatus;
import it.patric.cittaexp.core.model.CityTier;
import it.patric.cittaexp.persistence.config.PersistenceSettings;
import it.patric.cittaexp.persistence.domain.CityRecord;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("full")
@Testcontainers(disabledWithoutDocker = true)
class CityPersistenceServiceMysqlContainerTest {

    @Container
    @SuppressWarnings("resource")
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.39")
            .withDatabaseName("cittaexp")
            .withUsername("test")
            .withPassword("test");

    @TempDir
    Path tempDir;

    private CityPersistenceService service;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());

        PersistenceSettings settings = new PersistenceSettings(
                PersistenceSettings.PersistenceMode.MYSQL,
                true,
                new PersistenceSettings.MysqlSettings(
                        MYSQL.getHost(),
                        MYSQL.getFirstMappedPort(),
                        MYSQL.getDatabaseName(),
                        MYSQL.getUsername(),
                        MYSQL.getPassword(),
                        "useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true"
                ),
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
    void mysqlContainerCreateAndReadCityWorks() {
        UUID cityId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        long now = System.currentTimeMillis();

        assertTrue(service.createCity(new CityRecord(
                cityId,
                "ContainerCity",
                "CNT",
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
        )).success());

        assertTrue(service.findCityById(cityId).isPresent());
        assertEquals("ContainerCity", service.findCityById(cityId).orElseThrow().name());
    }
}
