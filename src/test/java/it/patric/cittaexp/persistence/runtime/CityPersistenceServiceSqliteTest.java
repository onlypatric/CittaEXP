package it.patric.cittaexp.persistence.runtime;

import it.patric.cittaexp.core.model.CityStatus;
import it.patric.cittaexp.core.model.CityTier;
import it.patric.cittaexp.core.model.LedgerEntryType;
import it.patric.cittaexp.persistence.config.PersistenceSettings;
import it.patric.cittaexp.persistence.domain.CapitalStateRecord;
import it.patric.cittaexp.persistence.domain.CityInvitationRecord;
import it.patric.cittaexp.persistence.domain.CityMemberRecord;
import it.patric.cittaexp.persistence.domain.CityRecord;
import it.patric.cittaexp.persistence.domain.CityRoleRecord;
import it.patric.cittaexp.persistence.domain.CityTreasuryLedgerRecord;
import it.patric.cittaexp.persistence.domain.CityViceRecord;
import it.patric.cittaexp.persistence.domain.ClaimBindingRecord;
import it.patric.cittaexp.persistence.domain.FreezeCaseRecord;
import it.patric.cittaexp.persistence.domain.MemberClaimPermissionRecord;
import it.patric.cittaexp.persistence.domain.MonthlyCycleStateRecord;
import it.patric.cittaexp.persistence.domain.PersistenceWriteOutcome;
import it.patric.cittaexp.persistence.domain.RankingSnapshotRecord;
import it.patric.cittaexp.persistence.domain.TaxPolicyRecord;
import it.patric.cittaexp.core.model.FreezeReason;
import it.patric.cittaexp.core.model.InvitationStatus;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CityPersistenceServiceSqliteTest {

    @TempDir
    Path tempDir;

    private CityPersistenceService service;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());

        PersistenceSettings settings = new PersistenceSettings(
                PersistenceSettings.PersistenceMode.SQLITE,
                true,
                new PersistenceSettings.MysqlSettings("127.0.0.1", 3306, "cittaexp", "root", "", ""),
                new PersistenceSettings.SqliteSettings("data/test.sqlite"),
                new PersistenceSettings.ReplaySettings(50, 5)
        );

        PersistenceModeManager modeManager = new PersistenceModeManager(settings, Logger.getLogger("test"));
        modeManager.initialize();

        SchemaInstaller schemaInstaller = new SchemaInstaller(plugin, settings, modeManager);
        service = new CityPersistenceService(plugin, settings, modeManager, schemaInstaller, Logger.getLogger("test"));
        service.ensureSchema();
    }

    @Test
    void createAndReadCityWorks() {
        UUID cityId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        long now = System.currentTimeMillis();

        PersistenceWriteOutcome created = service.createCity(new CityRecord(
                cityId,
                "Aurora",
                "AUR",
                leaderId,
                CityTier.BORGO,
                CityStatus.ACTIVE,
                false,
                false,
                1200L,
                1,
                10,
                now,
                now,
                0
        ));

        assertTrue(created.success());

        Optional<CityRecord> loaded = service.findCityById(cityId);
        assertTrue(loaded.isPresent());
        assertEquals("Aurora", loaded.get().name());
        assertEquals("AUR", loaded.get().tag());
    }

    @Test
    void optimisticLockingReturnsConflictOnStaleRevision() {
        UUID cityId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        long now = System.currentTimeMillis();

        service.createCity(new CityRecord(
                cityId,
                "Brina",
                "BRI",
                leaderId,
                CityTier.BORGO,
                CityStatus.ACTIVE,
                false,
                false,
                2000L,
                1,
                10,
                now,
                now,
                0
        ));

        PersistenceWriteOutcome stale = service.updateCity(new CityRecord(
                cityId,
                "Brina Nuova",
                "BRI",
                leaderId,
                CityTier.BORGO,
                CityStatus.ACTIVE,
                false,
                false,
                2500L,
                1,
                10,
                now,
                now + 1000,
                0
        ), 9);

        assertFalse(stale.success());
        assertTrue(stale.conflict());
    }

    @Test
    void fallbackWritesPopulateOutboxQueue() {
        UUID cityId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        long now = System.currentTimeMillis();

        service.createCity(new CityRecord(
                cityId,
                "Cenere",
                "CEN",
                leaderId,
                CityTier.BORGO,
                CityStatus.ACTIVE,
                false,
                false,
                1000L,
                1,
                10,
                now,
                now,
                0
        ));
        service.upsertMember(new CityMemberRecord(cityId, playerId, "membro", now, true));
        service.upsertRole(new CityRoleRecord(cityId, "membro", "Membro", 1, "{\"invite\":false}"));

        assertTrue(service.pendingCount() >= 3L);
    }

    @Test
    void enqueueInsideTransactionDoesNotLockSqlite() {
        UUID cityId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        long now = System.currentTimeMillis();

        assertDoesNotThrow(() ->
                service.withTransaction(connection -> {
                    PersistenceWriteOutcome created = service.createCity(new CityRecord(
                            cityId,
                            "TransCity",
                            "TRN",
                            leaderId,
                            CityTier.BORGO,
                            CityStatus.ACTIVE,
                            false,
                            false,
                            0L,
                            1,
                            10,
                            now,
                            now,
                            0
                    ));
                    assertTrue(created.success());
                    return null;
                })
        );

        assertTrue(service.findCityById(cityId).isPresent());
        assertTrue(service.pendingCount() >= 1L);
    }

    @Test
    void runtimeFailureInsideTransactionRollsBackCityAndOutbox() {
        UUID cityId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        long now = System.currentTimeMillis();

        assertThrows(IllegalStateException.class, () ->
                service.withTransaction(connection -> {
                    PersistenceWriteOutcome created = service.createCity(new CityRecord(
                            cityId,
                            "RollbackCity",
                            "RBC",
                            leaderId,
                            CityTier.BORGO,
                            CityStatus.ACTIVE,
                            false,
                            false,
                            0L,
                            1,
                            10,
                            now,
                            now,
                            0
                    ));
                    assertTrue(created.success());
                    throw new IllegalStateException("forced-runtime-failure");
                })
        );

        assertTrue(service.findCityById(cityId).isEmpty());
        assertEquals(0L, service.pendingCount());
    }

    @Test
    void invitationAndFreezeReadWriteWorks() {
        long now = System.currentTimeMillis();
        UUID cityId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        service.createCity(new CityRecord(
                cityId,
                "Fortezza",
                "FOR",
                leaderId,
                CityTier.BORGO,
                CityStatus.ACTIVE,
                false,
                false,
                0L,
                1,
                10,
                now,
                now,
                0
        ));

        CityInvitationRecord invitation = new CityInvitationRecord(
                UUID.randomUUID(),
                cityId,
                UUID.randomUUID(),
                leaderId,
                InvitationStatus.PENDING,
                now,
                now + 1000L,
                now
        );
        FreezeCaseRecord freezeCase = new FreezeCaseRecord(
                UUID.randomUUID(),
                cityId,
                FreezeReason.STAFF_MANUAL,
                "manual",
                true,
                leaderId,
                null,
                now,
                0L
        );

        assertTrue(service.upsertInvitation(invitation).success());
        assertTrue(service.upsertFreezeCase(freezeCase).success());
        assertTrue(service.findInvitation(invitation.invitationId()).isPresent());
        assertTrue(service.findActiveFreezeCase(cityId).isPresent());
    }

    @Test
    void hardDeleteCityAggregateRemovesCityAndClaimBinding() {
        long now = System.currentTimeMillis();
        UUID cityId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        service.createCity(new CityRecord(
                cityId,
                "TestDelete",
                "TST",
                leaderId,
                CityTier.BORGO,
                CityStatus.ACTIVE,
                false,
                false,
                0L,
                1,
                10,
                now,
                now,
                0
        ));
        service.upsertClaimBinding(new ClaimBindingRecord(
                cityId,
                "world",
                0,
                0,
                99,
                99,
                10000,
                now,
                now
        ));

        PersistenceWriteOutcome deleted = service.hardDeleteCityAggregate(cityId);
        assertTrue(deleted.success());
        assertTrue(service.findCityById(cityId).isEmpty());
        assertTrue(service.findClaimBinding(cityId).isEmpty());
    }

    @Test
    void governanceViceAndClaimPermissionsReadWriteWorks() {
        long now = System.currentTimeMillis();
        UUID cityId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        UUID viceId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        service.createCity(new CityRecord(
                cityId,
                "GovernanceVille",
                "GOV",
                leaderId,
                CityTier.BORGO,
                CityStatus.ACTIVE,
                false,
                false,
                0L,
                2,
                10,
                now,
                now,
                0
        ));

        assertTrue(service.upsertCityVice(new CityViceRecord(cityId, viceId, now)).success());
        assertTrue(service.upsertClaimPermissions(new MemberClaimPermissionRecord(
                cityId,
                memberId,
                true,
                true,
                false,
                leaderId,
                now
        )).success());

        assertTrue(service.findCityVice(cityId).isPresent());
        assertEquals(viceId, service.findCityVice(cityId).orElseThrow().viceUuid());
        assertTrue(service.findClaimPermissions(cityId, memberId).isPresent());
        assertTrue(service.findClaimPermissions(cityId, memberId).orElseThrow().container());

        assertTrue(service.clearCityVice(cityId, now + 1000L).success());
        assertTrue(service.deleteClaimPermissions(cityId, memberId).success());
        assertTrue(service.findCityVice(cityId).isPresent());
        assertTrue(service.findCityVice(cityId).orElseThrow().viceUuid() == null);
        assertTrue(service.findClaimPermissions(cityId, memberId).isEmpty());
    }

    @Test
    void economyPersistenceReadWriteWorks() {
        long now = System.currentTimeMillis();
        UUID cityId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();

        assertTrue(service.createCity(new CityRecord(
                cityId,
                "Economia",
                "ECO",
                leaderId,
                CityTier.REGNO,
                CityStatus.ACTIVE,
                false,
                false,
                100L,
                1,
                50,
                now,
                now,
                0
        )).success());

        assertTrue(service.upsertTaxPolicy(new TaxPolicyRecord(
                "default",
                100L,
                200L,
                300L,
                50L,
                25L,
                1,
                "Europe/Rome",
                now
        )).success());
        assertTrue(service.findTaxPolicy("default").isPresent());

        assertTrue(service.appendLedger(new CityTreasuryLedgerRecord(
                UUID.randomUUID(),
                cityId,
                LedgerEntryType.TAX_CHARGE,
                -100L,
                0L,
                "tax:2026-03",
                leaderId,
                now
        )).success());
        assertTrue(service.existsLedgerEntry(cityId, LedgerEntryType.TAX_CHARGE, "tax:2026-03"));
        assertEquals(1L, service.countLedgerByType(LedgerEntryType.TAX_CHARGE));

        assertTrue(service.upsertCapitalState(new CapitalStateRecord(
                "PRIMARY",
                cityId,
                now,
                now,
                "v1"
        )).success());
        assertTrue(service.findCapitalState("PRIMARY").isPresent());
        assertTrue(service.clearCapitalState("PRIMARY").success());
        assertTrue(service.findCapitalState("PRIMARY").isEmpty());

        assertTrue(service.upsertRankingSnapshot(new RankingSnapshotRecord(
                cityId,
                1,
                999L,
                "v1",
                now
        )).success());
        assertTrue(service.findRankingSnapshot(cityId).isPresent());
        assertFalse(service.listRankingSnapshots(5).isEmpty());
        assertTrue(service.deleteRankingSnapshot(cityId).success());
        assertTrue(service.findRankingSnapshot(cityId).isEmpty());

        assertTrue(service.upsertMonthlyCycleState(new MonthlyCycleStateRecord(
                "monthly-economy",
                "2026-03",
                now,
                "SUCCESS",
                ""
        )).success());
        assertTrue(service.findMonthlyCycleState("monthly-economy").isPresent());
    }
}
