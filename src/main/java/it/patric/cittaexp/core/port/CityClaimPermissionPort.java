package it.patric.cittaexp.core.port;

import it.patric.cittaexp.core.model.MemberClaimPermissions;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface CityClaimPermissionPort {

    CompletableFuture<Boolean> syncClaimPermissionsAsync(
            String world,
            int anchorX,
            int anchorZ,
            UUID playerUuid,
            MemberClaimPermissions permissions,
            boolean managementTrusted
    );

    CompletableFuture<Boolean> clearClaimPermissionsAsync(
            String world,
            int anchorX,
            int anchorZ,
            UUID playerUuid
    );
}
