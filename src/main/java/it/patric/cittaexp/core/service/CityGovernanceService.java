package it.patric.cittaexp.core.service;

import it.patric.cittaexp.core.model.MemberClaimPermissions;
import it.patric.cittaexp.core.view.MemberClaimPermissionsView;
import it.patric.cittaexp.core.view.ViceView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CityGovernanceService {

    ViceView setVice(UUID cityId, UUID actorUuid, UUID targetUuid, String reason);

    void clearVice(UUID cityId, UUID actorUuid, String reason);

    Optional<ViceView> vice(UUID cityId);

    MemberClaimPermissionsView setMemberClaimPermission(
            UUID cityId,
            UUID actorUuid,
            UUID targetUuid,
            MemberClaimPermissions permissions,
            String reason
    );

    void clearMemberClaimPermission(UUID cityId, UUID actorUuid, UUID targetUuid, String reason);

    Optional<MemberClaimPermissionsView> claimPermissions(UUID cityId, UUID targetUuid);

    List<MemberClaimPermissionsView> listClaimPermissions(UUID cityId);
}
