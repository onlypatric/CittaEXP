package it.patric.cittaexp.challenges;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.plugin.Plugin;

public final class StaffEventRepository {

    private final String jdbcUrl;

    public StaffEventRepository(Plugin plugin) {
        Path path = plugin.getDataFolder().toPath().resolve("challenges.db");
        this.jdbcUrl = "jdbc:sqlite:" + path;
    }

    public synchronized void initialize() {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS staff_events (
                        event_id TEXT PRIMARY KEY,
                        kind TEXT NOT NULL,
                        status TEXT NOT NULL,
                        title TEXT NOT NULL,
                        subtitle TEXT,
                        description TEXT,
                        start_at TEXT NOT NULL,
                        end_at TEXT NOT NULL,
                        created_by TEXT,
                        published_by TEXT,
                        closed_by TEXT,
                        visible INTEGER NOT NULL,
                        linked_challenge_instance_id TEXT,
                        payload_json TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        published_at TEXT,
                        completed_at TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS staff_event_submissions (
                        event_id TEXT NOT NULL,
                        town_id INTEGER NOT NULL,
                        submitted_by TEXT NOT NULL,
                        submitted_at TEXT NOT NULL,
                        world_name TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL,
                        note TEXT,
                        review_status TEXT NOT NULL,
                        reviewed_by TEXT,
                        reviewed_at TEXT,
                        staff_note TEXT,
                        placement INTEGER,
                        reward_granted INTEGER NOT NULL,
                        reward_granted_at TEXT,
                        PRIMARY KEY(event_id, town_id)
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_staff_events_status_start
                    ON staff_events(status, start_at)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_staff_event_submissions_event
                    ON staff_event_submissions(event_id, submitted_at)
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile inizializzare staff events in challenges.db", exception);
        }
    }

    public synchronized Map<String, StaffEvent> loadEvents() {
        Map<String, StaffEvent> result = new LinkedHashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT event_id, kind, status, title, subtitle, description, start_at, end_at, created_by, published_by, closed_by, visible, linked_challenge_instance_id, payload_json, created_at, updated_at, published_at, completed_at FROM staff_events");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                parseEvent(rs).ifPresent(event -> result.put(event.eventId(), event));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare staff events", exception);
        }
        return result;
    }

    public synchronized List<StaffEventSubmission> loadSubmissions() {
        List<StaffEventSubmission> result = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT event_id, town_id, submitted_by, submitted_at, world_name, x, y, z, note, review_status, reviewed_by, reviewed_at, staff_note, placement, reward_granted, reward_granted_at FROM staff_event_submissions");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                parseSubmission(rs).ifPresent(result::add);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare staff event submissions", exception);
        }
        return result;
    }

    public synchronized void upsertEvent(StaffEvent event) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO staff_events(
                         event_id, kind, status, title, subtitle, description, start_at, end_at,
                         created_by, published_by, closed_by, visible, linked_challenge_instance_id,
                         payload_json, created_at, updated_at, published_at, completed_at
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(event_id) DO UPDATE SET
                         kind = excluded.kind,
                         status = excluded.status,
                         title = excluded.title,
                         subtitle = excluded.subtitle,
                         description = excluded.description,
                         start_at = excluded.start_at,
                         end_at = excluded.end_at,
                         created_by = excluded.created_by,
                         published_by = excluded.published_by,
                         closed_by = excluded.closed_by,
                         visible = excluded.visible,
                         linked_challenge_instance_id = excluded.linked_challenge_instance_id,
                         payload_json = excluded.payload_json,
                         created_at = excluded.created_at,
                         updated_at = excluded.updated_at,
                         published_at = excluded.published_at,
                         completed_at = excluded.completed_at
                     """)) {
            statement.setString(1, event.eventId());
            statement.setString(2, event.kind().name());
            statement.setString(3, event.status().name());
            statement.setString(4, event.title());
            statement.setString(5, event.subtitle());
            statement.setString(6, event.description());
            statement.setString(7, event.startAt().toString());
            statement.setString(8, event.endAt().toString());
            statement.setString(9, toNullableString(event.createdBy()));
            statement.setString(10, toNullableString(event.publishedBy()));
            statement.setString(11, toNullableString(event.closedBy()));
            statement.setInt(12, event.visible() ? 1 : 0);
            statement.setString(13, event.linkedChallengeInstanceId());
            statement.setString(14, event.payload());
            statement.setString(15, event.createdAt().toString());
            statement.setString(16, event.updatedAt().toString());
            statement.setString(17, toNullableString(event.publishedAt()));
            statement.setString(18, toNullableString(event.completedAt()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile salvare staff event " + (event == null ? "-" : event.eventId()), exception);
        }
    }

    public synchronized void upsertSubmission(StaffEventSubmission submission) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO staff_event_submissions(
                         event_id, town_id, submitted_by, submitted_at, world_name, x, y, z, note,
                         review_status, reviewed_by, reviewed_at, staff_note, placement, reward_granted, reward_granted_at
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(event_id, town_id) DO UPDATE SET
                         submitted_by = excluded.submitted_by,
                         submitted_at = excluded.submitted_at,
                         world_name = excluded.world_name,
                         x = excluded.x,
                         y = excluded.y,
                         z = excluded.z,
                         note = excluded.note,
                         review_status = excluded.review_status,
                         reviewed_by = excluded.reviewed_by,
                         reviewed_at = excluded.reviewed_at,
                         staff_note = excluded.staff_note,
                         placement = excluded.placement,
                         reward_granted = excluded.reward_granted,
                         reward_granted_at = excluded.reward_granted_at
                     """)) {
            statement.setString(1, submission.eventId());
            statement.setInt(2, submission.townId());
            statement.setString(3, submission.submittedBy().toString());
            statement.setString(4, submission.submittedAt().toString());
            statement.setString(5, submission.worldName());
            statement.setDouble(6, submission.x());
            statement.setDouble(7, submission.y());
            statement.setDouble(8, submission.z());
            statement.setString(9, submission.note());
            statement.setString(10, submission.reviewStatus().name());
            statement.setString(11, toNullableString(submission.reviewedBy()));
            statement.setString(12, toNullableString(submission.reviewedAt()));
            statement.setString(13, submission.staffNote());
            if (submission.placement() == null) {
                statement.setNull(14, java.sql.Types.INTEGER);
            } else {
                statement.setInt(14, submission.placement());
            }
            statement.setInt(15, submission.rewardGranted() ? 1 : 0);
            statement.setString(16, toNullableString(submission.rewardGrantedAt()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile salvare submission staff event " + submission.eventId(), exception);
        }
    }

    public synchronized Optional<StaffEvent> findEvent(String eventId) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT event_id, kind, status, title, subtitle, description, start_at, end_at, created_by, published_by, closed_by, visible, linked_challenge_instance_id, payload_json, created_at, updated_at, published_at, completed_at FROM staff_events WHERE event_id = ?")) {
            statement.setString(1, eventId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return parseEvent(rs);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile cercare staff event " + eventId, exception);
        }
        return Optional.empty();
    }

    public synchronized Optional<StaffEventSubmission> findSubmission(String eventId, int townId) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT event_id, town_id, submitted_by, submitted_at, world_name, x, y, z, note, review_status, reviewed_by, reviewed_at, staff_note, placement, reward_granted, reward_granted_at FROM staff_event_submissions WHERE event_id = ? AND town_id = ?")) {
            statement.setString(1, eventId);
            statement.setInt(2, townId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return parseSubmission(rs);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile cercare submission staff event " + eventId + ":" + townId, exception);
        }
        return Optional.empty();
    }

    public synchronized List<StaffEventSubmission> listSubmissionsForEvent(String eventId) {
        List<StaffEventSubmission> result = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT event_id, town_id, submitted_by, submitted_at, world_name, x, y, z, note, review_status, reviewed_by, reviewed_at, staff_note, placement, reward_granted, reward_granted_at FROM staff_event_submissions WHERE event_id = ? ORDER BY submitted_at ASC")) {
            statement.setString(1, eventId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    parseSubmission(rs).ifPresent(result::add);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile listare submissions per event " + eventId, exception);
        }
        return result;
    }

    private Optional<StaffEvent> parseEvent(ResultSet rs) throws SQLException {
        try {
            return Optional.of(new StaffEvent(
                    rs.getString("event_id"),
                    StaffEventKind.valueOf(rs.getString("kind")),
                    StaffEventStatus.valueOf(rs.getString("status")),
                    rs.getString("title"),
                    rs.getString("subtitle"),
                    rs.getString("description"),
                    Instant.parse(rs.getString("start_at")),
                    Instant.parse(rs.getString("end_at")),
                    parseUuid(rs.getString("created_by")),
                    parseUuid(rs.getString("published_by")),
                    parseUuid(rs.getString("closed_by")),
                    rs.getInt("visible") == 1,
                    rs.getString("linked_challenge_instance_id"),
                    rs.getString("payload_json"),
                    Instant.parse(rs.getString("created_at")),
                    Instant.parse(rs.getString("updated_at")),
                    parseInstant(rs.getString("published_at")),
                    parseInstant(rs.getString("completed_at"))
            ));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private Optional<StaffEventSubmission> parseSubmission(ResultSet rs) throws SQLException {
        try {
            return Optional.of(new StaffEventSubmission(
                    rs.getString("event_id"),
                    rs.getInt("town_id"),
                    UUID.fromString(rs.getString("submitted_by")),
                    Instant.parse(rs.getString("submitted_at")),
                    rs.getString("world_name"),
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z"),
                    rs.getString("note"),
                    StaffEventReviewStatus.valueOf(rs.getString("review_status")),
                    parseUuid(rs.getString("reviewed_by")),
                    parseInstant(rs.getString("reviewed_at")),
                    rs.getString("staff_note"),
                    parseIntNullable(rs, "placement"),
                    rs.getInt("reward_granted") == 1,
                    parseInstant(rs.getString("reward_granted_at"))
            ));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private static String toNullableString(UUID value) {
        return value == null ? null : value.toString();
    }

    private static String toNullableString(Instant value) {
        return value == null ? null : value.toString();
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer parseIntNullable(ResultSet rs, String column) throws SQLException {
        int raw = rs.getInt(column);
        return rs.wasNull() ? null : raw;
    }
}
