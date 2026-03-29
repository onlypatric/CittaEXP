package it.patric.cittaexp.challenges;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

public final class MySqlChallengeBackend implements ChallengeBackend {

    public record Config(
            boolean enabled,
            String host,
            int port,
            String database,
            String username,
            String password,
            String params
    ) {
    }

    private final Config config;
    private final String jdbcUrl;

    public MySqlChallengeBackend(Config config) {
        this.config = config;
        String suffix = (config.params() == null || config.params().isBlank()) ? "" : "?" + config.params();
        this.jdbcUrl = "jdbc:mysql://" + config.host() + ":" + config.port() + "/" + config.database() + suffix;
    }

    @Override
    public boolean enabled() {
        return config.enabled();
    }

    @Override
    public void initialize() throws SQLException {
        if (!enabled()) {
            return;
        }
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS challenge_sync_log (
                        op_id VARCHAR(64) PRIMARY KEY,
                        op_type VARCHAR(64) NOT NULL,
                        payload TEXT,
                        created_at VARCHAR(64) NOT NULL,
                        applied_at VARCHAR(64) NOT NULL
                    )
                    """);
        }
    }

    @Override
    public void appendSyncLog(String opId, String opType, String payload, Instant createdAt) throws SQLException {
        if (!enabled()) {
            return;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT IGNORE INTO challenge_sync_log(op_id, op_type, payload, created_at, applied_at)
                             VALUES (?, ?, ?, ?, ?)
                             """)) {
            statement.setString(1, opId);
            statement.setString(2, opType);
            statement.setString(3, payload);
            statement.setString(4, (createdAt == null ? Instant.now() : createdAt).toString());
            statement.setString(5, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, config.username(), config.password());
    }
}
