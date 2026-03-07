package it.patric.cittaexp.persistence.runtime;

import com.google.gson.JsonObject;
import it.patric.cittaexp.persistence.config.PersistenceSettings;
import it.patric.cittaexp.persistence.domain.CityMemberRecord;
import it.patric.cittaexp.persistence.domain.CityRecord;
import it.patric.cittaexp.persistence.domain.CityRoleRecord;
import it.patric.cittaexp.persistence.domain.OutboxEvent;
import it.patric.cittaexp.persistence.domain.PersistenceWriteOutcome;
import it.patric.cittaexp.persistence.domain.ReplayStatus;
import it.patric.cittaexp.persistence.port.CityReadPort;
import it.patric.cittaexp.persistence.port.CityTxPort;
import it.patric.cittaexp.persistence.port.CityWritePort;
import it.patric.cittaexp.persistence.port.DomainEventOutboxPort;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

public final class CityPersistenceService
        implements CityReadPort, CityWritePort, CityTxPort, DomainEventOutboxPort, PersistenceStatusService {

    private static final String EVENT_CITY_UPSERT = "CITY_UPSERT";
    private static final String EVENT_MEMBER_UPSERT = "MEMBER_UPSERT";
    private static final String EVENT_MEMBER_DELETE = "MEMBER_DELETE";
    private static final String EVENT_ROLE_UPSERT = "ROLE_UPSERT";
    private static final String EVENT_ROLE_DELETE = "ROLE_DELETE";

    private final JavaPlugin plugin;
    private final PersistenceSettings settings;
    private final PersistenceModeManager modeManager;
    private final SchemaInstaller schemaInstaller;
    private final OutboxEventCodec codec;
    private final Logger logger;
    private final ThreadLocal<Connection> txConnection;

    public CityPersistenceService(
            JavaPlugin plugin,
            PersistenceSettings settings,
            PersistenceModeManager modeManager,
            SchemaInstaller schemaInstaller,
            Logger logger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.modeManager = Objects.requireNonNull(modeManager, "modeManager");
        this.schemaInstaller = Objects.requireNonNull(schemaInstaller, "schemaInstaller");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.codec = new OutboxEventCodec();
        this.txConnection = new ThreadLocal<>();
    }

    @Override
    public Optional<CityRecord> findCityById(UUID cityId) {
        Objects.requireNonNull(cityId, "cityId");
        return withReadConnection("SELECT * FROM cities WHERE city_id = ?", stmt -> {
            stmt.setString(1, cityId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapCity(rs)) : Optional.empty();
            }
        });
    }

    @Override
    public Optional<CityRecord> findCityByName(String name) {
        String normalized = normalize(name);
        return withReadConnection("SELECT * FROM cities WHERE name_normalized = ?", stmt -> {
            stmt.setString(1, normalized);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapCity(rs)) : Optional.empty();
            }
        });
    }

    @Override
    public Optional<CityRecord> findCityByTag(String tag) {
        String normalized = normalize(tag);
        return withReadConnection("SELECT * FROM cities WHERE tag_normalized = ?", stmt -> {
            stmt.setString(1, normalized);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapCity(rs)) : Optional.empty();
            }
        });
    }

    @Override
    public List<CityRecord> listCities() {
        return withReadConnection("SELECT * FROM cities ORDER BY treasury_balance DESC, name ASC", stmt -> {
            try (ResultSet rs = stmt.executeQuery()) {
                List<CityRecord> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapCity(rs));
                }
                return List.copyOf(rows);
            }
        });
    }

    @Override
    public List<CityMemberRecord> listMembers(UUID cityId) {
        Objects.requireNonNull(cityId, "cityId");
        return withReadConnection("SELECT * FROM city_members WHERE city_id = ? ORDER BY role_key ASC", stmt -> {
            stmt.setString(1, cityId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                List<CityMemberRecord> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new CityMemberRecord(
                            UUID.fromString(rs.getString("city_id")),
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("role_key"),
                            rs.getLong("joined_at"),
                            rs.getInt("active") == 1
                    ));
                }
                return List.copyOf(rows);
            }
        });
    }

    @Override
    public List<CityRoleRecord> listRoles(UUID cityId) {
        Objects.requireNonNull(cityId, "cityId");
        return withReadConnection("SELECT * FROM city_roles WHERE city_id = ? ORDER BY priority DESC, role_key ASC", stmt -> {
            stmt.setString(1, cityId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                List<CityRoleRecord> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new CityRoleRecord(
                            UUID.fromString(rs.getString("city_id")),
                            rs.getString("role_key"),
                            rs.getString("display_name"),
                            rs.getInt("priority"),
                            rs.getString("permissions_json")
                    ));
                }
                return List.copyOf(rows);
            }
        });
    }

    @Override
    public PersistenceWriteOutcome createCity(CityRecord city) {
        Objects.requireNonNull(city, "city");
        String sql = """
                INSERT INTO cities (
                    city_id, name, name_normalized, tag, tag_normalized, leader_uuid,
                    capital, frozen, treasury_balance, created_at, updated_at, revision
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try {
            runWritable("createCity", connection -> {
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, city.cityId().toString());
                    stmt.setString(2, city.name());
                    stmt.setString(3, normalize(city.name()));
                    stmt.setString(4, city.tag());
                    stmt.setString(5, normalize(city.tag()));
                    stmt.setString(6, city.leaderUuid().toString());
                    stmt.setInt(7, city.capital() ? 1 : 0);
                    stmt.setInt(8, city.frozen() ? 1 : 0);
                    stmt.setLong(9, city.treasuryBalance());
                    stmt.setLong(10, city.createdAtEpochMilli());
                    stmt.setLong(11, city.updatedAtEpochMilli());
                    stmt.setInt(12, Math.max(city.revision(), 0));
                    stmt.executeUpdate();
                }

                if (modeManager.currentMode() == PersistenceRuntimeMode.SQLITE_FALLBACK) {
                    enqueue(
                            OutboxEvent.pending(
                                    UUID.randomUUID(),
                                    "CITY",
                                    city.cityId().toString(),
                                    EVENT_CITY_UPSERT,
                                    codec.cityUpsertPayload(city, -1),
                                    System.currentTimeMillis()
                            )
                    );
                }
                return null;
            });
            return PersistenceWriteOutcome.success("city-created");
        } catch (SQLException ex) {
            if (isConflict(ex)) {
                return PersistenceWriteOutcome.conflict("city-name-or-tag-already-exists");
            }
            logger.log(Level.SEVERE, "[CittaEXP][persistence] createCity failed", ex);
            return PersistenceWriteOutcome.failure("sql-error");
        }
    }

    @Override
    public PersistenceWriteOutcome updateCity(CityRecord city, int expectedRevision) {
        Objects.requireNonNull(city, "city");
        String sql = """
                UPDATE cities
                   SET name = ?,
                       name_normalized = ?,
                       tag = ?,
                       tag_normalized = ?,
                       leader_uuid = ?,
                       capital = ?,
                       frozen = ?,
                       treasury_balance = ?,
                       updated_at = ?,
                       revision = revision + 1
                 WHERE city_id = ?
                   AND revision = ?
                """;
        try {
            int updated = runWritable("updateCity", connection -> {
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, city.name());
                    stmt.setString(2, normalize(city.name()));
                    stmt.setString(3, city.tag());
                    stmt.setString(4, normalize(city.tag()));
                    stmt.setString(5, city.leaderUuid().toString());
                    stmt.setInt(6, city.capital() ? 1 : 0);
                    stmt.setInt(7, city.frozen() ? 1 : 0);
                    stmt.setLong(8, city.treasuryBalance());
                    stmt.setLong(9, city.updatedAtEpochMilli());
                    stmt.setString(10, city.cityId().toString());
                    stmt.setInt(11, expectedRevision);
                    int count = stmt.executeUpdate();
                    if (count > 0 && modeManager.currentMode() == PersistenceRuntimeMode.SQLITE_FALLBACK) {
                        enqueue(
                                OutboxEvent.pending(
                                        UUID.randomUUID(),
                                        "CITY",
                                        city.cityId().toString(),
                                        EVENT_CITY_UPSERT,
                                        codec.cityUpsertPayload(city, expectedRevision),
                                        System.currentTimeMillis()
                                )
                        );
                    }
                    return count;
                }
            });
            if (updated == 0) {
                return PersistenceWriteOutcome.conflict("stale-revision");
            }
            return PersistenceWriteOutcome.success("city-updated");
        } catch (SQLException ex) {
            if (isConflict(ex)) {
                return PersistenceWriteOutcome.conflict("city-name-or-tag-already-exists");
            }
            logger.log(Level.SEVERE, "[CittaEXP][persistence] updateCity failed", ex);
            return PersistenceWriteOutcome.failure("sql-error");
        }
    }

    @Override
    public PersistenceWriteOutcome upsertMember(CityMemberRecord member) {
        Objects.requireNonNull(member, "member");
        try {
            runWritable("upsertMember", connection -> {
                String sql = upsertSql(
                        connection,
                        "city_members",
                        "city_id, player_uuid, role_key, joined_at, active",
                        "role_key = excluded.role_key, joined_at = excluded.joined_at, active = excluded.active",
                        "role_key = VALUES(role_key), joined_at = VALUES(joined_at), active = VALUES(active)"
                );
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, member.cityId().toString());
                    stmt.setString(2, member.playerUuid().toString());
                    stmt.setString(3, member.roleKey());
                    stmt.setLong(4, member.joinedAtEpochMilli());
                    stmt.setInt(5, member.active() ? 1 : 0);
                    stmt.executeUpdate();
                }
                if (modeManager.currentMode() == PersistenceRuntimeMode.SQLITE_FALLBACK) {
                    enqueue(
                            OutboxEvent.pending(
                                    UUID.randomUUID(),
                                    "CITY_MEMBER",
                                    member.cityId() + ":" + member.playerUuid(),
                                    EVENT_MEMBER_UPSERT,
                                    codec.memberUpsertPayload(member),
                                    System.currentTimeMillis()
                            )
                    );
                }
                return null;
            });
            return PersistenceWriteOutcome.success("member-upserted");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[CittaEXP][persistence] upsertMember failed", ex);
            return PersistenceWriteOutcome.failure("sql-error");
        }
    }

    @Override
    public PersistenceWriteOutcome deleteMember(UUID cityId, UUID playerUuid) {
        Objects.requireNonNull(cityId, "cityId");
        Objects.requireNonNull(playerUuid, "playerUuid");
        try {
            runWritable("deleteMember", connection -> {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM city_members WHERE city_id = ? AND player_uuid = ?"
                )) {
                    stmt.setString(1, cityId.toString());
                    stmt.setString(2, playerUuid.toString());
                    stmt.executeUpdate();
                }
                if (modeManager.currentMode() == PersistenceRuntimeMode.SQLITE_FALLBACK) {
                    enqueue(
                            OutboxEvent.pending(
                                    UUID.randomUUID(),
                                    "CITY_MEMBER",
                                    cityId + ":" + playerUuid,
                                    EVENT_MEMBER_DELETE,
                                    codec.memberDeletePayload(cityId, playerUuid),
                                    System.currentTimeMillis()
                            )
                    );
                }
                return null;
            });
            return PersistenceWriteOutcome.success("member-deleted");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[CittaEXP][persistence] deleteMember failed", ex);
            return PersistenceWriteOutcome.failure("sql-error");
        }
    }

    @Override
    public PersistenceWriteOutcome upsertRole(CityRoleRecord role) {
        Objects.requireNonNull(role, "role");
        try {
            runWritable("upsertRole", connection -> {
                String sql = upsertSql(
                        connection,
                        "city_roles",
                        "city_id, role_key, display_name, priority, permissions_json",
                        "display_name = excluded.display_name, priority = excluded.priority, permissions_json = excluded.permissions_json",
                        "display_name = VALUES(display_name), priority = VALUES(priority), permissions_json = VALUES(permissions_json)"
                );
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, role.cityId().toString());
                    stmt.setString(2, role.roleKey());
                    stmt.setString(3, role.displayName());
                    stmt.setInt(4, role.priority());
                    stmt.setString(5, role.permissionsJson());
                    stmt.executeUpdate();
                }
                if (modeManager.currentMode() == PersistenceRuntimeMode.SQLITE_FALLBACK) {
                    enqueue(
                            OutboxEvent.pending(
                                    UUID.randomUUID(),
                                    "CITY_ROLE",
                                    role.cityId() + ":" + role.roleKey(),
                                    EVENT_ROLE_UPSERT,
                                    codec.roleUpsertPayload(role),
                                    System.currentTimeMillis()
                            )
                    );
                }
                return null;
            });
            return PersistenceWriteOutcome.success("role-upserted");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[CittaEXP][persistence] upsertRole failed", ex);
            return PersistenceWriteOutcome.failure("sql-error");
        }
    }

    @Override
    public PersistenceWriteOutcome deleteRole(UUID cityId, String roleKey) {
        Objects.requireNonNull(cityId, "cityId");
        Objects.requireNonNull(roleKey, "roleKey");
        try {
            runWritable("deleteRole", connection -> {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM city_roles WHERE city_id = ? AND role_key = ?"
                )) {
                    stmt.setString(1, cityId.toString());
                    stmt.setString(2, roleKey);
                    stmt.executeUpdate();
                }
                if (modeManager.currentMode() == PersistenceRuntimeMode.SQLITE_FALLBACK) {
                    enqueue(
                            OutboxEvent.pending(
                                    UUID.randomUUID(),
                                    "CITY_ROLE",
                                    cityId + ":" + roleKey,
                                    EVENT_ROLE_DELETE,
                                    codec.roleDeletePayload(cityId, roleKey),
                                    System.currentTimeMillis()
                            )
                    );
                }
                return null;
            });
            return PersistenceWriteOutcome.success("role-deleted");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[CittaEXP][persistence] deleteRole failed", ex);
            return PersistenceWriteOutcome.failure("sql-error");
        }
    }

    @Override
    public <T> T withTransaction(SqlTransaction<T> transaction) {
        Objects.requireNonNull(transaction, "transaction");
        if (txConnection.get() != null) {
            try {
                return transaction.execute(txConnection.get());
            } catch (SQLException ex) {
                throw new IllegalStateException("nested transaction failed", ex);
            }
        }
        try (Connection connection = currentWriteConnection()) {
            connection.setAutoCommit(false);
            txConnection.set(connection);
            try {
                T result = transaction.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException ex) {
                connection.rollback();
                throw new IllegalStateException("transaction failed", ex);
            } finally {
                txConnection.remove();
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("cannot open transaction", ex);
        }
    }

    @Override
    public void enqueue(OutboxEvent event) {
        Objects.requireNonNull(event, "event");
        try (Connection connection = openSqliteConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     """
                     INSERT INTO city_outbox (
                         event_id, aggregate_type, aggregate_id, event_type,
                         payload_json, occurred_at, replay_status, replay_attempts, last_error
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """
             )) {
            stmt.setString(1, event.eventId().toString());
            stmt.setString(2, event.aggregateType());
            stmt.setString(3, event.aggregateId());
            stmt.setString(4, event.eventType());
            stmt.setString(5, event.payloadJson());
            stmt.setLong(6, event.occurredAtEpochMilli());
            stmt.setString(7, event.status().name());
            stmt.setInt(8, event.replayAttempts());
            stmt.setString(9, event.lastError());
            stmt.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot enqueue outbox event", ex);
        }
    }

    @Override
    public List<OutboxEvent> pollPending(int limit) {
        int bounded = Math.max(1, limit);
        try (Connection connection = openSqliteConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     """
                     SELECT * FROM city_outbox
                      WHERE replay_status = ?
                      ORDER BY occurred_at ASC
                      LIMIT ?
                     """
             )) {
            stmt.setString(1, ReplayStatus.PENDING.name());
            stmt.setInt(2, bounded);
            try (ResultSet rs = stmt.executeQuery()) {
                List<OutboxEvent> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapOutbox(rs));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot poll outbox events", ex);
        }
    }

    @Override
    public void markApplied(UUID eventId) {
        updateOutboxStatus(eventId, ReplayStatus.APPLIED, "");
    }

    @Override
    public void markConflict(UUID eventId, String reason) {
        updateOutboxStatus(eventId, ReplayStatus.CONFLICT, Objects.requireNonNullElse(reason, "conflict"));
    }

    @Override
    public void markFailed(UUID eventId, String reason) {
        updateOutboxStatus(eventId, ReplayStatus.FAILED, Objects.requireNonNullElse(reason, "failed"));
    }

    @Override
    public long pendingCount() {
        return countOutbox(ReplayStatus.PENDING);
    }

    @Override
    public long conflictCount() {
        return countOutbox(ReplayStatus.CONFLICT);
    }

    @Override
    public PersistenceStatusSnapshot snapshot() {
        return new PersistenceStatusSnapshot(
                modeManager.currentMode(),
                modeManager.mysqlReachable(),
                settings.fallbackEnabled(),
                pendingCount(),
                conflictCount(),
                modeManager.lastModeSwitchEpochMilli(),
                modeManager.lastSwitchReason()
        );
    }

    public void refreshHealth() {
        modeManager.refreshHealth();
    }

    public void replayPendingToMysql(int batchSize) {
        if (modeManager.currentMode() != PersistenceRuntimeMode.MYSQL_ACTIVE) {
            return;
        }

        List<OutboxEvent> pending = pollPending(batchSize);
        if (pending.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pending) {
            try {
                applyEventToMysql(event);
                markApplied(event.eventId());
            } catch (OptimisticConflictException conflict) {
                markConflict(event.eventId(), conflict.getMessage());
            } catch (RuntimeException ex) {
                logger.log(Level.WARNING, "[CittaEXP][persistence] replay failed event=" + event.eventId(), ex);
                markFailed(event.eventId(), ex.getClass().getSimpleName());
            }
        }
    }

    void ensureSchema() {
        schemaInstaller.ensureBaselineSchemas();
    }

    private void applyEventToMysql(OutboxEvent event) {
        JsonObject payload = codec.parse(event.payloadJson());
        runOnMysql(connection -> {
            switch (event.eventType()) {
                case EVENT_CITY_UPSERT -> replayCityUpsert(connection, payload);
                case EVENT_MEMBER_UPSERT -> replayMemberUpsert(connection, payload);
                case EVENT_MEMBER_DELETE -> replayMemberDelete(connection, payload);
                case EVENT_ROLE_UPSERT -> replayRoleUpsert(connection, payload);
                case EVENT_ROLE_DELETE -> replayRoleDelete(connection, payload);
                default -> throw new IllegalStateException("Unknown outbox eventType: " + event.eventType());
            }
            return null;
        });
    }

    private void replayCityUpsert(Connection connection, JsonObject payload) {
        String cityId = payload.get("cityId").getAsString();
        int expectedRevision = payload.get("expectedRevision").getAsInt();
        try {
            if (expectedRevision >= 0) {
                try (PreparedStatement verify = connection.prepareStatement("SELECT revision FROM cities WHERE city_id = ?")) {
                    verify.setString(1, cityId);
                    try (ResultSet rs = verify.executeQuery()) {
                        if (rs.next() && rs.getInt("revision") != expectedRevision) {
                            throw new OptimisticConflictException("stale-revision-replay");
                        }
                    }
                }
            }

            String upsert = upsertSql(
                    connection,
                    "cities",
                    "city_id, name, name_normalized, tag, tag_normalized, leader_uuid, capital, frozen, treasury_balance, created_at, updated_at, revision",
                    """
                    name = excluded.name,
                    name_normalized = excluded.name_normalized,
                    tag = excluded.tag,
                    tag_normalized = excluded.tag_normalized,
                    leader_uuid = excluded.leader_uuid,
                    capital = excluded.capital,
                    frozen = excluded.frozen,
                    treasury_balance = excluded.treasury_balance,
                    created_at = excluded.created_at,
                    updated_at = excluded.updated_at,
                    revision = CASE WHEN cities.revision >= excluded.revision THEN cities.revision + 1 ELSE excluded.revision + 1 END
                    """,
                    """
                    name = VALUES(name),
                    name_normalized = VALUES(name_normalized),
                    tag = VALUES(tag),
                    tag_normalized = VALUES(tag_normalized),
                    leader_uuid = VALUES(leader_uuid),
                    capital = VALUES(capital),
                    frozen = VALUES(frozen),
                    treasury_balance = VALUES(treasury_balance),
                    created_at = VALUES(created_at),
                    updated_at = VALUES(updated_at),
                    revision = revision + 1
                    """
            );
            try (PreparedStatement stmt = connection.prepareStatement(upsert)) {
                stmt.setString(1, cityId);
                stmt.setString(2, payload.get("name").getAsString());
                stmt.setString(3, normalize(payload.get("name").getAsString()));
                stmt.setString(4, payload.get("tag").getAsString());
                stmt.setString(5, normalize(payload.get("tag").getAsString()));
                stmt.setString(6, payload.get("leaderUuid").getAsString());
                stmt.setInt(7, payload.get("capital").getAsBoolean() ? 1 : 0);
                stmt.setInt(8, payload.get("frozen").getAsBoolean() ? 1 : 0);
                stmt.setLong(9, payload.get("treasuryBalance").getAsLong());
                stmt.setLong(10, payload.get("createdAt").getAsLong());
                stmt.setLong(11, payload.get("updatedAt").getAsLong());
                stmt.setInt(12, Math.max(expectedRevision, 0));
                stmt.executeUpdate();
            }
        } catch (SQLException ex) {
            if (isConflict(ex)) {
                throw new OptimisticConflictException("city-unique-conflict");
            }
            throw new IllegalStateException("city replay failed", ex);
        }
    }

    private void replayMemberUpsert(Connection connection, JsonObject payload) {
        try {
            String sql = upsertSql(
                    connection,
                    "city_members",
                    "city_id, player_uuid, role_key, joined_at, active",
                    "role_key = excluded.role_key, joined_at = excluded.joined_at, active = excluded.active",
                    "role_key = VALUES(role_key), joined_at = VALUES(joined_at), active = VALUES(active)"
            );
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, payload.get("cityId").getAsString());
                stmt.setString(2, payload.get("playerUuid").getAsString());
                stmt.setString(3, payload.get("roleKey").getAsString());
                stmt.setLong(4, payload.get("joinedAt").getAsLong());
                stmt.setInt(5, payload.get("active").getAsBoolean() ? 1 : 0);
                stmt.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("member replay failed", ex);
        }
    }

    private void replayMemberDelete(Connection connection, JsonObject payload) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM city_members WHERE city_id = ? AND player_uuid = ?"
        )) {
            stmt.setString(1, payload.get("cityId").getAsString());
            stmt.setString(2, payload.get("playerUuid").getAsString());
            stmt.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("member delete replay failed", ex);
        }
    }

    private void replayRoleUpsert(Connection connection, JsonObject payload) {
        try {
            String sql = upsertSql(
                    connection,
                    "city_roles",
                    "city_id, role_key, display_name, priority, permissions_json",
                    "display_name = excluded.display_name, priority = excluded.priority, permissions_json = excluded.permissions_json",
                    "display_name = VALUES(display_name), priority = VALUES(priority), permissions_json = VALUES(permissions_json)"
            );
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, payload.get("cityId").getAsString());
                stmt.setString(2, payload.get("roleKey").getAsString());
                stmt.setString(3, payload.get("displayName").getAsString());
                stmt.setInt(4, payload.get("priority").getAsInt());
                stmt.setString(5, payload.get("permissionsJson").getAsString());
                stmt.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("role replay failed", ex);
        }
    }

    private void replayRoleDelete(Connection connection, JsonObject payload) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "DELETE FROM city_roles WHERE city_id = ? AND role_key = ?"
        )) {
            stmt.setString(1, payload.get("cityId").getAsString());
            stmt.setString(2, payload.get("roleKey").getAsString());
            stmt.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("role delete replay failed", ex);
        }
    }

    private void updateOutboxStatus(UUID eventId, ReplayStatus status, String reason) {
        try (Connection connection = openSqliteConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     """
                     UPDATE city_outbox
                        SET replay_status = ?,
                            replay_attempts = replay_attempts + 1,
                            last_error = ?
                      WHERE event_id = ?
                     """
             )) {
            stmt.setString(1, status.name());
            stmt.setString(2, reason == null ? "" : reason);
            stmt.setString(3, eventId.toString());
            stmt.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot update outbox status", ex);
        }
    }

    private long countOutbox(ReplayStatus status) {
        try (Connection connection = openSqliteConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "SELECT COUNT(*) AS c FROM city_outbox WHERE replay_status = ?"
             )) {
            stmt.setString(1, status.name());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong("c") : 0L;
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[CittaEXP][persistence] outbox count failed", ex);
            return -1L;
        }
    }

    private Connection currentReadConnection() throws SQLException {
        Connection existing = txConnection.get();
        if (existing != null) {
            return existing;
        }
        return switch (modeManager.currentMode()) {
            case MYSQL_ACTIVE -> safeMysqlConnection();
            case SQLITE_FALLBACK -> openSqliteConnection();
            case UNAVAILABLE -> throw new SQLException("persistence unavailable");
        };
    }

    private Connection currentWriteConnection() throws SQLException {
        Connection existing = txConnection.get();
        if (existing != null) {
            return existing;
        }
        return switch (modeManager.currentMode()) {
            case MYSQL_ACTIVE -> safeMysqlConnection();
            case SQLITE_FALLBACK -> openSqliteConnection();
            case UNAVAILABLE -> throw new SQLException("persistence unavailable");
        };
    }

    private Connection safeMysqlConnection() throws SQLException {
        try {
            return openMysqlConnection();
        } catch (SQLException ex) {
            modeManager.reportMysqlFailure(ex.getClass().getSimpleName());
            if (settings.fallbackEnabled()) {
                return openSqliteConnection();
            }
            throw ex;
        }
    }

    private Connection openMysqlConnection() throws SQLException {
        return DriverManager.getConnection(modeManager.mysqlUrl(), settings.mysql().username(), settings.mysql().password());
    }

    private Connection openSqliteConnection() throws SQLException {
        return DriverManager.getConnection(schemaInstaller.sqliteUrl());
    }

    private <T> T runWritable(String operation, SqlFunction<Connection, T> body) throws SQLException {
        Connection existing = txConnection.get();
        if (existing != null) {
            return body.apply(existing);
        }
        try (Connection connection = currentWriteConnection()) {
            return body.apply(connection);
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "[CittaEXP][persistence] write failed operation=" + operation, ex);
            throw ex;
        }
    }

    private <T> T runOnMysql(SqlFunction<Connection, T> body) {
        try (Connection connection = openMysqlConnection()) {
            return body.apply(connection);
        } catch (SQLException ex) {
            modeManager.reportMysqlFailure(ex.getClass().getSimpleName());
            throw new IllegalStateException("mysql operation failed", ex);
        }
    }

    private <T> T withReadConnection(String sql, StatementReader<T> reader) {
        Connection existing = txConnection.get();
        if (existing != null) {
            try (PreparedStatement stmt = existing.prepareStatement(sql)) {
                return reader.read(stmt);
            } catch (SQLException ex) {
                throw new IllegalStateException("read failed", ex);
            }
        }

        try (Connection connection = currentReadConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            return reader.read(stmt);
        } catch (SQLException ex) {
            throw new IllegalStateException("read failed", ex);
        }
    }

    private static CityRecord mapCity(ResultSet rs) throws SQLException {
        return new CityRecord(
                UUID.fromString(rs.getString("city_id")),
                rs.getString("name"),
                rs.getString("tag"),
                UUID.fromString(rs.getString("leader_uuid")),
                rs.getInt("capital") == 1,
                rs.getInt("frozen") == 1,
                rs.getLong("treasury_balance"),
                rs.getLong("created_at"),
                rs.getLong("updated_at"),
                rs.getInt("revision")
        );
    }

    private static OutboxEvent mapOutbox(ResultSet rs) throws SQLException {
        return new OutboxEvent(
                UUID.fromString(rs.getString("event_id")),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                rs.getString("event_type"),
                rs.getString("payload_json"),
                rs.getLong("occurred_at"),
                ReplayStatus.valueOf(rs.getString("replay_status")),
                rs.getInt("replay_attempts"),
                rs.getString("last_error")
        );
    }

    private static String normalize(String raw) {
        return Objects.requireNonNull(raw, "raw").trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isConflict(SQLException ex) {
        String state = ex.getSQLState();
        int code = ex.getErrorCode();
        return "23000".equals(state) || "23505".equals(state) || code == 1062 || code == 19;
    }

    private static String upsertSql(
            Connection connection,
            String table,
            String columns,
            String sqliteAssignments,
            String mysqlAssignments
    ) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        if (product.contains("mysql")) {
            return "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders(columns) + ")"
                    + " ON DUPLICATE KEY UPDATE " + mysqlAssignments;
        }
        return "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders(columns) + ")"
                + " ON CONFLICT DO UPDATE SET " + sqliteAssignments;
    }

    private static String placeholders(String columns) {
        int count = columns.split(",").length;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('?');
        }
        return builder.toString();
    }

    @FunctionalInterface
    private interface StatementReader<T> {
        T read(PreparedStatement stmt) throws SQLException;
    }

    @FunctionalInterface
    private interface SqlFunction<I, O> {
        O apply(I input) throws SQLException;
    }

    private static final class OptimisticConflictException extends RuntimeException {
        private OptimisticConflictException(String message) {
            super(message);
        }
    }
}
