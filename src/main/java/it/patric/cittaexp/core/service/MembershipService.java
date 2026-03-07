package it.patric.cittaexp.core.service;

import it.patric.cittaexp.core.model.CityInvitation;
import it.patric.cittaexp.core.model.CityMember;
import it.patric.cittaexp.core.model.JoinRequest;
import java.util.List;
import java.util.UUID;

public interface MembershipService {

    CityInvitation invite(UUID cityId, UUID invitedBy, UUID invitedPlayer);

    CityMember acceptInvite(UUID invitationId, UUID playerUuid);

    void declineInvite(UUID invitationId, UUID playerUuid, String note);

    JoinRequest requestJoin(UUID cityId, UUID playerUuid, String message);

    CityMember approveJoinRequest(UUID requestId, UUID reviewerUuid, String note);

    void rejectJoinRequest(UUID requestId, UUID reviewerUuid, String note);

    void kickMember(UUID cityId, UUID actorUuid, UUID targetUuid, String reason);

    void leaveCity(UUID cityId, UUID playerUuid, String reason);

    List<CityMember> listActiveMembers(UUID cityId);
}
