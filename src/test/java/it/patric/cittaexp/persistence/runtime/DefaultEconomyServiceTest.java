package it.patric.cittaexp.persistence.runtime;

import dev.patric.commonlib.api.CommonScheduler;
import dev.patric.commonlib.api.TaskHandle;
import it.patric.cittaexp.core.model.CityStatus;
import it.patric.cittaexp.core.model.CityTier;
import it.patric.cittaexp.core.model.LedgerEntryType;
import it.patric.cittaexp.core.port.RankingPort;
import it.patric.cittaexp.core.port.VaultEconomyPort;
import it.patric.cittaexp.core.service.TaxService;
import it.patric.cittaexp.economy.config.EconomySettings;
import it.patric.cittaexp.economy.runtime.DefaultEconomyService;
import it.patric.cittaexp.persistence.config.PersistenceSettings;
import it.patric.cittaexp.persistence.domain.CityRecord;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultEconomyServiceTest {

    @TempDir
    Path tempDir;

    private CityPersistenceService persistence;
    private StubVaultEconomy vault;
    private StubRanking ranking;
    private DefaultEconomyService service;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());

        PersistenceSettings persistenceSettings = new PersistenceSettings(
                PersistenceSettings.PersistenceMode.SQLITE,
                true,
                new PersistenceSettings.MysqlSettings("127.0.0.1", 3306, "cittaexp", "root", "", ""),
                new PersistenceSettings.SqliteSettings("data/test.sqlite"),
                new PersistenceSettings.ReplaySettings(50, 5)
        );
        PersistenceModeManager modeManager = new PersistenceModeManager(persistenceSettings, Logger.getLogger("test"));
        modeManager.initialize();
        SchemaInstaller schemaInstaller = new SchemaInstaller(plugin, persistenceSettings, modeManager);
        persistence = new CityPersistenceService(plugin, persistenceSettings, modeManager, schemaInstaller, Logger.getLogger("test"));
        persistence.ensureSchema();

        vault = new StubVaultEconomy();
        ranking = new StubRanking();
        EconomySettings economySettings = new EconomySettings(
                true,
                new EconomySettings.ScheduleSettings("Europe/Rome", LocalTime.of(0, 5), true, true, 30),
                new EconomySettings.TaxSettings(1000L, 2500L, 5000L, 0L, 1),
                new EconomySettings.CapitalSettings(1500L, "PRIMARY")
        );
        service = new DefaultEconomyService(
                persistence,
                persistence,
                persistence,
                persistence,
                persistence,
                persistence,
                vault,
                ranking,
                new NoopScheduler(),
                economySettings,
                Logger.getLogger("test")
        );
    }

    @Test
    void runMonthlyCycleUsesTreasuryThenLeaderWallet() {
        UUID cityId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        YearMonth targetMonth = YearMonth.now(ZoneId.of("Europe/Rome")).minusMonths(1);
        createCity(cityId, leaderId, CityTier.BORGO, CityStatus.ACTIVE, false, 200L, targetMonth.minusMonths(1));

        vault.withdrawResult = true;

        TaxService.MonthlyCycleReport report = service.runMonthlyCycle(targetMonth, TaxService.TriggerType.MANUAL);

        CityRecord updated = persistence.findCityById(cityId).orElseThrow();
        assertEquals(0L, updated.treasuryBalance());
        assertEquals(CityStatus.ACTIVE, updated.status());
        assertTrue(persistence.existsLedgerEntry(cityId, LedgerEntryType.TAX_CHARGE, "tax:" + targetMonth));
        assertEquals(800L, vault.lastWithdrawAmount.get());
        assertEquals(1, report.successfulCities());
        assertEquals(0, report.frozenCities());
    }

    @Test
    void runMonthlyCycleFreezesWhenResidualRemainsUnpaid() {
        UUID cityId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        YearMonth targetMonth = YearMonth.now(ZoneId.of("Europe/Rome")).minusMonths(1);
        createCity(cityId, leaderId, CityTier.BORGO, CityStatus.ACTIVE, false, 100L, targetMonth.minusMonths(1));

        vault.withdrawResult = false;

        TaxService.MonthlyCycleReport report = service.runMonthlyCycle(targetMonth, TaxService.TriggerType.MANUAL);

        CityRecord updated = persistence.findCityById(cityId).orElseThrow();
        assertEquals(0L, updated.treasuryBalance());
        assertEquals(CityStatus.FROZEN, updated.status());
        assertTrue(updated.frozen());
        assertEquals(1L, persistence.countActiveFreezeCasesByReason(it.patric.cittaexp.core.model.FreezeReason.TAX_DEFAULT));
        assertEquals(1, report.frozenCities());
    }

    @Test
    void syncCapitalAndBonusAssignsAndRevokesCapital() {
        UUID cityId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        YearMonth month = YearMonth.now(ZoneId.of("Europe/Rome")).minusMonths(1);
        createCity(cityId, leaderId, CityTier.REGNO, CityStatus.ACTIVE, false, 0L, month.minusMonths(1));

        ranking.entry = Optional.of(new RankingPort.CityRankingEntry(
                cityId,
                1,
                5000L,
                "v1",
                System.currentTimeMillis()
        ));

        service.syncCapitalAndBonus(month);

        CityRecord assigned = persistence.findCityById(cityId).orElseThrow();
        assertTrue(assigned.capital());
        assertEquals(1500L, assigned.treasuryBalance());
        assertTrue(persistence.existsLedgerEntry(cityId, LedgerEntryType.CAPITAL_BONUS, "capital_bonus:" + month));
        assertTrue(persistence.findCapitalState("PRIMARY").isPresent());

        ranking.entry = Optional.empty();
        service.syncCapitalAndBonus(month.plusMonths(1));

        CityRecord revoked = persistence.findCityById(cityId).orElseThrow();
        assertFalse(revoked.capital());
        assertTrue(persistence.findCapitalState("PRIMARY").isEmpty());
    }

    private void createCity(
            UUID cityId,
            UUID leaderId,
            CityTier tier,
            CityStatus status,
            boolean frozen,
            long treasury,
            YearMonth createdMonth
    ) {
        long createdAt = LocalDate.of(createdMonth.getYear(), createdMonth.getMonth(), 1)
                .atStartOfDay(ZoneId.of("Europe/Rome"))
                .toInstant()
                .toEpochMilli();
        assertTrue(persistence.createCity(new CityRecord(
                cityId,
                "City" + cityId.toString().substring(0, 5),
                "C" + cityId.toString().substring(0, 3),
                leaderId,
                tier,
                status,
                false,
                frozen,
                treasury,
                1,
                50,
                createdAt,
                createdAt,
                0
        )).success());
    }

    private static final class StubVaultEconomy implements VaultEconomyPort {

        private volatile boolean withdrawResult = true;
        private final AtomicLong lastWithdrawAmount = new AtomicLong();

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public long balance(UUID playerUuid) {
            return 1_000_000L;
        }

        @Override
        public boolean withdraw(UUID playerUuid, long amount, String reason) {
            lastWithdrawAmount.set(amount);
            return withdrawResult;
        }

        @Override
        public boolean deposit(UUID playerUuid, long amount, String reason) {
            return true;
        }
    }

    private static final class StubRanking implements RankingPort {

        private volatile Optional<CityRankingEntry> entry = Optional.empty();

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public Optional<CityRankingEntry> findByCityId(UUID cityId) {
            return entry.filter(value -> value.cityId().equals(cityId));
        }

        @Override
        public Optional<CityRankingEntry> topKingdom() {
            return entry;
        }
    }

    private static final class NoopScheduler implements CommonScheduler {

        @Override
        public TaskHandle runSync(Runnable task) {
            task.run();
            return NoopTaskHandle.INSTANCE;
        }

        @Override
        public TaskHandle runSyncLater(long delayTicks, Runnable task) {
            task.run();
            return NoopTaskHandle.INSTANCE;
        }

        @Override
        public TaskHandle runSyncRepeating(long delayTicks, long periodTicks, Runnable task) {
            return NoopTaskHandle.INSTANCE;
        }

        @Override
        public TaskHandle runAsync(Runnable task) {
            task.run();
            return NoopTaskHandle.INSTANCE;
        }

        @Override
        public <T> CompletableFuture<T> supplyAsync(java.util.function.Supplier<T> supplier) {
            return CompletableFuture.completedFuture(supplier.get());
        }

        @Override
        public boolean isPrimaryThread() {
            return true;
        }

        @Override
        public void requirePrimaryThread(String operationName) {
            // noop
        }
    }

    private enum NoopTaskHandle implements TaskHandle {
        INSTANCE;

        @Override
        public void cancel() {
            // noop
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }
}
