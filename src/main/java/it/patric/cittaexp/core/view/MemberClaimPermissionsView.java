package it.patric.cittaexp.core.view;

import it.patric.cittaexp.core.model.MemberClaimPermissions;
import java.util.UUID;

public record MemberClaimPermissionsView(
        UUID cityId,
        UUID playerUuid,
        MemberClaimPermissions permissions,
        UUID updatedBy,
        long updatedAtEpochMilli
) {
}
