package it.patric.cittaexp.persistence.port;

import it.patric.cittaexp.persistence.domain.CityMemberRecord;
import it.patric.cittaexp.persistence.domain.CityRecord;
import it.patric.cittaexp.persistence.domain.CityRoleRecord;
import it.patric.cittaexp.persistence.domain.CityInvitationRecord;
import it.patric.cittaexp.persistence.domain.ClaimBindingRecord;
import it.patric.cittaexp.persistence.domain.FreezeCaseRecord;
import it.patric.cittaexp.persistence.domain.JoinRequestRecord;
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

    PersistenceWriteOutcome appendAuditEvent(AuditEventRecord event);

    PersistenceWriteOutcome hardDeleteCityAggregate(UUID cityId);
}
