package it.patric.cittaexp.persistence.runtime;

import it.patric.cittaexp.persistence.config.PersistenceSettings;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

final class SchemaInstaller {

    private final JavaPlugin plugin;
    private final PersistenceSettings settings;
    private final PersistenceModeManager modeManager;

    SchemaInstaller(JavaPlugin plugin, PersistenceSettings settings, PersistenceModeManager modeManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.modeManager = Objects.requireNonNull(modeManager, "modeManager");
    }

    void ensureBaselineSchemas() {
        ensureSqliteSchema();
        if (modeManager.mysqlReachable()) {
            ensureMysqlSchema();
        }
    }

    void ensureSqliteSchema() {
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection connection = DriverManager.getConnection(sqliteUrl())) {
                applySchema(connection, true);
            }
        } catch (ClassNotFoundException | SQLException ex) {
            throw new IllegalStateException("Cannot initialize SQLite schema", ex);
        }
    }

    void ensureMysqlSchema() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection connection = DriverManager.getConnection(
                    modeManager.mysqlUrl(),
                    settings.mysql().username(),
                    settings.mysql().password()
            )) {
                applySchema(connection, false);
            }
        } catch (ClassNotFoundException | SQLException ex) {
            throw new IllegalStateException("Cannot initialize MySQL schema", ex);
        }
    }

    String sqliteUrl() {
        File dbFile = sqliteFile();
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Cannot create sqlite folder: " + parent.getAbsolutePath());
        }
        return "jdbc:sqlite:" + dbFile.getAbsolutePath();
    }

    private File sqliteFile() {
        String configured = settings.sqlite().file();
        File raw = new File(configured);
        return raw.isAbsolute() ? raw : new File(plugin.getDataFolder(), configured);
    }

    private static void applySchema(Connection connection, boolean sqlite) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS cities (
                        city_id VARCHAR(36) PRIMARY KEY,
                        name VARCHAR(64) NOT NULL,
                        name_normalized VARCHAR(64) NOT NULL UNIQUE,
                        tag VARCHAR(16) NOT NULL,
                        tag_normalized VARCHAR(16) NOT NULL UNIQUE,
                        leader_uuid VARCHAR(36) NOT NULL,
                        capital INTEGER NOT NULL,
                        frozen INTEGER NOT NULL,
                        treasury_balance BIGINT NOT NULL,
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL,
                        revision INTEGER NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS city_members (
                        city_id VARCHAR(36) NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        role_key VARCHAR(64) NOT NULL,
                        joined_at BIGINT NOT NULL,
                        active INTEGER NOT NULL,
                        PRIMARY KEY (city_id, player_uuid)
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS city_roles (
                        city_id VARCHAR(36) NOT NULL,
                        role_key VARCHAR(64) NOT NULL,
                        display_name VARCHAR(64) NOT NULL,
                        priority INTEGER NOT NULL,
                        permissions_json TEXT NOT NULL,
                        PRIMARY KEY (city_id, role_key)
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS city_outbox (
                        event_id VARCHAR(36) PRIMARY KEY,
                        aggregate_type VARCHAR(64) NOT NULL,
                        aggregate_id VARCHAR(128) NOT NULL,
                        event_type VARCHAR(64) NOT NULL,
                        payload_json TEXT NOT NULL,
                        occurred_at BIGINT NOT NULL,
                        replay_status VARCHAR(16) NOT NULL,
                        replay_attempts INTEGER NOT NULL,
                        last_error TEXT NOT NULL
                    )
                    """);

            if (sqlite) {
                statement.executeUpdate(
                        "CREATE INDEX IF NOT EXISTS idx_city_outbox_status_occurred ON city_outbox(replay_status, occurred_at)"
                );
            } else {
                try {
                    statement.executeUpdate(
                            "CREATE INDEX idx_city_outbox_status_occurred ON city_outbox(replay_status, occurred_at)"
                    );
                } catch (SQLException ignored) {
                    // index already exists
                }
            }
        }
    }
}
