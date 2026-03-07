package it.patric.cittaexp.persistence.runtime;

import it.patric.cittaexp.persistence.config.PersistenceSettings;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersistenceModeManagerTest {

    @Test
    void sqliteConfiguredBootsInSqliteFallbackMode() {
        PersistenceSettings settings = new PersistenceSettings(
                PersistenceSettings.PersistenceMode.SQLITE,
                true,
                new PersistenceSettings.MysqlSettings("127.0.0.1", 3306, "cittaexp", "root", "", ""),
                new PersistenceSettings.SqliteSettings("data/test.sqlite"),
                new PersistenceSettings.ReplaySettings(100, 10)
        );

        PersistenceModeManager manager = new PersistenceModeManager(settings, Logger.getLogger("test"));
        PersistenceRuntimeMode mode = manager.initialize();

        assertEquals(PersistenceRuntimeMode.SQLITE_FALLBACK, mode);
    }
}
