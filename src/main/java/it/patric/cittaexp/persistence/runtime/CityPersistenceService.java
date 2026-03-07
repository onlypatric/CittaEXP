package it.patric.cittaexp.persistence.runtime;

import com.google.gson.JsonObject;
import it.patric.cittaexp.core.model.CityStatus;
import it.patric.cittaexp.core.model.CityTier;
import it.patric.cittaexp.core.model.FreezeReason;
import it.patric.cittaexp.core.model.InvitationStatus;
import it.patric.cittaexp.core.model.JoinRequestStatus;
import it.patric.cittaexp.persistence.config.PersistenceSettings;
import it.patric.cittaexp.persistence.domain.AuditEventRecord;
import it.patric.cittaexp.persistence.domain.CityInvitationRecord;
import it.patric.cittaexp.persistence.domain.CityMemberRecord;
import it.patric.cittaexp.persistence.domain.CityRecord;
import it.patric.cittaexp.persistence.domain.CityRoleRecord;
import it.patric.cittaexp.persistence.domain.ClaimBindingRecord;
import it.patric.cittaexp.persistence.domain.FreezeCaseRecord;
import it.patric.cittaexp.persistence.domain.JoinRequestRecord;
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
    private static final String EVENT_CITY_HARD_DELETE = "CITY_HARD_DELETE";
    private static final String EVENT_MEMBER_UPSERT = "MEMBER_UPSERT";
    private static final String EVENT_MEMBER_DELETE = "MEMBER_DELETE";
    private static final String EVENT_ROLE_UPSERT = "ROLE_UPSERT";
    private static final String EVENT_ROLE_DELETE = "ROLE_DELETE";
    private static final String EVENT_INVITATION_UPSERT = "INVITATION_UPSERT";
    private static final String EVENT_INVITATION_STATUS = "INVITATION_STATUS";
    private static final String EVENT_JOIN_REQUEST_UPSERT = "JOIN_REQUEST_UPSERT";
    private static final String EVENT_JOIN_REQUEST_STATUS = "JOIN_REQUEST_STATUS";
    private static final String EVENT_FREEZE_UPSERT = "FREEZE_UPSERT";
    private static final String EVENT_FREEZE_CLOSE = "FREEZE_CLOSE";
    private static final String EVENT_CLAIM_BINDING_UPSERT = "CLAIM_BINDING_UPSERT";
    private static final String EVENT_AUDIT_APPEND = "AUDIT_APPEND";

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
    public Optional<CityMemberRecord> findMember(UUID cityId, UUID playerUuid) {
        Objects.requireNonNull(cityId, "cityId");
        Objects.requireNonNull(playerUuid, "playerUuid");
        return withReadConnection(
                "SELECT * FROM city_members WHERE city_id = ? AND player_uuid = ? LIMIT 1",
                stmt -> {
                    stmt.setString(1, cityId.toString());
                    stmt.setString(2, playerUuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        return rs.next() ? Optional.of(mapMember(rs)) : Optional.empty();
                    }
                }
        );
    }

    @Override
    public Optional<CityMemberRecord> findActiveMember(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return withReadConnection(
                "SELECT * FROM city_members WHERE player_uuid = ? AND active = 1 ORDER BY joined_at ASC LIMIT 1",
                stmt -> {
                    stmt.setString(1, playerUuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        return rs.next() ? Optional.of(mapMember(rs)) : Optional.empty();
                    }
                }
        );
    }

    @Override
    public long countActiveMembers(UUID cityId) {
        Objects.requireNonNull(cityId, "cityId");
        return withReadConnection(
                "SELECT COUNT(*) AS c FROM city_members WHERE city_id = ? AND active = 1",
                stmt -> {
                    stmt.setString(1, cityId.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        return rs.next() ? rs.getLong("c") : 0L;
                    }
                }
        );
    }

    @Override
    public List<CityMemberRecord> listMembers(UUID cityId) {
        Objects.requireNonNull(cityId, "cityId");
        return withReadConnection("SELECT * FROM city_members WHERE city_id = ? ORDER BY role_key ASC", stmt -> {
            stmt.setString(1, cityId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                List<CityMemberRecord> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(mapMember(rs));
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
    public Optional<CityInvitationRecord> findInvitation(UUID invitationId) {
        Objects.requireNonNull(invitationId, "invitationId");
        return withReadConnection("SELECT * FROM city_invitations WHERE invitation_id = ? LIMIT 1", stmt -> {
            stmt.setString(1, invitationId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapInvitation(rs)) : Optional.empty();
            }
        });
    }

    @Override
    public List<CityInvitationRecord> listPendingInvitations(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return withReadConnection(
                "SELECT * FROM city_invitations WHERE invited_player_uuid = ? AND status = ? ORDER BY created_at ASC",
                stmt -> {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setString(2, InvitationStatus.PENDING.name());
                    try (ResultSet rs = stmt.executeQuery()) {
                        List<CityInvitationRecord> rows = new ArrayList<>();
                        while (rs.next()) {
                            rows.add(mapInvitation(rs));
                        }
                        return List.copyOf(rows);
                    }
                }
        );
    }

    @Override
    public Optional<JoinRequestRecord> findJoinRequest(UUID requestId) {
        Objects.requireNonNull(requestId, "requestId");
        return withReadConnection("SELECT * FROM join_requests WHERE request_id = ? LIMIT 1", stmt -> {
            stmt.setString(1, requestId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapJoinRequest(rs)) : Optional.empty();
            }
        });
    }

    @Override
    public Optional<JoinRequestRecord> findPendingJoinRequest(UUID cityId, UUID playerUuid) {
        Objects.requireNonNull(cityId, "cityId");
        Objects.requireNonNull(playerUuid, "playerUuid");
        return withReadConnection(
                "SELECT * FROM join_requests WHERE city_id = ? AND player_uuid = ? AND status = ? ORDER BY requested_at DESC LIMIT 1",
                stmt -> {
                    stmt.setString(1, cityId.toString());
                    stmt.setString(2, playerUuid.toString());
                    stmt.setString(3, JoinRequestStatus.PENDING.name());
                    try (ResultSet rs = stmt.executeQuery()) {
                        return rs.next() ? Optional.of(mapJoinRequest(rs)) : Optional.empty();
                    }
                }
        );
    }

    @Override
    public Optional<FreezeCaseRecord> findActiveFreezeCase(UUID cityId) {
        Objects.requireNonNull(cityId, "cityId");
        return withReadConnection(
                "SELECT * FROM freeze_cases WHERE city_id = ? AND active = 1 ORDER BY opened_at DESC LIMIT 1",
                stmt -> {
                    stmt.setString(1, cityId.toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        return rs.next() ? Optional.of(mapFreezeCase(rs)) : Optional.empty();
                    }
                }
        );
    }

    @Override
    public Optional<ClaimBindingRecord> findClaimBinding(UUID cityId) {
        Objects.requireNonNull(cityId, "cityId");
        return withReadConnection("SELECT * FROM claim_bindings WHERE city_id = ? LIMIT 1", stmt -> {
            stmt.setString(1, cityId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(mapClaimBinding(rs)) : Optional.empty();
            }
        });
    }

    @Override
    public long countActiveFreezeCases() {
        return countByQuery("SELECT COUNT(*) AS c FROM freeze_cases WHERE active = 1");
    }

    @Override
    public long countPendingInvitations() {
        return countByStatus("city_invitations", "status", InvitationStatus.PENDING.name());
    }

    @Override
    public long countPendingJoinRequests() {
        return countByStatus("join_requests", "status", JoinRequestStatus.PENDING.name());
    }

    @Override
    public PersistenceWriteOutcome createCity(CityRecord city) {
        Objects.requireNonNull(city, "city");
        String sql = """
                INSERT INTO cities (
                    city_id, name, name_normalized, tag, tag_normalized, leader_uuid,
                    tier, status, capital, frozen, treasury_balance, member_count, max_members,
                    created_at, updated_at, revision
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                    stmt.setString(7, city.tier().name());
                    stmt.setString(8, city.status().name());
                    stmt.setInt(9, city.capital() ? 1 : 0);
                    stmt.setInt(10, city.frozen() ? 1 : 0);
                    stmt.setLong(11, city.treasuryBalance());
                    stmt.setInt(12, city.memberCount());
                    stmt.setInt(13, city.maxMembers());
                    stmt.setLong(14, city.createdAtEpochMilli());
                    stmt.setLong(15, city.updatedAtEpochMilli());
                    stmt.setInt(16, Math.max(city.revision(), 0));
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
                       tier = ?,
                       status = ?,
                       capital = ?,
                       frozen = ?,
                       treasury_balance = ?,
                       member_count = ?,
                       max_members = ?,
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
                    stmt.setString(6, city.tier().name());
                    stmt.setString(7, city.status().name());
                    stmt.setInt(8, city.capital() ? 1 : 0);
                    stmt.setInt(9, city.frozen() ? 1 : 0);
                    stmt.setLong(10, city.treasuryBalance());
                    stmt.setInt(11, city.memberCount());
                    stmt.setInt(12, city.maxMembers());
                    stmt.setLong(13, city.updatedAtEpochMilli());
                    stmt.setString(14, city.cityId().toString());
                    stmt.setInt(15, expectedRevision);
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
    public PersistenceWriteOutcome upsertInvitation(CityInvitationRecord invitation) {
        Objects.requireNonNull(invitation, "invitation");
        try {
            runWritable("upsertInvitation", connection -> {
                String sql = upsertSql(
                        connection,
                        "city_invitations",
                        "invitation_id, city_id, invited_player_uuid, invited_by_uuid, status, created_at, expires_at, updated_at",
                        "city_id = excluded.city_id, invited_player_uuid = excluded.invited_player_uuid, invited_by_uuid = excluded.invited_by_uuid, status = excluded.status, created_at = excluded.created_at, expires_at = excluded.expires_at, updated_at = excluded.updated_at",
                        "city_id = VALUES(city_id), invited_player_uuid = VALUES(invited_player_uuid), invited_by_uuid = VALUES(invited_by_uuid), status = VALUES(status), created_at = VALUES(created_at), expires_at = VALUES(expires_at), updated_at = VALUES(updated_at)"
                );
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, invitation.invitationId().toString());
                    stmt.setString(2, invitation.cityId().toString());
                    stmt.setString(3, invitation.invitedPlayerUuid().toString());
                    stmt.setString(4, invitation.invitedByUuid().toString());
                    stmt.setString(5, invitation.status().name());
                    stmt.setLong(6, invitation.createdAtEpochMilli());
                    stmt.setLong(7, invitation.expiresAtEpochMilli());
                    stmt.setLong(8, invitation.updatedAtEpochMilli());
                    stmt.executeUpdate();
                }
                if (modeManager.currentMode() == PersistenceRuntimeMode.SQLITE_FALLBACK) {
                    enqueue(OutboxEvent.pending(
                            UUID.randomUUID(),
                            "CITY_INVITATION",
                            invitation.invitationId().toString(),
                            EVENT_INVITATION_UPSERT,
                            codec.invitationUpsertPayload(invitation),
                            System.currentTimeMillis()
                    ));
                }
                return null;
            });
            return PersistenceWriteOutcome.success("invitation-upserted");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[CittaEXP][persistence] upsertInvitation failed", ex);
            return PersistenceWriteOutcome.failure("sql-error");
        }
    }

    @Override
    public PersistenceWriteOutcome updateInvitationStatus(UUID invitationId, InvitationStatus status, long updatedAtEpochMilli) {
        Objects.requireNonNull(invitationId, "invitationId");
        Objects.requireNonNull(status, "status");
        try {
            runWritable("updateInvitationStatus", connection -> {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "UPDATE city_invitations SET status = ?, updated_at = ? WHERE invitation_id = ?"
                )) {
                    stmt.setString(1, status.name());
                    stmt.setLong(2, updatedAtEpochMilli);
                    stmt.setString(3, invitationId.toString());
                    stmt.executeUpdate();
                }
                if (modeManager.currentMode() == PersistenceRuntimeMode.SQLITE_FALLBACK) {
                    enqueue(OutboxEvent.pending(
                            UUID.randomUUID(),
                            "CITY_INVITATION",
                            invitationId.toString(),
                            EVENT_INVITATION_STATUS,
                            codec.invitationStatusPayload(invitationId, status, updatedAtEpochMilli),
                            System.currentTimeMillis()
                    ));
                }
                return null;
            });
            return PersistenceWriteOutcome.success("invitation-status-updated");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[CittaEXP][persistence] updateInvitationStatus failed", ex);
            return PersistenceWriteOutcome.failure("sql-error");
        }
    }

    @Override
    public PersistenceWriteOutcome upsertJoinRequest(JoinRequestRecord request) {
        Objects.requireNonNull(request, "request");
        try {
            runWritable("upsertJoinRequest", connection -> {
                String sql = upsertSql(
                        connection,
                        "join_requests",
                        "request_id, city_id, player_uuid, status, message, reviewed_by_uuid, requested_at, reviewed_at",
                        "city_id = excluded.city_id, player_uuid = excluded.player_uuid, status = excluded.status, message = excluded.message, reviewed_by_uuid = excluded.reviewed_by_uuid, requested_at = excluded.requested_at, reviewed_at = excluded.reviewed_at",
                        "city_id = VALUES(city_id), player_uuid = VALUES(player_uuid), status = VALUES(status), message = VALUES(message), reviewed_by_uuid = VALUES(reviewed_by_uuid), requested_at = VALUES(requested_at), reviewed_at = VALUES(reviewed_at)"
                );
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, request.requestId().toString());
                    stmt.setString(2, request.cityId().toString());
                    stmt.setString(3, request.playerUuid().toString());
                    stmt.setString(4, request.status().name());
                    stmt.setString(5, request.message());
                    stmt.setString(6, request.reviewedByUuid() == null ? null : request.reviewedByUuid().toString());
                    stmt.setLong(7, request.requestedAtEpochMilli());
                    stmt.setLong(8, request.reviewedAtEpochMilli());
                    stmt.executeUpdate();
                }
                if (modeManager.currentMode() == PersistenceRuntimeMode.SQLITE_FALLBACK) {
                    enqueue(OutboxEvent.pending(
                            UUID.randomUUID(),
                            "JOIN_REQUEST",
                            request.requestId().toString(),
                            EVENT_JOIN_REQUEST_UPSERT,
                            codec.joinRequestUpsertPayload(request),
                            System.currentTimeMillis()
                    ));
                }
                return null;
            });
            return PersistenceWriteOutcome.success("join-request-upserted");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[CittaEXP][persistence] upsertJoinRequest failed", ex);
            return PersistenceWriteOutcome.failure("sql-error");
        }
    }

    @Override
    public PersistenceWriteOutcome updateJoinRequestStatus(
            UUID requestId,
            JoinRequestStatus status,
            UUID reviewedByUuid,
            long reviewedAtEpochMilli
    ) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(status, "status");
        try {
            runWritable("updateJoinRequestStatus", connection -> {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "UPDATE join_requests SET status = ?, reviewed_by_uuid = ?, reviewed_at = ? WHERE request_id = ?"
                )) {
                    stmt.setString(1, status.name());
                    stmt.setString(2, reviewedByUuid == null ? null : reviewedByUuid.toString());
                    stmt.setLong(3, reviewedAtEpochMilli);
                    stmt.setString(4, requestId.toString());
                    stmt.executeUpdate();
                }
                if (modeManager.currentMode() == PersistenceRuntimeMode.SQLITE_FALLBACK) {
                    enqueue(OutboxEvent.pending(
                            UUID.randomUUID(),
                            "JOIN_REQUEST",
                            requestId.toString(),
                            EVENT_JOIN_REQUEST_STATUS,
                            codec.joinRequestStatusPayload(requestId, status, reviewedByUuid, reviewedAtEpochMilli),
                            System.currentTimeMillis()
                    ));
                }
                return null;
            });
            return PersistenceWriteOutcome.success("join-request-status-updated");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[CittaEXP][persistence] updateJoinRequestStatus failed", ex);
            return PersistenceWriteOutcome.failure("sql-error");
        }
    }

    @Override
    public PersistenceWriteOutcome upsertFreezeCase(FreezeCaseRecord freezeCase) {
        Objects.requireNonNull(freezeCase, "freezeCase");
        try {
            runWritable("upsertFreezeCase", connection -> {
                String sql = upsertSql(
                        connection,
                        "freeze_cases",
                        "case_id, city_id, reason, details, active, opened_by, closed_by, opened_at, closed_at",
                        "city_id = excluded.city_id, reason = excluded.reason, details = excluded.details, active = excluded.active, opened_by = excluded.opened_by, closed_by = excluded.closed_by, opened_at = excluded.opened_at, closed_at = excluded.closed_at",
                        "city_id = VALUES(city_id), reason = VALUES(reason), details = VALUES(details), active = VALUES(active), opened_by = VALUES(opened_by), closed_by = VALUES(closed_by), opened_at = VALUES(opened_at), closed_at = VALUES(closed_at)"
                );
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, freezeCase.caseId().toString());
                    stmt.setString(2, freezeCase.cityId().toString());
                    stmt.setString(3, freezeCase.reason().name());
                    stmt.setString(4, freezeCase.details());
                    stmt.setInt(5, freezeCase.active() ? 1 : 0);
                    stmt.setString(6, freezeCase.openedBy() == null ? null : freezeCase.openedBy().toString());
                    stmt.setString(7, freezeCase.closedBy() == null ? null : freezeCase.closedBy().toString());
                    stmt.setLong(8, freezeCase.openedAtEpochMilli());
                    stmt.setLong(9, freezeCase.closedAtEpochMilli());
                    stmt.executeUpdate();
                }
                if (modeManager.currentMode() == PersistenceRuntimeMode.SQLITE_FALLBACK) {
                    enqueue(OutboxEvent.pending(
                            UUID.randomUUID(),
                            "FREEZE_CASE",
                            freezeCase.caseId().toString(),
                            EVENT_FREEZE_UPSERT,
                            codec.freezeCaseUpsertPayload(freezeCase),
                            System.currentTimeMillis()
                    ));
                }
                return null;
            });
            return PersistenceWriteOutcome.success("freeze-upserted");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[CittaEXP][persistence] upsertFreezeCase failed", ex);
            return PersistenceWriteOutcome.failure("sql-error");
        }
    }

    @Override
    public PersistenceWriteOutcome closeFreezeCase(UUID caseId, UUID closedBy, long closedAtEpochMilli) {
        Objects.requireNonNull(caseId, "caseId");
        try {
            runWritable("closeFreezeCase", connection -> {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "UPDATE freeze_cases SET active = 0, closed_by = ?, closed_at = ? WHERE case_id = ?"
                )) {
                    stmt.setString(1, closedBy == null ? null : closedBy.toString());
                    stmt.setLong(2, closedAtEpochMilli);
                    stmt.setString(3, caseId.toString());
                    stmt.executeUpdate();
                }
                if (modeManager.currentMode() == PersistenceRuntimeMode.SQLITE_FALLBACK) {
                    enqueue(OutboxEvent.pending(
                            UUID.randomUUID(),
                            "FREEZE_CASE",
                            caseId.toString(),
                            EVENT_FREEZE_CLOSE,
                            codec.freezeCaseClosePayload(caseId, closedBy, closedAtEpochMilli),
                            System.currentTimeMillis()
                    ));
                }
                return null;
            });
            return PersistenceWriteOutcome.success("freeze-closed");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[CittaEXP][persistence] closeFreezeCase failed", ex);
            return PersistenceWriteOutcome.failure("sql-error");
        }
    }

    @Override
    public PersistenceWriteOutcome upsertClaimBinding(ClaimBindingRecord claimBinding) {
        Objects.requireNonNull(claimBinding, "claimBinding");
        try {
            runWritable("upsertClaimBinding", connection -> {
                String sql = upsertSql(
                        connection,
                        "claim_bindings",
                        "city_id, world_name, min_x, min_z, max_x, max_z, area, created_at, updated_at",
                        "world_name = excluded.world_name, min_x = excluded.min_x, min_z = excluded.min_z, max_x = excluded.max_x, max_z = excluded.max_z, area = excluded.area, created_at = excluded.created_at, updated_at = excluded.updated_at",
                        "world_name = VALUES(world_name), min_x = VALUES(min_x), min_z = VALUES(min_z), max_x = VALUES(max_x), max_z = VALUES(max_z), area = VALUES(area), created_at = VALUES(created_at), updated_at = VALUES(updated_at)"
                );
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, claimBinding.cityId().toString());
                    stmt.setString(2, claimBinding.worldName());
                    stmt.setInt(3, claimBinding.minX());
                    stmt.setInt(4, claimBinding.minZ());
                    stmt.setInt(5, claimBinding.maxX());
                    stmt.setInt(6, claimBinding.maxZ());
                    stmt.setInt(7, claimBinding.area());
                    stmt.setLong(8, claimBinding.createdAtEpochMilli());
                    stmt.setLong(9, claimBinding.updatedAtEpochMilli());
                    stmt.executeUpdate();
                }
                if (modeManager.currentMode() == PersistenceRuntimeMode.SQLITE_FALLBACK) {
                    enqueue(OutboxEvent.pending(
                            UUID.randomUUID(),
                            "CLAIM_BINDING",
                            claimBinding.cityId().toString(),
                            EVENT_CLAIM_BINDING_UPSERT,
                            codec.claimBindingUpsertPayload(claimBinding),
                            System.currentTimeMillis()
                    ));
                }
                return null;
            });
            return PersistenceWriteOutcome.success("claim-binding-upserted");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[CittaEXP][persistence] upsertClaimBinding failed", ex);
            return PersistenceWriteOutcome.failure("sql-error");
        }
    }

    @Override
    public PersistenceWriteOutcome appendAuditEvent(AuditEventRecord event) {
        Objects.requireNonNull(event, "event");
        try {
            runWritable("appendAuditEvent", connection -> {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "INSERT INTO audit_events (event_id, aggregate_type, aggregate_id, event_type, actor_uuid, payload_json, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
                )) {
                    stmt.setString(1, event.eventId().toString());
                    stmt.setString(2, event.aggregateType());
                    stmt.setString(3, event.aggregateId());
                    stmt.setString(4, event.eventType());
                    stmt.setString(5, event.actorUuid() == null ? null : event.actorUuid().toString());
                    stmt.setString(6, event.payloadJson());
                    stmt.setLong(7, event.occurredAtEpochMilli());
                    stmt.executeUpdate();
                }
                if (modeManager.currentMode() == PersistenceRuntimeMode.SQLITE_FALLBACK) {
                    enqueue(OutboxEvent.pending(
                            UUID.randomUUID(),
                            "AUDIT_EVENT",
                            event.eventId().toString(),
                            EVENT_AUDIT_APPEND,
                            codec.auditAppendPayload(event),
                            System.currentTimeMillis()
                    ));
                }
                return null;
            });
            return PersistenceWriteOutcome.success("audit-appended");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[CittaEXP][persistence] appendAuditEvent failed", ex);
            return PersistenceWriteOutcome.failure("sql-error");
        }
    }

    @Override
    public PersistenceWriteOutcome hardDeleteCityAggregate(UUID cityId) {
        Objects.requireNonNull(cityId, "cityId");
        try {
            runWritable("hardDeleteCityAggregate", connection -> {
                deleteCityAggregate(connection, cityId.toString());
                if (modeManager.currentMode() == PersistenceRuntimeMode.SQLITE_FALLBACK) {
                    enqueue(OutboxEvent.pending(
                            UUID.randomUUID(),
                            "CITY",
                            cityId.toString(),
                            EVENT_CITY_HARD_DELETE,
                            codec.cityHardDeletePayload(cityId),
                            System.currentTimeMillis()
                    ));
                }
                return null;
            });
            return PersistenceWriteOutcome.success("city-hard-deleted");
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[CittaEXP][persistence] hardDeleteCityAggregate failed", ex);
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
                logger.log(Level.SEVERE, "[CittaEXP][persistence] nested transaction failed", ex);
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
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    logger.log(Level.SEVERE, "[CittaEXP][persistence] transaction rollback failed", rollbackError);
                }
                logger.log(
                        Level.SEVERE,
                        "[CittaEXP][persistence] transaction failed mode=" + modeManager.currentMode(),
                        ex
                );
                throw new IllegalStateException("transaction failed", ex);
            } finally {
                txConnection.remove();
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException autoCommitError) {
                    logger.log(Level.WARNING, "[CittaEXP][persistence] restore autocommit failed", autoCommitError);
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "[CittaEXP][persistence] cannot open transaction", ex);
            throw new IllegalStateException("cannot open transaction", ex);
        }
    }

    @Override
    public void enqueue(OutboxEvent event) {
        Objects.requireNonNull(event, "event");
        Connection activeTx = txConnection.get();
        if (activeTx != null) {
            try {
                insertOutboxEvent(activeTx, event);
                return;
            } catch (SQLException ex) {
                logger.log(
                        Level.SEVERE,
                        "[CittaEXP][persistence] enqueue failed on active transaction event="
                                + event.eventId()
                                + " type=" + event.eventType(),
                        ex
                );
                throw new IllegalStateException("Cannot enqueue outbox event", ex);
            }
        }

        try (Connection connection = openSqliteConnection()) {
            insertOutboxEvent(connection, event);
        } catch (SQLException ex) {
            logger.log(
                    Level.SEVERE,
                    "[CittaEXP][persistence] enqueue failed event="
                            + event.eventId()
                            + " type=" + event.eventType(),
                    ex
            );
            throw new IllegalStateException("Cannot enqueue outbox event", ex);
        }
    }

    private static void insertOutboxEvent(Connection connection, OutboxEvent event) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
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
                case EVENT_CITY_HARD_DELETE -> replayCityHardDelete(connection, payload);
                case EVENT_MEMBER_UPSERT -> replayMemberUpsert(connection, payload);
                case EVENT_MEMBER_DELETE -> replayMemberDelete(connection, payload);
                case EVENT_ROLE_UPSERT -> replayRoleUpsert(connection, payload);
                case EVENT_ROLE_DELETE -> replayRoleDelete(connection, payload);
                case EVENT_INVITATION_UPSERT -> replayInvitationUpsert(connection, payload);
                case EVENT_INVITATION_STATUS -> replayInvitationStatus(connection, payload);
                case EVENT_JOIN_REQUEST_UPSERT -> replayJoinRequestUpsert(connection, payload);
                case EVENT_JOIN_REQUEST_STATUS -> replayJoinRequestStatus(connection, payload);
                case EVENT_FREEZE_UPSERT -> replayFreezeUpsert(connection, payload);
                case EVENT_FREEZE_CLOSE -> replayFreezeClose(connection, payload);
                case EVENT_CLAIM_BINDING_UPSERT -> replayClaimBindingUpsert(connection, payload);
                case EVENT_AUDIT_APPEND -> replayAuditAppend(connection, payload);
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
                    "city_id, name, name_normalized, tag, tag_normalized, leader_uuid, tier, status, capital, frozen, treasury_balance, member_count, max_members, created_at, updated_at, revision",
                    """
                    name = excluded.name,
                    name_normalized = excluded.name_normalized,
                    tag = excluded.tag,
                    tag_normalized = excluded.tag_normalized,
                    leader_uuid = excluded.leader_uuid,
                    tier = excluded.tier,
                    status = excluded.status,
                    capital = excluded.capital,
                    frozen = excluded.frozen,
                    treasury_balance = excluded.treasury_balance,
                    member_count = excluded.member_count,
                    max_members = excluded.max_members,
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
                    tier = VALUES(tier),
                    status = VALUES(status),
                    capital = VALUES(capital),
                    frozen = VALUES(frozen),
                    treasury_balance = VALUES(treasury_balance),
                    member_count = VALUES(member_count),
                    max_members = VALUES(max_members),
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
                stmt.setString(7, payload.get("tier").getAsString());
                stmt.setString(8, payload.get("status").getAsString());
                stmt.setInt(9, payload.get("capital").getAsBoolean() ? 1 : 0);
                stmt.setInt(10, payload.get("frozen").getAsBoolean() ? 1 : 0);
                stmt.setLong(11, payload.get("treasuryBalance").getAsLong());
                stmt.setInt(12, payload.get("memberCount").getAsInt());
                stmt.setInt(13, payload.get("maxMembers").getAsInt());
                stmt.setLong(14, payload.get("createdAt").getAsLong());
                stmt.setLong(15, payload.get("updatedAt").getAsLong());
                stmt.setInt(16, Math.max(expectedRevision, 0));
                stmt.executeUpdate();
            }
        } catch (SQLException ex) {
            if (isConflict(ex)) {
                throw new OptimisticConflictException("city-unique-conflict");
            }
            throw new IllegalStateException("city replay failed", ex);
        }
    }

    private void replayCityHardDelete(Connection connection, JsonObject payload) {
        try {
            deleteCityAggregate(connection, payload.get("cityId").getAsString());
        } catch (SQLException ex) {
            throw new IllegalStateException("city hard delete replay failed", ex);
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

    private void replayInvitationUpsert(Connection connection, JsonObject payload) {
        try {
            String sql = upsertSql(
                    connection,
                    "city_invitations",
                    "invitation_id, city_id, invited_player_uuid, invited_by_uuid, status, created_at, expires_at, updated_at",
                    "city_id = excluded.city_id, invited_player_uuid = excluded.invited_player_uuid, invited_by_uuid = excluded.invited_by_uuid, status = excluded.status, created_at = excluded.created_at, expires_at = excluded.expires_at, updated_at = excluded.updated_at",
                    "city_id = VALUES(city_id), invited_player_uuid = VALUES(invited_player_uuid), invited_by_uuid = VALUES(invited_by_uuid), status = VALUES(status), created_at = VALUES(created_at), expires_at = VALUES(expires_at), updated_at = VALUES(updated_at)"
            );
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, payload.get("invitationId").getAsString());
                stmt.setString(2, payload.get("cityId").getAsString());
                stmt.setString(3, payload.get("invitedPlayerUuid").getAsString());
                stmt.setString(4, payload.get("invitedByUuid").getAsString());
                stmt.setString(5, payload.get("status").getAsString());
                stmt.setLong(6, payload.get("createdAt").getAsLong());
                stmt.setLong(7, payload.get("expiresAt").getAsLong());
                stmt.setLong(8, payload.get("updatedAt").getAsLong());
                stmt.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("invitation replay failed", ex);
        }
    }

    private void replayInvitationStatus(Connection connection, JsonObject payload) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE city_invitations SET status = ?, updated_at = ? WHERE invitation_id = ?"
        )) {
            stmt.setString(1, payload.get("status").getAsString());
            stmt.setLong(2, payload.get("updatedAt").getAsLong());
            stmt.setString(3, payload.get("invitationId").getAsString());
            stmt.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("invitation status replay failed", ex);
        }
    }

    private void replayJoinRequestUpsert(Connection connection, JsonObject payload) {
        try {
            String sql = upsertSql(
                    connection,
                    "join_requests",
                    "request_id, city_id, player_uuid, status, message, reviewed_by_uuid, requested_at, reviewed_at",
                    "city_id = excluded.city_id, player_uuid = excluded.player_uuid, status = excluded.status, message = excluded.message, reviewed_by_uuid = excluded.reviewed_by_uuid, requested_at = excluded.requested_at, reviewed_at = excluded.reviewed_at",
                    "city_id = VALUES(city_id), player_uuid = VALUES(player_uuid), status = VALUES(status), message = VALUES(message), reviewed_by_uuid = VALUES(reviewed_by_uuid), requested_at = VALUES(requested_at), reviewed_at = VALUES(reviewed_at)"
            );
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, payload.get("requestId").getAsString());
                stmt.setString(2, payload.get("cityId").getAsString());
                stmt.setString(3, payload.get("playerUuid").getAsString());
                stmt.setString(4, payload.get("status").getAsString());
                stmt.setString(5, payload.get("message").getAsString());
                stmt.setString(6, toNullableValue(payload.get("reviewedByUuid").getAsString()));
                stmt.setLong(7, payload.get("requestedAt").getAsLong());
                stmt.setLong(8, payload.get("reviewedAt").getAsLong());
                stmt.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("join request replay failed", ex);
        }
    }

    private void replayJoinRequestStatus(Connection connection, JsonObject payload) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE join_requests SET status = ?, reviewed_by_uuid = ?, reviewed_at = ? WHERE request_id = ?"
        )) {
            stmt.setString(1, payload.get("status").getAsString());
            stmt.setString(2, toNullableValue(payload.get("reviewedByUuid").getAsString()));
            stmt.setLong(3, payload.get("reviewedAt").getAsLong());
            stmt.setString(4, payload.get("requestId").getAsString());
            stmt.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("join request status replay failed", ex);
        }
    }

    private void replayFreezeUpsert(Connection connection, JsonObject payload) {
        try {
            String sql = upsertSql(
                    connection,
                    "freeze_cases",
                    "case_id, city_id, reason, details, active, opened_by, closed_by, opened_at, closed_at",
                    "city_id = excluded.city_id, reason = excluded.reason, details = excluded.details, active = excluded.active, opened_by = excluded.opened_by, closed_by = excluded.closed_by, opened_at = excluded.opened_at, closed_at = excluded.closed_at",
                    "city_id = VALUES(city_id), reason = VALUES(reason), details = VALUES(details), active = VALUES(active), opened_by = VALUES(opened_by), closed_by = VALUES(closed_by), opened_at = VALUES(opened_at), closed_at = VALUES(closed_at)"
            );
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, payload.get("caseId").getAsString());
                stmt.setString(2, payload.get("cityId").getAsString());
                stmt.setString(3, payload.get("reason").getAsString());
                stmt.setString(4, payload.get("details").getAsString());
                stmt.setInt(5, payload.get("active").getAsBoolean() ? 1 : 0);
                stmt.setString(6, toNullableValue(payload.get("openedBy").getAsString()));
                stmt.setString(7, toNullableValue(payload.get("closedBy").getAsString()));
                stmt.setLong(8, payload.get("openedAt").getAsLong());
                stmt.setLong(9, payload.get("closedAt").getAsLong());
                stmt.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("freeze replay failed", ex);
        }
    }

    private void replayFreezeClose(Connection connection, JsonObject payload) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE freeze_cases SET active = 0, closed_by = ?, closed_at = ? WHERE case_id = ?"
        )) {
            stmt.setString(1, toNullableValue(payload.get("closedBy").getAsString()));
            stmt.setLong(2, payload.get("closedAt").getAsLong());
            stmt.setString(3, payload.get("caseId").getAsString());
            stmt.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("freeze close replay failed", ex);
        }
    }

    private void replayClaimBindingUpsert(Connection connection, JsonObject payload) {
        try {
            String sql = upsertSql(
                    connection,
                    "claim_bindings",
                    "city_id, world_name, min_x, min_z, max_x, max_z, area, created_at, updated_at",
                    "world_name = excluded.world_name, min_x = excluded.min_x, min_z = excluded.min_z, max_x = excluded.max_x, max_z = excluded.max_z, area = excluded.area, created_at = excluded.created_at, updated_at = excluded.updated_at",
                    "world_name = VALUES(world_name), min_x = VALUES(min_x), min_z = VALUES(min_z), max_x = VALUES(max_x), max_z = VALUES(max_z), area = VALUES(area), created_at = VALUES(created_at), updated_at = VALUES(updated_at)"
            );
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, payload.get("cityId").getAsString());
                stmt.setString(2, payload.get("worldName").getAsString());
                stmt.setInt(3, payload.get("minX").getAsInt());
                stmt.setInt(4, payload.get("minZ").getAsInt());
                stmt.setInt(5, payload.get("maxX").getAsInt());
                stmt.setInt(6, payload.get("maxZ").getAsInt());
                stmt.setInt(7, payload.get("area").getAsInt());
                stmt.setLong(8, payload.get("createdAt").getAsLong());
                stmt.setLong(9, payload.get("updatedAt").getAsLong());
                stmt.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("claim binding replay failed", ex);
        }
    }

    private void replayAuditAppend(Connection connection, JsonObject payload) {
        try {
            String sql = upsertSql(
                    connection,
                    "audit_events",
                    "event_id, aggregate_type, aggregate_id, event_type, actor_uuid, payload_json, occurred_at",
                    "aggregate_type = excluded.aggregate_type, aggregate_id = excluded.aggregate_id, event_type = excluded.event_type, actor_uuid = excluded.actor_uuid, payload_json = excluded.payload_json, occurred_at = excluded.occurred_at",
                    "aggregate_type = VALUES(aggregate_type), aggregate_id = VALUES(aggregate_id), event_type = VALUES(event_type), actor_uuid = VALUES(actor_uuid), payload_json = VALUES(payload_json), occurred_at = VALUES(occurred_at)"
            );
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, payload.get("eventId").getAsString());
                stmt.setString(2, payload.get("aggregateType").getAsString());
                stmt.setString(3, payload.get("aggregateId").getAsString());
                stmt.setString(4, payload.get("eventType").getAsString());
                stmt.setString(5, toNullableValue(payload.get("actorUuid").getAsString()));
                stmt.setString(6, payload.get("payloadJson").getAsString());
                stmt.setLong(7, payload.get("occurredAt").getAsLong());
                stmt.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("audit replay failed", ex);
        }
    }

    private static void deleteCityAggregate(Connection connection, String cityId) throws SQLException {
        deleteByCityId(connection, "city_members", cityId);
        deleteByCityId(connection, "city_roles", cityId);
        deleteByCityId(connection, "city_invitations", cityId);
        deleteByCityId(connection, "join_requests", cityId);
        deleteByCityId(connection, "freeze_cases", cityId);
        deleteByCityId(connection, "claim_bindings", cityId);
        deleteByCityId(connection, "city_treasury_ledger", cityId);
        deleteByCityId(connection, "ranking_snapshot", cityId);
        deleteByCityId(connection, "staff_approval_tickets", cityId);
        deleteByCityId(connection, "city_deletion_cases", cityId);
        deleteByCityId(connection, "capital_state", cityId);
        deleteByAggregateId(connection, cityId);
        deleteByCityId(connection, "cities", cityId);
    }

    private static void deleteByCityId(Connection connection, String table, String cityId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM " + table + " WHERE city_id = ?")) {
            stmt.setString(1, cityId);
            stmt.executeUpdate();
        }
    }

    private static void deleteByAggregateId(Connection connection, String cityId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM audit_events WHERE aggregate_id = ?")) {
            stmt.setString(1, cityId);
            stmt.executeUpdate();
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

    private long countByStatus(String table, String column, String status) {
        return withReadConnection(
                "SELECT COUNT(*) AS c FROM " + table + " WHERE " + column + " = ?",
                stmt -> {
                    stmt.setString(1, status);
                    try (ResultSet rs = stmt.executeQuery()) {
                        return rs.next() ? rs.getLong("c") : 0L;
                    }
                }
        );
    }

    private long countByQuery(String query) {
        return withReadConnection(query, stmt -> {
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong("c") : 0L;
            }
        });
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
                CityTier.valueOf(rs.getString("tier")),
                CityStatus.valueOf(rs.getString("status")),
                rs.getInt("capital") == 1,
                rs.getInt("frozen") == 1,
                rs.getLong("treasury_balance"),
                rs.getInt("member_count"),
                rs.getInt("max_members"),
                rs.getLong("created_at"),
                rs.getLong("updated_at"),
                rs.getInt("revision")
        );
    }

    private static CityMemberRecord mapMember(ResultSet rs) throws SQLException {
        return new CityMemberRecord(
                UUID.fromString(rs.getString("city_id")),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("role_key"),
                rs.getLong("joined_at"),
                rs.getInt("active") == 1
        );
    }

    private static CityInvitationRecord mapInvitation(ResultSet rs) throws SQLException {
        return new CityInvitationRecord(
                UUID.fromString(rs.getString("invitation_id")),
                UUID.fromString(rs.getString("city_id")),
                UUID.fromString(rs.getString("invited_player_uuid")),
                UUID.fromString(rs.getString("invited_by_uuid")),
                InvitationStatus.valueOf(rs.getString("status")),
                rs.getLong("created_at"),
                rs.getLong("expires_at"),
                rs.getLong("updated_at")
        );
    }

    private static JoinRequestRecord mapJoinRequest(ResultSet rs) throws SQLException {
        return new JoinRequestRecord(
                UUID.fromString(rs.getString("request_id")),
                UUID.fromString(rs.getString("city_id")),
                UUID.fromString(rs.getString("player_uuid")),
                JoinRequestStatus.valueOf(rs.getString("status")),
                rs.getString("message"),
                nullableUuid(rs.getString("reviewed_by_uuid")),
                rs.getLong("requested_at"),
                rs.getLong("reviewed_at")
        );
    }

    private static FreezeCaseRecord mapFreezeCase(ResultSet rs) throws SQLException {
        return new FreezeCaseRecord(
                UUID.fromString(rs.getString("case_id")),
                UUID.fromString(rs.getString("city_id")),
                FreezeReason.valueOf(rs.getString("reason")),
                rs.getString("details"),
                rs.getInt("active") == 1,
                nullableUuid(rs.getString("opened_by")),
                nullableUuid(rs.getString("closed_by")),
                rs.getLong("opened_at"),
                rs.getLong("closed_at")
        );
    }

    private static ClaimBindingRecord mapClaimBinding(ResultSet rs) throws SQLException {
        return new ClaimBindingRecord(
                UUID.fromString(rs.getString("city_id")),
                rs.getString("world_name"),
                rs.getInt("min_x"),
                rs.getInt("min_z"),
                rs.getInt("max_x"),
                rs.getInt("max_z"),
                rs.getInt("area"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
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

    private static UUID nullableUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return UUID.fromString(raw);
    }

    private static String toNullableValue(String raw) {
        return (raw == null || raw.isBlank()) ? null : raw;
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
