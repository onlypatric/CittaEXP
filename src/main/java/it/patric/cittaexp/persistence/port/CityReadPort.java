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
import it.patric.cittaexp.core.model.FreezeReason;
import it.patric.cittaexp.core.model.LedgerEntryType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CityReadPort {

    Optional<CityRecord> findCityById(UUID cityId);

    Optional<CityRecord> findCityByName(String name);

    Optional<CityRecord> findCityByTag(String tag);

    List<CityRecord> listCities();

    Optional<CityMemberRecord> findMember(UUID cityId, UUID playerUuid);

    Optional<CityMemberRecord> findActiveMember(UUID playerUuid);

    long countActiveMembers(UUID cityId);

    List<CityMemberRecord> listMembers(UUID cityId);

    List<CityRoleRecord> listRoles(UUID cityId);

    Optional<CityInvitationRecord> findInvitation(UUID invitationId);

    List<CityInvitationRecord> listPendingInvitations(UUID playerUuid);

    Optional<JoinRequestRecord> findJoinRequest(UUID requestId);

    Optional<JoinRequestRecord> findPendingJoinRequest(UUID cityId, UUID playerUuid);

    Optional<FreezeCaseRecord> findActiveFreezeCase(UUID cityId);

    Optional<ClaimBindingRecord> findClaimBinding(UUID cityId);

    Optional<CityViceRecord> findCityVice(UUID cityId);

    Optional<MemberClaimPermissionRecord> findClaimPermissions(UUID cityId, UUID playerUuid);

    List<MemberClaimPermissionRecord> listClaimPermissions(UUID cityId);

    Optional<TaxPolicyRecord> findTaxPolicy(String policyId);

    List<CityTreasuryLedgerRecord> listLedger(UUID cityId, int limit);

    boolean existsLedgerEntry(UUID cityId, LedgerEntryType entryType, String reason);

    long countLedgerByType(LedgerEntryType entryType);

    Optional<CapitalStateRecord> findCapitalState(String capitalSlot);

    Optional<RankingSnapshotRecord> findRankingSnapshot(UUID cityId);

    List<RankingSnapshotRecord> listRankingSnapshots(int limit);

    Optional<MonthlyCycleStateRecord> findMonthlyCycleState(String cycleKey);

    long countActiveFreezeCases();

    long countActiveFreezeCasesByReason(FreezeReason reason);

    long countPendingInvitations();

    long countPendingJoinRequests();
}
