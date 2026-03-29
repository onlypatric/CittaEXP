package it.patric.cittaexp.permissions;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.plugin.Plugin;

public final class TownMemberPermissionRepository {

    private final String jdbcUrl;

    public TownMemberPermissionRepository(Plugin plugin) {
        this(plugin.getDataFolder().toPath().resolve("town-member-permissions.db"));
    }

    public TownMemberPermissionRepository(Path path) {
        this.jdbcUrl = "jdbc:sqlite:" + path;
    }

    public synchronized void initialize() {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_member_permissions (
                        town_id INTEGER NOT NULL,
                        player_uuid TEXT NOT NULL,
                        permission_id TEXT NOT NULL,
                        allowed INTEGER NOT NULL,
                        updated_at TEXT NOT NULL,
                        PRIMARY KEY(town_id, player_uuid, permission_id)
                    )
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile inizializzare repository town member permissions", exception);
        }
    }

    public synchronized Map<UUID, EnumMap<TownMemberPermission, Boolean>> loadTownPermissions(int townId) {
        Map<UUID, EnumMap<TownMemberPermission, Boolean>> result = new HashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT player_uuid, permission_id, allowed
                     FROM town_member_permissions
                     WHERE town_id = ?
                     """)) {
            statement.setInt(1, townId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UUID playerId = parseUuid(rs.getString("player_uuid"));
                    if (playerId == null) {
                        continue;
                    }
                    TownMemberPermission permission = parsePermission(rs.getString("permission_id"));
                    if (permission == null) {
                        continue;
                    }
                    if (rs.getInt("allowed") != 1) {
                        continue;
                    }
                    result.computeIfAbsent(playerId, ignored -> new EnumMap<>(TownMemberPermission.class))
                            .put(permission, true);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare permessi membri town=" + townId, exception);
        }
        return result;
    }

    public synchronized void setPermission(
            int townId,
            UUID playerId,
            TownMemberPermission permission,
            boolean allowed,
            Instant updatedAt
    ) {
        if (allowed) {
            upsertPermission(townId, playerId, permission, updatedAt);
            return;
        }
        deletePermission(townId, playerId, permission);
    }

    public synchronized void deleteTown(int townId) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM town_member_permissions WHERE town_id = ?"
             )) {
            statement.setInt(1, townId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile eliminare permessi town=" + townId, exception);
        }
    }

    public synchronized void deletePlayer(int townId, UUID playerId) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM town_member_permissions WHERE town_id = ? AND player_uuid = ?"
             )) {
            statement.setInt(1, townId);
            statement.setString(2, playerId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile eliminare permessi player town=" + townId, exception);
        }
    }

    private void upsertPermission(
            int townId,
            UUID playerId,
            TownMemberPermission permission,
            Instant updatedAt
    ) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO town_member_permissions(town_id, player_uuid, permission_id, allowed, updated_at)
                     VALUES (?, ?, ?, 1, ?)
                     ON CONFLICT(town_id, player_uuid, permission_id) DO UPDATE SET
                         allowed = 1,
                         updated_at = excluded.updated_at
                     """)) {
            statement.setInt(1, townId);
            statement.setString(2, playerId.toString());
            statement.setString(3, permission.name());
            statement.setString(4, updatedAt.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile salvare permesso membro town=" + townId, exception);
        }
    }

    private void deletePermission(int townId, UUID playerId, TownMemberPermission permission) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     DELETE FROM town_member_permissions
                     WHERE town_id = ? AND player_uuid = ? AND permission_id = ?
                     """)) {
            statement.setInt(1, townId);
            statement.setString(2, playerId.toString());
            statement.setString(3, permission.name());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile eliminare permesso membro town=" + townId, exception);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static TownMemberPermission parsePermission(String value) {
        try {
            return value == null ? null : TownMemberPermission.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
