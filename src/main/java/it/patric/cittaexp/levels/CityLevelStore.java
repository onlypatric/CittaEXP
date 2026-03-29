package it.patric.cittaexp.levels;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.bukkit.plugin.Plugin;

public final class CityLevelStore {

    public record StoreDiagnostics(
            String storageMode,
            int progressionCacheSize,
            int approvalCacheSize,
            int dirtyEntries,
            int outboxPending,
            int outboxFailed,
            String lastMySqlSuccessAt,
            String lastMySqlError,
            String lastMySqlErrorAt
    ) {
    }

    private static final String SYSTEM_KEY_LAST_TAX_MONTH = "last_tax_month";
    private static final String SYSTEM_KEY_LEVEL_SCHEMA_VERSION = "level_schema_version";
    private static final String LEVEL_SCHEMA_M1 = "m1_v2";
    private static final int STAGE_SEALS_REQUIRED = 6;

    private final Plugin plugin;
    private final CityLevelRepository sqlite;
    private final MySqlLevelBackend mysql;
    private final CityLevelSettings settings;
    private final ScheduledExecutorService ioScheduler;
    private final Queue<StoreMutation> mutationQueue;
    private final ConcurrentHashMap<Integer, TownProgression> progressionCache;
    private final ConcurrentHashMap<Integer, Map<String, Long>> highwaterCache;
    private final ConcurrentHashMap<Integer, DebtState> debtCache;
    private final ConcurrentHashMap<Integer, ScanState> scanStateCache;
    private final ConcurrentHashMap<String, String> systemStateCache;
    private final ConcurrentHashMap<Long, StaffApprovalRequest> approvalCache;
    private final ConcurrentHashMap<Integer, AtomicInteger> pendingMutationsByTown;
    private final ConcurrentHashMap<Integer, Long> townLastAccessMillis;

    private final AtomicLong approvalSequence;
    private final AtomicLong mutationSequence;
    private final AtomicLong xpLedgerEventSequence;
    private final AtomicLong taxLedgerEventSequence;
    private final AtomicLong approvalEventSequence;

    private final AtomicInteger outboxPendingCount;
    private final AtomicInteger outboxFailedCount;
    private final AtomicBoolean mysqlAvailable;
    private final AtomicBoolean started;

    private volatile Instant lastMySqlSuccessAt;
    private volatile String lastMySqlError;
    private volatile Instant lastMySqlErrorAt;

    public CityLevelStore(
            Plugin plugin,
            CityLevelRepository sqlite,
            CityLevelSettings settings,
            MySqlLevelBackend mysql
    ) {
        this.plugin = plugin;
        this.sqlite = sqlite;
        this.settings = settings;
        this.mysql = mysql;
        this.ioScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "cittaexp-level-store-io");
            thread.setDaemon(true);
            return thread;
        });
        this.mutationQueue = new ConcurrentLinkedQueue<>();
        this.progressionCache = new ConcurrentHashMap<>();
        this.highwaterCache = new ConcurrentHashMap<>();
        this.debtCache = new ConcurrentHashMap<>();
        this.scanStateCache = new ConcurrentHashMap<>();
        this.systemStateCache = new ConcurrentHashMap<>();
        this.approvalCache = new ConcurrentHashMap<>();
        this.pendingMutationsByTown = new ConcurrentHashMap<>();
        this.townLastAccessMillis = new ConcurrentHashMap<>();
        this.approvalSequence = new AtomicLong(0L);
        this.mutationSequence = new AtomicLong(0L);
        this.xpLedgerEventSequence = new AtomicLong(System.currentTimeMillis());
        this.taxLedgerEventSequence = new AtomicLong(System.currentTimeMillis());
        this.approvalEventSequence = new AtomicLong(System.currentTimeMillis());
        this.outboxPendingCount = new AtomicInteger(0);
        this.outboxFailedCount = new AtomicInteger(0);
        this.mysqlAvailable = new AtomicBoolean(false);
        this.started = new AtomicBoolean(false);
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        ioScheduler.execute(this::bootstrap);
        long flushPeriod = Math.max(1L, settings.storeFlushSeconds());
        ioScheduler.scheduleAtFixedRate(this::flushAndReplaySafe, flushPeriod, flushPeriod, TimeUnit.SECONDS);
        long evictPeriod = Math.max(30L, settings.cacheEvictIntervalSeconds());
        ioScheduler.scheduleAtFixedRate(this::evictIdleEntriesSafe, evictPeriod, evictPeriod, TimeUnit.SECONDS);
        long resyncPeriod = Math.max(30L, settings.storeResyncSeconds());
        ioScheduler.scheduleAtFixedRate(this::resyncMySqlFromCacheSafe, resyncPeriod, resyncPeriod, TimeUnit.SECONDS);
    }

    public void stop() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(1, settings.storeShutdownDrainSeconds()));
        while (System.nanoTime() < deadlineNanos && !mutationQueue.isEmpty()) {
            flushAndReplaySafe();
            try {
                Thread.sleep(50L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        ioScheduler.shutdown();
        try {
            ioScheduler.awaitTermination(Math.max(1, settings.storeShutdownDrainSeconds()), TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public TownProgression getOrCreateProgression(int townId) {
        touchTown(townId);
        return progressionCache.computeIfAbsent(townId, ignored -> new TownProgression(
                townId,
                0L,
                1,
                TownStage.AVAMPOSTO_I,
                0,
                STAGE_SEALS_REQUIRED,
                null,
                null,
                Instant.now()
        ));
    }

    public Map<String, Long> getHighwaterByMaterial(int townId) {
        touchTown(townId);
        Map<String, Long> source = highwaterCache.get(townId);
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return new HashMap<>(source);
    }

    public CityScanResult applyScanAward(
            int townId,
            long awardedScaled,
            Map<String, Long> highwaterUpdates,
            int xpPerLevelScaled,
            int levelCap,
            String error,
            String source,
            UUID actor,
            String reason
    ) {
        touchTown(townId);
        TownProgression previous = getOrCreateProgression(townId);
        long safeAward = Math.max(0L, awardedScaled);
        long newXp = previous.xpScaled() + safeAward;
        int previousLevel = earnedLevelForXp(previous.xpScaled());
        int newLevel = earnedLevelForXp(newXp);
        TownProgression updated = new TownProgression(
                previous.townId(),
                newXp,
                previous.cityLevel(),
                previous.stage(),
                previous.stageSealsEarned(),
                previous.stageSealsRequired(),
                previous.stageCompletedAt(),
                previous.subTierCompletedAt(),
                Instant.now()
        );
        progressionCache.put(townId, updated);

        Map<String, Long> townHighwater = highwaterCache.computeIfAbsent(townId, ignored -> new HashMap<>());
        if (highwaterUpdates != null && !highwaterUpdates.isEmpty()) {
            townHighwater.putAll(highwaterUpdates);
        }

        ScanState newScanState = new ScanState(Instant.now(), safeAward, error);
        scanStateCache.put(townId, newScanState);

        enqueueMutation(new UpsertProgressionMutation(nextMutationId(), updated));
        if (!townHighwater.isEmpty()) {
            enqueueMutation(new UpsertHighwaterMutation(nextMutationId(), townId, new HashMap<>(townHighwater)));
        }
        enqueueMutation(new UpsertScanStateMutation(nextMutationId(), townId, newScanState.lastScanAt(), newScanState.lastAwardedScaled(), newScanState.lastError()));

        if (safeAward > 0L) {
            long eventId = xpLedgerEventSequence.incrementAndGet();
            enqueueMutation(new AppendXpLedgerMutation(
                    nextMutationId(),
                    eventId,
                    townId,
                    safeAward,
                    source,
                    actor == null ? null : actor.toString(),
                    reason,
                    newXp,
                    newLevel,
                    Instant.now()
            ));
        }

        return new CityScanResult(townId, safeAward, previousLevel, newLevel, newXp, error);
    }

    public TownProgression unlockLevel(int townId, int targetLevel) {
        TownProgression current = getOrCreateProgression(townId);
        int nextLevel = Math.max(1, Math.min(settings.levelCap(), targetLevel));
        TownProgression updated = new TownProgression(
                current.townId(),
                current.xpScaled(),
                nextLevel,
                current.stage(),
                current.stageSealsEarned(),
                current.stageSealsRequired(),
                current.stageCompletedAt(),
                current.subTierCompletedAt(),
                Instant.now()
        );
        progressionCache.put(townId, updated);
        enqueueMutation(new UpsertProgressionMutation(nextMutationId(), updated));
        return updated;
    }

    public TownProgression unlockLevelAndStage(int townId, int targetLevel, TownStage targetStage) {
        TownProgression current = getOrCreateProgression(townId);
        int nextLevel = Math.max(1, Math.min(settings.levelCap(), targetLevel));
        TownProgression updated = new TownProgression(
                current.townId(),
                current.xpScaled(),
                nextLevel,
                targetStage == null ? current.stage() : targetStage,
                current.stageSealsEarned(),
                current.stageSealsRequired(),
                current.stageCompletedAt(),
                current.subTierCompletedAt(),
                Instant.now()
        );
        progressionCache.put(townId, updated);
        enqueueMutation(new UpsertProgressionMutation(nextMutationId(), updated));
        return updated;
    }

    public int earnedLevel(int townId) {
        TownProgression current = getOrCreateProgression(townId);
        return earnedLevelForXp(current.xpScaled());
    }

    private int earnedLevelForXp(long xpScaled) {
        return CityLevelCurve.levelFromXpScaled(
                xpScaled,
                settings.levelCap(),
                settings.xpPerLevel(),
                settings.xpScale(),
                settings.curveExponent()
        );
    }

    public void updateStage(int townId, TownStage stage) {
        TownProgression current = getOrCreateProgression(townId);
        TownProgression updated = new TownProgression(
                townId,
                current.xpScaled(),
                current.cityLevel(),
                stage,
                0,
                STAGE_SEALS_REQUIRED,
                null,
                null,
                Instant.now()
        );
        progressionCache.put(townId, updated);
        enqueueMutation(new UpsertProgressionMutation(nextMutationId(), updated));
    }

    public TownProgression addStageSeals(int townId, int delta, String milestoneKey) {
        TownProgression current = getOrCreateProgression(townId);
        if (delta <= 0) {
            return current;
        }
        TownProgression updated = setStageSeals(townId, current.stageSealsEarned() + delta, true);
        if (milestoneKey != null && !milestoneKey.isBlank()) {
            String stateKey = "seal_milestone:" + townId + ':' + milestoneKey;
            String now = Instant.now().toString();
            systemStateCache.put(stateKey, now);
            enqueueMutation(new SetSystemStateMutation(nextMutationId(), stateKey, now));
        }
        return updated;
    }

    public TownProgression setStageSeals(int townId, int seals, boolean preserveCurrentStage) {
        TownProgression current = getOrCreateProgression(townId);
        int required = Math.max(STAGE_SEALS_REQUIRED, current.stageSealsRequired());
        int nextSeals = Math.min(required, Math.max(0, seals));
        Instant now = Instant.now();
        Instant subTierCompletedAt = nextSeals > 0
                ? (current.subTierCompletedAt() == null ? now : current.subTierCompletedAt())
                : null;
        Instant stageCompletedAt = nextSeals >= required
                ? (current.stageCompletedAt() == null ? now : current.stageCompletedAt())
                : null;
        TownProgression updated = new TownProgression(
                current.townId(),
                current.xpScaled(),
                current.cityLevel(),
                current.stage(),
                nextSeals,
                required,
                stageCompletedAt,
                subTierCompletedAt,
                now
        );
        progressionCache.put(townId, updated);
        enqueueMutation(new UpsertProgressionMutation(nextMutationId(), updated));
        return updated;
    }

    public TownProgression setXpScaled(int townId, long targetXpScaled, String source, UUID actor, String reason) {
        touchTown(townId);
        TownProgression current = getOrCreateProgression(townId);
        long nextXp = Math.max(0L, targetXpScaled);
        int nextLevel = earnedLevelForXp(nextXp);
        TownProgression updated = new TownProgression(
                current.townId(),
                nextXp,
                current.cityLevel(),
                current.stage(),
                current.stageSealsEarned(),
                current.stageSealsRequired(),
                current.stageCompletedAt(),
                current.subTierCompletedAt(),
                Instant.now()
        );
        progressionCache.put(townId, updated);
        enqueueMutation(new UpsertProgressionMutation(nextMutationId(), updated));

        long delta = nextXp - current.xpScaled();
        if (delta != 0L) {
            long eventId = xpLedgerEventSequence.incrementAndGet();
            enqueueMutation(new AppendXpLedgerMutation(
                    nextMutationId(),
                    eventId,
                    townId,
                    delta,
                    source == null || source.isBlank() ? "xp_admin_set" : source,
                    actor == null ? null : actor.toString(),
                    reason == null || reason.isBlank() ? "admin-xp-set" : reason,
                    nextXp,
                    nextLevel,
                    Instant.now()
            ));
        }
        return updated;
    }

    public boolean hasSealMilestone(int townId, String milestoneKey) {
        if (milestoneKey == null || milestoneKey.isBlank()) {
            return false;
        }
        return systemStateCache.containsKey("seal_milestone:" + townId + ':' + milestoneKey);
    }

    public double getDebt(int townId) {
        touchTown(townId);
        return debtCache.getOrDefault(townId, DebtState.ZERO).debt();
    }

    public void setDebt(int townId, double debt) {
        touchTown(townId);
        DebtState state = new DebtState(Math.max(0.0D, debt), Instant.now());
        debtCache.put(townId, state);
        enqueueMutation(new UpsertDebtMutation(nextMutationId(), townId, state.debt(), state.updatedAt()));
    }

    public void appendTaxLedger(
            int townId,
            String monthKey,
            TownStage stage,
            double due,
            double paid,
            double debtAfter,
            String status
    ) {
        long eventId = taxLedgerEventSequence.incrementAndGet();
        enqueueMutation(new AppendTaxLedgerMutation(
                nextMutationId(),
                eventId,
                townId,
                monthKey,
                stage,
                due,
                paid,
                debtAfter,
                status,
                Instant.now()
        ));
    }

    public Optional<String> getLastTaxMonthKey() {
        return Optional.ofNullable(systemStateCache.get(SYSTEM_KEY_LAST_TAX_MONTH));
    }

    public void setLastTaxMonthKey(String monthKey) {
        if (monthKey == null) {
            return;
        }
        systemStateCache.put(SYSTEM_KEY_LAST_TAX_MONTH, monthKey);
        enqueueMutation(new SetSystemStateMutation(nextMutationId(), SYSTEM_KEY_LAST_TAX_MONTH, monthKey));
    }

    public CityLevelRepository.ScanStats scanStats() {
        long towns = progressionCache.size();
        long totalXp = progressionCache.values().stream().mapToLong(TownProgression::xpScaled).sum();
        Instant lastUpdated = progressionCache.values().stream()
                .map(TownProgression::updatedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new CityLevelRepository.ScanStats(towns, totalXp, lastUpdated);
    }

    public long nextApprovalRequestId() {
        return approvalSequence.incrementAndGet();
    }

    public void upsertApprovalRequest(StaffApprovalRequest request) {
        approvalCache.put(request.id(), request);
        enqueueMutation(new UpsertApprovalRequestMutation(nextMutationId(), request));
    }

    public Optional<StaffApprovalRequest> findApprovalRequest(long requestId) {
        return Optional.ofNullable(approvalCache.get(requestId));
    }

    public List<StaffApprovalRequest> listApprovalRequests(
            Optional<CityLevelRepository.RequestStatus> status,
            Optional<StaffApprovalType> type
    ) {
        return approvalCache.values().stream()
                .filter(request -> status.map(s -> s == request.status()).orElse(true))
                .filter(request -> type.map(t -> t == request.type()).orElse(true))
                .sorted(Comparator.comparingLong(StaffApprovalRequest::id).reversed())
                .collect(Collectors.toList());
    }

    public void appendApprovalEvent(long requestId, int townId, String eventType, UUID actor, String payload) {
        long eventId = approvalEventSequence.incrementAndGet();
        enqueueMutation(new AppendApprovalEventMutation(
                nextMutationId(),
                eventId,
                requestId,
                townId,
                eventType,
                actor == null ? null : actor.toString(),
                payload,
                Instant.now()
        ));
    }

    public int countPendingRequests() {
        return (int) approvalCache.values().stream()
                .filter(request -> request.status() == CityLevelRepository.RequestStatus.PENDING)
                .count();
    }

    public StoreDiagnostics diagnostics() {
        int dirtyEntries = pendingMutationsByTown.values().stream().mapToInt(AtomicInteger::get).sum();
        return new StoreDiagnostics(
                mysql.enabled() ? "mysql+sqlite" : "sqlite",
                progressionCache.size(),
                approvalCache.size(),
                dirtyEntries,
                outboxPendingCount.get(),
                outboxFailedCount.get(),
                lastMySqlSuccessAt == null ? "-" : lastMySqlSuccessAt.toString(),
                lastMySqlError == null ? "-" : lastMySqlError,
                lastMySqlErrorAt == null ? "-" : lastMySqlErrorAt.toString()
        );
    }

    private void bootstrap() {
        try {
            sqlite.initialize();
            if (mysql.enabled()) {
                try {
                    mysql.initialize();
                    mysqlAvailable.set(true);
                    lastMySqlError = null;
                } catch (Exception exception) {
                    mysqlAvailable.set(false);
                    lastMySqlError = normalizeError(exception);
                    lastMySqlErrorAt = Instant.now();
                    plugin.getLogger().warning("[levels-store] MySQL init failed, fallback sqlite-only reason=" + lastMySqlError);
                }
            }

            systemStateCache.putAll(sqlite.loadAllSystemState());
            if (!LEVEL_SCHEMA_M1.equalsIgnoreCase(systemStateCache.get(SYSTEM_KEY_LEVEL_SCHEMA_VERSION))) {
                plugin.getLogger().warning("[levels-store] applying M1 cutover reset (non-prod)");
                sqlite.resetForM1Cutover();
                systemStateCache.clear();
                systemStateCache.put(SYSTEM_KEY_LEVEL_SCHEMA_VERSION, LEVEL_SCHEMA_M1);
                sqlite.setSystemState(SYSTEM_KEY_LEVEL_SCHEMA_VERSION, LEVEL_SCHEMA_M1);
            }

            Map<Integer, TownProgression> loadedProgressions = sqlite.loadAllProgressions();
            for (TownProgression progression : loadedProgressions.values()) {
                int unlockedLevel = Math.max(1, Math.min(settings.levelCap(), progression.cityLevel()));
                int required = Math.max(STAGE_SEALS_REQUIRED, progression.stageSealsRequired());
                TownProgression normalized = new TownProgression(
                        progression.townId(),
                        progression.xpScaled(),
                        unlockedLevel,
                        progression.stage(),
                        progression.stageSealsEarned(),
                        required,
                        progression.stageCompletedAt(),
                        progression.subTierCompletedAt(),
                        progression.updatedAt()
                );
                progressionCache.put(normalized.townId(), normalized);
                if (!normalized.equals(progression)) {
                    sqlite.upsertProgression(normalized);
                    if (mysql.enabled() && mysqlAvailable.get()) {
                        try {
                            mysql.upsertProgression(normalized);
                        } catch (Exception ignored) {
                            // Non blocca bootstrap: verra allineato dal replay.
                        }
                    }
                }
            }
            highwaterCache.putAll(sqlite.loadAllHighwater());
            sqlite.loadAllDebts().forEach((townId, debt) -> debtCache.put(townId, new DebtState(debt, Instant.now())));

            List<StaffApprovalRequest> approvals = sqlite.loadAllApprovalRequests();
            for (StaffApprovalRequest approval : approvals) {
                approvalCache.put(approval.id(), approval);
            }
            reconcilePendingStageApprovals();
            approvalSequence.set(Math.max(approvalSequence.get(), sqlite.maxStaffApprovalId()));

            outboxPendingCount.set(sqlite.countOutboxPending());
            outboxFailedCount.set(sqlite.countOutboxFailed());

            replayOutboxToMySql();
            resyncMySqlFromCacheSafe();
        } catch (Exception exception) {
            plugin.getLogger().severe("[levels-store] bootstrap failed reason=" + normalizeError(exception));
        }
    }

    private void reconcilePendingStageApprovals() {
        for (StaffApprovalRequest request : approvalCache.values()) {
            if (request.status() != CityLevelRepository.RequestStatus.PENDING
                    || request.type() != StaffApprovalType.STAGE_UPGRADE) {
                continue;
            }
            String rawStage = request.payloadValue("targetStage");
            if (rawStage == null || rawStage.isBlank()) {
                continue;
            }
            final TownStage targetStage;
            try {
                targetStage = TownStage.valueOf(rawStage.trim());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            TownProgression progression = progressionCache.get(request.townId());
            if (progression == null || progression.stage() != targetStage) {
                continue;
            }
            TownStage fallbackStage = targetStage.ordinal() > 0
                    ? TownStage.values()[targetStage.ordinal() - 1]
                    : TownStage.AVAMPOSTO_I;
            int requiredSeals = Math.max(STAGE_SEALS_REQUIRED, progression.stageSealsRequired());
            Instant now = Instant.now();
            TownProgression reconciled = new TownProgression(
                    progression.townId(),
                    progression.xpScaled(),
                    progression.cityLevel(),
                    fallbackStage,
                    requiredSeals,
                    requiredSeals,
                    progression.stageCompletedAt() == null ? now : progression.stageCompletedAt(),
                    progression.subTierCompletedAt() == null ? now : progression.subTierCompletedAt(),
                    now
            );
            progressionCache.put(reconciled.townId(), reconciled);
            sqlite.upsertProgression(reconciled);
            if (mysql.enabled() && mysqlAvailable.get()) {
                try {
                    mysql.upsertProgression(reconciled);
                } catch (Exception ignored) {
                    // Replay/resync will heal MySQL later.
                }
            }
            plugin.getLogger().warning("[levels-store] reconciled pending stage town=" + reconciled.townId()
                    + " target=" + targetStage.name()
                    + " fallback=" + fallbackStage.name());
        }
    }

    private TownStage resolveSubTier(PrincipalStage principalStage, SubTier subTier) {
        for (TownStage value : TownStage.values()) {
            if (value.principalStage() == principalStage && value.subTier() == subTier) {
                return value;
            }
        }
        return TownStage.AVAMPOSTO_I;
    }

    private void flushAndReplaySafe() {
        try {
            flushQueue();
            replayOutboxToMySql();
            outboxPendingCount.set(sqlite.countOutboxPending());
            outboxFailedCount.set(sqlite.countOutboxFailed());
        } catch (Exception exception) {
            plugin.getLogger().warning("[levels-store] flush failed reason=" + normalizeError(exception));
        }
    }

    private void flushQueue() {
        int batchSize = Math.max(1, settings.storeFlushBatchSize());
        for (int i = 0; i < batchSize; i++) {
            StoreMutation mutation = mutationQueue.poll();
            if (mutation == null) {
                return;
            }
            try {
                mutation.applySqlite(sqlite);
                String encoded = encodeMutation(mutation);
                sqlite.appendOutbox(mutation.opId(), mutation.type(), encoded);
                if (mysql.enabled()) {
                    tryApplyMutationToMySql(mutation);
                } else {
                    sqlite.markOutboxDone(mutation.opId());
                }
            } catch (Exception exception) {
                sqlite.markOutboxFailed(mutation.opId(), normalizeError(exception), settings.storeReplayBackoffSeconds());
            } finally {
                if (mutation.townId() > 0) {
                    AtomicInteger pending = pendingMutationsByTown.get(mutation.townId());
                    if (pending != null && pending.decrementAndGet() <= 0) {
                        pendingMutationsByTown.remove(mutation.townId());
                    }
                }
            }
        }
    }

    private void tryApplyMutationToMySql(StoreMutation mutation) {
        if (!mysqlAvailable.get()) {
            sqlite.markOutboxFailed(mutation.opId(), "mysql-unavailable", settings.storeReplayBackoffSeconds());
            return;
        }
        try {
            mutation.applyMySql(mysql);
            sqlite.markOutboxDone(mutation.opId());
            lastMySqlSuccessAt = Instant.now();
            lastMySqlError = null;
            mysqlAvailable.set(true);
        } catch (Exception exception) {
            mysqlAvailable.set(false);
            lastMySqlError = normalizeError(exception);
            lastMySqlErrorAt = Instant.now();
            sqlite.markOutboxFailed(mutation.opId(), lastMySqlError, settings.storeReplayBackoffSeconds());
        }
    }

    private void replayOutboxToMySql() {
        if (!mysql.enabled()) {
            return;
        }
        if (!mysqlAvailable.get()) {
            try {
                mysql.initialize();
                mysqlAvailable.set(true);
            } catch (Exception exception) {
                lastMySqlError = normalizeError(exception);
                lastMySqlErrorAt = Instant.now();
                return;
            }
        }
        List<CityLevelRepository.OutboxItem> retryable = sqlite.listRetryableOutbox(settings.storeFlushBatchSize(), Instant.now());
        for (CityLevelRepository.OutboxItem item : retryable) {
            try {
                StoreMutation mutation = decodeMutation(item.payload());
                mutation.applyMySql(mysql);
                sqlite.markOutboxDone(item.opId());
                lastMySqlSuccessAt = Instant.now();
                lastMySqlError = null;
            } catch (Exception exception) {
                mysqlAvailable.set(false);
                lastMySqlError = normalizeError(exception);
                lastMySqlErrorAt = Instant.now();
                sqlite.markOutboxFailed(item.opId(), lastMySqlError, settings.storeReplayBackoffSeconds());
                break;
            }
        }
    }

    private void resyncMySqlFromCacheSafe() {
        if (!mysql.enabled() || !mysqlAvailable.get()) {
            return;
        }
        try {
            for (TownProgression progression : progressionCache.values()) {
                mysql.upsertProgression(progression);
            }
            for (Map.Entry<Integer, Map<String, Long>> entry : highwaterCache.entrySet()) {
                mysql.upsertHighwater(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<Integer, DebtState> entry : debtCache.entrySet()) {
                mysql.upsertDebt(entry.getKey(), entry.getValue().debt(), entry.getValue().updatedAt());
            }
            for (Map.Entry<String, String> state : systemStateCache.entrySet()) {
                mysql.setSystemState(state.getKey(), state.getValue());
            }
            for (StaffApprovalRequest request : approvalCache.values()) {
                mysql.upsertApprovalRequest(request);
            }
        } catch (Exception exception) {
            mysqlAvailable.set(false);
            lastMySqlError = normalizeError(exception);
            lastMySqlErrorAt = Instant.now();
        }
    }

    private void evictIdleEntriesSafe() {
        long now = System.currentTimeMillis();
        long ttlMillis = Math.max(60L, settings.cacheIdleTtlSeconds()) * 1000L;
        for (Map.Entry<Integer, Long> entry : new ArrayList<>(townLastAccessMillis.entrySet())) {
            int townId = entry.getKey();
            long lastAccess = entry.getValue();
            AtomicInteger pending = pendingMutationsByTown.get(townId);
            if (pending != null && pending.get() > 0) {
                continue;
            }
            if (now - lastAccess < ttlMillis) {
                continue;
            }
            progressionCache.remove(townId);
            highwaterCache.remove(townId);
            debtCache.remove(townId);
            scanStateCache.remove(townId);
            townLastAccessMillis.remove(townId);
        }
    }

    private void enqueueMutation(StoreMutation mutation) {
        mutationQueue.add(mutation);
        if (mutation.townId() > 0) {
            pendingMutationsByTown
                    .computeIfAbsent(mutation.townId(), ignored -> new AtomicInteger(0))
                    .incrementAndGet();
            touchTown(mutation.townId());
        }
    }

    private long nextMutationId() {
        return mutationSequence.incrementAndGet() + System.currentTimeMillis();
    }

    private void touchTown(int townId) {
        if (townId <= 0) {
            return;
        }
        townLastAccessMillis.put(townId, System.currentTimeMillis());
    }

    private static String normalizeError(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }

    private static String encodeMutation(StoreMutation mutation) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ObjectOutputStream stream = new ObjectOutputStream(output)) {
            stream.writeObject(mutation);
            stream.flush();
            return Base64.getEncoder().encodeToString(output.toByteArray());
        }
    }

    private static StoreMutation decodeMutation(String raw) throws IOException, ClassNotFoundException {
        byte[] data = Base64.getDecoder().decode(raw);
        try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return (StoreMutation) stream.readObject();
        }
    }

    private record DebtState(double debt, Instant updatedAt) {
        private static final DebtState ZERO = new DebtState(0.0D, Instant.EPOCH);
    }

    private record ScanState(Instant lastScanAt, long lastAwardedScaled, String lastError) {
    }

    private interface StoreMutation extends Serializable {
        long opId();

        String type();

        int townId();

        void applySqlite(CityLevelRepository repository);

        void applyMySql(MySqlLevelBackend backend) throws Exception;
    }

    private record UpsertProgressionMutation(long opId, TownProgression progression) implements StoreMutation {
        @Override
        public String type() {
            return "UPSERT_PROGRESSION";
        }

        @Override
        public int townId() {
            return progression.townId();
        }

        @Override
        public void applySqlite(CityLevelRepository repository) {
            repository.upsertProgression(progression);
        }

        @Override
        public void applyMySql(MySqlLevelBackend backend) throws Exception {
            backend.upsertProgression(progression);
        }
    }

    private record UpsertHighwaterMutation(long opId, int townId, Map<String, Long> highwater) implements StoreMutation {
        @Override
        public String type() {
            return "UPSERT_HIGHWATER";
        }

        @Override
        public void applySqlite(CityLevelRepository repository) {
            repository.upsertHighwater(townId, highwater);
        }

        @Override
        public void applyMySql(MySqlLevelBackend backend) throws Exception {
            backend.upsertHighwater(townId, highwater);
        }
    }

    private record UpsertScanStateMutation(long opId, int townId, Instant lastScanAt, long lastAwardedScaled, String lastError)
            implements StoreMutation {
        @Override
        public String type() {
            return "UPSERT_SCAN_STATE";
        }

        @Override
        public void applySqlite(CityLevelRepository repository) {
            repository.upsertScanState(townId, lastScanAt, lastAwardedScaled, lastError);
        }

        @Override
        public void applyMySql(MySqlLevelBackend backend) throws Exception {
            backend.upsertScanState(townId, lastScanAt, lastAwardedScaled, lastError);
        }
    }

    private record UpsertDebtMutation(long opId, int townId, double debt, Instant updatedAt) implements StoreMutation {
        @Override
        public String type() {
            return "UPSERT_DEBT";
        }

        @Override
        public void applySqlite(CityLevelRepository repository) {
            repository.setDebt(townId, debt);
        }

        @Override
        public void applyMySql(MySqlLevelBackend backend) throws Exception {
            backend.upsertDebt(townId, debt, updatedAt);
        }
    }

    private record AppendTaxLedgerMutation(
            long opId,
            long eventId,
            int townId,
            String monthKey,
            TownStage stage,
            double due,
            double paid,
            double debtAfter,
            String status,
            Instant processedAt
    ) implements StoreMutation {
        @Override
        public String type() {
            return "APPEND_TAX_LEDGER";
        }

        @Override
        public void applySqlite(CityLevelRepository repository) {
            repository.appendTaxLedgerV2(eventId, townId, monthKey, stage, due, paid, debtAfter, status, processedAt);
        }

        @Override
        public void applyMySql(MySqlLevelBackend backend) throws Exception {
            backend.appendTaxLedger(eventId, townId, monthKey, stage, due, paid, debtAfter, status, processedAt);
        }
    }

    private record AppendXpLedgerMutation(
            long opId,
            long eventId,
            int townId,
            long deltaXpScaled,
            String source,
            String actorUuid,
            String reason,
            long resultingXpScaled,
            int resultingLevel,
            Instant occurredAt
    ) implements StoreMutation {
        @Override
        public String type() {
            return "APPEND_XP_LEDGER";
        }

        @Override
        public int townId() {
            return townId;
        }

        @Override
        public void applySqlite(CityLevelRepository repository) {
            repository.appendXpLedger(eventId, townId, deltaXpScaled, source, actorUuid, reason, resultingXpScaled, resultingLevel, occurredAt);
        }

        @Override
        public void applyMySql(MySqlLevelBackend backend) throws Exception {
            backend.appendXpLedger(eventId, townId, deltaXpScaled, source, actorUuid, reason, resultingXpScaled, resultingLevel, occurredAt);
        }
    }

    private record UpsertApprovalRequestMutation(long opId, StaffApprovalRequest request) implements StoreMutation {
        @Override
        public String type() {
            return "UPSERT_APPROVAL_REQUEST";
        }

        @Override
        public int townId() {
            return request.townId();
        }

        @Override
        public void applySqlite(CityLevelRepository repository) {
            repository.upsertApprovalRequest(request);
        }

        @Override
        public void applyMySql(MySqlLevelBackend backend) throws Exception {
            backend.upsertApprovalRequest(request);
        }
    }

    private record AppendApprovalEventMutation(
            long opId,
            long eventId,
            long requestId,
            int townId,
            String eventType,
            String actorUuid,
            String payload,
            Instant createdAt
    ) implements StoreMutation {
        @Override
        public String type() {
            return "APPEND_APPROVAL_EVENT";
        }

        @Override
        public int townId() {
            return townId;
        }

        @Override
        public void applySqlite(CityLevelRepository repository) {
            repository.appendApprovalEvent(eventId, requestId, townId, eventType, actorUuid, payload, createdAt);
        }

        @Override
        public void applyMySql(MySqlLevelBackend backend) throws Exception {
            backend.appendApprovalEvent(eventId, requestId, townId, eventType, actorUuid, payload, createdAt);
        }
    }

    private record SetSystemStateMutation(long opId, String key, String value) implements StoreMutation {
        @Override
        public String type() {
            return "SET_SYSTEM_STATE";
        }

        @Override
        public int townId() {
            return -1;
        }

        @Override
        public void applySqlite(CityLevelRepository repository) {
            repository.setSystemState(key, value);
        }

        @Override
        public void applyMySql(MySqlLevelBackend backend) throws Exception {
            backend.setSystemState(key, value);
        }
    }
}
