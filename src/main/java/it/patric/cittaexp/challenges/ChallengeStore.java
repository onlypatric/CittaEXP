package it.patric.cittaexp.challenges;

import it.patric.cittaexp.vault.VaultAclEntry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.plugin.Plugin;

public final class ChallengeStore {

    public record Diagnostics(
            String mode,
            boolean mysqlUp,
            int outboxPending,
            int outboxFailed,
            String lastSyncOk,
            String lastSyncError,
            String lastSyncErrorAt,
            long replayLagSeconds
    ) {
    }

    private final Plugin plugin;
    private final CityChallengeRepository sqlite;
    private final ChallengeBackend mysql;
    private final ChallengeBackend sqliteBackend;
    private final ChallengeStoreStateMachine stateMachine;
    private final int flushSeconds;
    private final int flushBatchSize;
    private final int replayBackoffSeconds;
    private final int shutdownDrainSeconds;
    private final ScheduledExecutorService replayScheduler;
    private final AtomicInteger outboxPending;
    private final AtomicInteger outboxFailed;
    private volatile boolean mysqlUp;
    private volatile Instant lastSyncOk;
    private volatile String lastSyncError;
    private volatile Instant lastSyncErrorAt;

    public ChallengeStore(
            Plugin plugin,
            CityChallengeRepository sqlite,
            MySqlChallengeBackend mysql,
            int flushSeconds,
            int flushBatchSize,
            int replayBackoffSeconds,
            int shutdownDrainSeconds
    ) {
        this.plugin = plugin;
        this.sqlite = sqlite;
        this.mysql = mysql;
        this.sqliteBackend = new SqliteChallengeBackend(sqlite);
        this.stateMachine = new ChallengeStoreStateMachine();
        this.flushSeconds = Math.max(1, flushSeconds);
        this.flushBatchSize = Math.max(1, flushBatchSize);
        this.replayBackoffSeconds = Math.max(1, replayBackoffSeconds);
        this.shutdownDrainSeconds = Math.max(1, shutdownDrainSeconds);
        this.replayScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "cittaexp-challenges-store-replay");
            thread.setDaemon(true);
            return thread;
        });
        this.outboxPending = new AtomicInteger(0);
        this.outboxFailed = new AtomicInteger(0);
        this.mysqlUp = false;
        this.lastSyncOk = null;
        this.lastSyncError = "-";
        this.lastSyncErrorAt = null;
        if (mysql.enabled()) {
            this.stateMachine.toRecovery();
        } else {
            this.stateMachine.toSqliteOnly();
        }
    }

    public void start() {
        try {
            sqliteBackend.initialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Impossibile inizializzare backend SQLite challenge", exception);
        }
        refreshOutboxCounters();
        if (mysql.enabled()) {
            try {
                mysql.initialize();
                mysqlUp = true;
                stateMachine.toNormal();
                lastSyncError = "-";
                lastSyncErrorAt = null;
            } catch (Exception exception) {
                mysqlUp = false;
                stateMachine.toDegraded();
                lastSyncError = normalizeError(exception);
                lastSyncErrorAt = Instant.now();
                plugin.getLogger().warning("[challenges-store] mysql init failed, fallback sqlite reason=" + lastSyncError);
            }
        } else {
            stateMachine.toSqliteOnly();
        }
        replayScheduler.scheduleAtFixedRate(this::replaySafe, flushSeconds, flushSeconds, TimeUnit.SECONDS);
    }

    public void stop() {
        replayScheduler.shutdown();
        try {
            replayScheduler.awaitTermination(shutdownDrainSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        replaySafe();
    }

    public Diagnostics diagnostics() {
        long replayLag = sqlite.oldestOutboxPendingCreatedAt()
                .map(ts -> Math.max(0L, Instant.now().getEpochSecond() - ts.getEpochSecond()))
                .orElse(0L);
        return new Diagnostics(
                stateMachine.modeLabel(),
                mysqlUp,
                outboxPending.get(),
                outboxFailed.get(),
                lastSyncOk == null ? "-" : lastSyncOk.toString(),
                lastSyncError == null ? "-" : lastSyncError,
                lastSyncErrorAt == null ? "-" : lastSyncErrorAt.toString(),
                replayLag
        );
    }

    private void replaySafe() {
        try {
            replay();
        } catch (Exception exception) {
            lastSyncError = normalizeError(exception);
            lastSyncErrorAt = Instant.now();
            if (mysql.enabled()) {
                stateMachine.toDegraded();
            }
        }
    }

    private void replay() {
        refreshOutboxCounters();
        if (!mysql.enabled()) {
            stateMachine.toSqliteOnly();
            return;
        }
        if (!mysqlUp) {
            stateMachine.toRecovery();
            try {
                mysql.initialize();
                mysqlUp = true;
                stateMachine.toNormal();
            } catch (Exception exception) {
                mysqlUp = false;
                stateMachine.toDegraded();
                lastSyncError = normalizeError(exception);
                lastSyncErrorAt = Instant.now();
                refreshOutboxCounters();
                return;
            }
        }

        List<CityChallengeRepository.OutboxItem> retryable = sqlite.listRetryableOutbox(flushBatchSize);
        if (retryable.isEmpty()) {
            lastSyncOk = Instant.now();
            refreshOutboxCounters();
            return;
        }

        for (CityChallengeRepository.OutboxItem item : retryable) {
            try {
                mysql.appendSyncLog(item.opId(), item.opType(), item.payload(), item.createdAt());
                sqlite.markOutboxDone(item.opId());
                lastSyncOk = Instant.now();
                lastSyncError = "-";
                lastSyncErrorAt = null;
                stateMachine.toNormal();
                mysqlUp = true;
            } catch (Exception exception) {
                mysqlUp = false;
                stateMachine.toDegraded();
                lastSyncError = normalizeError(exception);
                lastSyncErrorAt = Instant.now();
                sqlite.markOutboxFailed(item.opId(), lastSyncError, replayBackoffSeconds);
                break;
            }
        }
        refreshOutboxCounters();
    }

    private void refreshOutboxCounters() {
        outboxPending.set(sqlite.countOutboxPending());
        outboxFailed.set(sqlite.countOutboxFailed());
    }

    private void recordMutation(String opType, String payload) {
        String opId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        sqlite.appendOutbox(opId, opType, payload, now);
        if (mysql.enabled() && mysqlUp) {
            try {
                mysql.appendSyncLog(opId, opType, payload, now);
                sqlite.markOutboxDone(opId);
                lastSyncOk = now;
                lastSyncError = "-";
                lastSyncErrorAt = null;
                stateMachine.toNormal();
            } catch (Exception exception) {
                mysqlUp = false;
                stateMachine.toDegraded();
                lastSyncError = normalizeError(exception);
                lastSyncErrorAt = now;
                sqlite.markOutboxFailed(opId, lastSyncError, replayBackoffSeconds);
            }
        }
        refreshOutboxCounters();
    }

    private static String payload(String... kvPairs) {
        if (kvPairs == null || kvPairs.length == 0) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            String key = kvPairs[i] == null ? "-" : kvPairs[i];
            String value = kvPairs[i + 1] == null ? "-" : kvPairs[i + 1];
            builder.append(key).append('=').append(value.replace(';', '_'));
        }
        return builder.toString();
    }

    private static String normalizeError(Throwable throwable) {
        if (throwable == null) {
            return "-";
        }
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return message;
    }

    public void initialize() {
        try {
            sqliteBackend.initialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Impossibile inizializzare backend SQLite challenge", exception);
        }
    }

    public void replaceDefinitions(Map<String, ChallengeDefinition> definitions) {
        sqlite.replaceDefinitions(definitions);
        recordMutation("replace-definitions", payload("size", Integer.toString(definitions == null ? 0 : definitions.size())));
    }

    public Map<String, ChallengeInstance> loadInstances() {
        return sqlite.loadInstances();
    }

    public Map<String, Map<Integer, TownChallengeProgress>> loadProgressByInstance() {
        return sqlite.loadProgressByInstance();
    }

    public Set<String> loadRewardGrantKeys() {
        return sqlite.loadRewardGrantKeys();
    }

    public Map<String, Long> loadObjectiveCheckpoints() {
        return sqlite.loadObjectiveCheckpoints();
    }

    public Map<String, Instant> loadPlacedBlocks() {
        return sqlite.loadPlacedBlocks();
    }

    public Map<String, Integer> loadPlayerContributions() {
        return sqlite.loadPlayerContributions();
    }

    public Map<String, Integer> loadDailyCategoryCaps() {
        return sqlite.loadDailyCategoryCaps();
    }

    public Map<ChallengeCycleType, CityChallengeRepository.CycleState> loadCycleState() {
        return sqlite.loadCycleState();
    }

    public Map<Integer, CityChallengeStreakService.State> loadWeeklyStreakState() {
        return sqlite.loadWeeklyStreakState();
    }

    public Map<String, CityChallengeRepository.GovernanceState> loadGovernanceState() {
        return sqlite.loadGovernanceState();
    }

    public Map<String, CityChallengeRepository.MilestoneState> loadMilestoneState() {
        return sqlite.loadMilestoneState();
    }

    public List<CityChallengeRepository.SelectionHistoryEntry> loadSelectionHistory() {
        return sqlite.loadSelectionHistory();
    }

    public void upsertInstance(ChallengeInstance instance) {
        sqlite.upsertInstance(instance);
        recordMutation("upsert-instance", payload("instanceId", instance == null ? "-" : instance.instanceId()));
    }

    public void clearRuntimeStateForRegenerate() {
        sqlite.clearRuntimeStateForRegenerate();
        recordMutation("clear-runtime", "-");
    }

    public void clearAllChallengeStateForM11Cutover() {
        sqlite.clearAllChallengeStateForM11Cutover();
        recordMutation("m11-full-reset", "-");
    }

    public void upsertGovernanceState(CityChallengeRepository.GovernanceState state) {
        sqlite.upsertGovernanceState(state);
        recordMutation("upsert-governance", payload("townId", state == null ? "-" : Integer.toString(state.townId())));
    }

    public void upsertMilestoneState(CityChallengeRepository.MilestoneState state) {
        sqlite.upsertMilestoneState(state);
        recordMutation("upsert-milestone", payload("townId", state == null ? "-" : Integer.toString(state.townId())));
    }

    public boolean tryInsertWeeklyCompletion(int townId, String weeklyCycleKey, Instant completedAt) {
        boolean inserted = sqlite.tryInsertWeeklyCompletion(townId, weeklyCycleKey, completedAt);
        if (inserted) {
            recordMutation("weekly-completion", payload("townId", Integer.toString(townId), "cycle", weeklyCycleKey));
        }
        return inserted;
    }

    public void appendSelectionHistory(CityChallengeRepository.SelectionHistoryEntry entry) {
        sqlite.appendSelectionHistory(entry);
        recordMutation("selection-history", payload("challengeId", entry == null ? "-" : entry.challengeId()));
    }

    public void upsertProgress(TownChallengeProgress progress) {
        sqlite.upsertProgress(progress);
        recordMutation("upsert-progress", payload("instanceId", progress == null ? "-" : progress.instanceId()));
    }

    public boolean tryInsertWinner(String instanceId, int townId, UUID playerId, Instant wonAt) {
        boolean inserted = sqlite.tryInsertWinner(instanceId, townId, playerId, wonAt);
        if (inserted) {
            recordMutation("insert-winner", payload("instanceId", instanceId, "townId", Integer.toString(townId)));
        }
        return inserted;
    }

    public boolean markRewardGrant(
            String grantKey,
            int townId,
            String instanceId,
            ChallengeRewardType rewardType,
            String payload,
            Instant grantedAt
    ) {
        boolean marked = sqlite.markRewardGrant(grantKey, townId, instanceId, rewardType, payload, grantedAt);
        if (marked) {
            recordMutation("reward-grant", ChallengeStore.payload("grantKey", grantKey, "townId", Integer.toString(townId)));
        }
        return marked;
    }

    public boolean markPersonalRewardGrant(
            String grantKey,
            String instanceId,
            int townId,
            UUID playerId,
            String rewardBlock,
            ChallengeRewardType rewardType,
            String payload,
            Instant grantedAt
    ) {
        boolean marked = sqlite.markPersonalRewardGrant(
                grantKey,
                instanceId,
                townId,
                playerId,
                rewardBlock,
                rewardType,
                payload,
                grantedAt
        );
        if (marked) {
            recordMutation("personal-reward-grant", ChallengeStore.payload("grantKey", grantKey, "townId", Integer.toString(townId)));
        }
        return marked;
    }

    public int countActiveContributorsSince(int townId, Instant since) {
        return sqlite.countActiveContributorsSince(townId, since);
    }

    public void upsertObjectiveCheckpoint(UUID playerId, String objectiveKey, long value, Instant updatedAt) {
        sqlite.upsertObjectiveCheckpoint(playerId, objectiveKey, value, updatedAt);
        recordMutation("objective-checkpoint", payload("player", playerId == null ? "-" : playerId.toString(), "objective", objectiveKey));
    }

    public void upsertPlacedBlock(String blockKey, Instant placedAt) {
        sqlite.upsertPlacedBlock(blockKey, placedAt);
        recordMutation("placed-block-upsert", payload("block", blockKey));
    }

    public void deletePlacedBlock(String blockKey) {
        sqlite.deletePlacedBlock(blockKey);
        recordMutation("placed-block-delete", payload("block", blockKey));
    }

    public void deletePlacedBlocks(List<String> blockKeys) {
        sqlite.deletePlacedBlocks(blockKeys);
        recordMutation("placed-block-delete-batch", payload("count", Integer.toString(blockKeys == null ? 0 : blockKeys.size())));
    }

    public void upsertPlayerContribution(String instanceId, int townId, UUID playerId, int contribution, Instant updatedAt) {
        sqlite.upsertPlayerContribution(instanceId, townId, playerId, contribution, updatedAt);
        recordMutation("player-contribution", payload("instanceId", instanceId, "townId", Integer.toString(townId)));
    }

    public void upsertDailyCategoryCap(String dayKey, int townId, String categoryBucket, int consumed, Instant updatedAt) {
        sqlite.upsertDailyCategoryCap(dayKey, townId, categoryBucket, consumed, updatedAt);
        recordMutation("daily-category-cap", payload("dayKey", dayKey, "townId", Integer.toString(townId)));
    }

    public void upsertCycleState(ChallengeCycleType cycleType, String cycleKey, Instant generatedAt) {
        sqlite.upsertCycleState(cycleType, cycleKey, generatedAt);
        recordMutation("cycle-state", payload("cycleType", cycleType == null ? "-" : cycleType.name(), "cycleKey", cycleKey));
    }

    public void upsertWeeklyStreakState(CityChallengeStreakService.State state) {
        sqlite.upsertWeeklyStreakState(state);
        recordMutation("weekly-streak", payload("townId", state == null ? "-" : Integer.toString(state.townId())));
    }

    public Map<Integer, String> loadVaultSlots(int townId) {
        return sqlite.loadVaultSlots(townId);
    }

    public void replaceVaultSlots(int townId, Map<Integer, String> slots) {
        sqlite.replaceVaultSlots(townId, slots);
        recordMutation("vault-slots", payload("townId", Integer.toString(townId)));
    }

    public Map<UUID, VaultAclEntry> loadVaultAcl(int townId) {
        return sqlite.loadVaultAcl(townId);
    }

    public void upsertVaultAcl(int townId, VaultAclEntry entry) {
        sqlite.upsertVaultAcl(townId, entry);
        recordMutation("vault-acl", payload("townId", Integer.toString(townId), "player", entry == null ? "-" : entry.playerId().toString()));
    }

    public void appendVaultAudit(int townId, UUID actorId, String action, UUID targetId, String itemBase64, int amount, String note) {
        sqlite.appendVaultAudit(townId, actorId, action, targetId, itemBase64, amount, note);
        recordMutation("vault-audit", payload("townId", Integer.toString(townId), "action", action));
    }

    public List<CityChallengeRepository.PendingRewardEntry> loadPendingRewardEntries(int townId) {
        return sqlite.loadPendingRewardEntries(townId);
    }

    public void appendPendingRewardEntries(int townId, List<String> encodedItems, String note, Instant createdAt) {
        sqlite.appendPendingRewardEntries(townId, encodedItems, note, createdAt);
        recordMutation("pending-reward-append", payload(
                "townId", Integer.toString(townId),
                "count", Integer.toString(encodedItems == null ? 0 : encodedItems.size())
        ));
    }

    public int deletePendingRewardEntries(int townId, List<Long> entryIds) {
        int removed = sqlite.deletePendingRewardEntries(townId, entryIds);
        if (removed > 0) {
            recordMutation("pending-reward-delete", payload(
                    "townId", Integer.toString(townId),
                    "count", Integer.toString(removed)
            ));
        }
        return removed;
    }

    public Map<String, SuspicionScoreStore.SuspicionState> loadSuspicionScores() {
        return sqlite.loadSuspicionScores();
    }

    public void upsertSuspicionScore(int townId, UUID playerId, String cycleKey, int score, boolean reviewFlagged, Instant updatedAt) {
        sqlite.upsertSuspicionScore(townId, playerId, cycleKey, score, reviewFlagged, updatedAt);
        recordMutation("suspicion-score", payload("townId", Integer.toString(townId), "cycleKey", cycleKey));
    }

    public boolean tryInsertObjectiveDedupe(
            String dedupeKey,
            String instanceId,
            int townId,
            UUID playerId,
            String objectiveType,
            Instant createdAt
    ) {
        boolean inserted = sqlite.tryInsertObjectiveDedupe(dedupeKey, instanceId, townId, playerId, objectiveType, createdAt);
        if (inserted) {
            recordMutation("objective-dedupe", payload("instanceId", instanceId, "townId", Integer.toString(townId)));
        }
        return inserted;
    }

    public Optional<Integer> findWinnerTownId(String instanceId) {
        return sqlite.findWinnerTownId(instanceId);
    }

    public Map<Integer, Map<String, Long>> loadAtlasProgressByTown() {
        return sqlite.loadAtlasProgressByTown();
    }

    public Set<String> loadAtlasRewardGrantKeys() {
        return sqlite.loadAtlasRewardGrantKeys();
    }

    public void upsertAtlasFamilyProgress(int townId, String familyId, long progress, Instant updatedAt) {
        sqlite.upsertAtlasFamilyProgress(townId, familyId, progress, updatedAt);
        recordMutation("atlas-progress", payload("townId", Integer.toString(townId), "family", familyId));
    }

    public void appendAtlasActivity(int townId, AtlasChapter chapter, String familyId, long deltaProgress, Instant createdAt) {
        sqlite.appendAtlasActivity(townId, chapter, familyId, deltaProgress, createdAt);
        recordMutation("atlas-activity", payload(
                "townId", Integer.toString(townId),
                "chapter", chapter == null ? "-" : chapter.name(),
                "family", familyId
        ));
    }

    public Optional<AtlasChapter> loadMostActiveAtlasChapterSince(int townId, Instant since) {
        return sqlite.loadMostActiveAtlasChapterSince(townId, since);
    }

    public boolean markAtlasRewardGrant(
            String grantKey,
            int townId,
            String familyId,
            AtlasTier tier,
            ChallengeRewardType rewardType,
            String payload,
            Instant grantedAt
    ) {
        boolean marked = sqlite.markAtlasRewardGrant(grantKey, townId, familyId, tier, rewardType, payload, grantedAt);
        if (marked) {
            recordMutation("atlas-reward", ChallengeStore.payload("grantKey", grantKey, "townId", Integer.toString(townId)));
        }
        return marked;
    }

    public Optional<String> getRuntimeState(String key) {
        return sqlite.getRuntimeState(key);
    }

    public void setRuntimeState(String key, String value) {
        sqlite.setRuntimeState(key, value);
        recordMutation("runtime-state", payload("key", key));
    }

    public void clearAtlasState() {
        sqlite.clearAtlasState();
        recordMutation("atlas-reset", "-");
    }

    public Map<String, Instant> loadDefenseCooldowns() {
        return sqlite.loadDefenseCooldowns();
    }

    public void upsertDefenseCooldown(int townId, String tier, Instant availableAt, Instant updatedAt) {
        sqlite.upsertDefenseCooldown(townId, tier, availableAt, updatedAt);
        recordMutation("defense-cooldown", payload("townId", Integer.toString(townId), "tier", tier));
    }

    public void appendDefenseSessionStart(String sessionId, int townId, String townName, String tier, Instant startedAt) {
        sqlite.appendDefenseSessionStart(sessionId, townId, townName, tier, startedAt);
        recordMutation("defense-session-start", payload("sessionId", sessionId, "townId", Integer.toString(townId)));
    }

    public long abortActiveDefenseSessionsOnStartup(Instant endedAt, String reason) {
        long count = sqlite.abortActiveDefenseSessionsOnStartup(endedAt, reason);
        if (count > 0L) {
            recordMutation("defense-session-abort-startup", payload("count", Long.toString(count), "reason", reason));
        }
        return count;
    }

    public void completeDefenseSession(
            String sessionId,
            String status,
            String reason,
            Instant endedAt,
            double rewardMoneyAmount,
            long rewardXpScaled,
            String rewardItemsPayload
    ) {
        sqlite.completeDefenseSession(sessionId, status, reason, endedAt, rewardMoneyAmount, rewardXpScaled, rewardItemsPayload);
        recordMutation("defense-session-end", payload("sessionId", sessionId, "status", status));
    }

    public boolean markDefenseRewardGrant(
            String grantKey,
            int townId,
            String sessionId,
            ChallengeRewardType rewardType,
            String payload,
            Instant grantedAt
    ) {
        boolean marked = sqlite.markDefenseRewardGrant(grantKey, townId, sessionId, rewardType, payload, grantedAt);
        if (marked) {
            recordMutation("defense-reward", payload("grantKey", grantKey, "townId", Integer.toString(townId)));
        }
        return marked;
    }

    public Set<String> loadSuccessfulDefenseTiers(int townId) {
        return sqlite.loadSuccessfulDefenseTiers(townId);
    }

    public boolean hasSuccessfulDefenseCompletion(int townId, String tier) {
        return sqlite.hasSuccessfulDefenseCompletion(townId, tier);
    }

    public void appendCycleRecap(CycleRecapSnapshot recap) {
        sqlite.appendCycleRecap(recap);
        recordMutation("cycle-recap", payload("townId", recap == null ? "-" : Integer.toString(recap.townId()),
                "cycle", recap == null ? "-" : recap.cycleKey()));
    }

    public Optional<CycleRecapSnapshot> loadLatestRecap(int townId) {
        return sqlite.loadLatestRecap(townId);
    }

    public List<CycleRecapSnapshot> loadRecapHistory(int townId, int limit, int offset) {
        return sqlite.loadRecapHistory(townId, limit, offset);
    }

    public void pruneRecapHistory(int townId, int retainCycles) {
        sqlite.pruneRecapHistory(townId, retainCycles);
        recordMutation("recap-prune", payload("townId", Integer.toString(townId), "retain", Integer.toString(retainCycles)));
    }

    public List<CycleRecapSnapshot> loadAllRecaps(int limit) {
        return sqlite.loadAllRecaps(limit);
    }
}
