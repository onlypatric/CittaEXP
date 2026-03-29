package it.patric.cittaexp.challenges;

import it.patric.cittaexp.vault.VaultAclEntry;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.plugin.Plugin;

public final class CityChallengeRepository {

    public record CycleState(
            ChallengeCycleType cycleType,
            String cycleKey,
            Instant generatedAt
    ) {
    }

    public record WeeklyStreakState(
            int townId,
            int streakCount,
            String lastCompletedWeekKey,
            Instant updatedAt
    ) {
    }

    public record GovernanceState(
            int townId,
            String seasonKey,
            Integer vetoCategory,
            int rerollUsed,
            Instant updatedAt
    ) {
    }

    public record MilestoneState(
            int townId,
            String seasonKey,
            int weeklyCompletedCount,
            boolean milestone1Done,
            boolean milestone2Done,
            boolean milestone3Done,
            boolean seasonalFinalDone,
            Instant updatedAt
    ) {
    }

    public record SelectionHistoryEntry(
            Integer townId,
            ChallengeMode mode,
            String cycleKey,
            String challengeId,
            int category,
            String objectiveFamily,
            String focusKey,
            String signatureKey,
            Instant selectedAt
    ) {
    }

    public record OutboxItem(
            String opId,
            String opType,
            String payload,
            int attempts,
            Instant createdAt,
            Instant nextAttemptAt
    ) {
    }

    public record DefenseSessionState(
            String sessionId,
            int townId,
            String townName,
            String tier,
            String status,
            Instant startedAt,
            Instant endedAt,
            String endReason
    ) {
    }

    public record PendingRewardEntry(
            long entryId,
            int townId,
            String itemBase64,
            String note,
            Instant createdAt
    ) {
    }

    private final String jdbcUrl;

    public CityChallengeRepository(Plugin plugin) {
        Path path = plugin.getDataFolder().toPath().resolve("challenges.db");
        this.jdbcUrl = "jdbc:sqlite:" + path;
    }

    public synchronized void initialize() {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS challenge_definitions (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        objective_type TEXT NOT NULL,
                        target INTEGER NOT NULL,
                        weight INTEGER NOT NULL,
                        standard_enabled INTEGER NOT NULL,
                        race_enabled INTEGER NOT NULL,
                        reward_bundle_id TEXT,
                        enabled INTEGER NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS challenge_instances (
                        instance_id TEXT PRIMARY KEY,
                        town_id INTEGER,
                        cycle_key TEXT NOT NULL,
                        challenge_id TEXT NOT NULL,
                        challenge_name TEXT NOT NULL,
                        objective_type TEXT NOT NULL,
                        target INTEGER NOT NULL,
                        base_target INTEGER NOT NULL DEFAULT 1,
                        excellence_target INTEGER NOT NULL DEFAULT 1,
                        fairness_multiplier REAL NOT NULL DEFAULT 1.0,
                        active_contributors_7d INTEGER NOT NULL DEFAULT 0,
                        size_snapshot INTEGER,
                        online_snapshot INTEGER,
                        target_formula_version TEXT NOT NULL DEFAULT 'm5-v1',
                        variant_type TEXT NOT NULL DEFAULT 'GLOBAL',
                        focus_type TEXT NOT NULL DEFAULT 'NONE',
                        focus_key TEXT,
                        focus_label TEXT,
                        biome_key TEXT,
                        dimension_key TEXT,
                        signature_key TEXT,
                        mode TEXT NOT NULL,
                        status TEXT NOT NULL,
                        winner_town_id INTEGER,
                        winner_player_uuid TEXT,
                        started_at TEXT NOT NULL,
                        ended_at TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_challenge_progress (
                        instance_id TEXT NOT NULL,
                        town_id INTEGER NOT NULL,
                        progress INTEGER NOT NULL,
                        completed_at TEXT,
                        excellence_completed_at TEXT,
                        updated_at TEXT NOT NULL,
                        PRIMARY KEY(instance_id, town_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS challenge_winners (
                        instance_id TEXT PRIMARY KEY,
                        town_id INTEGER NOT NULL,
                        player_uuid TEXT,
                        won_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS challenge_reward_ledger (
                        grant_key TEXT PRIMARY KEY,
                        town_id INTEGER NOT NULL,
                        instance_id TEXT NOT NULL,
                        reward_type TEXT NOT NULL,
                        payload TEXT,
                        granted_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_challenge_personal_reward_ledger (
                        grant_key TEXT PRIMARY KEY,
                        instance_id TEXT NOT NULL,
                        town_id INTEGER NOT NULL,
                        player_uuid TEXT NOT NULL,
                        reward_block TEXT NOT NULL,
                        reward_type TEXT NOT NULL,
                        payload TEXT,
                        granted_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS challenge_cycle_state (
                        cycle_type TEXT PRIMARY KEY,
                        cycle_key TEXT NOT NULL,
                        generated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_weekly_streak_state (
                        town_id INTEGER PRIMARY KEY,
                        streak_count INTEGER NOT NULL,
                        last_completed_week_key TEXT,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_challenge_governance (
                        town_id INTEGER NOT NULL,
                        season_key TEXT NOT NULL,
                        veto_category INTEGER,
                        reroll_used INTEGER NOT NULL,
                        updated_at TEXT NOT NULL,
                        PRIMARY KEY(town_id, season_key)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_challenge_milestones (
                        town_id INTEGER NOT NULL,
                        season_key TEXT NOT NULL,
                        weekly_completed_count INTEGER NOT NULL,
                        milestone1_done INTEGER NOT NULL,
                        milestone2_done INTEGER NOT NULL,
                        milestone3_done INTEGER NOT NULL,
                        seasonal_final_done INTEGER NOT NULL,
                        updated_at TEXT NOT NULL,
                        PRIMARY KEY(town_id, season_key)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_weekly_completion_log (
                        town_id INTEGER NOT NULL,
                        weekly_cycle_key TEXT NOT NULL,
                        completed_at TEXT NOT NULL,
                        PRIMARY KEY(town_id, weekly_cycle_key)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS challenge_selection_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        town_id INTEGER,
                        mode TEXT NOT NULL,
                        cycle_key TEXT NOT NULL,
                        challenge_id TEXT NOT NULL,
                        category INTEGER NOT NULL,
                        objective_family TEXT,
                        focus_key TEXT,
                        signature_key TEXT,
                        selected_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS challenge_sync_outbox (
                        op_id TEXT PRIMARY KEY,
                        op_type TEXT NOT NULL,
                        payload TEXT,
                        status TEXT NOT NULL,
                        attempts INTEGER NOT NULL,
                        next_attempt_at TEXT,
                        last_error TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_cycle_recap (
                        recap_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        town_id INTEGER NOT NULL,
                        cycle_type TEXT NOT NULL,
                        cycle_key TEXT NOT NULL,
                        completed_count INTEGER NOT NULL,
                        total_count INTEGER NOT NULL,
                        xp_scaled INTEGER NOT NULL,
                        excellence_xp_scaled INTEGER NOT NULL,
                        streak_delta INTEGER NOT NULL,
                        leaderboard_position INTEGER NOT NULL,
                        rewards_granted INTEGER NOT NULL,
                        top_contributor_uuid TEXT,
                        top_contribution INTEGER NOT NULL,
                        created_at TEXT NOT NULL,
                        UNIQUE(town_id, cycle_type, cycle_key)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_player_objective_checkpoint (
                        player_uuid TEXT NOT NULL,
                        objective_type TEXT NOT NULL,
                        value_long INTEGER NOT NULL,
                        updated_at TEXT NOT NULL,
                        PRIMARY KEY(player_uuid, objective_type)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS challenge_placed_blocks (
                        block_key TEXT PRIMARY KEY,
                        placed_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS challenge_objective_dedupe (
                        dedupe_key TEXT PRIMARY KEY,
                        instance_id TEXT NOT NULL,
                        town_id INTEGER NOT NULL,
                        player_uuid TEXT,
                        objective_type TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_challenge_player_contrib (
                        instance_id TEXT NOT NULL,
                        town_id INTEGER NOT NULL,
                        player_uuid TEXT NOT NULL,
                        contribution INTEGER NOT NULL,
                        updated_at TEXT NOT NULL,
                        PRIMARY KEY(instance_id, town_id, player_uuid)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_challenge_daily_category_cap (
                        day_key TEXT NOT NULL,
                        town_id INTEGER NOT NULL,
                        category_bucket TEXT NOT NULL,
                        consumed INTEGER NOT NULL,
                        updated_at TEXT NOT NULL,
                        PRIMARY KEY(day_key, town_id, category_bucket)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_challenge_suspicion (
                        town_id INTEGER NOT NULL,
                        player_uuid TEXT NOT NULL,
                        cycle_key TEXT NOT NULL,
                        score INTEGER NOT NULL,
                        review_flagged INTEGER NOT NULL,
                        updated_at TEXT NOT NULL,
                        PRIMARY KEY(town_id, player_uuid, cycle_key)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_item_vault_slots (
                        town_id INTEGER NOT NULL,
                        slot_idx INTEGER NOT NULL,
                        item_base64 TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        PRIMARY KEY(town_id, slot_idx)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_vault_member_acl (
                        town_id INTEGER NOT NULL,
                        player_uuid TEXT NOT NULL,
                        can_deposit INTEGER NOT NULL,
                        can_withdraw INTEGER NOT NULL,
                        updated_at TEXT NOT NULL,
                        PRIMARY KEY(town_id, player_uuid)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_vault_audit (
                        event_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        town_id INTEGER NOT NULL,
                        actor_uuid TEXT,
                        action TEXT NOT NULL,
                        target_uuid TEXT,
                        item_base64 TEXT,
                        amount INTEGER,
                        note TEXT,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS town_pending_reward_claims (
                        entry_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        town_id INTEGER NOT NULL,
                        item_base64 TEXT NOT NULL,
                        note TEXT,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_pending_reward_claims_town_created
                    ON town_pending_reward_claims(town_id, created_at)
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS atlas_family_progress (
                        town_id INTEGER NOT NULL,
                        family_id TEXT NOT NULL,
                        progress INTEGER NOT NULL,
                        updated_at TEXT NOT NULL,
                        PRIMARY KEY(town_id, family_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS atlas_reward_ledger (
                        grant_key TEXT PRIMARY KEY,
                        town_id INTEGER NOT NULL,
                        family_id TEXT NOT NULL,
                        tier TEXT NOT NULL,
                        reward_type TEXT NOT NULL,
                        payload TEXT,
                        granted_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS atlas_activity_ledger (
                        entry_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        town_id INTEGER NOT NULL,
                        chapter TEXT NOT NULL,
                        family_id TEXT NOT NULL,
                        delta_progress INTEGER NOT NULL,
                        created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_atlas_activity_town_created
                    ON atlas_activity_ledger(town_id, created_at)
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS challenge_runtime_state (
                        state_key TEXT PRIMARY KEY,
                        state_value TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS city_defense_cooldown (
                        town_id INTEGER NOT NULL,
                        tier TEXT NOT NULL,
                        available_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        PRIMARY KEY(town_id, tier)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS city_defense_sessions (
                        session_id TEXT PRIMARY KEY,
                        town_id INTEGER NOT NULL,
                        town_name TEXT NOT NULL,
                        tier TEXT NOT NULL,
                        status TEXT NOT NULL,
                        started_at TEXT NOT NULL,
                        ended_at TEXT,
                        end_reason TEXT,
                        reward_money_amount REAL NOT NULL DEFAULT 0,
                        reward_xp_scaled INTEGER NOT NULL DEFAULT 0,
                        reward_items_payload TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS city_defense_reward_ledger (
                        grant_key TEXT PRIMARY KEY,
                        town_id INTEGER NOT NULL,
                        session_id TEXT NOT NULL,
                        reward_type TEXT NOT NULL,
                        payload TEXT,
                        granted_at TEXT NOT NULL
                    )
                    """);
            ensureColumn(connection, "challenge_definitions", "category", "INTEGER NOT NULL DEFAULT 1");
            ensureColumn(connection, "challenge_definitions", "cycles_supported", "TEXT NOT NULL DEFAULT 'DAILY_STANDARD'");
            ensureColumn(connection, "challenge_instances", "cycle_type", "TEXT NOT NULL DEFAULT 'DAILY'");
            ensureColumn(connection, "challenge_instances", "window_key", "TEXT NOT NULL DEFAULT 'daily'");
            ensureColumn(connection, "challenge_instances", "town_id", "INTEGER");
            ensureColumn(connection, "challenge_instances", "category", "INTEGER NOT NULL DEFAULT 1");
            ensureColumn(connection, "challenge_instances", "cycle_start_at", "TEXT");
            ensureColumn(connection, "challenge_instances", "cycle_end_at", "TEXT");
            ensureColumn(connection, "challenge_instances", "variant_type", "TEXT NOT NULL DEFAULT 'GLOBAL'");
            ensureColumn(connection, "challenge_instances", "focus_type", "TEXT NOT NULL DEFAULT 'NONE'");
            ensureColumn(connection, "challenge_instances", "focus_key", "TEXT");
            ensureColumn(connection, "challenge_instances", "focus_label", "TEXT");
            ensureColumn(connection, "challenge_instances", "biome_key", "TEXT");
            ensureColumn(connection, "challenge_instances", "dimension_key", "TEXT");
            ensureColumn(connection, "challenge_instances", "signature_key", "TEXT");
            ensureColumn(connection, "challenge_instances", "base_target", "INTEGER NOT NULL DEFAULT 1");
            ensureColumn(connection, "challenge_instances", "excellence_target", "INTEGER NOT NULL DEFAULT 1");
            ensureColumn(connection, "city_defense_sessions", "reward_money_amount", "REAL NOT NULL DEFAULT 0");
            ensureColumn(connection, "challenge_instances", "fairness_multiplier", "REAL NOT NULL DEFAULT 1.0");
            ensureColumn(connection, "challenge_instances", "active_contributors_7d", "INTEGER NOT NULL DEFAULT 0");
            ensureColumn(connection, "challenge_instances", "size_snapshot", "INTEGER");
            ensureColumn(connection, "challenge_instances", "online_snapshot", "INTEGER");
            ensureColumn(connection, "challenge_instances", "target_formula_version", "TEXT NOT NULL DEFAULT 'm5-v1'");
            ensureColumn(connection, "challenge_definitions", "bundle_slot", "TEXT NOT NULL DEFAULT 'support'");
            ensureColumn(connection, "challenge_definitions", "difficulty_tag", "TEXT NOT NULL DEFAULT 'medium'");
            ensureColumn(connection, "challenge_definitions", "objective_family", "TEXT NOT NULL DEFAULT 'generic'");
            ensureColumn(connection, "challenge_selection_history", "signature_key", "TEXT");
            ensureColumn(connection, "town_challenge_progress", "excellence_completed_at", "TEXT");
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile inizializzare challenges.db", exception);
        }
    }

    public synchronized void replaceDefinitions(Map<String, ChallengeDefinition> definitions) {
        Instant now = Instant.now();
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try (Statement clear = connection.createStatement()) {
                clear.execute("DELETE FROM challenge_definitions");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO challenge_definitions(id, name, objective_type, category, bundle_slot, difficulty_tag, objective_family,
                                                             target, weight, standard_enabled, race_enabled,
                                                             cycles_supported, reward_bundle_id, enabled, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """)) {
                for (ChallengeDefinition definition : definitions.values()) {
                    statement.setString(1, definition.id());
                    statement.setString(2, definition.displayName());
                    statement.setString(3, definition.objectiveType().id());
                    statement.setInt(4, definition.category());
                    statement.setString(5, definition.bundleSlot());
                    statement.setString(6, definition.difficultyTag());
                    statement.setString(7, definition.objectiveFamily());
                    statement.setInt(8, definition.target());
                    statement.setInt(9, definition.weight());
                    statement.setInt(10, definition.cyclesSupported().contains(ChallengeMode.DAILY_STANDARD) ? 1 : 0);
                    statement.setInt(11, definition.cyclesSupported().stream().anyMatch(ChallengeMode::race) ? 1 : 0);
                    statement.setString(12, toCycles(definition.cyclesSupported()));
                    statement.setString(13, definition.rewardBundleId());
                    statement.setInt(14, definition.enabled() ? 1 : 0);
                    statement.setString(15, now.toString());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile salvare challenge definitions", exception);
        }
    }

    public synchronized Map<String, ChallengeInstance> loadInstances() {
        Map<String, ChallengeInstance> result = new LinkedHashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT instance_id, cycle_key, cycle_type, window_key, challenge_id, challenge_name, objective_type, category,
                                    town_id,
                                    target, base_target, excellence_target, fairness_multiplier, active_contributors_7d,
                                    size_snapshot, online_snapshot, target_formula_version,
                                    variant_type, focus_type, focus_key, focus_label, biome_key, dimension_key, signature_key,
                                    mode, status, winner_town_id, winner_player_uuid, cycle_start_at, cycle_end_at, started_at, ended_at
                             FROM challenge_instances
                             """);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                ChallengeObjectiveType objectiveType = ChallengeObjectiveType.fromId(rs.getString("objective_type"));
                if (objectiveType == null) {
                    continue;
                }
                ChallengeMode mode;
                ChallengeInstanceStatus status;
                ChallengeCycleType cycleType;
                try {
                    mode = ChallengeMode.valueOf(rs.getString("mode"));
                    status = ChallengeInstanceStatus.valueOf(rs.getString("status"));
                    cycleType = ChallengeCycleType.valueOf(rs.getString("cycle_type"));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                UUID winnerPlayerId = parseUuidNullable(rs.getString("winner_player_uuid"));
                Integer winnerTown = null;
                int winnerTownRaw = rs.getInt("winner_town_id");
                if (!rs.wasNull()) {
                    winnerTown = winnerTownRaw;
                }
                ChallengeVariantType variantType;
                ChallengeFocusType focusType;
                try {
                    variantType = ChallengeVariantType.valueOf(rs.getString("variant_type"));
                } catch (IllegalArgumentException | NullPointerException ignored) {
                    variantType = ChallengeVariantType.GLOBAL;
                }
                try {
                    focusType = ChallengeFocusType.valueOf(rs.getString("focus_type"));
                } catch (IllegalArgumentException | NullPointerException ignored) {
                    focusType = ChallengeFocusType.NONE;
                }
                ChallengeInstance instance = new ChallengeInstance(
                        rs.getString("instance_id"),
                        parseIntNullable(rs, "town_id"),
                        rs.getString("cycle_key"),
                        cycleType,
                        rs.getString("window_key"),
                        rs.getString("challenge_id"),
                        rs.getString("challenge_name"),
                        objectiveType,
                        rs.getInt("category"),
                        Math.max(1, rs.getInt("base_target")),
                        rs.getInt("target"),
                        Math.max(1, rs.getInt("excellence_target")),
                        Math.max(0.01D, rs.getDouble("fairness_multiplier")),
                        Math.max(0, rs.getInt("active_contributors_7d")),
                        parseIntNullable(rs, "size_snapshot"),
                        parseIntNullable(rs, "online_snapshot"),
                        Optional.ofNullable(rs.getString("target_formula_version")).orElse("m5-v1"),
                        variantType,
                        focusType,
                        rs.getString("focus_key"),
                        rs.getString("focus_label"),
                        rs.getString("biome_key"),
                        rs.getString("dimension_key"),
                        rs.getString("signature_key"),
                        mode,
                        status,
                        winnerTown,
                        winnerPlayerId,
                        parseInstantNullable(rs.getString("cycle_start_at")),
                        parseInstantNullable(rs.getString("cycle_end_at")),
                        parseInstant(rs.getString("started_at")),
                        parseInstantNullable(rs.getString("ended_at"))
                );
                result.put(instance.instanceId(), instance);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare challenge instances", exception);
        }
        return result;
    }

    public synchronized Map<String, Map<Integer, TownChallengeProgress>> loadProgressByInstance() {
        Map<String, Map<Integer, TownChallengeProgress>> result = new HashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT instance_id, town_id, progress, completed_at, excellence_completed_at, updated_at
                             FROM town_challenge_progress
                             """);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                TownChallengeProgress progress = new TownChallengeProgress(
                        rs.getString("instance_id"),
                        rs.getInt("town_id"),
                        rs.getInt("progress"),
                        parseInstantNullable(rs.getString("completed_at")),
                        parseInstantNullable(rs.getString("excellence_completed_at")),
                        parseInstant(rs.getString("updated_at"))
                );
                result.computeIfAbsent(progress.instanceId(), ignored -> new HashMap<>()).put(progress.townId(), progress);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare challenge progress", exception);
        }
        return result;
    }

    public synchronized Set<String> loadRewardGrantKeys() {
        java.util.HashSet<String> result = new java.util.HashSet<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT grant_key FROM challenge_reward_ledger");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString("grant_key"));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare reward ledger keys", exception);
        }
        return result;
    }

    public synchronized Map<String, Long> loadObjectiveCheckpoints() {
        Map<String, Long> result = new HashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT player_uuid, objective_type, value_long FROM town_player_objective_checkpoint");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String player = rs.getString("player_uuid");
                String objective = rs.getString("objective_type");
                long value = rs.getLong("value_long");
                if (player == null || player.isBlank() || objective == null || objective.isBlank()) {
                    continue;
                }
                result.put(player + '|' + objective, value);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare checkpoint obiettivi", exception);
        }
        return result;
    }

    public synchronized Map<String, Instant> loadPlacedBlocks() {
        Map<String, Instant> result = new HashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT block_key, placed_at FROM challenge_placed_blocks");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String blockKey = rs.getString("block_key");
                if (blockKey == null || blockKey.isBlank()) {
                    continue;
                }
                result.put(blockKey, parseInstant(rs.getString("placed_at")));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare placed blocks", exception);
        }
        return result;
    }

    public synchronized Map<String, Integer> loadPlayerContributions() {
        Map<String, Integer> result = new HashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT instance_id, town_id, player_uuid, contribution FROM town_challenge_player_contrib");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String instanceId = rs.getString("instance_id");
                UUID playerId = parseUuidNullable(rs.getString("player_uuid"));
                int townId = rs.getInt("town_id");
                if (instanceId == null || instanceId.isBlank() || playerId == null || townId <= 0) {
                    continue;
                }
                result.put(instanceId + "|" + townId + "|" + playerId, Math.max(0, rs.getInt("contribution")));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare contributi player challenge", exception);
        }
        return result;
    }

    public synchronized Map<String, Integer> loadDailyCategoryCaps() {
        Map<String, Integer> result = new HashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT day_key, town_id, category_bucket, consumed FROM town_challenge_daily_category_cap");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String dayKey = rs.getString("day_key");
                String bucket = rs.getString("category_bucket");
                int townId = rs.getInt("town_id");
                if (dayKey == null || dayKey.isBlank() || bucket == null || bucket.isBlank() || townId <= 0) {
                    continue;
                }
                result.put(dayKey + "|" + townId + "|" + bucket, Math.max(0, rs.getInt("consumed")));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare cap giornalieri per categoria", exception);
        }
        return result;
    }

    public synchronized Map<ChallengeCycleType, CycleState> loadCycleState() {
        Map<ChallengeCycleType, CycleState> result = new EnumMap<>(ChallengeCycleType.class);
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT cycle_type, cycle_key, generated_at FROM challenge_cycle_state");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                try {
                    ChallengeCycleType type = ChallengeCycleType.valueOf(rs.getString("cycle_type"));
                    result.put(type, new CycleState(type, rs.getString("cycle_key"), parseInstant(rs.getString("generated_at"))));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare challenge cycle state", exception);
        }
        return result;
    }

    public synchronized Map<Integer, CityChallengeStreakService.State> loadWeeklyStreakState() {
        Map<Integer, CityChallengeStreakService.State> result = new HashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT town_id, streak_count, last_completed_week_key, updated_at
                             FROM town_weekly_streak_state
                             """);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                int townId = rs.getInt("town_id");
                if (townId <= 0) {
                    continue;
                }
                result.put(townId, new CityChallengeStreakService.State(
                        townId,
                        Math.max(0, rs.getInt("streak_count")),
                        rs.getString("last_completed_week_key"),
                        parseInstant(rs.getString("updated_at"))
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare weekly streak state", exception);
        }
        return result;
    }

    public synchronized void upsertInstance(ChallengeInstance instance) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO challenge_instances(instance_id, town_id, cycle_key, cycle_type, window_key, challenge_id, challenge_name,
                                                             objective_type, category, base_target, target, excellence_target, fairness_multiplier, active_contributors_7d,
                                                             size_snapshot, online_snapshot, target_formula_version,
                                                             variant_type, focus_type, focus_key, focus_label, biome_key, dimension_key, signature_key,
                                                             mode, status,
                                                             winner_town_id, winner_player_uuid, cycle_start_at, cycle_end_at, started_at, ended_at)
                             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                             ON CONFLICT(instance_id) DO UPDATE SET
                                 town_id = excluded.town_id,
                                 cycle_key = excluded.cycle_key,
                                 cycle_type = excluded.cycle_type,
                                 window_key = excluded.window_key,
                                 challenge_id = excluded.challenge_id,
                                 challenge_name = excluded.challenge_name,
                                 objective_type = excluded.objective_type,
                                 category = excluded.category,
                                 base_target = excluded.base_target,
                                 target = excluded.target,
                                 excellence_target = excluded.excellence_target,
                                 fairness_multiplier = excluded.fairness_multiplier,
                                 active_contributors_7d = excluded.active_contributors_7d,
                                 size_snapshot = excluded.size_snapshot,
                                 online_snapshot = excluded.online_snapshot,
                                 target_formula_version = excluded.target_formula_version,
                                 variant_type = excluded.variant_type,
                                 focus_type = excluded.focus_type,
                                 focus_key = excluded.focus_key,
                                 focus_label = excluded.focus_label,
                                 biome_key = excluded.biome_key,
                                 dimension_key = excluded.dimension_key,
                                 signature_key = excluded.signature_key,
                                 mode = excluded.mode,
                                 status = excluded.status,
                                 winner_town_id = excluded.winner_town_id,
                                 winner_player_uuid = excluded.winner_player_uuid,
                                 cycle_start_at = excluded.cycle_start_at,
                                 cycle_end_at = excluded.cycle_end_at,
                                 ended_at = excluded.ended_at
                             """)) {
            statement.setString(1, instance.instanceId());
            if (instance.townId() == null) {
                statement.setObject(2, null);
            } else {
                statement.setInt(2, instance.townId());
            }
            statement.setString(3, instance.cycleKey());
            statement.setString(4, instance.cycleType().name());
            statement.setString(5, instance.windowKey());
            statement.setString(6, instance.challengeId());
            statement.setString(7, instance.challengeName());
            statement.setString(8, instance.objectiveType().id());
            statement.setInt(9, instance.category());
            statement.setInt(10, instance.baseTarget());
            statement.setInt(11, instance.target());
            statement.setInt(12, instance.excellenceTarget());
            statement.setDouble(13, instance.fairnessMultiplier());
            statement.setInt(14, Math.max(0, instance.activeContributors7d()));
            if (instance.sizeSnapshot() == null) {
                statement.setObject(15, null);
            } else {
                statement.setInt(15, Math.max(1, instance.sizeSnapshot()));
            }
            if (instance.onlineSnapshot() == null) {
                statement.setObject(16, null);
            } else {
                statement.setInt(16, Math.max(0, instance.onlineSnapshot()));
            }
            statement.setString(17, instance.targetFormulaVersion() == null || instance.targetFormulaVersion().isBlank()
                    ? "m5-v1"
                    : instance.targetFormulaVersion());
            statement.setString(18, instance.variantType() == null ? ChallengeVariantType.GLOBAL.name() : instance.variantType().name());
            statement.setString(19, instance.focusType() == null ? ChallengeFocusType.NONE.name() : instance.focusType().name());
            statement.setString(20, instance.focusKey());
            statement.setString(21, instance.focusLabel());
            statement.setString(22, instance.biomeKey());
            statement.setString(23, instance.dimensionKey());
            statement.setString(24, instance.signatureKey());
            statement.setString(25, instance.mode().name());
            statement.setString(26, instance.status().name());
            if (instance.winnerTownId() == null) {
                statement.setObject(27, null);
            } else {
                statement.setInt(27, instance.winnerTownId());
            }
            statement.setString(28, instance.winnerPlayerId() == null ? null : instance.winnerPlayerId().toString());
            statement.setString(29, instance.cycleStartAt() == null ? null : instance.cycleStartAt().toString());
            statement.setString(30, instance.cycleEndAt() == null ? null : instance.cycleEndAt().toString());
            statement.setString(31, instance.startedAt().toString());
            statement.setString(32, instance.endedAt() == null ? null : instance.endedAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile upsert challenge instance=" + instance.instanceId(), exception);
        }
    }

    public synchronized void clearRuntimeStateForRegenerate() {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate("DELETE FROM town_challenge_progress");
            statement.executeUpdate("DELETE FROM challenge_winners");
            statement.executeUpdate("DELETE FROM challenge_reward_ledger");
            statement.executeUpdate("DELETE FROM town_challenge_personal_reward_ledger");
            statement.executeUpdate("DELETE FROM town_challenge_player_contrib");
            statement.executeUpdate("DELETE FROM town_challenge_daily_category_cap");
            statement.executeUpdate("DELETE FROM town_challenge_governance");
            statement.executeUpdate("DELETE FROM town_challenge_milestones");
            statement.executeUpdate("DELETE FROM town_weekly_completion_log");
            statement.executeUpdate("DELETE FROM challenge_selection_history");
            statement.executeUpdate("DELETE FROM challenge_objective_dedupe");
            statement.executeUpdate("DELETE FROM town_challenge_suspicion");
            statement.executeUpdate("DELETE FROM challenge_instances");
            statement.executeUpdate("DELETE FROM challenge_cycle_state");
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile pulire runtime challenge state", exception);
        }
    }

    public synchronized void clearAllChallengeStateForM11Cutover() {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate("DELETE FROM town_challenge_progress");
            statement.executeUpdate("DELETE FROM challenge_winners");
            statement.executeUpdate("DELETE FROM challenge_reward_ledger");
            statement.executeUpdate("DELETE FROM town_challenge_personal_reward_ledger");
            statement.executeUpdate("DELETE FROM challenge_cycle_state");
            statement.executeUpdate("DELETE FROM town_weekly_streak_state");
            statement.executeUpdate("DELETE FROM town_challenge_governance");
            statement.executeUpdate("DELETE FROM town_challenge_milestones");
            statement.executeUpdate("DELETE FROM town_weekly_completion_log");
            statement.executeUpdate("DELETE FROM challenge_selection_history");
            statement.executeUpdate("DELETE FROM challenge_sync_outbox");
            statement.executeUpdate("DELETE FROM town_cycle_recap");
            statement.executeUpdate("DELETE FROM town_player_objective_checkpoint");
            statement.executeUpdate("DELETE FROM challenge_placed_blocks");
            statement.executeUpdate("DELETE FROM challenge_objective_dedupe");
            statement.executeUpdate("DELETE FROM town_challenge_player_contrib");
            statement.executeUpdate("DELETE FROM town_challenge_daily_category_cap");
            statement.executeUpdate("DELETE FROM town_challenge_suspicion");
            statement.executeUpdate("DELETE FROM atlas_family_progress");
            statement.executeUpdate("DELETE FROM atlas_reward_ledger");
            statement.executeUpdate("DELETE FROM atlas_activity_ledger");
            statement.executeUpdate("DELETE FROM challenge_runtime_state");
            statement.executeUpdate("DELETE FROM challenge_instances");
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile eseguire reset completo challenge state (M11 cutover)", exception);
        }
    }

    public synchronized Map<String, GovernanceState> loadGovernanceState() {
        Map<String, GovernanceState> result = new HashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT town_id, season_key, veto_category, reroll_used, updated_at FROM town_challenge_governance");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                int townId = rs.getInt("town_id");
                String seasonKey = rs.getString("season_key");
                if (townId <= 0 || seasonKey == null || seasonKey.isBlank()) {
                    continue;
                }
                GovernanceState state = new GovernanceState(
                        townId,
                        seasonKey,
                        parseIntNullable(rs, "veto_category"),
                        Math.max(0, rs.getInt("reroll_used")),
                        parseInstant(rs.getString("updated_at"))
                );
                result.put(townId + "|" + seasonKey, state);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare governance state challenge", exception);
        }
        return result;
    }

    public synchronized void upsertGovernanceState(GovernanceState state) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO town_challenge_governance(town_id, season_key, veto_category, reroll_used, updated_at)
                             VALUES (?, ?, ?, ?, ?)
                             ON CONFLICT(town_id, season_key) DO UPDATE SET
                                 veto_category = excluded.veto_category,
                                 reroll_used = excluded.reroll_used,
                                 updated_at = excluded.updated_at
                             """)) {
            statement.setInt(1, state.townId());
            statement.setString(2, state.seasonKey());
            if (state.vetoCategory() == null) {
                statement.setObject(3, null);
            } else {
                statement.setInt(3, state.vetoCategory());
            }
            statement.setInt(4, Math.max(0, state.rerollUsed()));
            statement.setString(5, state.updatedAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile upsert governance state challenge", exception);
        }
    }

    public synchronized Map<String, MilestoneState> loadMilestoneState() {
        Map<String, MilestoneState> result = new HashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT town_id, season_key, weekly_completed_count, milestone1_done, milestone2_done, milestone3_done,
                                    seasonal_final_done, updated_at
                             FROM town_challenge_milestones
                             """);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                int townId = rs.getInt("town_id");
                String seasonKey = rs.getString("season_key");
                if (townId <= 0 || seasonKey == null || seasonKey.isBlank()) {
                    continue;
                }
                MilestoneState state = new MilestoneState(
                        townId,
                        seasonKey,
                        Math.max(0, rs.getInt("weekly_completed_count")),
                        rs.getInt("milestone1_done") == 1,
                        rs.getInt("milestone2_done") == 1,
                        rs.getInt("milestone3_done") == 1,
                        rs.getInt("seasonal_final_done") == 1,
                        parseInstant(rs.getString("updated_at"))
                );
                result.put(townId + "|" + seasonKey, state);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare milestone challenge", exception);
        }
        return result;
    }

    public synchronized void upsertMilestoneState(MilestoneState state) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO town_challenge_milestones(town_id, season_key, weekly_completed_count, milestone1_done, milestone2_done,
                                                                  milestone3_done, seasonal_final_done, updated_at)
                             VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                             ON CONFLICT(town_id, season_key) DO UPDATE SET
                                 weekly_completed_count = excluded.weekly_completed_count,
                                 milestone1_done = excluded.milestone1_done,
                                 milestone2_done = excluded.milestone2_done,
                                 milestone3_done = excluded.milestone3_done,
                                 seasonal_final_done = excluded.seasonal_final_done,
                                 updated_at = excluded.updated_at
                             """)) {
            statement.setInt(1, state.townId());
            statement.setString(2, state.seasonKey());
            statement.setInt(3, Math.max(0, state.weeklyCompletedCount()));
            statement.setInt(4, state.milestone1Done() ? 1 : 0);
            statement.setInt(5, state.milestone2Done() ? 1 : 0);
            statement.setInt(6, state.milestone3Done() ? 1 : 0);
            statement.setInt(7, state.seasonalFinalDone() ? 1 : 0);
            statement.setString(8, state.updatedAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile upsert milestone challenge", exception);
        }
    }

    public synchronized boolean tryInsertWeeklyCompletion(int townId, String weeklyCycleKey, Instant completedAt) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT OR IGNORE INTO town_weekly_completion_log(town_id, weekly_cycle_key, completed_at)
                             VALUES (?, ?, ?)
                             """)) {
            statement.setInt(1, townId);
            statement.setString(2, weeklyCycleKey);
            statement.setString(3, completedAt.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile inserire weekly completion", exception);
        }
    }

    public synchronized List<SelectionHistoryEntry> loadSelectionHistory() {
        List<SelectionHistoryEntry> result = new java.util.ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT town_id, mode, cycle_key, challenge_id, category, objective_family, focus_key, signature_key, selected_at
                             FROM challenge_selection_history
                             ORDER BY id DESC
                             """);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                ChallengeMode mode;
                try {
                    mode = ChallengeMode.valueOf(rs.getString("mode"));
                } catch (IllegalArgumentException exception) {
                    continue;
                }
                result.add(new SelectionHistoryEntry(
                        parseIntNullable(rs, "town_id"),
                        mode,
                        rs.getString("cycle_key"),
                        rs.getString("challenge_id"),
                        rs.getInt("category"),
                        rs.getString("objective_family"),
                        rs.getString("focus_key"),
                        rs.getString("signature_key"),
                        parseInstant(rs.getString("selected_at"))
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare history curation challenge", exception);
        }
        return result;
    }

    public synchronized void appendSelectionHistory(SelectionHistoryEntry entry) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO challenge_selection_history(town_id, mode, cycle_key, challenge_id, category, objective_family, focus_key, signature_key, selected_at)
                             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                             """)) {
            if (entry.townId() == null) {
                statement.setObject(1, null);
            } else {
                statement.setInt(1, entry.townId());
            }
            statement.setString(2, entry.mode().name());
            statement.setString(3, entry.cycleKey());
            statement.setString(4, entry.challengeId());
            statement.setInt(5, entry.category());
            statement.setString(6, entry.objectiveFamily());
            statement.setString(7, entry.focusKey());
            statement.setString(8, entry.signatureKey());
            statement.setString(9, entry.selectedAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile append history curation challenge", exception);
        }
    }

    public synchronized void upsertProgress(TownChallengeProgress progress) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO town_challenge_progress(instance_id, town_id, progress, completed_at, excellence_completed_at, updated_at)
                             VALUES (?, ?, ?, ?, ?, ?)
                             ON CONFLICT(instance_id, town_id) DO UPDATE SET
                                 progress = excluded.progress,
                                 completed_at = excluded.completed_at,
                                 excellence_completed_at = excluded.excellence_completed_at,
                                 updated_at = excluded.updated_at
                             """)) {
            statement.setString(1, progress.instanceId());
            statement.setInt(2, progress.townId());
            statement.setInt(3, progress.progress());
            statement.setString(4, progress.completedAt() == null ? null : progress.completedAt().toString());
            statement.setString(5, progress.excellenceCompletedAt() == null ? null : progress.excellenceCompletedAt().toString());
            statement.setString(6, progress.updatedAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile upsert challenge progress instance=" + progress.instanceId(), exception);
        }
    }

    public synchronized boolean tryInsertWinner(String instanceId, int townId, UUID playerId, Instant wonAt) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT OR IGNORE INTO challenge_winners(instance_id, town_id, player_uuid, won_at)
                             VALUES (?, ?, ?, ?)
                             """)) {
            statement.setString(1, instanceId);
            statement.setInt(2, townId);
            statement.setString(3, playerId == null ? null : playerId.toString());
            statement.setString(4, wonAt.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile inserire winner instance=" + instanceId, exception);
        }
    }

    public synchronized boolean markRewardGrant(
            String grantKey,
            int townId,
            String instanceId,
            ChallengeRewardType rewardType,
            String payload,
            Instant grantedAt
    ) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT OR IGNORE INTO challenge_reward_ledger(grant_key, town_id, instance_id, reward_type, payload, granted_at)
                             VALUES (?, ?, ?, ?, ?, ?)
                             """)) {
            statement.setString(1, grantKey);
            statement.setInt(2, townId);
            statement.setString(3, instanceId);
            statement.setString(4, rewardType.name());
            statement.setString(5, payload);
            statement.setString(6, grantedAt.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile marcare reward grant key=" + grantKey, exception);
        }
    }

    public synchronized boolean markPersonalRewardGrant(
            String grantKey,
            String instanceId,
            int townId,
            UUID playerId,
            String rewardBlock,
            ChallengeRewardType rewardType,
            String payload,
            Instant grantedAt
    ) {
        if (grantKey == null || grantKey.isBlank() || instanceId == null || instanceId.isBlank()
                || townId <= 0 || playerId == null || rewardBlock == null || rewardBlock.isBlank()
                || rewardType == null || grantedAt == null) {
            return false;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT OR IGNORE INTO town_challenge_personal_reward_ledger(
                                 grant_key, instance_id, town_id, player_uuid, reward_block, reward_type, payload, granted_at
                             )
                             VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                             """)) {
            statement.setString(1, grantKey);
            statement.setString(2, instanceId);
            statement.setInt(3, townId);
            statement.setString(4, playerId.toString());
            statement.setString(5, rewardBlock);
            statement.setString(6, rewardType.name());
            statement.setString(7, payload);
            statement.setString(8, grantedAt.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile marcare personal reward grant key=" + grantKey, exception);
        }
    }

    public synchronized int countActiveContributorsSince(int townId, Instant since) {
        if (townId <= 0 || since == null) {
            return 0;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT COUNT(DISTINCT player_uuid) AS cnt
                             FROM town_challenge_player_contrib
                             WHERE town_id = ? AND contribution > 0 AND updated_at >= ?
                             """)) {
            statement.setInt(1, townId);
            statement.setString(2, since.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Math.max(0, rs.getInt("cnt"));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile contare active contributors town=" + townId, exception);
        }
        return 0;
    }

    public synchronized void upsertObjectiveCheckpoint(UUID playerId, String objectiveKey, long value, Instant updatedAt) {
        if (playerId == null || objectiveKey == null || objectiveKey.isBlank()) {
            return;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO town_player_objective_checkpoint(player_uuid, objective_type, value_long, updated_at)
                             VALUES (?, ?, ?, ?)
                             ON CONFLICT(player_uuid, objective_type) DO UPDATE SET
                                 value_long = excluded.value_long,
                                 updated_at = excluded.updated_at
                             """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, objectiveKey);
            statement.setLong(3, value);
            statement.setString(4, updatedAt.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile upsert objective checkpoint", exception);
        }
    }

    public synchronized void upsertPlacedBlock(String blockKey, Instant placedAt) {
        if (blockKey == null || blockKey.isBlank() || placedAt == null) {
            return;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO challenge_placed_blocks(block_key, placed_at)
                             VALUES (?, ?)
                             ON CONFLICT(block_key) DO UPDATE SET
                                 placed_at = excluded.placed_at
                             """)) {
            statement.setString(1, blockKey);
            statement.setString(2, placedAt.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile upsert placed block key=" + blockKey, exception);
        }
    }

    public synchronized void deletePlacedBlock(String blockKey) {
        if (blockKey == null || blockKey.isBlank()) {
            return;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM challenge_placed_blocks WHERE block_key = ?")) {
            statement.setString(1, blockKey);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile cancellare placed block key=" + blockKey, exception);
        }
    }

    public synchronized void deletePlacedBlocks(List<String> blockKeys) {
        if (blockKeys == null || blockKeys.isEmpty()) {
            return;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM challenge_placed_blocks WHERE block_key = ?")) {
            for (String blockKey : blockKeys) {
                if (blockKey == null || blockKey.isBlank()) {
                    continue;
                }
                statement.setString(1, blockKey);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile cancellare batch placed blocks", exception);
        }
    }

    public synchronized void upsertPlayerContribution(
            String instanceId,
            int townId,
            UUID playerId,
            int contribution,
            Instant updatedAt
    ) {
        if (instanceId == null || instanceId.isBlank() || townId <= 0 || playerId == null || updatedAt == null) {
            return;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO town_challenge_player_contrib(instance_id, town_id, player_uuid, contribution, updated_at)
                             VALUES (?, ?, ?, ?, ?)
                             ON CONFLICT(instance_id, town_id, player_uuid) DO UPDATE SET
                                 contribution = excluded.contribution,
                                 updated_at = excluded.updated_at
                             """)) {
            statement.setString(1, instanceId);
            statement.setInt(2, townId);
            statement.setString(3, playerId.toString());
            statement.setInt(4, Math.max(0, contribution));
            statement.setString(5, updatedAt.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile upsert contributo player", exception);
        }
    }

    public synchronized void upsertDailyCategoryCap(
            String dayKey,
            int townId,
            String categoryBucket,
            int consumed,
            Instant updatedAt
    ) {
        if (dayKey == null || dayKey.isBlank() || townId <= 0 || categoryBucket == null
                || categoryBucket.isBlank() || updatedAt == null) {
            return;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO town_challenge_daily_category_cap(day_key, town_id, category_bucket, consumed, updated_at)
                             VALUES (?, ?, ?, ?, ?)
                             ON CONFLICT(day_key, town_id, category_bucket) DO UPDATE SET
                                 consumed = excluded.consumed,
                                 updated_at = excluded.updated_at
                             """)) {
            statement.setString(1, dayKey);
            statement.setInt(2, townId);
            statement.setString(3, categoryBucket);
            statement.setInt(4, Math.max(0, consumed));
            statement.setString(5, updatedAt.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile upsert cap giornaliero categoria", exception);
        }
    }

    public synchronized void upsertCycleState(ChallengeCycleType cycleType, String cycleKey, Instant generatedAt) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO challenge_cycle_state(cycle_type, cycle_key, generated_at)
                             VALUES (?, ?, ?)
                             ON CONFLICT(cycle_type) DO UPDATE SET
                                 cycle_key = excluded.cycle_key,
                                 generated_at = excluded.generated_at
                             """)) {
            statement.setString(1, cycleType.name());
            statement.setString(2, cycleKey);
            statement.setString(3, generatedAt.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile upsert cycle state type=" + cycleType, exception);
        }
    }

    public synchronized void upsertWeeklyStreakState(CityChallengeStreakService.State state) {
        if (state == null || state.townId() <= 0) {
            return;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO town_weekly_streak_state(town_id, streak_count, last_completed_week_key, updated_at)
                             VALUES (?, ?, ?, ?)
                             ON CONFLICT(town_id) DO UPDATE SET
                                 streak_count = excluded.streak_count,
                                 last_completed_week_key = excluded.last_completed_week_key,
                                 updated_at = excluded.updated_at
                             """)) {
            statement.setInt(1, state.townId());
            statement.setInt(2, Math.max(0, state.streakCount()));
            statement.setString(3, state.lastCompletedWeekKey());
            statement.setString(4, (state.updatedAt() == null ? Instant.now() : state.updatedAt()).toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile upsert weekly streak state town=" + state.townId(), exception);
        }
    }

    public synchronized Map<Integer, String> loadVaultSlots(int townId) {
        Map<Integer, String> result = new HashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT slot_idx, item_base64 FROM town_item_vault_slots WHERE town_id = ?")) {
            statement.setInt(1, townId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getInt("slot_idx"), rs.getString("item_base64"));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare vault slots town=" + townId, exception);
        }
        return result;
    }

    public synchronized void replaceVaultSlots(int townId, Map<Integer, String> slots) {
        Instant now = Instant.now();
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM town_item_vault_slots WHERE town_id = ?")) {
                delete.setInt(1, townId);
                delete.executeUpdate();
            }
            if (slots != null && !slots.isEmpty()) {
                try (PreparedStatement insert = connection.prepareStatement(
                        """
                                INSERT INTO town_item_vault_slots(town_id, slot_idx, item_base64, updated_at)
                                VALUES (?, ?, ?, ?)
                                """)) {
                    for (Map.Entry<Integer, String> entry : slots.entrySet()) {
                        insert.setInt(1, townId);
                        insert.setInt(2, entry.getKey());
                        insert.setString(3, entry.getValue());
                        insert.setString(4, now.toString());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile salvare vault slots town=" + townId, exception);
        }
    }

    public synchronized Map<UUID, VaultAclEntry> loadVaultAcl(int townId) {
        Map<UUID, VaultAclEntry> result = new HashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT player_uuid, can_deposit, can_withdraw, updated_at
                             FROM town_vault_member_acl
                             WHERE town_id = ?
                             """)) {
            statement.setInt(1, townId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UUID playerId = parseUuidNullable(rs.getString("player_uuid"));
                    if (playerId == null) {
                        continue;
                    }
                    result.put(playerId, new VaultAclEntry(
                            playerId,
                            rs.getInt("can_deposit") == 1,
                            rs.getInt("can_withdraw") == 1,
                            parseInstant(rs.getString("updated_at"))
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare vault ACL town=" + townId, exception);
        }
        return result;
    }

    public synchronized void upsertVaultAcl(int townId, VaultAclEntry entry) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO town_vault_member_acl(town_id, player_uuid, can_deposit, can_withdraw, updated_at)
                             VALUES (?, ?, ?, ?, ?)
                             ON CONFLICT(town_id, player_uuid) DO UPDATE SET
                                 can_deposit = excluded.can_deposit,
                                 can_withdraw = excluded.can_withdraw,
                                 updated_at = excluded.updated_at
                             """)) {
            statement.setInt(1, townId);
            statement.setString(2, entry.playerId().toString());
            statement.setInt(3, entry.canDeposit() ? 1 : 0);
            statement.setInt(4, entry.canWithdraw() ? 1 : 0);
            statement.setString(5, entry.updatedAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile salvare ACL vault town=" + townId, exception);
        }
    }

    public synchronized void appendVaultAudit(
            int townId,
            UUID actorId,
            String action,
            UUID targetId,
            String itemBase64,
            int amount,
            String note
    ) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO town_vault_audit(town_id, actor_uuid, action, target_uuid, item_base64, amount, note, created_at)
                             VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                             """)) {
            statement.setInt(1, townId);
            statement.setString(2, actorId == null ? null : actorId.toString());
            statement.setString(3, action);
            statement.setString(4, targetId == null ? null : targetId.toString());
            statement.setString(5, itemBase64);
            statement.setInt(6, amount);
            statement.setString(7, note);
            statement.setString(8, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile append vault audit town=" + townId, exception);
        }
    }

    public synchronized List<PendingRewardEntry> loadPendingRewardEntries(int townId) {
        java.util.ArrayList<PendingRewardEntry> result = new java.util.ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT entry_id, town_id, item_base64, note, created_at
                             FROM town_pending_reward_claims
                             WHERE town_id = ?
                             ORDER BY created_at ASC, entry_id ASC
                             """)) {
            statement.setInt(1, townId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new PendingRewardEntry(
                            rs.getLong("entry_id"),
                            rs.getInt("town_id"),
                            rs.getString("item_base64"),
                            rs.getString("note"),
                            parseInstant(rs.getString("created_at"))
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare reward pending town=" + townId, exception);
        }
        return result;
    }

    public synchronized void appendPendingRewardEntries(int townId, List<String> encodedItems, String note, Instant createdAt) {
        if (townId <= 0 || encodedItems == null || encodedItems.isEmpty()) {
            return;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO town_pending_reward_claims(town_id, item_base64, note, created_at)
                             VALUES (?, ?, ?, ?)
                             """)) {
            for (String encoded : encodedItems) {
                if (encoded == null || encoded.isBlank()) {
                    continue;
                }
                statement.setInt(1, townId);
                statement.setString(2, encoded);
                statement.setString(3, note);
                statement.setString(4, createdAt.toString());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile salvare reward pending town=" + townId, exception);
        }
    }

    public synchronized int deletePendingRewardEntries(int townId, List<Long> entryIds) {
        if (townId <= 0 || entryIds == null || entryIds.isEmpty()) {
            return 0;
        }
        int removed = 0;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             DELETE FROM town_pending_reward_claims
                             WHERE town_id = ?
                               AND entry_id = ?
                             """)) {
            for (Long entryId : entryIds) {
                if (entryId == null || entryId.longValue() <= 0L) {
                    continue;
                }
                statement.setInt(1, townId);
                statement.setLong(2, entryId.longValue());
                removed += statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile rimuovere reward pending town=" + townId, exception);
        }
        return removed;
    }

    public synchronized Map<String, SuspicionScoreStore.SuspicionState> loadSuspicionScores() {
        Map<String, SuspicionScoreStore.SuspicionState> result = new HashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT town_id, player_uuid, cycle_key, score, review_flagged, updated_at
                             FROM town_challenge_suspicion
                             """);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                int townId = rs.getInt("town_id");
                UUID playerId = parseUuidNullable(rs.getString("player_uuid"));
                String cycleKey = rs.getString("cycle_key");
                if (townId <= 0 || playerId == null || cycleKey == null || cycleKey.isBlank()) {
                    continue;
                }
                SuspicionScoreStore.SuspicionState state = new SuspicionScoreStore.SuspicionState(
                        townId,
                        playerId,
                        cycleKey,
                        Math.max(0, rs.getInt("score")),
                        rs.getInt("review_flagged") == 1,
                        parseInstant(rs.getString("updated_at"))
                );
                result.put(townId + "|" + playerId + "|" + cycleKey, state);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare suspicion scores", exception);
        }
        return result;
    }

    public synchronized void upsertSuspicionScore(
            int townId,
            UUID playerId,
            String cycleKey,
            int score,
            boolean reviewFlagged,
            Instant updatedAt
    ) {
        if (townId <= 0 || playerId == null || cycleKey == null || cycleKey.isBlank()) {
            return;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO town_challenge_suspicion(town_id, player_uuid, cycle_key, score, review_flagged, updated_at)
                             VALUES (?, ?, ?, ?, ?, ?)
                             ON CONFLICT(town_id, player_uuid, cycle_key) DO UPDATE SET
                                 score = excluded.score,
                                 review_flagged = excluded.review_flagged,
                                 updated_at = excluded.updated_at
                             """)) {
            statement.setInt(1, townId);
            statement.setString(2, playerId.toString());
            statement.setString(3, cycleKey.trim());
            statement.setInt(4, Math.max(0, score));
            statement.setInt(5, reviewFlagged ? 1 : 0);
            statement.setString(6, (updatedAt == null ? Instant.now() : updatedAt).toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile upsert suspicion score town=" + townId, exception);
        }
    }

    public synchronized boolean tryInsertObjectiveDedupe(
            String dedupeKey,
            String instanceId,
            int townId,
            UUID playerId,
            String objectiveType,
            Instant createdAt
    ) {
        if (dedupeKey == null || dedupeKey.isBlank()
                || instanceId == null || instanceId.isBlank()
                || townId <= 0
                || objectiveType == null || objectiveType.isBlank()) {
            return false;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT OR IGNORE INTO challenge_objective_dedupe(
                                 dedupe_key, instance_id, town_id, player_uuid, objective_type, created_at
                             )
                             VALUES (?, ?, ?, ?, ?, ?)
                             """)) {
            statement.setString(1, dedupeKey.trim().toLowerCase(java.util.Locale.ROOT));
            statement.setString(2, instanceId.trim());
            statement.setInt(3, townId);
            statement.setString(4, playerId == null ? null : playerId.toString());
            statement.setString(5, objectiveType.trim());
            statement.setString(6, (createdAt == null ? Instant.now() : createdAt).toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile inserire dedupe objective key=" + dedupeKey, exception);
        }
    }

    public synchronized Optional<Integer> findWinnerTownId(String instanceId) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT town_id FROM challenge_winners WHERE instance_id = ?")) {
            statement.setString(1, instanceId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getInt("town_id"));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile leggere winner instance=" + instanceId, exception);
        }
        return Optional.empty();
    }

    public synchronized Map<Integer, Map<String, Long>> loadAtlasProgressByTown() {
        Map<Integer, Map<String, Long>> result = new HashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT town_id, family_id, progress
                             FROM atlas_family_progress
                             """);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                int townId = rs.getInt("town_id");
                String familyId = rs.getString("family_id");
                long progress = Math.max(0L, rs.getLong("progress"));
                if (townId <= 0 || familyId == null || familyId.isBlank()) {
                    continue;
                }
                result.computeIfAbsent(townId, ignored -> new HashMap<>())
                        .put(familyId, progress);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare progress Atlas", exception);
        }
        return result;
    }

    public synchronized void upsertAtlasFamilyProgress(int townId, String familyId, long progress, Instant updatedAt) {
        if (townId <= 0 || familyId == null || familyId.isBlank()) {
            return;
        }
        Instant now = updatedAt == null ? Instant.now() : updatedAt;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO atlas_family_progress(town_id, family_id, progress, updated_at)
                             VALUES (?, ?, ?, ?)
                             ON CONFLICT(town_id, family_id) DO UPDATE SET
                                 progress = excluded.progress,
                                 updated_at = excluded.updated_at
                             """)) {
            statement.setInt(1, townId);
            statement.setString(2, familyId);
            statement.setLong(3, Math.max(0L, progress));
            statement.setString(4, now.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile upsert progress Atlas town=" + townId + ", family=" + familyId, exception);
        }
    }

    public synchronized Set<String> loadAtlasRewardGrantKeys() {
        Set<String> result = new java.util.HashSet<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT grant_key FROM atlas_reward_ledger");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String key = rs.getString("grant_key");
                if (key != null && !key.isBlank()) {
                    result.add(key);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare atlas reward ledger", exception);
        }
        return Set.copyOf(result);
    }

    public synchronized void appendAtlasActivity(
            int townId,
            AtlasChapter chapter,
            String familyId,
            long deltaProgress,
            Instant createdAt
    ) {
        if (townId <= 0 || chapter == null || familyId == null || familyId.isBlank() || deltaProgress <= 0L) {
            return;
        }
        Instant now = createdAt == null ? Instant.now() : createdAt;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO atlas_activity_ledger(town_id, chapter, family_id, delta_progress, created_at)
                             VALUES (?, ?, ?, ?, ?)
                             """)) {
            statement.setInt(1, townId);
            statement.setString(2, chapter.name());
            statement.setString(3, familyId);
            statement.setLong(4, Math.max(1L, deltaProgress));
            statement.setString(5, now.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile append activity Atlas town=" + townId, exception);
        }
    }

    public synchronized Optional<AtlasChapter> loadMostActiveAtlasChapterSince(int townId, Instant since) {
        if (townId <= 0) {
            return Optional.empty();
        }
        Instant minInstant = since == null ? Instant.EPOCH : since;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT chapter, SUM(delta_progress) AS total_delta
                             FROM atlas_activity_ledger
                             WHERE town_id = ? AND created_at >= ?
                             GROUP BY chapter
                             ORDER BY total_delta DESC, chapter ASC
                             LIMIT 1
                             """)) {
            statement.setInt(1, townId);
            statement.setString(2, minInstant.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String raw = rs.getString("chapter");
                if (raw == null || raw.isBlank()) {
                    return Optional.empty();
                }
                try {
                    return Optional.of(AtlasChapter.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    return Optional.empty();
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile leggere chapter Atlas più attivo town=" + townId, exception);
        }
    }

    public synchronized boolean markAtlasRewardGrant(
            String grantKey,
            int townId,
            String familyId,
            AtlasTier tier,
            ChallengeRewardType rewardType,
            String payload,
            Instant grantedAt
    ) {
        if (grantKey == null || grantKey.isBlank() || townId <= 0 || familyId == null || familyId.isBlank()
                || tier == null || rewardType == null) {
            return false;
        }
        Instant now = grantedAt == null ? Instant.now() : grantedAt;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT OR IGNORE INTO atlas_reward_ledger(
                                 grant_key, town_id, family_id, tier, reward_type, payload, granted_at
                             )
                             VALUES (?, ?, ?, ?, ?, ?, ?)
                             """)) {
            statement.setString(1, grantKey);
            statement.setInt(2, townId);
            statement.setString(3, familyId);
            statement.setString(4, tier.name());
            statement.setString(5, rewardType.name());
            statement.setString(6, payload);
            statement.setString(7, now.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile registrare grant atlas key=" + grantKey, exception);
        }
    }

    public synchronized Optional<String> getRuntimeState(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT state_value FROM challenge_runtime_state WHERE state_key = ?")) {
            statement.setString(1, key);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("state_value"));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile leggere runtime state key=" + key, exception);
        }
        return Optional.empty();
    }

    public synchronized void setRuntimeState(String key, String value) {
        if (key == null || key.isBlank() || value == null) {
            return;
        }
        Instant now = Instant.now();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO challenge_runtime_state(state_key, state_value, updated_at)
                             VALUES (?, ?, ?)
                             ON CONFLICT(state_key) DO UPDATE SET
                                 state_value = excluded.state_value,
                                 updated_at = excluded.updated_at
                             """)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.setString(3, now.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile salvare runtime state key=" + key, exception);
        }
    }

    public synchronized void clearAtlasState() {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM atlas_family_progress");
            statement.executeUpdate("DELETE FROM atlas_reward_ledger");
            statement.executeUpdate("DELETE FROM atlas_activity_ledger");
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile resettare stato Atlas", exception);
        }
    }

    public synchronized Map<String, Instant> loadDefenseCooldowns() {
        Map<String, Instant> result = new HashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT town_id, tier, available_at FROM city_defense_cooldown");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                int townId = rs.getInt("town_id");
                String tier = rs.getString("tier");
                if (townId <= 0 || tier == null || tier.isBlank()) {
                    continue;
                }
                Instant availableAt = parseInstantNullable(rs.getString("available_at"));
                if (availableAt == null) {
                    continue;
                }
                result.put(townId + "|" + tier.trim().toUpperCase(java.util.Locale.ROOT), availableAt);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare cooldown difesa", exception);
        }
        return result;
    }

    public synchronized void upsertDefenseCooldown(int townId, String tier, Instant availableAt, Instant updatedAt) {
        if (townId <= 0 || tier == null || tier.isBlank() || availableAt == null) {
            return;
        }
        Instant now = updatedAt == null ? Instant.now() : updatedAt;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO city_defense_cooldown(town_id, tier, available_at, updated_at)
                             VALUES (?, ?, ?, ?)
                             ON CONFLICT(town_id, tier) DO UPDATE SET
                                 available_at = excluded.available_at,
                                 updated_at = excluded.updated_at
                             """)) {
            statement.setInt(1, townId);
            statement.setString(2, tier.trim().toUpperCase(java.util.Locale.ROOT));
            statement.setString(3, availableAt.toString());
            statement.setString(4, now.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile salvare cooldown difesa town=" + townId, exception);
        }
    }

    public synchronized void appendDefenseSessionStart(
            String sessionId,
            int townId,
            String townName,
            String tier,
            Instant startedAt
    ) {
        if (sessionId == null || sessionId.isBlank() || townId <= 0 || startedAt == null) {
            return;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO city_defense_sessions(
                                 session_id, town_id, town_name, tier, status,
                                 started_at, ended_at, end_reason, reward_money_amount, reward_xp_scaled, reward_items_payload
                             )
                             VALUES (?, ?, ?, ?, 'ACTIVE', ?, NULL, NULL, 0, 0, NULL)
                             ON CONFLICT(session_id) DO UPDATE SET
                                 town_id = excluded.town_id,
                                 town_name = excluded.town_name,
                                 tier = excluded.tier,
                                 status = 'ACTIVE',
                                 started_at = excluded.started_at,
                                 ended_at = NULL,
                                 end_reason = NULL,
                                 reward_money_amount = 0,
                                 reward_xp_scaled = 0,
                                 reward_items_payload = NULL
                             """)) {
            statement.setString(1, sessionId);
            statement.setInt(2, townId);
            statement.setString(3, townName == null ? "#" + townId : townName);
            statement.setString(4, tier == null ? "L1" : tier.trim().toUpperCase(java.util.Locale.ROOT));
            statement.setString(5, startedAt.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile registrare start difesa session=" + sessionId, exception);
        }
    }

    public synchronized long abortActiveDefenseSessionsOnStartup(Instant endedAt, String reason) {
        Instant now = endedAt == null ? Instant.now() : endedAt;
        String finalReason = reason == null || reason.isBlank() ? "aborted_on_restart" : reason;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             UPDATE city_defense_sessions
                             SET status = 'ABORTED',
                                 ended_at = ?,
                                 end_reason = ?,
                                 reward_money_amount = 0,
                                 reward_xp_scaled = 0,
                                 reward_items_payload = NULL
                             WHERE status = 'ACTIVE'
                             """)) {
            statement.setString(1, now.toString());
            statement.setString(2, finalReason);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile abort sessioni difesa attive", exception);
        }
    }

    public synchronized void completeDefenseSession(
            String sessionId,
            String status,
            String reason,
            Instant endedAt,
            double rewardMoneyAmount,
            long rewardXpScaled,
            String rewardItemsPayload
    ) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        String finalStatus = status == null || status.isBlank() ? "DEFEAT" : status.trim().toUpperCase(java.util.Locale.ROOT);
        Instant now = endedAt == null ? Instant.now() : endedAt;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             UPDATE city_defense_sessions
                             SET status = ?,
                                 ended_at = ?,
                                 end_reason = ?,
                                 reward_money_amount = ?,
                                 reward_xp_scaled = ?,
                                 reward_items_payload = ?
                             WHERE session_id = ?
                             """)) {
            statement.setString(1, finalStatus);
            statement.setString(2, now.toString());
            statement.setString(3, reason);
            statement.setDouble(4, Math.max(0.0D, rewardMoneyAmount));
            statement.setLong(5, Math.max(0L, rewardXpScaled));
            statement.setString(6, rewardItemsPayload);
            statement.setString(7, sessionId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile completare sessione difesa session=" + sessionId, exception);
        }
    }

    public synchronized boolean hasSuccessfulDefenseCompletion(int townId, String tier) {
        if (townId <= 0 || tier == null || tier.isBlank()) {
            return false;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT 1
                             FROM city_defense_sessions
                             WHERE town_id = ?
                               AND tier = ?
                               AND status = 'VICTORY'
                             LIMIT 1
                             """)) {
            statement.setInt(1, townId);
            statement.setString(2, tier.trim().toUpperCase(java.util.Locale.ROOT));
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile verificare completamento difesa town=" + townId + " tier=" + tier, exception);
        }
    }

    public synchronized boolean markDefenseRewardGrant(
            String grantKey,
            int townId,
            String sessionId,
            ChallengeRewardType rewardType,
            String payload,
            Instant grantedAt
    ) {
        if (grantKey == null || grantKey.isBlank() || townId <= 0 || sessionId == null || sessionId.isBlank()) {
            return false;
        }
        Instant now = grantedAt == null ? Instant.now() : grantedAt;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT OR IGNORE INTO city_defense_reward_ledger(
                                 grant_key, town_id, session_id, reward_type, payload, granted_at
                             )
                             VALUES (?, ?, ?, ?, ?, ?)
                             """)) {
            statement.setString(1, grantKey);
            statement.setInt(2, townId);
            statement.setString(3, sessionId);
            statement.setString(4, rewardType == null ? "UNKNOWN" : rewardType.name());
            statement.setString(5, payload);
            statement.setString(6, now.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile marcare reward difesa key=" + grantKey, exception);
        }
    }

    public synchronized Set<String> loadSuccessfulDefenseTiers(int townId) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        if (townId <= 0) {
            return result;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT DISTINCT tier
                             FROM city_defense_sessions
                             WHERE town_id = ?
                               AND status = 'VICTORY'
                             ORDER BY tier ASC
                             """)) {
            statement.setInt(1, townId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String tier = rs.getString("tier");
                    if (tier != null && !tier.isBlank()) {
                        result.add(tier.trim().toUpperCase(java.util.Locale.ROOT));
                    }
                }
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare tier difesa vinte town=" + townId, exception);
        }
    }

    public synchronized void appendOutbox(String opId, String opType, String payload, Instant createdAt) {
        if (opId == null || opId.isBlank() || opType == null || opType.isBlank()) {
            return;
        }
        Instant now = createdAt == null ? Instant.now() : createdAt;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT OR IGNORE INTO challenge_sync_outbox(
                                 op_id, op_type, payload, status, attempts, next_attempt_at, last_error, created_at, updated_at
                             )
                             VALUES (?, ?, ?, 'PENDING', 0, ?, NULL, ?, ?)
                             """)) {
            statement.setString(1, opId);
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

    public synchronized void markOutboxDone(String opId) {
        if (opId == null || opId.isBlank()) {
            return;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             UPDATE challenge_sync_outbox
                             SET status = 'DONE', updated_at = ?
                             WHERE op_id = ?
                             """)) {
            statement.setString(1, Instant.now().toString());
            statement.setString(2, opId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile mark outbox done op=" + opId, exception);
        }
    }

    public synchronized void markOutboxFailed(String opId, String error, int backoffSeconds) {
        if (opId == null || opId.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        Instant next = now.plusSeconds(Math.max(1, backoffSeconds));
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             UPDATE challenge_sync_outbox
                             SET status = 'FAILED',
                                 attempts = attempts + 1,
                                 next_attempt_at = ?,
                                 last_error = ?,
                                 updated_at = ?
                             WHERE op_id = ?
                             """)) {
            statement.setString(1, next.toString());
            statement.setString(2, error == null ? "-" : error);
            statement.setString(3, now.toString());
            statement.setString(4, opId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile mark outbox failed op=" + opId, exception);
        }
    }

    public synchronized List<OutboxItem> listRetryableOutbox(int limit) {
        List<OutboxItem> result = new java.util.ArrayList<>();
        int safeLimit = Math.max(1, limit);
        Instant now = Instant.now();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT op_id, op_type, payload, attempts, created_at, next_attempt_at
                             FROM challenge_sync_outbox
                             WHERE status = 'PENDING'
                                OR (status = 'FAILED' AND (next_attempt_at IS NULL OR next_attempt_at <= ?))
                             ORDER BY created_at ASC
                             LIMIT ?
                             """)) {
            statement.setString(1, now.toString());
            statement.setInt(2, safeLimit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new OutboxItem(
                            rs.getString("op_id"),
                            rs.getString("op_type"),
                            rs.getString("payload"),
                            Math.max(0, rs.getInt("attempts")),
                            parseInstant(rs.getString("created_at")),
                            parseInstantNullable(rs.getString("next_attempt_at"))
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile listare outbox retryable", exception);
        }
        return List.copyOf(result);
    }

    public synchronized int countOutboxPending() {
        return countOutboxByStatus("PENDING");
    }

    public synchronized int countOutboxFailed() {
        return countOutboxByStatus("FAILED");
    }

    public synchronized Optional<Instant> oldestOutboxPendingCreatedAt() {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT created_at
                             FROM challenge_sync_outbox
                             WHERE status = 'PENDING' OR status = 'FAILED'
                             ORDER BY created_at ASC
                             LIMIT 1
                             """);
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                return Optional.ofNullable(parseInstantNullable(rs.getString("created_at")));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile leggere oldest outbox item", exception);
        }
        return Optional.empty();
    }

    public synchronized void appendCycleRecap(CycleRecapSnapshot recap) {
        if (recap == null || recap.townId() <= 0 || recap.cycleType() == null || recap.cycleKey() == null || recap.cycleKey().isBlank()) {
            return;
        }
        Instant createdAt = recap.createdAt() == null ? Instant.now() : recap.createdAt();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             INSERT INTO town_cycle_recap(
                                 town_id, cycle_type, cycle_key,
                                 completed_count, total_count,
                                 xp_scaled, excellence_xp_scaled,
                                 streak_delta, leaderboard_position, rewards_granted,
                                 top_contributor_uuid, top_contribution, created_at
                             )
                             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                             ON CONFLICT(town_id, cycle_type, cycle_key) DO UPDATE SET
                                 completed_count = excluded.completed_count,
                                 total_count = excluded.total_count,
                                 xp_scaled = excluded.xp_scaled,
                                 excellence_xp_scaled = excluded.excellence_xp_scaled,
                                 streak_delta = excluded.streak_delta,
                                 leaderboard_position = excluded.leaderboard_position,
                                 rewards_granted = excluded.rewards_granted,
                                 top_contributor_uuid = excluded.top_contributor_uuid,
                                 top_contribution = excluded.top_contribution,
                                 created_at = excluded.created_at
                             """)) {
            statement.setInt(1, recap.townId());
            statement.setString(2, recap.cycleType().name());
            statement.setString(3, recap.cycleKey());
            statement.setInt(4, Math.max(0, recap.completedChallenges()));
            statement.setInt(5, Math.max(0, recap.totalChallenges()));
            statement.setLong(6, Math.max(0L, recap.xpScaled()));
            statement.setLong(7, Math.max(0L, recap.excellenceXpScaled()));
            statement.setInt(8, recap.streakDelta());
            statement.setInt(9, Math.max(0, recap.leaderboardPosition()));
            statement.setInt(10, Math.max(0, recap.rewardsGranted()));
            statement.setString(11, recap.topContributorId() == null ? null : recap.topContributorId().toString());
            statement.setInt(12, Math.max(0, recap.topContribution()));
            statement.setString(13, createdAt.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile append cycle recap town=" + recap.townId(), exception);
        }
    }

    public synchronized Optional<CycleRecapSnapshot> loadLatestRecap(int townId) {
        if (townId <= 0) {
            return Optional.empty();
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT recap_id, town_id, cycle_type, cycle_key, completed_count, total_count,
                                    xp_scaled, excellence_xp_scaled, streak_delta, leaderboard_position,
                                    rewards_granted, top_contributor_uuid, top_contribution, created_at
                             FROM town_cycle_recap
                             WHERE town_id = ?
                             ORDER BY recap_id DESC
                             LIMIT 1
                             """)) {
            statement.setInt(1, townId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(readRecap(rs));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare latest recap town=" + townId, exception);
        }
        return Optional.empty();
    }

    public synchronized List<CycleRecapSnapshot> loadRecapHistory(int townId, int limit, int offset) {
        if (townId <= 0) {
            return List.of();
        }
        List<CycleRecapSnapshot> result = new java.util.ArrayList<>();
        int safeLimit = Math.max(1, limit);
        int safeOffset = Math.max(0, offset);
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT recap_id, town_id, cycle_type, cycle_key, completed_count, total_count,
                                    xp_scaled, excellence_xp_scaled, streak_delta, leaderboard_position,
                                    rewards_granted, top_contributor_uuid, top_contribution, created_at
                             FROM town_cycle_recap
                             WHERE town_id = ?
                             ORDER BY recap_id DESC
                             LIMIT ? OFFSET ?
                             """)) {
            statement.setInt(1, townId);
            statement.setInt(2, safeLimit);
            statement.setInt(3, safeOffset);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(readRecap(rs));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare history recap town=" + townId, exception);
        }
        return List.copyOf(result);
    }

    public synchronized List<CycleRecapSnapshot> loadAllRecaps(int limit) {
        List<CycleRecapSnapshot> result = new java.util.ArrayList<>();
        int safeLimit = Math.max(1, limit);
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             SELECT recap_id, town_id, cycle_type, cycle_key, completed_count, total_count,
                                    xp_scaled, excellence_xp_scaled, streak_delta, leaderboard_position,
                                    rewards_granted, top_contributor_uuid, top_contribution, created_at
                             FROM town_cycle_recap
                             ORDER BY recap_id DESC
                             LIMIT ?
                             """)) {
            statement.setInt(1, safeLimit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(readRecap(rs));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile caricare recap globali", exception);
        }
        return List.copyOf(result);
    }

    public synchronized void pruneRecapHistory(int townId, int retainCycles) {
        if (townId <= 0 || retainCycles <= 0) {
            return;
        }
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     """
                             DELETE FROM town_cycle_recap
                             WHERE town_id = ?
                               AND recap_id NOT IN (
                                   SELECT recap_id
                                   FROM town_cycle_recap
                                   WHERE town_id = ?
                                   ORDER BY recap_id DESC
                                   LIMIT ?
                               )
                             """)) {
            statement.setInt(1, townId);
            statement.setInt(2, townId);
            statement.setInt(3, retainCycles);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile fare prune recap town=" + townId, exception);
        }
    }

    private int countOutboxByStatus(String status) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) AS c FROM challenge_sync_outbox WHERE status = ?")) {
            statement.setString(1, status);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Math.max(0, rs.getInt("c"));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Impossibile contare outbox status=" + status, exception);
        }
        return 0;
    }

    private static CycleRecapSnapshot readRecap(ResultSet rs) throws SQLException {
        ChallengeCycleType cycleType;
        try {
            cycleType = ChallengeCycleType.valueOf(rs.getString("cycle_type"));
        } catch (IllegalArgumentException exception) {
            cycleType = ChallengeCycleType.DAILY;
        }
        return new CycleRecapSnapshot(
                rs.getLong("recap_id"),
                rs.getInt("town_id"),
                cycleType,
                rs.getString("cycle_key"),
                Math.max(0, rs.getInt("completed_count")),
                Math.max(0, rs.getInt("total_count")),
                Math.max(0L, rs.getLong("xp_scaled")),
                Math.max(0L, rs.getLong("excellence_xp_scaled")),
                rs.getInt("streak_delta"),
                Math.max(0, rs.getInt("leaderboard_position")),
                Math.max(0, rs.getInt("rewards_granted")),
                parseUuidNullable(rs.getString("top_contributor_uuid")),
                Math.max(0, rs.getInt("top_contribution")),
                parseInstant(rs.getString("created_at"))
        );
    }

    private static void ensureColumn(Connection connection, String table, String column, String ddl) throws SQLException {
        if (hasColumn(connection, table, column)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + ddl);
        }
    }

    private static boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static UUID parseUuidNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Integer parseIntNullable(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        if (rs.wasNull()) {
            return null;
        }
        return value;
    }

    private static String toCycles(Set<ChallengeMode> cycles) {
        return cycles.stream().map(Enum::name).sorted().reduce((a, b) -> a + "," + b).orElse("");
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.now();
        }
        return Instant.parse(raw);
    }

    private static Instant parseInstantNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Instant.parse(raw);
    }
}
