package it.patric.cittaexp.discord;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.plugin.Plugin;

public final class DiscordLinkRepository {

    public record PlayerDiscordLink(
            UUID minecraftUuid,
            long discordUserId,
            String discordUsername,
            String discordGlobalName,
            Instant linkedAt,
            Instant updatedAt
    ) {
    }

    public record LinkCodeRecord(
            UUID minecraftUuid,
            String code,
            Instant expiresAt,
            Instant createdAt
    ) {
    }

    public record TownDiscordBinding(
            int townId,
            long guildId,
            Long roleId,
            Long textChannelId,
            Instant lastSyncedAt
    ) {
    }

    private final String jdbcUrl;

    public DiscordLinkRepository(Plugin plugin) {
        this(plugin.getDataFolder().toPath().resolve("discord.db"));
    }

    public DiscordLinkRepository(Path path) {
        this.jdbcUrl = "jdbc:sqlite:" + path;
    }

    public synchronized void initialize() {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_discord_links (
                        minecraft_uuid TEXT PRIMARY KEY,
                        discord_user_id TEXT NOT NULL UNIQUE,
                        discord_username TEXT,
                        discord_global_name TEXT,
                        linked_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS discord_link_codes (
                        code TEXT PRIMARY KEY,
                        minecraft_uuid TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        expires_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_discord_bindings (
                        town_id INTEGER NOT NULL,
                        guild_id TEXT NOT NULL,
                        role_id TEXT,
                        text_channel_id TEXT,
                        last_synced_at TEXT NOT NULL,
                        PRIMARY KEY(town_id, guild_id)
                    )
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile inizializzare repository Discord", exception);
        }
    }

    public synchronized void createOrReplaceLinkCode(UUID minecraftUuid, String code, Instant createdAt, Instant expiresAt) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement deleteExisting = connection.prepareStatement(
                    "DELETE FROM discord_link_codes WHERE minecraft_uuid = ? OR code = ?"
            ); PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO discord_link_codes(code, minecraft_uuid, created_at, expires_at) VALUES (?, ?, ?, ?)"
            )) {
                deleteExisting.setString(1, minecraftUuid.toString());
                deleteExisting.setString(2, code);
                deleteExisting.executeUpdate();

                insert.setString(1, code);
                insert.setString(2, minecraftUuid.toString());
                insert.setString(3, createdAt.toString());
                insert.setString(4, expiresAt.toString());
                insert.executeUpdate();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile salvare codice link Discord", exception);
        }
    }

    public synchronized Optional<LinkCodeRecord> consumeLinkCode(String code, Instant now) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT minecraft_uuid, code, expires_at, created_at FROM discord_link_codes WHERE code = ?"
            )) {
                select.setString(1, code);
                try (ResultSet resultSet = select.executeQuery()) {
                    if (!resultSet.next()) {
                        connection.commit();
                        return Optional.empty();
                    }
                    LinkCodeRecord record = mapLinkCode(resultSet);
                    try (PreparedStatement delete = connection.prepareStatement(
                            "DELETE FROM discord_link_codes WHERE code = ?"
                    )) {
                        delete.setString(1, code);
                        delete.executeUpdate();
                    }
                    connection.commit();
                    if (record.expiresAt().isBefore(now)) {
                        return Optional.empty();
                    }
                    return Optional.of(record);
                }
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile consumare codice link Discord", exception);
        }
    }

    public synchronized int purgeExpiredLinkCodes(Instant now) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM discord_link_codes WHERE expires_at <= ?"
        )) {
            statement.setString(1, now.toString());
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile ripulire codici Discord scaduti", exception);
        }
    }

    public synchronized Optional<PlayerDiscordLink> findLinkByMinecraft(UUID minecraftUuid) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT minecraft_uuid, discord_user_id, discord_username, discord_global_name, linked_at, updated_at FROM player_discord_links WHERE minecraft_uuid = ?"
        )) {
            statement.setString(1, minecraftUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapPlayerLink(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile leggere link Discord per player", exception);
        }
    }

    public synchronized Optional<PlayerDiscordLink> findLinkByDiscord(long discordUserId) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT minecraft_uuid, discord_user_id, discord_username, discord_global_name, linked_at, updated_at FROM player_discord_links WHERE discord_user_id = ?"
        )) {
            statement.setString(1, Long.toUnsignedString(discordUserId));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapPlayerLink(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile leggere link Discord per utente Discord", exception);
        }
    }

    public synchronized List<PlayerDiscordLink> allLinks() {
        List<PlayerDiscordLink> links = new ArrayList<>();
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT minecraft_uuid, discord_user_id, discord_username, discord_global_name, linked_at, updated_at FROM player_discord_links"
        ); ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                links.add(mapPlayerLink(resultSet));
            }
            return links;
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile leggere tutti i link Discord", exception);
        }
    }

    public synchronized void upsertPlayerLink(
            UUID minecraftUuid,
            long discordUserId,
            String discordUsername,
            String discordGlobalName,
            Instant linkedAt,
            Instant updatedAt
    ) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_discord_links(minecraft_uuid, discord_user_id, discord_username, discord_global_name, linked_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(minecraft_uuid) DO UPDATE SET
                    discord_user_id = excluded.discord_user_id,
                    discord_username = excluded.discord_username,
                    discord_global_name = excluded.discord_global_name,
                    linked_at = excluded.linked_at,
                    updated_at = excluded.updated_at
                """)) {
            statement.setString(1, minecraftUuid.toString());
            statement.setString(2, Long.toUnsignedString(discordUserId));
            statement.setString(3, discordUsername);
            statement.setString(4, discordGlobalName);
            statement.setString(5, linkedAt.toString());
            statement.setString(6, updatedAt.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile salvare link Discord", exception);
        }
    }

    public synchronized boolean deleteLinkByMinecraft(UUID minecraftUuid) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM player_discord_links WHERE minecraft_uuid = ?"
        )) {
            statement.setString(1, minecraftUuid.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile rimuovere link Discord", exception);
        }
    }

    public synchronized boolean deleteLinkByDiscord(long discordUserId) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM player_discord_links WHERE discord_user_id = ?"
        )) {
            statement.setString(1, Long.toUnsignedString(discordUserId));
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile rimuovere link Discord per utente Discord", exception);
        }
    }

    public synchronized Optional<TownDiscordBinding> findTownBinding(int townId, long guildId) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT town_id, guild_id, role_id, text_channel_id, last_synced_at FROM town_discord_bindings WHERE town_id = ? AND guild_id = ?"
        )) {
            statement.setInt(1, townId);
            statement.setString(2, Long.toUnsignedString(guildId));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapTownBinding(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile leggere binding Discord citta", exception);
        }
    }

    public synchronized List<TownDiscordBinding> allTownBindings(long guildId) {
        List<TownDiscordBinding> bindings = new ArrayList<>();
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT town_id, guild_id, role_id, text_channel_id, last_synced_at FROM town_discord_bindings WHERE guild_id = ?"
        )) {
            statement.setString(1, Long.toUnsignedString(guildId));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    bindings.add(mapTownBinding(resultSet));
                }
            }
            return bindings;
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile leggere tutti i binding Discord citta", exception);
        }
    }

    public synchronized void upsertTownBinding(int townId, long guildId, Long roleId, Long textChannelId, Instant lastSyncedAt) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO town_discord_bindings(town_id, guild_id, role_id, text_channel_id, last_synced_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(town_id, guild_id) DO UPDATE SET
                    role_id = excluded.role_id,
                    text_channel_id = excluded.text_channel_id,
                    last_synced_at = excluded.last_synced_at
                """)) {
            statement.setInt(1, townId);
            statement.setString(2, Long.toUnsignedString(guildId));
            statement.setString(3, roleId == null ? null : Long.toUnsignedString(roleId));
            statement.setString(4, textChannelId == null ? null : Long.toUnsignedString(textChannelId));
            statement.setString(5, lastSyncedAt.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile salvare binding Discord citta", exception);
        }
    }

    public synchronized void deleteTownBinding(int townId, long guildId) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM town_discord_bindings WHERE town_id = ? AND guild_id = ?"
        )) {
            statement.setInt(1, townId);
            statement.setString(2, Long.toUnsignedString(guildId));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile rimuovere binding Discord citta", exception);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private static PlayerDiscordLink mapPlayerLink(ResultSet resultSet) throws SQLException {
        return new PlayerDiscordLink(
                UUID.fromString(resultSet.getString("minecraft_uuid")),
                Long.parseUnsignedLong(resultSet.getString("discord_user_id")),
                resultSet.getString("discord_username"),
                resultSet.getString("discord_global_name"),
                Instant.parse(resultSet.getString("linked_at")),
                Instant.parse(resultSet.getString("updated_at"))
        );
    }

    private static LinkCodeRecord mapLinkCode(ResultSet resultSet) throws SQLException {
        return new LinkCodeRecord(
                UUID.fromString(resultSet.getString("minecraft_uuid")),
                resultSet.getString("code"),
                Instant.parse(resultSet.getString("expires_at")),
                Instant.parse(resultSet.getString("created_at"))
        );
    }

    private static TownDiscordBinding mapTownBinding(ResultSet resultSet) throws SQLException {
        String roleRaw = resultSet.getString("role_id");
        String channelRaw = resultSet.getString("text_channel_id");
        return new TownDiscordBinding(
                resultSet.getInt("town_id"),
                Long.parseUnsignedLong(resultSet.getString("guild_id")),
                roleRaw == null || roleRaw.isBlank() ? null : Long.parseUnsignedLong(roleRaw),
                channelRaw == null || channelRaw.isBlank() ? null : Long.parseUnsignedLong(channelRaw),
                Instant.parse(resultSet.getString("last_synced_at"))
        );
    }
}
