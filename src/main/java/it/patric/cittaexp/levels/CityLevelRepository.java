package it.patric.cittaexp.levels;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.plugin.Plugin;

public final class CityLevelRepository {

    public enum RequestStatus {
        PENDING,
        APPROVED,
        REJECTED,
        CANCELLED
    }

    public record ScanStats(long townCount, long totalXpScaled, Instant lastScanAt) {
    }

    public record OutboxItem(
            long opId,
            String opType,
            String payload,
            String status,
            int attempts,
            Instant nextAttemptAt,
            String lastError
    ) {
    }

    private final String jdbcUrl;

    public CityLevelRepository(Plugin plugin) {
        Path path = plugin.getDataFolder().toPath().resolve("levels.db");
        this.jdbcUrl = "jdbc:sqlite:" + path;
    }

    public synchronized void initialize() {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_progression (
                        town_id INTEGER PRIMARY KEY,
                        xp_scaled INTEGER NOT NULL,
                        city_level INTEGER NOT NULL,
                        stage TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_material_highwater (
                        town_id INTEGER NOT NULL,
                        material TEXT NOT NULL,
                        max_amount INTEGER NOT NULL,
                        PRIMARY KEY (town_id, material)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_level_requests (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        town_id INTEGER NOT NULL,
                        target_stage TEXT NOT NULL,
                        status TEXT NOT NULL,
                        requested_by TEXT NOT NULL,
                        reviewed_by TEXT,
                        reason TEXT,
                        note TEXT,
                        created_at TEXT NOT NULL,
                        reviewed_at TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_tax_ledger (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        town_id INTEGER NOT NULL,
                        month_key TEXT NOT NULL,
                        stage TEXT NOT NULL,
                        due REAL NOT NULL,
                        paid REAL NOT NULL,
                        debt_after REAL NOT NULL,
                        status TEXT NOT NULL,
                        processed_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_tax_ledger_v2 (
                        event_id INTEGER PRIMARY KEY,
                        town_id INTEGER NOT NULL,
                        month_key TEXT NOT NULL,
                        stage TEXT NOT NULL,
                        due REAL NOT NULL,
                        paid REAL NOT NULL,
                        debt_after REAL NOT NULL,
                        status TEXT NOT NULL,
                        processed_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_tax_state (
                        town_id INTEGER PRIMARY KEY,
                        debt REAL NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_scan_state (
                        town_id INTEGER PRIMARY KEY,
                        last_scan_at TEXT,
                        last_awarded_scaled INTEGER NOT NULL,
                        last_error TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS system_state (
                        key TEXT PRIMARY KEY,
                        value TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_xp_ledger (
                        event_id INTEGER PRIMARY KEY,
                        town_id INTEGER NOT NULL,
                        delta_xp_scaled INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        actor_uuid TEXT,
                        reason TEXT,
                        resulting_xp_scaled INTEGER NOT NULL,
                        resulting_level INTEGER NOT NULL,
                        occurred_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS staff_approval_requests (
                        id INTEGER PRIMARY KEY,
                        town_id INTEGER NOT NULL,
                        request_type TEXT NOT NULL,
                        payload TEXT,
                        status TEXT NOT NULL,
                        requested_by TEXT NOT NULL,
                        reviewed_by TEXT,
                        reason TEXT,
                        note TEXT,
                        created_at TEXT NOT NULL,
                        reviewed_at TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS staff_approval_events (
                        event_id INTEGER PRIMARY KEY,
                        request_id INTEGER NOT NULL,
                        town_id INTEGER NOT NULL,
                        event_type TEXT NOT NULL,
                        actor_uuid TEXT,
                        payload TEXT,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sync_outbox (
                        op_id INTEGER PRIMARY KEY,
                        op_type TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        status TEXT NOT NULL,
                        attempts INTEGER NOT NULL,
                        next_attempt_at TEXT,
                        last_error TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile inizializzare levels.db", exception);
        }
    }

    public synchronized void setDebt(int townId, double debt) {
        Instant now = Instant.now();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO town_tax_state(town_id, debt, updated_at)
                             VALUES (?, ?, ?)
                             ON CONFLICT(town_id) DO UPDATE SET debt = excluded.debt, updated_at = excluded.updated_at
                             """)) {
            statement.setInt(1, townId);
            statement.setDouble(2, debt);
            statement.setString(3, now.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile aggiornare debt town=" + townId, exception);
        }
    }

    public synchronized void appendTaxLedgerV2(
            long eventId,
            int townId,
            String monthKey,
            TownStage stage,
            double due,
            double paid,
            double debtAfter,
            String status,
            Instant processedAt
    ) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT OR IGNORE INTO town_tax_ledger_v2(event_id, town_id, month_key, stage, due, paid, debt_after, status, processed_at)
                             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                             """)) {
            statement.setLong(1, eventId);
            statement.setInt(2, townId);
            statement.setString(3, monthKey);
            statement.setString(4, stage.name());
            statement.setDouble(5, due);
            statement.setDouble(6, paid);
            statement.setDouble(7, debtAfter);
            statement.setString(8, status);
            statement.setString(9, processedAt.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile append tax ledger v2 town=" + townId, exception);
        }
    }

    public synchronized void setSystemState(String key, String value) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO system_state(key, value)
                             VALUES (?, ?)
                             ON CONFLICT(key) DO UPDATE SET value = excluded.value
                             """)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile aggiornare system_state key=" + key, exception);
        }
    }

    public synchronized Map<Integer, TownProgression> loadAllProgressions() {
        Map<Integer, TownProgression> result = new HashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT town_id, xp_scaled, city_level, stage, updated_at FROM town_progression");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                TownProgression progression = new TownProgression(
                        rs.getInt("town_id"),
                        rs.getLong("xp_scaled"),
                        rs.getInt("city_level"),
                        TownStage.fromDbValue(rs.getString("stage")),
                        parseInstant(rs.getString("updated_at"))
                );
                result.put(progression.townId(), progression);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare progression cache", exception);
        }
        return result;
    }

    public synchronized Map<Integer, Map<String, Long>> loadAllHighwater() {
        Map<Integer, Map<String, Long>> result = new HashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT town_id, material, max_amount FROM town_material_highwater");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                int townId = rs.getInt("town_id");
                result.computeIfAbsent(townId, ignored -> new HashMap<>())
                        .put(rs.getString("material"), rs.getLong("max_amount"));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare highwater cache", exception);
        }
        return result;
    }

    public synchronized Map<Integer, Double> loadAllDebts() {
        Map<Integer, Double> result = new HashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT town_id, debt FROM town_tax_state");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getInt("town_id"), rs.getDouble("debt"));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare debt cache", exception);
        }
        return result;
    }

    public synchronized Map<String, String> loadAllSystemState() {
        Map<String, String> result = new HashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT key, value FROM system_state");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString("key"), rs.getString("value"));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare system_state cache", exception);
        }
        return result;
    }

    public synchronized long maxStaffApprovalId() {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COALESCE(MAX(id), 0) AS max_id FROM staff_approval_requests");
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                return rs.getLong("max_id");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile leggere max staff approval id", exception);
        }
        return 0L;
    }

    public synchronized List<StaffApprovalRequest> loadAllApprovalRequests() {
        List<StaffApprovalRequest> requests = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT id, town_id, request_type, payload, status, requested_by, reviewed_by, reason, note, created_at, reviewed_at
                             FROM staff_approval_requests
                             ORDER BY id DESC
                             """);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                requests.add(mapApprovalRequest(rs));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare staff approval requests", exception);
        }
        return requests;
    }

    public synchronized void upsertProgression(TownProgression progression) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO town_progression(town_id, xp_scaled, city_level, stage, updated_at)
                             VALUES(?, ?, ?, ?, ?)
                             ON CONFLICT(town_id) DO UPDATE SET
                                 xp_scaled = excluded.xp_scaled,
                                 city_level = excluded.city_level,
                                 stage = excluded.stage,
                                 updated_at = excluded.updated_at
                             """)) {
            statement.setInt(1, progression.townId());
            statement.setLong(2, progression.xpScaled());
            statement.setInt(3, progression.cityLevel());
            statement.setString(4, progression.stage().name());
            statement.setString(5, progression.updatedAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile upsert progression town=" + progression.townId(), exception);
        }
    }

    public synchronized void upsertHighwater(int townId, Map<String, Long> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO town_material_highwater(town_id, material, max_amount)
                             VALUES (?, ?, ?)
                             ON CONFLICT(town_id, material) DO UPDATE SET max_amount = excluded.max_amount
                             """)) {
            for (Map.Entry<String, Long> entry : values.entrySet()) {
                statement.setInt(1, townId);
                statement.setString(2, entry.getKey());
                statement.setLong(3, entry.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile upsert highwater town=" + townId, exception);
        }
    }

    public synchronized void upsertScanState(int townId, Instant lastScanAt, long lastAwardedScaled, String lastError) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO town_scan_state(town_id, last_scan_at, last_awarded_scaled, last_error)
                             VALUES (?, ?, ?, ?)
                             ON CONFLICT(town_id) DO UPDATE SET
                                 last_scan_at = excluded.last_scan_at,
                                 last_awarded_scaled = excluded.last_awarded_scaled,
                                 last_error = excluded.last_error
                             """)) {
            statement.setInt(1, townId);
            statement.setString(2, lastScanAt == null ? null : lastScanAt.toString());
            statement.setLong(3, lastAwardedScaled);
            statement.setString(4, lastError);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile upsert scan state town=" + townId, exception);
        }
    }

    public synchronized void appendXpLedger(
            long eventId,
            int townId,
            long deltaXpScaled,
            String source,
            String actorUuid,
            String reason,
            long resultingXpScaled,
            int resultingLevel,
            Instant occurredAt
    ) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT OR IGNORE INTO town_xp_ledger(event_id, town_id, delta_xp_scaled, source, actor_uuid, reason,
                                                                   resulting_xp_scaled, resulting_level, occurred_at)
                             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                             """)) {
            statement.setLong(1, eventId);
            statement.setInt(2, townId);
            statement.setLong(3, deltaXpScaled);
            statement.setString(4, source);
            statement.setString(5, actorUuid);
            statement.setString(6, reason);
            statement.setLong(7, resultingXpScaled);
            statement.setInt(8, resultingLevel);
            statement.setString(9, occurredAt.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile append xp ledger town=" + townId, exception);
        }
    }

    public synchronized void upsertApprovalRequest(StaffApprovalRequest request) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO staff_approval_requests(id, town_id, request_type, payload, status, requested_by,
                                                                reviewed_by, reason, note, created_at, reviewed_at)
                             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                             ON CONFLICT(id) DO UPDATE SET
                                 town_id = excluded.town_id,
                                 request_type = excluded.request_type,
                                 payload = excluded.payload,
                                 status = excluded.status,
                                 reviewed_by = excluded.reviewed_by,
                                 reason = excluded.reason,
                                 note = excluded.note,
                                 reviewed_at = excluded.reviewed_at
                             """)) {
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
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile upsert staff approval id=" + request.id(), exception);
        }
    }

    public synchronized void appendApprovalEvent(
            long eventId,
            long requestId,
            int townId,
            String eventType,
            String actorUuid,
            String payload,
            Instant createdAt
    ) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT OR IGNORE INTO staff_approval_events(event_id, request_id, town_id, event_type, actor_uuid, payload, created_at)
                             VALUES (?, ?, ?, ?, ?, ?, ?)
                             """)) {
            statement.setLong(1, eventId);
            statement.setLong(2, requestId);
            statement.setInt(3, townId);
            statement.setString(4, eventType);
            statement.setString(5, actorUuid);
            statement.setString(6, payload);
            statement.setString(7, createdAt.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile append staff approval event id=" + eventId, exception);
        }
    }

    public synchronized void appendOutbox(long opId, String opType, String payload) {
        Instant now = Instant.now();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO sync_outbox(op_id, op_type, payload, status, attempts, next_attempt_at, last_error, created_at, updated_at)
                             VALUES (?, ?, ?, 'PENDING', 0, ?, NULL, ?, ?)
                             ON CONFLICT(op_id) DO UPDATE SET
                                 op_type = excluded.op_type,
                                 payload = excluded.payload,
                                 updated_at = excluded.updated_at
                             """)) {
            statement.setLong(1, opId);
            statement.setString(2, opType);
            statement.setString(3, payload);
            statement.setString(4, now.toString());
            statement.setString(5, now.toString());
            statement.setString(6, now.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile append outbox op=" + opId, exception);
        }
    }

    public synchronized void markOutboxDone(long opId) {
        Instant now = Instant.now();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             UPDATE sync_outbox
                             SET status = 'DONE', last_error = NULL, updated_at = ?
                             WHERE op_id = ?
                             """)) {
            statement.setString(1, now.toString());
            statement.setLong(2, opId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile marcare outbox done op=" + opId, exception);
        }
    }

    public synchronized void markOutboxFailed(long opId, String error, int backoffSeconds) {
        Instant now = Instant.now();
        Instant nextAttempt = now.plusSeconds(Math.max(1, backoffSeconds));
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             UPDATE sync_outbox
                             SET status = 'FAILED',
                                 attempts = attempts + 1,
                                 next_attempt_at = ?,
                                 last_error = ?,
                                 updated_at = ?
                             WHERE op_id = ?
                             """)) {
            statement.setString(1, nextAttempt.toString());
            statement.setString(2, error);
            statement.setString(3, now.toString());
            statement.setLong(4, opId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile marcare outbox failed op=" + opId, exception);
        }
    }

    public synchronized List<OutboxItem> listRetryableOutbox(int limit, Instant now) {
        List<OutboxItem> items = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT op_id, op_type, payload, status, attempts, next_attempt_at, last_error
                             FROM sync_outbox
                             WHERE status IN ('PENDING', 'FAILED')
                               AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                             ORDER BY op_id ASC
                             LIMIT ?
                             """)) {
            statement.setString(1, now.toString());
            statement.setInt(2, Math.max(1, limit));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(new OutboxItem(
                            rs.getLong("op_id"),
                            rs.getString("op_type"),
                            rs.getString("payload"),
                            rs.getString("status"),
                            rs.getInt("attempts"),
                            parseInstantNullable(rs.getString("next_attempt_at")),
                            rs.getString("last_error")
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile listare outbox retryable", exception);
        }
        return items;
    }

    public synchronized int countOutboxPending() {
        return countOutboxByStatus("PENDING");
    }

    public synchronized int countOutboxFailed() {
        return countOutboxByStatus("FAILED");
    }

    private int countOutboxByStatus(String status) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) AS c FROM sync_outbox WHERE status = ?");
             ) {
            statement.setString(1, status);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("c");
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile contare outbox status=" + status, exception);
        }
        return 0;
    }

    private StaffApprovalRequest mapApprovalRequest(ResultSet rs) throws SQLException {
        String reviewedBy = rs.getString("reviewed_by");
        return new StaffApprovalRequest(
                rs.getLong("id"),
                rs.getInt("town_id"),
                StaffApprovalType.valueOf(rs.getString("request_type")),
                rs.getString("payload"),
                RequestStatus.valueOf(rs.getString("status")),
                UUID.fromString(rs.getString("requested_by")),
                reviewedBy == null || reviewedBy.isBlank() ? null : UUID.fromString(reviewedBy),
                rs.getString("reason"),
                rs.getString("note"),
                parseInstant(rs.getString("created_at")),
                parseInstantNullable(rs.getString("reviewed_at"))
        );
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.now();
        }
        return Instant.parse(raw);
    }

    private Instant parseInstantNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Instant.parse(raw);
    }
}
