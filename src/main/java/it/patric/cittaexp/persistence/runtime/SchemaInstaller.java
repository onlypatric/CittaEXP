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
                        tier VARCHAR(32) NOT NULL,
                        status VARCHAR(32) NOT NULL,
                        capital INTEGER NOT NULL,
                        frozen INTEGER NOT NULL,
                        treasury_balance BIGINT NOT NULL,
                        member_count INTEGER NOT NULL,
                        max_members INTEGER NOT NULL,
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL,
                        revision INTEGER NOT NULL
                    )
                    """);
            ensureCityColumns(statement);

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
                    CREATE TABLE IF NOT EXISTS city_governance (
                        city_id VARCHAR(36) PRIMARY KEY,
                        vice_uuid VARCHAR(36),
                        updated_at BIGINT NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS city_member_claim_permissions (
                        city_id VARCHAR(36) NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        perm_access INTEGER NOT NULL,
                        perm_container INTEGER NOT NULL,
                        perm_build INTEGER NOT NULL,
                        updated_by VARCHAR(36),
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (city_id, player_uuid)
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

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS tax_policy (
                        policy_id VARCHAR(64) PRIMARY KEY,
                        borgo_monthly_cost BIGINT NOT NULL,
                        villaggio_monthly_cost BIGINT NOT NULL,
                        regno_monthly_cost BIGINT NOT NULL,
                        regno_shop_monthly_extra BIGINT NOT NULL,
                        capitale_monthly_bonus BIGINT NOT NULL,
                        due_day_of_month INTEGER NOT NULL,
                        timezone_id VARCHAR(64) NOT NULL,
                        updated_at BIGINT NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS city_treasury_ledger (
                        entry_id VARCHAR(36) PRIMARY KEY,
                        city_id VARCHAR(36) NOT NULL,
                        entry_type VARCHAR(32) NOT NULL,
                        amount BIGINT NOT NULL,
                        resulting_balance BIGINT NOT NULL,
                        reason VARCHAR(255) NOT NULL,
                        actor_uuid VARCHAR(36),
                        occurred_at BIGINT NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS capital_state (
                        capital_slot VARCHAR(32) PRIMARY KEY,
                        city_id VARCHAR(36) NOT NULL,
                        assigned_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL,
                        source_ranking_version VARCHAR(64) NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS claim_bindings (
                        city_id VARCHAR(36) PRIMARY KEY,
                        world_name VARCHAR(64) NOT NULL,
                        min_x INTEGER NOT NULL,
                        min_z INTEGER NOT NULL,
                        max_x INTEGER NOT NULL,
                        max_z INTEGER NOT NULL,
                        area INTEGER NOT NULL,
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ranking_snapshot (
                        city_id VARCHAR(36) PRIMARY KEY,
                        rank_pos INTEGER NOT NULL,
                        score BIGINT NOT NULL,
                        source_version VARCHAR(64) NOT NULL,
                        fetched_at BIGINT NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS monthly_cycle_state (
                        cycle_key VARCHAR(32) PRIMARY KEY,
                        last_processed_month VARCHAR(7) NOT NULL,
                        last_run_at BIGINT NOT NULL,
                        last_status VARCHAR(32) NOT NULL,
                        last_error TEXT NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS staff_approval_tickets (
                        ticket_id VARCHAR(36) PRIMARY KEY,
                        city_id VARCHAR(36) NOT NULL,
                        ticket_type VARCHAR(32) NOT NULL,
                        status VARCHAR(32) NOT NULL,
                        requester_uuid VARCHAR(36) NOT NULL,
                        reviewer_uuid VARCHAR(36),
                        reason VARCHAR(255) NOT NULL,
                        payload_json TEXT NOT NULL,
                        requested_at BIGINT NOT NULL,
                        reviewed_at BIGINT NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS freeze_cases (
                        case_id VARCHAR(36) PRIMARY KEY,
                        city_id VARCHAR(36) NOT NULL,
                        reason VARCHAR(32) NOT NULL,
                        details TEXT NOT NULL,
                        active INTEGER NOT NULL,
                        opened_by VARCHAR(36),
                        closed_by VARCHAR(36),
                        opened_at BIGINT NOT NULL,
                        closed_at BIGINT NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS city_deletion_cases (
                        case_id VARCHAR(36) PRIMARY KEY,
                        city_id VARCHAR(36) NOT NULL,
                        requires_staff_approval INTEGER NOT NULL,
                        status VARCHAR(32) NOT NULL,
                        requester_uuid VARCHAR(36) NOT NULL,
                        reviewer_uuid VARCHAR(36),
                        reason VARCHAR(255) NOT NULL,
                        requested_at BIGINT NOT NULL,
                        resolved_at BIGINT NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS city_invitations (
                        invitation_id VARCHAR(36) PRIMARY KEY,
                        city_id VARCHAR(36) NOT NULL,
                        invited_player_uuid VARCHAR(36) NOT NULL,
                        invited_by_uuid VARCHAR(36) NOT NULL,
                        status VARCHAR(32) NOT NULL,
                        created_at BIGINT NOT NULL,
                        expires_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS join_requests (
                        request_id VARCHAR(36) PRIMARY KEY,
                        city_id VARCHAR(36) NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        status VARCHAR(32) NOT NULL,
                        message TEXT NOT NULL,
                        reviewed_by_uuid VARCHAR(36),
                        requested_at BIGINT NOT NULL,
                        reviewed_at BIGINT NOT NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS audit_events (
                        event_id VARCHAR(36) PRIMARY KEY,
                        aggregate_type VARCHAR(64) NOT NULL,
                        aggregate_id VARCHAR(128) NOT NULL,
                        event_type VARCHAR(64) NOT NULL,
                        actor_uuid VARCHAR(36),
                        payload_json TEXT NOT NULL,
                        occurred_at BIGINT NOT NULL
                    )
                    """);

            if (sqlite) {
                statement.executeUpdate(
                        "CREATE INDEX IF NOT EXISTS idx_city_outbox_status_occurred ON city_outbox(replay_status, occurred_at)"
                );
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_city_members_city ON city_members(city_id)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_city_roles_city ON city_roles(city_id)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_city_governance_vice ON city_governance(vice_uuid)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_city_claim_perms_city ON city_member_claim_permissions(city_id)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_city_claim_perms_player ON city_member_claim_permissions(player_uuid)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ledger_city_time ON city_treasury_ledger(city_id, occurred_at)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ledger_type_time ON city_treasury_ledger(entry_type, occurred_at)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_tickets_status_time ON staff_approval_tickets(status, requested_at)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_freeze_city_active ON freeze_cases(city_id, active)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_freeze_reason_active ON freeze_cases(reason, active)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_invite_city_status ON city_invitations(city_id, status)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_join_city_status ON join_requests(city_id, status)");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_audit_agg_time ON audit_events(aggregate_type, aggregate_id, occurred_at)");
            } else {
                try {
                    statement.executeUpdate(
                        "CREATE INDEX idx_city_outbox_status_occurred ON city_outbox(replay_status, occurred_at)"
                    );
                } catch (SQLException ignored) {
                    // index already exists
                }
                createIndexIfMissing(statement, "idx_city_members_city", "city_members(city_id)");
                createIndexIfMissing(statement, "idx_city_roles_city", "city_roles(city_id)");
                createIndexIfMissing(statement, "idx_city_governance_vice", "city_governance(vice_uuid)");
                createIndexIfMissing(statement, "idx_city_claim_perms_city", "city_member_claim_permissions(city_id)");
                createIndexIfMissing(statement, "idx_city_claim_perms_player", "city_member_claim_permissions(player_uuid)");
                createIndexIfMissing(statement, "idx_ledger_city_time", "city_treasury_ledger(city_id, occurred_at)");
                createIndexIfMissing(statement, "idx_ledger_type_time", "city_treasury_ledger(entry_type, occurred_at)");
                createIndexIfMissing(statement, "idx_tickets_status_time", "staff_approval_tickets(status, requested_at)");
                createIndexIfMissing(statement, "idx_freeze_city_active", "freeze_cases(city_id, active)");
                createIndexIfMissing(statement, "idx_freeze_reason_active", "freeze_cases(reason, active)");
                createIndexIfMissing(statement, "idx_invite_city_status", "city_invitations(city_id, status)");
                createIndexIfMissing(statement, "idx_join_city_status", "join_requests(city_id, status)");
                createIndexIfMissing(statement, "idx_audit_agg_time", "audit_events(aggregate_type, aggregate_id, occurred_at)");
            }
        }
    }

    private static void createIndexIfMissing(Statement statement, String indexName, String expression) {
        try {
            statement.executeUpdate("CREATE INDEX " + indexName + " ON " + expression);
        } catch (SQLException ignored) {
            // index already exists
        }
    }

    private static void ensureCityColumns(Statement statement) {
        addColumnIfMissing(statement, "ALTER TABLE cities ADD COLUMN tier VARCHAR(32) NOT NULL DEFAULT 'BORGO'");
        addColumnIfMissing(statement, "ALTER TABLE cities ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'");
        addColumnIfMissing(statement, "ALTER TABLE cities ADD COLUMN member_count INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(statement, "ALTER TABLE cities ADD COLUMN max_members INTEGER NOT NULL DEFAULT 10");
    }

    private static void addColumnIfMissing(Statement statement, String ddl) {
        try {
            statement.executeUpdate(ddl);
        } catch (SQLException ignored) {
            // column already exists / unsupported alter variant
        }
    }
}
