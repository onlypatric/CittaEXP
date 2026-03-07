package it.patric.cittaexp.economy.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.patric.commonlib.api.CommonScheduler;
import dev.patric.commonlib.api.TaskHandle;
import it.patric.cittaexp.core.model.AuditEvent;
import it.patric.cittaexp.core.model.CapitalState;
import it.patric.cittaexp.core.model.CityStatus;
import it.patric.cittaexp.core.model.CityTier;
import it.patric.cittaexp.core.model.CityTreasuryLedgerEntry;
import it.patric.cittaexp.core.model.FreezeReason;
import it.patric.cittaexp.core.model.LedgerEntryType;
import it.patric.cittaexp.core.model.RankingSnapshot;
import it.patric.cittaexp.core.model.TaxPolicy;
import it.patric.cittaexp.core.port.CityTreasuryLedgerPort;
import it.patric.cittaexp.core.port.EconomyStatePort;
import it.patric.cittaexp.core.port.RankingPort;
import it.patric.cittaexp.core.port.TaxPolicyPort;
import it.patric.cittaexp.core.port.VaultEconomyPort;
import it.patric.cittaexp.core.service.CapitalService;
import it.patric.cittaexp.core.service.EconomyDiagnosticsService;
import it.patric.cittaexp.core.service.RankingReadService;
import it.patric.cittaexp.core.service.TaxService;
import it.patric.cittaexp.economy.config.EconomySettings;
import it.patric.cittaexp.persistence.domain.AuditEventRecord;
import it.patric.cittaexp.persistence.domain.CapitalStateRecord;
import it.patric.cittaexp.persistence.domain.CityRecord;
import it.patric.cittaexp.persistence.domain.CityTreasuryLedgerRecord;
import it.patric.cittaexp.persistence.domain.FreezeCaseRecord;
import it.patric.cittaexp.persistence.domain.MonthlyCycleStateRecord;
import it.patric.cittaexp.persistence.domain.PersistenceWriteOutcome;
import it.patric.cittaexp.persistence.domain.RankingSnapshotRecord;
import it.patric.cittaexp.persistence.domain.TaxPolicyRecord;
import it.patric.cittaexp.persistence.port.CityReadPort;
import it.patric.cittaexp.persistence.port.CityTxPort;
import it.patric.cittaexp.persistence.port.CityWritePort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DefaultEconomyService implements TaxService, CapitalService, RankingReadService, EconomyDiagnosticsService {

    private static final Gson GSON = new Gson();
    private static final String DEFAULT_POLICY_ID = "default";
    private static final String MONTHLY_CYCLE_KEY = "monthly-economy";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final CityReadPort readPort;
    private final CityWritePort writePort;
    private final CityTxPort txPort;
    private final TaxPolicyPort taxPolicyPort;
    private final CityTreasuryLedgerPort ledgerPort;
    private final EconomyStatePort economyStatePort;
    private final VaultEconomyPort vaultEconomyPort;
    private final RankingPort rankingPort;
    private final CommonScheduler scheduler;
    private final EconomySettings settings;
    private final ZoneId zoneId;
    private final LocalTime runTime;
    private final Logger logger;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean cycleRunning = new AtomicBoolean(false);

    private volatile TaskHandle scheduleHandle;

    public DefaultEconomyService(
            CityReadPort readPort,
            CityWritePort writePort,
            CityTxPort txPort,
            TaxPolicyPort taxPolicyPort,
            CityTreasuryLedgerPort ledgerPort,
            EconomyStatePort economyStatePort,
            VaultEconomyPort vaultEconomyPort,
            RankingPort rankingPort,
            CommonScheduler scheduler,
            EconomySettings settings,
            Logger logger
    ) {
        this.readPort = Objects.requireNonNull(readPort, "readPort");
        this.writePort = Objects.requireNonNull(writePort, "writePort");
        this.txPort = Objects.requireNonNull(txPort, "txPort");
        this.taxPolicyPort = Objects.requireNonNull(taxPolicyPort, "taxPolicyPort");
        this.ledgerPort = Objects.requireNonNull(ledgerPort, "ledgerPort");
        this.economyStatePort = Objects.requireNonNull(economyStatePort, "economyStatePort");
        this.vaultEconomyPort = Objects.requireNonNull(vaultEconomyPort, "vaultEconomyPort");
        this.rankingPort = Objects.requireNonNull(rankingPort, "rankingPort");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.zoneId = ZoneId.of(settings.schedule().timezone());
        this.runTime = settings.schedule().runTime();
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void start() {
        if (!settings.enabled()) {
            logger.info("[CittaEXP][economy] monthly engine disabled by config");
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        seedDefaultPolicyIfMissing();
        scheduler.runAsync(() -> runToTarget(YearMonth.now(zoneId), TriggerType.STARTUP_CATCHUP));
        long intervalTicks = Math.max(20L, settings.schedule().tickIntervalSeconds() * 20L);
        scheduleHandle = scheduler.runSyncRepeating(intervalTicks, intervalTicks, () -> scheduler.runAsync(this::scheduledTick));
        logger.info(
                "[CittaEXP][economy] monthly engine started timezone="
                        + zoneId + " runTime=" + runTime + " catchUp=" + settings.schedule().catchUpEnabled()
        );
    }

    public void stop() {
        started.set(false);
        TaskHandle handle = scheduleHandle;
        if (handle != null) {
            handle.cancel();
            scheduleHandle = null;
        }
    }

    @Override
    public TaxPolicy currentPolicy() {
        return taxPolicyPort.findTaxPolicy(DEFAULT_POLICY_ID)
                .map(this::toTaxPolicy)
                .orElseGet(this::defaultPolicy);
    }

    @Override
    public void updatePolicy(TaxPolicy policy, String reason) {
        Objects.requireNonNull(policy, "policy");
        long now = System.currentTimeMillis();
        TaxPolicyRecord record = new TaxPolicyRecord(
                DEFAULT_POLICY_ID,
                policy.borgoMonthlyCost(),
                policy.villaggioMonthlyCost(),
                policy.regnoMonthlyCost(),
                policy.regnoShopMonthlyExtraCost(),
                policy.capitaleMonthlyBonus(),
                policy.dueDayOfMonth(),
                policy.timezoneId(),
                now
        );
        expect(taxPolicyPort.upsertTaxPolicy(record), "upsert-tax-policy");
        appendAudit(audit(
                null,
                "ECONOMY",
                "tax_policy_updated",
                null,
                jsonPayload("reason", safe(reason)),
                now
        ));
    }

    @Override
    public MonthlyCycleReport runMonthlyCycle(YearMonth triggerMonth, TriggerType triggerType) {
        Objects.requireNonNull(triggerMonth, "triggerMonth");
        Objects.requireNonNull(triggerType, "triggerType");
        if (!settings.enabled()) {
            return new MonthlyCycleReport(triggerMonth, 0, 0, 0, 0, 0, List.of());
        }
        if (!cycleRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("economy-cycle-already-running");
        }
        try {
            MonthlyCycleReport report = processSingleMonth(triggerMonth, triggerType);
            syncCapitalAndBonus(triggerMonth);
            recordCycleState(triggerMonth, STATUS_SUCCESS, "");
            return report;
        } catch (RuntimeException ex) {
            YearMonth fallback = triggerMonth.minusMonths(1);
            recordCycleState(fallback, STATUS_FAILED, ex.getClass().getSimpleName() + ":" + safe(ex.getMessage()));
            throw ex;
        } finally {
            cycleRunning.set(false);
        }
    }

    @Override
    public CityTaxPreview previewCityTax(UUID cityId, YearMonth month) {
        Objects.requireNonNull(cityId, "cityId");
        Objects.requireNonNull(month, "month");
        CityRecord city = readPort.findCityById(cityId)
                .orElseThrow(() -> new IllegalStateException("city-not-found"));
        TaxPolicy policy = currentPolicy();
        boolean skip = shouldSkipForCreationMonth(city, month);
        if (skip) {
            return new CityTaxPreview(cityId, month, 0L, 0L, 0L, 0L, true);
        }
        long total = taxForTier(city.tier(), policy);
        long treasury = Math.min(city.treasuryBalance(), total);
        long leader = Math.min(Math.max(0L, total - treasury), Math.max(0L, vaultEconomyPort.balance(city.leaderUuid())));
        long unpaid = Math.max(0L, total - treasury - leader);
        return new CityTaxPreview(cityId, month, total, treasury, leader, unpaid, false);
    }

    @Override
    public Optional<CapitalState> currentCapital() {
        return economyStatePort.findCapitalState(settings.capital().slotKey())
                .map(value -> new CapitalState(
                        value.cityId(),
                        value.assignedAtEpochMilli(),
                        value.updatedAtEpochMilli(),
                        value.sourceRankingVersion()
                ));
    }

    @Override
    public Optional<CapitalState> syncCapitalAndBonus(YearMonth month) {
        Objects.requireNonNull(month, "month");
        TaxPolicy policy = currentPolicy();
        Optional<RankingPort.CityRankingEntry> candidate = rankingPort.topKingdom()
                .flatMap(this::asValidCapitalCandidate);
        long now = System.currentTimeMillis();
        String bonusReason = bonusReason(month);

        return txPort.withTransaction(connection -> {
            Optional<CapitalStateRecord> currentState = economyStatePort.findCapitalState(settings.capital().slotKey());
            CapitalStateRecord nextState = null;

            if (candidate.isEmpty()) {
                if (currentState.isPresent()) {
                    clearCapitalFlagIfPresent(currentState.get().cityId(), now);
                    expect(economyStatePort.clearCapitalState(settings.capital().slotKey()), "clear-capital-state");
                    appendAudit(audit(
                            currentState.get().cityId(),
                            "CITY",
                            "capital_revoked",
                            null,
                            jsonPayload("reason", "no-valid-ranking-candidate"),
                            now
                    ));
                }
                return Optional.<CapitalState>empty();
            }

            RankingPort.CityRankingEntry top = candidate.get();
            expect(writePort.upsertRankingSnapshot(new RankingSnapshotRecord(
                    top.cityId(),
                    top.rank(),
                    top.score(),
                    top.sourceVersion(),
                    top.fetchedAtEpochMilli()
            )), "upsert-ranking-snapshot");

            boolean changed = currentState.isEmpty() || !currentState.get().cityId().equals(top.cityId());
            if (changed && currentState.isPresent()) {
                clearCapitalFlagIfPresent(currentState.get().cityId(), now);
                appendAudit(audit(
                        currentState.get().cityId(),
                        "CITY",
                        "capital_revoked",
                        null,
                        jsonPayload("reason", "new-capital-assigned"),
                        now
                ));
            }

            CityRecord city = readPort.findCityById(top.cityId())
                    .orElseThrow(() -> new IllegalStateException("capital-city-not-found"));
            long assignedAt = currentState.filter(value -> value.cityId().equals(city.cityId()))
                    .map(CapitalStateRecord::assignedAtEpochMilli)
                    .orElse(now);

            long bonusAmount = policy.capitaleMonthlyBonus();
            boolean bonusAlready = ledgerPort.existsLedgerEntry(city.cityId(), LedgerEntryType.CAPITAL_BONUS, bonusReason);
            long appliedBonus = 0L;
            if (bonusAmount > 0L && !bonusAlready && city.status() == CityStatus.ACTIVE && !city.frozen()) {
                appliedBonus = bonusAmount;
            }

            CityRecord updatedCity = new CityRecord(
                    city.cityId(),
                    city.name(),
                    city.tag(),
                    city.leaderUuid(),
                    city.tier(),
                    city.status(),
                    true,
                    city.frozen(),
                    city.treasuryBalance() + appliedBonus,
                    city.memberCount(),
                    city.maxMembers(),
                    city.createdAtEpochMilli(),
                    now,
                    city.revision()
            );
            expect(writePort.updateCity(updatedCity, city.revision()), "set-capital-city");
            nextState = new CapitalStateRecord(
                    settings.capital().slotKey(),
                    city.cityId(),
                    assignedAt,
                    now,
                    top.sourceVersion()
            );
            expect(economyStatePort.upsertCapitalState(nextState), "upsert-capital-state");

            if (changed) {
                appendAudit(audit(
                        city.cityId(),
                        "CITY",
                        "capital_assigned",
                        null,
                        jsonPayload("rank", String.valueOf(top.rank()), "score", String.valueOf(top.score())),
                        now
                ));
            }

            if (appliedBonus > 0L) {
                CityTreasuryLedgerRecord bonusEntry = new CityTreasuryLedgerRecord(
                        UUID.randomUUID(),
                        city.cityId(),
                        LedgerEntryType.CAPITAL_BONUS,
                        appliedBonus,
                        updatedCity.treasuryBalance(),
                        bonusReason,
                        null,
                        now
                );
                expect(ledgerPort.appendLedger(bonusEntry), "append-capital-bonus-ledger");
                appendAudit(audit(
                        city.cityId(),
                        "CITY",
                        "capital_bonus_paid",
                        null,
                        jsonPayload("month", month.toString(), "amount", String.valueOf(appliedBonus)),
                        now
                ));
            }
            return Optional.of(new CapitalState(
                    nextState.cityId(),
                    nextState.assignedAtEpochMilli(),
                    nextState.updatedAtEpochMilli(),
                    nextState.sourceRankingVersion()
            ));
        });
    }

    @Override
    public Optional<RankingSnapshot> citySnapshot(UUID cityId) {
        Objects.requireNonNull(cityId, "cityId");
        return economyStatePort.findRankingSnapshot(cityId)
                .map(value -> new RankingSnapshot(
                        value.cityId(),
                        value.rank(),
                        value.score(),
                        value.sourceVersion(),
                        value.fetchedAtEpochMilli()
                ));
    }

    @Override
    public Optional<RankingSnapshot> topKingdom() {
        return economyStatePort.listRankingSnapshots(1).stream()
                .findFirst()
                .map(value -> new RankingSnapshot(
                        value.cityId(),
                        value.rank(),
                        value.score(),
                        value.sourceVersion(),
                        value.fetchedAtEpochMilli()
                ));
    }

    @Override
    public List<RankingSnapshot> top(int limit) {
        return economyStatePort.listRankingSnapshots(Math.max(1, limit)).stream()
                .map(value -> new RankingSnapshot(
                        value.cityId(),
                        value.rank(),
                        value.score(),
                        value.sourceVersion(),
                        value.fetchedAtEpochMilli()
                ))
                .toList();
    }

    @Override
    public Snapshot snapshot() {
        Optional<MonthlyCycleStateRecord> state = economyStatePort.findMonthlyCycleState(MONTHLY_CYCLE_KEY);
        YearMonth lastProcessed = state.flatMap(this::parseMonthSafe).orElse(null);
        YearMonth current = YearMonth.now(zoneId);
        int backlog = 0;
        if (lastProcessed != null && lastProcessed.isBefore(current)) {
            backlog = (int) Math.max(0L, ChronoUnit.MONTHS.between(lastProcessed.plusMonths(1), current.plusMonths(1)));
        }
        return new Snapshot(
                settings.enabled(),
                state.map(MonthlyCycleStateRecord::lastProcessedMonth).orElse("-"),
                backlog,
                readPort.countActiveFreezeCasesByReason(FreezeReason.TAX_DEFAULT),
                economyStatePort.findCapitalState(settings.capital().slotKey()).map(CapitalStateRecord::cityId).orElse(null),
                ledgerPort.countLedgerByType(LedgerEntryType.CAPITAL_BONUS),
                state.map(MonthlyCycleStateRecord::lastRunAtEpochMilli).orElse(0L),
                state.map(MonthlyCycleStateRecord::lastStatus).orElse("-"),
                state.map(MonthlyCycleStateRecord::lastError).orElse("")
        );
    }

    private void scheduledTick() {
        if (!settings.enabled() || !started.get()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(zoneId);
        if (now.getDayOfMonth() != 1) {
            return;
        }
        if (now.toLocalTime().isBefore(runTime)) {
            return;
        }
        runToTarget(YearMonth.from(now), TriggerType.SCHEDULED);
    }

    private void runToTarget(YearMonth target, TriggerType triggerType) {
        if (!settings.enabled()) {
            return;
        }
        if (!cycleRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            YearMonth lastProcessed = economyStatePort.findMonthlyCycleState(MONTHLY_CYCLE_KEY)
                    .flatMap(this::parseMonthSafe)
                    .orElse(null);
            List<YearMonth> months = monthsToProcess(lastProcessed, target, settings.schedule().catchUpEnabled());
            if (months.isEmpty()) {
                return;
            }
            YearMonth lastSuccessful = lastProcessed;
            for (YearMonth month : months) {
                try {
                    MonthlyCycleReport report = processSingleMonth(month, triggerType);
                    syncCapitalAndBonus(month);
                    lastSuccessful = month;
                    recordCycleState(lastSuccessful, STATUS_SUCCESS, "");
                    logger.info(
                            "[CittaEXP][economy] monthly cycle completed month="
                                    + month
                                    + " processed=" + report.processedCities()
                                    + " ok=" + report.successfulCities()
                                    + " frozen=" + report.frozenCities()
                                    + " failed=" + report.failedCities()
                                    + " skipped=" + report.skippedCities()
                    );
                } catch (RuntimeException ex) {
                    YearMonth fallbackMonth = lastSuccessful == null ? month.minusMonths(1) : lastSuccessful;
                    recordCycleState(fallbackMonth, STATUS_FAILED, ex.getClass().getSimpleName() + ":" + safe(ex.getMessage()));
                    logger.log(Level.SEVERE, "[CittaEXP][economy] monthly cycle failed month=" + month, ex);
                    break;
                }
            }
        } finally {
            cycleRunning.set(false);
        }
    }

    private MonthlyCycleReport processSingleMonth(YearMonth month, TriggerType triggerType) {
        TaxPolicy policy = currentPolicy();
        List<CityRecord> cities = readPort.listCities();
        List<CityTreasuryLedgerEntry> ledgerEntries = new ArrayList<>();
        int processed = 0;
        int success = 0;
        int frozen = 0;
        int failed = 0;
        int skipped = 0;
        for (CityRecord city : cities) {
            processed++;
            try {
                TaxCityResult result = collectTax(city, policy, month, triggerType);
                if (result.skipped()) {
                    skipped++;
                    continue;
                }
                if (result.frozen()) {
                    frozen++;
                } else {
                    success++;
                }
                if (result.ledger() != null) {
                    ledgerEntries.add(result.ledger());
                }
            } catch (RuntimeException ex) {
                failed++;
                logger.log(
                        Level.WARNING,
                        "[CittaEXP][economy] tax collection failed city="
                                + city.cityId() + " month=" + month + " reason=" + safe(ex.getMessage()),
                        ex
                );
            }
        }
        appendAudit(audit(
                null,
                "ECONOMY",
                "monthly_cycle_completed",
                null,
                jsonPayload(
                        "month", month.toString(),
                        "trigger", triggerType.name(),
                        "processed", String.valueOf(processed),
                        "ok", String.valueOf(success),
                        "frozen", String.valueOf(frozen),
                        "failed", String.valueOf(failed),
                        "skipped", String.valueOf(skipped)
                ),
                System.currentTimeMillis()
        ));
        return new MonthlyCycleReport(month, processed, success, frozen, failed, skipped, List.copyOf(ledgerEntries));
    }

    private TaxCityResult collectTax(CityRecord city, TaxPolicy policy, YearMonth month, TriggerType triggerType) {
        if (shouldSkipForCreationMonth(city, month)) {
            return TaxCityResult.skippedResult();
        }
        String reason = taxReason(month);
        if (ledgerPort.existsLedgerEntry(city.cityId(), LedgerEntryType.TAX_CHARGE, reason)) {
            return TaxCityResult.skippedResult();
        }

        long now = System.currentTimeMillis();
        long total = taxForTier(city.tier(), policy);
        long treasuryContribution = Math.min(Math.max(0L, city.treasuryBalance()), total);
        long remaining = Math.max(0L, total - treasuryContribution);
        long leaderContribution = 0L;
        if (remaining > 0L && vaultEconomyPort.available()) {
            if (vaultEconomyPort.withdraw(city.leaderUuid(), remaining, "cittaexp-tax-" + month)) {
                leaderContribution = remaining;
            }
        }
        long unpaid = Math.max(0L, remaining - leaderContribution);
        long newTreasury = Math.max(0L, city.treasuryBalance() - treasuryContribution);
        boolean frozenByDebt = unpaid > 0L;
        CityStatus nextStatus = frozenByDebt ? CityStatus.FROZEN : city.status();
        boolean nextFrozen = city.frozen() || frozenByDebt;
        long leaderContributionFinal = leaderContribution;

        CityRecord updated = new CityRecord(
                city.cityId(),
                city.name(),
                city.tag(),
                city.leaderUuid(),
                city.tier(),
                nextStatus,
                city.capital(),
                nextFrozen,
                newTreasury,
                city.memberCount(),
                city.maxMembers(),
                city.createdAtEpochMilli(),
                now,
                city.revision()
        );
        CityTreasuryLedgerRecord ledgerRecord = new CityTreasuryLedgerRecord(
                UUID.randomUUID(),
                city.cityId(),
                LedgerEntryType.TAX_CHARGE,
                -treasuryContribution,
                newTreasury,
                reason,
                city.leaderUuid(),
                now
        );

        try {
            txPort.withTransaction(connection -> {
                expect(writePort.updateCity(updated, city.revision()), "tax-update-city");
                expect(ledgerPort.appendLedger(ledgerRecord), "tax-append-ledger");
                if (frozenByDebt && readPort.findActiveFreezeCase(city.cityId()).isEmpty()) {
                    FreezeCaseRecord freeze = new FreezeCaseRecord(
                            UUID.randomUUID(),
                            city.cityId(),
                            FreezeReason.TAX_DEFAULT,
                            "tax-insolvency:" + month + ":unpaid=" + unpaid,
                            true,
                            null,
                            null,
                            now,
                            0L
                    );
                    expect(writePort.upsertFreezeCase(freeze), "tax-freeze-upsert");
                    appendAudit(audit(
                            city.cityId(),
                            "CITY",
                            "city_frozen",
                            null,
                            jsonPayload("reason", "tax-insolvency", "month", month.toString(), "unpaid", String.valueOf(unpaid)),
                            now
                    ));
                }
                appendAudit(audit(
                        city.cityId(),
                        "CITY",
                        frozenByDebt ? "tax_partial_unpaid_freeze" : "tax_collected",
                        city.leaderUuid(),
                        jsonPayload(
                                "month", month.toString(),
                                "trigger", triggerType.name(),
                                "total", String.valueOf(total),
                                "treasury", String.valueOf(treasuryContribution),
                                "leader", String.valueOf(leaderContributionFinal),
                                "unpaid", String.valueOf(unpaid)
                        ),
                        now
                ));
                return null;
            });
        } catch (RuntimeException ex) {
            if (leaderContribution > 0L) {
                boolean refunded = vaultEconomyPort.deposit(
                        city.leaderUuid(),
                        leaderContribution,
                        "cittaexp-tax-refund-" + month
                );
                logger.warning(
                        "[CittaEXP][economy] leader tax compensation city="
                                + city.cityId() + " month=" + month + " refunded=" + refunded
                );
            }
            throw ex;
        }

        return new TaxCityResult(
                false,
                frozenByDebt,
                new CityTreasuryLedgerEntry(
                        ledgerRecord.entryId(),
                        ledgerRecord.cityId(),
                        ledgerRecord.entryType(),
                        ledgerRecord.amount(),
                        ledgerRecord.resultingBalance(),
                        ledgerRecord.reason(),
                        ledgerRecord.actorUuid(),
                        ledgerRecord.occurredAtEpochMilli()
                )
        );
    }

    private void clearCapitalFlagIfPresent(UUID cityId, long now) {
        CityRecord city = readPort.findCityById(cityId).orElse(null);
        if (city == null || !city.capital()) {
            return;
        }
        CityRecord updated = new CityRecord(
                city.cityId(),
                city.name(),
                city.tag(),
                city.leaderUuid(),
                city.tier(),
                city.status(),
                false,
                city.frozen(),
                city.treasuryBalance(),
                city.memberCount(),
                city.maxMembers(),
                city.createdAtEpochMilli(),
                now,
                city.revision()
        );
        expect(writePort.updateCity(updated, city.revision()), "clear-capital-flag");
    }

    private Optional<RankingPort.CityRankingEntry> asValidCapitalCandidate(RankingPort.CityRankingEntry entry) {
        CityRecord city = readPort.findCityById(entry.cityId()).orElse(null);
        if (city == null) {
            return Optional.empty();
        }
        if (city.tier() != CityTier.REGNO) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    private List<YearMonth> monthsToProcess(YearMonth lastProcessed, YearMonth target, boolean catchUp) {
        if (target == null) {
            return List.of();
        }
        if (lastProcessed == null) {
            return List.of(target);
        }
        if (!target.isAfter(lastProcessed)) {
            return List.of();
        }
        if (!catchUp) {
            return List.of(target);
        }
        List<YearMonth> months = new ArrayList<>();
        YearMonth cursor = lastProcessed.plusMonths(1);
        while (!cursor.isAfter(target)) {
            months.add(cursor);
            cursor = cursor.plusMonths(1);
        }
        return List.copyOf(months);
    }

    private void recordCycleState(YearMonth month, String status, String error) {
        long now = System.currentTimeMillis();
        MonthlyCycleStateRecord state = new MonthlyCycleStateRecord(
                MONTHLY_CYCLE_KEY,
                month.toString(),
                now,
                status,
                error == null ? "" : error
        );
        expect(economyStatePort.upsertMonthlyCycleState(state), "upsert-monthly-cycle-state");
    }

    private Optional<YearMonth> parseMonthSafe(MonthlyCycleStateRecord record) {
        try {
            return Optional.of(YearMonth.parse(record.lastProcessedMonth()));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private boolean shouldSkipForCreationMonth(CityRecord city, YearMonth month) {
        if (!settings.schedule().skipCreationMonth()) {
            return false;
        }
        LocalDate created = Instant.ofEpochMilli(city.createdAtEpochMilli()).atZone(zoneId).toLocalDate();
        return YearMonth.from(created).equals(month);
    }

    private long taxForTier(CityTier tier, TaxPolicy policy) {
        return switch (tier) {
            case BORGO -> policy.borgoMonthlyCost();
            case VILLAGGIO -> policy.villaggioMonthlyCost();
            case REGNO -> policy.regnoMonthlyCost() + policy.regnoShopMonthlyExtraCost();
        };
    }

    private String taxReason(YearMonth month) {
        return "tax:" + month;
    }

    private String bonusReason(YearMonth month) {
        return "capital_bonus:" + month;
    }

    private TaxPolicy defaultPolicy() {
        return new TaxPolicy(
                settings.tax().borgoMonthlyCost(),
                settings.tax().villaggioMonthlyCost(),
                settings.tax().regnoMonthlyCost(),
                settings.tax().regnoShopMonthlyExtraCost(),
                settings.capital().monthlyBonus(),
                settings.tax().dueDayOfMonth(),
                settings.schedule().timezone()
        );
    }

    private TaxPolicy toTaxPolicy(TaxPolicyRecord record) {
        return new TaxPolicy(
                record.borgoMonthlyCost(),
                record.villaggioMonthlyCost(),
                record.regnoMonthlyCost(),
                record.regnoShopMonthlyExtra(),
                record.capitaleMonthlyBonus(),
                record.dueDayOfMonth(),
                record.timezoneId()
        );
    }

    private void seedDefaultPolicyIfMissing() {
        if (taxPolicyPort.findTaxPolicy(DEFAULT_POLICY_ID).isPresent()) {
            return;
        }
        TaxPolicy policy = defaultPolicy();
        expect(taxPolicyPort.upsertTaxPolicy(new TaxPolicyRecord(
                DEFAULT_POLICY_ID,
                policy.borgoMonthlyCost(),
                policy.villaggioMonthlyCost(),
                policy.regnoMonthlyCost(),
                policy.regnoShopMonthlyExtraCost(),
                policy.capitaleMonthlyBonus(),
                policy.dueDayOfMonth(),
                policy.timezoneId(),
                System.currentTimeMillis()
        )), "seed-tax-policy");
    }

    private void appendAudit(AuditEvent event) {
        AuditEventRecord record = new AuditEventRecord(
                event.eventId(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.actorUuid(),
                event.payloadJson(),
                event.occurredAtEpochMilli()
        );
        expect(writePort.appendAuditEvent(record), "append-audit");
    }

    private static AuditEvent audit(
            UUID aggregateId,
            String aggregateType,
            String eventType,
            UUID actor,
            String payloadJson,
            long now
    ) {
        return new AuditEvent(
                UUID.randomUUID(),
                aggregateType,
                aggregateId == null ? "economy" : aggregateId.toString(),
                eventType,
                actor,
                payloadJson,
                now
        );
    }

    private static String jsonPayload(String... entries) {
        JsonObject root = new JsonObject();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            root.addProperty(entries[i], entries[i + 1]);
        }
        return GSON.toJson(root);
    }

    private static void expect(PersistenceWriteOutcome outcome, String operation) {
        if (outcome == null || !outcome.success()) {
            throw new IllegalStateException(operation + ":" + (outcome == null ? "null-outcome" : outcome.message()));
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record TaxCityResult(
            boolean skipped,
            boolean frozen,
            CityTreasuryLedgerEntry ledger
    ) {

        static TaxCityResult skippedResult() {
            return new TaxCityResult(true, false, null);
        }
    }
}
