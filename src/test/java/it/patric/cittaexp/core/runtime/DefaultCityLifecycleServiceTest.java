package it.patric.cittaexp.core.runtime;

import it.patric.cittaexp.core.model.CityStatus;
import it.patric.cittaexp.core.model.CityTier;
import it.patric.cittaexp.core.model.InvitationStatus;
import it.patric.cittaexp.core.port.HuskClaimsPort;
import it.patric.cittaexp.persistence.domain.CityInvitationRecord;
import it.patric.cittaexp.persistence.domain.CityMemberRecord;
import it.patric.cittaexp.persistence.domain.CityRecord;
import it.patric.cittaexp.persistence.domain.CityViceRecord;
import it.patric.cittaexp.persistence.domain.ClaimBindingRecord;
import it.patric.cittaexp.persistence.domain.PersistenceWriteOutcome;
import it.patric.cittaexp.persistence.port.CityReadPort;
import it.patric.cittaexp.persistence.port.CityTxPort;
import it.patric.cittaexp.persistence.port.CityWritePort;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultCityLifecycleServiceTest {

    @Test
    void createCityRejectsPlayerAlreadyInCity() {
        CityReadPort readPort = mock(CityReadPort.class);
        CityWritePort writePort = mock(CityWritePort.class);
        HuskClaimsPort claimsPort = mock(HuskClaimsPort.class);

        UUID creator = UUID.randomUUID();
        when(readPort.findActiveMember(creator)).thenReturn(Optional.of(new CityMemberRecord(
                UUID.randomUUID(),
                creator,
                "leader",
                System.currentTimeMillis(),
                true
        )));

        DefaultCityLifecycleService service = new DefaultCityLifecycleService(
                readPort,
                writePort,
                passthroughTx(),
                claimsPort,
                Logger.getLogger("test")
        );

        assertThrows(IllegalStateException.class, () -> service.createCity(
                new it.patric.cittaexp.core.service.CityLifecycleService.CreateCityCommand(
                        creator,
                        "Aurora",
                        "AUR",
                        "world",
                        0,
                        0,
                        "",
                        ""
                )
        ));
    }

    @Test
    void inviteBlockedWhenCityIsFrozen() {
        CityReadPort readPort = mock(CityReadPort.class);
        CityWritePort writePort = mock(CityWritePort.class);
        HuskClaimsPort claimsPort = mock(HuskClaimsPort.class);

        UUID cityId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        CityRecord frozenCity = new CityRecord(
                cityId,
                "Aurora",
                "AUR",
                actor,
                CityTier.BORGO,
                CityStatus.FROZEN,
                false,
                true,
                0L,
                1,
                10,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                0
        );
        when(readPort.findCityById(cityId)).thenReturn(Optional.of(frozenCity));

        DefaultCityLifecycleService service = new DefaultCityLifecycleService(
                readPort,
                writePort,
                passthroughTx(),
                claimsPort,
                Logger.getLogger("test")
        );

        assertThrows(IllegalStateException.class, () -> service.invite(cityId, actor, UUID.randomUUID()));
    }

    @Test
    void declineInviteUpdatesStatusWithoutThrowing() {
        CityReadPort readPort = mock(CityReadPort.class);
        CityWritePort writePort = mock(CityWritePort.class);
        HuskClaimsPort claimsPort = mock(HuskClaimsPort.class);

        UUID invitationId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        CityInvitationRecord invitation = new CityInvitationRecord(
                invitationId,
                cityId,
                playerUuid,
                UUID.randomUUID(),
                InvitationStatus.PENDING,
                System.currentTimeMillis(),
                System.currentTimeMillis() + 1000,
                System.currentTimeMillis()
        );

        when(readPort.findInvitation(invitationId)).thenReturn(Optional.of(invitation));
        when(writePort.updateInvitationStatus(any(), any(), anyLong())).thenReturn(PersistenceWriteOutcome.success("ok"));
        when(writePort.appendAuditEvent(any())).thenReturn(PersistenceWriteOutcome.success("ok"));

        DefaultCityLifecycleService service = new DefaultCityLifecycleService(
                readPort,
                writePort,
                passthroughTx(),
                claimsPort,
                Logger.getLogger("test")
        );

        assertDoesNotThrow(() -> service.declineInvite(invitationId, playerUuid, "no"));
    }

    @Test
    void leaveCityDeletesAggregateWhenLastMemberLeaves() {
        CityReadPort readPort = mock(CityReadPort.class);
        CityWritePort writePort = mock(CityWritePort.class);
        HuskClaimsPort claimsPort = mock(HuskClaimsPort.class);

        UUID cityId = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        long now = System.currentTimeMillis();
        CityRecord city = new CityRecord(
                cityId,
                "Aurora",
                "AUR",
                leader,
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
        );
        ClaimBindingRecord claim = new ClaimBindingRecord(cityId, "world", 0, 0, 99, 99, 10000, now, now);

        when(readPort.findCityById(cityId)).thenReturn(Optional.of(city));
        when(readPort.findMember(cityId, leader)).thenReturn(Optional.of(new CityMemberRecord(
                cityId,
                leader,
                "leader",
                now,
                true
        )));
        when(readPort.findClaimBinding(cityId)).thenReturn(Optional.of(claim));
        when(claimsPort.deleteClaimAtAsync(eq("world"), eq(49), eq(49))).thenReturn(CompletableFuture.completedFuture(true));
        when(writePort.hardDeleteCityAggregate(cityId)).thenReturn(PersistenceWriteOutcome.success("ok"));
        when(writePort.appendAuditEvent(any())).thenReturn(PersistenceWriteOutcome.success("ok"));

        DefaultCityLifecycleService service = new DefaultCityLifecycleService(
                readPort,
                writePort,
                passthroughTx(),
                claimsPort,
                Logger.getLogger("test")
        );

        assertDoesNotThrow(() -> service.leaveCity(cityId, leader, "leave"));
        verify(writePort).hardDeleteCityAggregate(cityId);
    }

    @Test
    void leaderLeavePromotesViceAutomatically() {
        CityReadPort readPort = mock(CityReadPort.class);
        CityWritePort writePort = mock(CityWritePort.class);
        HuskClaimsPort claimsPort = mock(HuskClaimsPort.class);

        UUID cityId = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        UUID vice = UUID.randomUUID();
        long now = System.currentTimeMillis();
        CityRecord city = new CityRecord(
                cityId,
                "Aurora",
                "AUR",
                leader,
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
        );
        ClaimBindingRecord claim = new ClaimBindingRecord(cityId, "world", 0, 0, 99, 99, 10000, now, now);

        when(readPort.findCityById(cityId)).thenReturn(Optional.of(city));
        when(readPort.findMember(cityId, leader)).thenReturn(Optional.of(new CityMemberRecord(cityId, leader, "leader", now, true)));
        when(readPort.findMember(cityId, vice)).thenReturn(Optional.of(new CityMemberRecord(cityId, vice, "membro", now, true)));
        when(readPort.findCityVice(cityId)).thenReturn(Optional.of(new CityViceRecord(cityId, vice, now)));
        when(readPort.findClaimBinding(cityId)).thenReturn(Optional.of(claim));
        when(claimsPort.syncClaimPermissionsAsync(any(), anyInt(), anyInt(), eq(vice), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(true));
        when(claimsPort.clearClaimPermissionsAsync(any(), anyInt(), anyInt(), eq(leader)))
                .thenReturn(CompletableFuture.completedFuture(true));
        when(writePort.deleteMember(any(), any())).thenReturn(PersistenceWriteOutcome.success("ok"));
        when(writePort.deleteClaimPermissions(any(), any())).thenReturn(PersistenceWriteOutcome.success("ok"));
        when(writePort.upsertMember(any())).thenReturn(PersistenceWriteOutcome.success("ok"));
        when(writePort.upsertClaimPermissions(any())).thenReturn(PersistenceWriteOutcome.success("ok"));
        when(writePort.clearCityVice(any(), anyLong())).thenReturn(PersistenceWriteOutcome.success("ok"));
        when(writePort.updateCity(any(), anyInt())).thenReturn(PersistenceWriteOutcome.success("ok"));
        when(writePort.appendAuditEvent(any())).thenReturn(PersistenceWriteOutcome.success("ok"));

        DefaultCityLifecycleService service = new DefaultCityLifecycleService(
                readPort,
                writePort,
                passthroughTx(),
                claimsPort,
                Logger.getLogger("test")
        );

        assertDoesNotThrow(() -> service.leaveCity(cityId, leader, "leave"));
        verify(writePort).clearCityVice(eq(cityId), anyLong());
        verify(writePort).upsertMember(any());
    }

    @Test
    void viceCannotKickLeader() {
        CityReadPort readPort = mock(CityReadPort.class);
        CityWritePort writePort = mock(CityWritePort.class);
        HuskClaimsPort claimsPort = mock(HuskClaimsPort.class);

        UUID cityId = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        UUID vice = UUID.randomUUID();
        long now = System.currentTimeMillis();
        CityRecord city = new CityRecord(
                cityId,
                "Aurora",
                "AUR",
                leader,
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
        );

        when(readPort.findCityById(cityId)).thenReturn(Optional.of(city));
        when(readPort.findCityVice(cityId)).thenReturn(Optional.of(new CityViceRecord(cityId, vice, now)));
        when(readPort.findMember(cityId, vice)).thenReturn(Optional.of(new CityMemberRecord(cityId, vice, "membro", now, true)));
        when(readPort.findMember(cityId, leader)).thenReturn(Optional.of(new CityMemberRecord(cityId, leader, "leader", now, true)));

        DefaultCityLifecycleService service = new DefaultCityLifecycleService(
                readPort,
                writePort,
                passthroughTx(),
                claimsPort,
                Logger.getLogger("test")
        );

        assertThrows(IllegalStateException.class, () -> service.kickMember(cityId, vice, leader, "no"));
    }

    private static CityTxPort passthroughTx() {
        return new CityTxPort() {
            @Override
            public <T> T withTransaction(SqlTransaction<T> transaction) {
                try {
                    return transaction.execute(null);
                } catch (SQLException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        };
    }
}
