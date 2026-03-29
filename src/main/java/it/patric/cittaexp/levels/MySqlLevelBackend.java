package it.patric.cittaexp.levels;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;

public final class MySqlLevelBackend {
    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }


    public record Config(
            boolean enabled,
            String host,
            int port,
            String database,
            String username,
            String password,
            String params
    ) {
        public String jdbcUrl() {
            String suffix = params == null || params.isBlank()
                    ? "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                    : params;
            return "jdbc:mysql://" + host + ':' + port + '/' + database + '?' + suffix;
        }
    }

    private final Config config;

    public MySqlLevelBackend(Config config) {
        this.config = config;
    }

    public boolean enabled() {
        return config.enabled();
    }

    public void initialize() throws SQLException {
        if (!enabled()) {
            return;
        }
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_progression (
                        town_id INT PRIMARY KEY,
                        xp_scaled BIGINT NOT NULL,
                        city_level INT NOT NULL,
                        unlocked_level INT NOT NULL DEFAULT 1,
                        stage VARCHAR(32) NOT NULL,
                        stage_seals_earned INT NOT NULL DEFAULT 0,
                        stage_seals_required INT NOT NULL DEFAULT 6,
                        stage_completed_at VARCHAR(64),
                        subtier_completed_at VARCHAR(64),
                        updated_at VARCHAR(64) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_material_highwater (
                        town_id INT NOT NULL,
                        material VARCHAR(64) NOT NULL,
                        max_amount BIGINT NOT NULL,
                        PRIMARY KEY (town_id, material)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_scan_state (
                        town_id INT PRIMARY KEY,
                        last_scan_at VARCHAR(64),
                        last_awarded_scaled BIGINT NOT NULL,
                        last_error TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_tax_state (
                        town_id INT PRIMARY KEY,
                        debt DOUBLE NOT NULL,
                        updated_at VARCHAR(64) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_tax_ledger_v2 (
                        event_id BIGINT PRIMARY KEY,
                        town_id INT NOT NULL,
                        month_key VARCHAR(16) NOT NULL,
                        stage VARCHAR(32) NOT NULL,
                        due DOUBLE NOT NULL,
                        paid DOUBLE NOT NULL,
                        debt_after DOUBLE NOT NULL,
                        status VARCHAR(32) NOT NULL,
                        processed_at VARCHAR(64) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_xp_ledger (
                        event_id BIGINT PRIMARY KEY,
                        town_id INT NOT NULL,
                        delta_xp_scaled BIGINT NOT NULL,
                        source VARCHAR(64) NOT NULL,
                        actor_uuid VARCHAR(64),
                        reason TEXT,
                        resulting_xp_scaled BIGINT NOT NULL,
                        resulting_level INT NOT NULL,
                        occurred_at VARCHAR(64) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS staff_approval_requests (
                        id BIGINT PRIMARY KEY,
                        town_id INT NOT NULL,
                        request_type VARCHAR(64) NOT NULL,
                        payload TEXT,
                        status VARCHAR(32) NOT NULL,
                        requested_by VARCHAR(64) NOT NULL,
                        reviewed_by VARCHAR(64),
                        reason TEXT,
                        note TEXT,
                        created_at VARCHAR(64) NOT NULL,
                        reviewed_at VARCHAR(64)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS staff_approval_events (
                        event_id BIGINT PRIMARY KEY,
                        request_id BIGINT NOT NULL,
                        town_id INT NOT NULL,
                        event_type VARCHAR(32) NOT NULL,
                        actor_uuid VARCHAR(64),
                        payload TEXT,
                        created_at VARCHAR(64) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS system_state (
                        state_key VARCHAR(128) PRIMARY KEY,
                        state_value TEXT
                    )
                    """);
            statement.execute("ALTER TABLE town_progression ADD COLUMN IF NOT EXISTS stage_seals_earned INT NOT NULL DEFAULT 0");
            statement.execute("ALTER TABLE town_progression ADD COLUMN IF NOT EXISTS stage_seals_required INT NOT NULL DEFAULT 6");
            statement.execute("ALTER TABLE town_progression ADD COLUMN IF NOT EXISTS stage_completed_at VARCHAR(64) NULL");
            statement.execute("ALTER TABLE town_progression ADD COLUMN IF NOT EXISTS subtier_completed_at VARCHAR(64) NULL");
            statement.execute("ALTER TABLE town_progression ADD COLUMN IF NOT EXISTS unlocked_level INT NOT NULL DEFAULT 1");
            statement.execute("UPDATE town_progression SET unlocked_level = city_level WHERE unlocked_level IS NULL OR unlocked_level <= 0");
        }
    }

    public void upsertProgression(TownProgression progression) throws SQLException {
        executeUpdate(
                """
                        INSERT INTO town_progression(town_id, xp_scaled, city_level, unlocked_level, stage, stage_seals_earned, stage_seals_required,
                                                     stage_completed_at, subtier_completed_at, updated_at)
                        VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            xp_scaled = VALUES(xp_scaled),
                            city_level = VALUES(city_level),
                            unlocked_level = VALUES(unlocked_level),
                            stage = VALUES(stage),
                            stage_seals_earned = VALUES(stage_seals_earned),
                            stage_seals_required = VALUES(stage_seals_required),
                            stage_completed_at = VALUES(stage_completed_at),
                            subtier_completed_at = VALUES(subtier_completed_at),
                            updated_at = VALUES(updated_at)
                        """,
                statement -> {
                    statement.setInt(1, progression.townId());
                    statement.setLong(2, progression.xpScaled());
                    statement.setInt(3, progression.cityLevel());
                    statement.setInt(4, progression.cityLevel());
                    statement.setString(5, progression.stage().name());
                    statement.setInt(6, progression.stageSealsEarned());
                    statement.setInt(7, progression.stageSealsRequired());
                    statement.setString(8, progression.stageCompletedAt() == null ? null : progression.stageCompletedAt().toString());
                    statement.setString(9, progression.subTierCompletedAt() == null ? null : progression.subTierCompletedAt().toString());
                    statement.setString(10, progression.updatedAt().toString());
                }
        );
    }

    public void upsertHighwater(int townId, Map<String, Long> values) throws SQLException {
        if (!enabled() || values == null || values.isEmpty()) {
            return;
        }
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                """
                        INSERT INTO town_material_highwater(town_id, material, max_amount)
                        VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE max_amount = VALUES(max_amount)
                        """)) {
            for (Map.Entry<String, Long> entry : values.entrySet()) {
                statement.setInt(1, townId);
                statement.setString(2, entry.getKey());
                statement.setLong(3, entry.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public void upsertScanState(int townId, Instant lastScanAt, long lastAwardedScaled, String lastError) throws SQLException {
        executeUpdate(
                """
                        INSERT INTO town_scan_state(town_id, last_scan_at, last_awarded_scaled, last_error)
                        VALUES (?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            last_scan_at = VALUES(last_scan_at),
                            last_awarded_scaled = VALUES(last_awarded_scaled),
                            last_error = VALUES(last_error)
                        """,
                statement -> {
                    statement.setInt(1, townId);
                    statement.setString(2, lastScanAt == null ? null : lastScanAt.toString());
                    statement.setLong(3, lastAwardedScaled);
                    statement.setString(4, lastError);
                }
        );
    }

    public void upsertDebt(int townId, double debt, Instant updatedAt) throws SQLException {
        executeUpdate(
                """
                        INSERT INTO town_tax_state(town_id, debt, updated_at)
                        VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE debt = VALUES(debt), updated_at = VALUES(updated_at)
                        """,
                statement -> {
                    statement.setInt(1, townId);
                    statement.setDouble(2, debt);
                    statement.setString(3, updatedAt.toString());
                }
        );
    }

    public void appendTaxLedger(
            long eventId,
            int townId,
            String monthKey,
            TownStage stage,
            double due,
            double paid,
            double debtAfter,
            String status,
            Instant processedAt
    ) throws SQLException {
        executeUpdate(
                """
                        INSERT IGNORE INTO town_tax_ledger_v2(event_id, town_id, month_key, stage, due, paid, debt_after, status, processed_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                statement -> {
                    statement.setLong(1, eventId);
                    statement.setInt(2, townId);
                    statement.setString(3, monthKey);
                    statement.setString(4, stage.name());
                    statement.setDouble(5, due);
                    statement.setDouble(6, paid);
                    statement.setDouble(7, debtAfter);
                    statement.setString(8, status);
                    statement.setString(9, processedAt.toString());
                }
        );
    }

    public void appendXpLedger(
            long eventId,
            int townId,
            long deltaXpScaled,
            String source,
            String actorUuid,
            String reason,
            long resultingXpScaled,
            int resultingLevel,
            Instant occurredAt
    ) throws SQLException {
        executeUpdate(
                """
                        INSERT IGNORE INTO town_xp_ledger(event_id, town_id, delta_xp_scaled, source, actor_uuid, reason,
                                                          resulting_xp_scaled, resulting_level, occurred_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                statement -> {
                    statement.setLong(1, eventId);
                    statement.setInt(2, townId);
                    statement.setLong(3, deltaXpScaled);
                    statement.setString(4, source);
                    statement.setString(5, actorUuid);
                    statement.setString(6, reason);
                    statement.setLong(7, resultingXpScaled);
                    statement.setInt(8, resultingLevel);
                    statement.setString(9, occurredAt.toString());
                }
        );
    }

    public void upsertApprovalRequest(StaffApprovalRequest request) throws SQLException {
        executeUpdate(
                """
                        INSERT INTO staff_approval_requests(id, town_id, request_type, payload, status, requested_by,
                                                           reviewed_by, reason, note, created_at, reviewed_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            town_id = VALUES(town_id),
                            request_type = VALUES(request_type),
                            payload = VALUES(payload),
                            status = VALUES(status),
                            reviewed_by = VALUES(reviewed_by),
                            reason = VALUES(reason),
                            note = VALUES(note),
                            reviewed_at = VALUES(reviewed_at)
                        """,
                statement -> {
                    statement.setLong(1, request.id());
                    statement.setInt(2, request.townId());
                    statement.setString(3, request.type().name());
                    statement.setString(4, request.payload());
                    statement.setString(5, request.status().name());
                    statement.setString(6, request.requestedBy().toString());
                    statement.setString(7, request.reviewedBy() == null ? null : request.reviewedBy().toString());
                    statement.setString(8, request.reason());
                    statement.setString(9, request.note());
                    statement.setString(10, request.createdAt().toString());
                    statement.setString(11, request.reviewedAt() == null ? null : request.reviewedAt().toString());
                }
        );
    }

    public void appendApprovalEvent(
            long eventId,
            long requestId,
            int townId,
            String eventType,
            String actorUuid,
            String payload,
            Instant createdAt
    ) throws SQLException {
        executeUpdate(
                """
                        INSERT IGNORE INTO staff_approval_events(event_id, request_id, town_id, event_type, actor_uuid, payload, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                statement -> {
                    statement.setLong(1, eventId);
                    statement.setLong(2, requestId);
                    statement.setInt(3, townId);
                    statement.setString(4, eventType);
                    statement.setString(5, actorUuid);
                    statement.setString(6, payload);
                    statement.setString(7, createdAt.toString());
                }
        );
    }

    public void setSystemState(String key, String value) throws SQLException {
        executeUpdate(
                """
                        INSERT INTO system_state(state_key, state_value)
                        VALUES (?, ?)
                        ON DUPLICATE KEY UPDATE state_value = VALUES(state_value)
                        """,
                statement -> {
                    statement.setString(1, key);
                    statement.setString(2, value);
                }
        );
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password());
    }

    private void executeUpdate(String sql, SqlBinder binder) throws SQLException {
        if (!enabled()) {
            return;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            statement.executeUpdate();
        }
    }
}
