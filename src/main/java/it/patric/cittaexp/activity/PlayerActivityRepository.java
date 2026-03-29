package it.patric.cittaexp.activity;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.OptionalLong;
import java.util.UUID;

public final class PlayerActivityRepository {

    private final Path databasePath;

    public PlayerActivityRepository(Path databasePath) {
        this.databasePath = databasePath;
    }

    public void initialize() {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_activity (
                        player_uuid TEXT PRIMARY KEY,
                        last_seen_at INTEGER NOT NULL
                    )
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile inizializzare player_activity", exception);
        }
    }

    public void upsertLastSeen(UUID playerId, long lastSeenAt) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO player_activity (player_uuid, last_seen_at)
                     VALUES (?, ?)
                     ON CONFLICT(player_uuid) DO UPDATE SET last_seen_at = excluded.last_seen_at
                     """)) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, lastSeenAt);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile aggiornare player_activity per " + playerId, exception);
        }
    }

    public OptionalLong findLastSeen(UUID playerId) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT last_seen_at FROM player_activity WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return OptionalLong.empty();
                }
                return OptionalLong.of(resultSet.getLong("last_seen_at"));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile leggere player_activity per " + playerId, exception);
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
    }
}
