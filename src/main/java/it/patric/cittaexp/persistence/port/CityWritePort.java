package it.patric.cittaexp.persistence.port;

import it.patric.cittaexp.persistence.domain.CityMemberRecord;
import it.patric.cittaexp.persistence.domain.CityRecord;
import it.patric.cittaexp.persistence.domain.CityRoleRecord;
import it.patric.cittaexp.persistence.domain.CityInvitationRecord;
import it.patric.cittaexp.persistence.domain.CityViceRecord;
import it.patric.cittaexp.persistence.domain.CityTreasuryLedgerRecord;
import it.patric.cittaexp.persistence.domain.CapitalStateRecord;
import it.patric.cittaexp.persistence.domain.ClaimBindingRecord;
import it.patric.cittaexp.persistence.domain.FreezeCaseRecord;
import it.patric.cittaexp.persistence.domain.JoinRequestRecord;
import it.patric.cittaexp.persistence.domain.MemberClaimPermissionRecord;
import it.patric.cittaexp.persistence.domain.MonthlyCycleStateRecord;
import it.patric.cittaexp.persistence.domain.RankingSnapshotRecord;
import it.patric.cittaexp.persistence.domain.TaxPolicyRecord;
import it.patric.cittaexp.persistence.domain.AuditEventRecord;
import it.patric.cittaexp.core.model.InvitationStatus;
import it.patric.cittaexp.core.model.JoinRequestStatus;
import it.patric.cittaexp.persistence.domain.PersistenceWriteOutcome;
import java.util.UUID;

public interface CityWritePort {

    PersistenceWriteOutcome createCity(CityRecord city);

    PersistenceWriteOutcome updateCity(CityRecord city, int expectedRevision);

    PersistenceWriteOutcome upsertMember(CityMemberRecord member);

    PersistenceWriteOutcome deleteMember(UUID cityId, UUID playerUuid);

    PersistenceWriteOutcome upsertRole(CityRoleRecord role);

    PersistenceWriteOutcome deleteRole(UUID cityId, String roleKey);

    PersistenceWriteOutcome upsertInvitation(CityInvitationRecord invitation);

    PersistenceWriteOutcome updateInvitationStatus(UUID invitationId, InvitationStatus status, long updatedAtEpochMilli);

    PersistenceWriteOutcome upsertJoinRequest(JoinRequestRecord request);

    PersistenceWriteOutcome updateJoinRequestStatus(
            UUID requestId,
            JoinRequestStatus status,
            UUID reviewedByUuid,
            long reviewedAtEpochMilli
    );

    PersistenceWriteOutcome upsertFreezeCase(FreezeCaseRecord freezeCase);

    PersistenceWriteOutcome closeFreezeCase(UUID caseId, UUID closedBy, long closedAtEpochMilli);

    PersistenceWriteOutcome upsertClaimBinding(ClaimBindingRecord claimBinding);

    PersistenceWriteOutcome upsertCityVice(CityViceRecord viceRecord);

    PersistenceWriteOutcome clearCityVice(UUID cityId, long updatedAtEpochMilli);

    PersistenceWriteOutcome upsertClaimPermissions(MemberClaimPermissionRecord permissions);

    PersistenceWriteOutcome deleteClaimPermissions(UUID cityId, UUID playerUuid);

    PersistenceWriteOutcome upsertTaxPolicy(TaxPolicyRecord policy);

    PersistenceWriteOutcome appendLedger(CityTreasuryLedgerRecord entry);

    PersistenceWriteOutcome upsertCapitalState(CapitalStateRecord state);

    PersistenceWriteOutcome clearCapitalState(String capitalSlot);

    PersistenceWriteOutcome upsertRankingSnapshot(RankingSnapshotRecord snapshot);

    PersistenceWriteOutcome deleteRankingSnapshot(UUID cityId);

    PersistenceWriteOutcome upsertMonthlyCycleState(MonthlyCycleStateRecord cycleState);

    PersistenceWriteOutcome appendAuditEvent(AuditEventRecord event);

    PersistenceWriteOutcome hardDeleteCityAggregate(UUID cityId);
}
