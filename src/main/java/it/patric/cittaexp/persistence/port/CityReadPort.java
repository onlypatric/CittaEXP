package it.patric.cittaexp.persistence.port;

import it.patric.cittaexp.persistence.domain.CityMemberRecord;
import it.patric.cittaexp.persistence.domain.CityRecord;
import it.patric.cittaexp.persistence.domain.CityRoleRecord;
import it.patric.cittaexp.persistence.domain.CityInvitationRecord;
import it.patric.cittaexp.persistence.domain.CityViceRecord;
import it.patric.cittaexp.persistence.domain.ClaimBindingRecord;
import it.patric.cittaexp.persistence.domain.FreezeCaseRecord;
import it.patric.cittaexp.persistence.domain.JoinRequestRecord;
import it.patric.cittaexp.persistence.domain.MemberClaimPermissionRecord;
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

    long countActiveFreezeCases();

    long countPendingInvitations();

    long countPendingJoinRequests();
}
