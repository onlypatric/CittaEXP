package it.patric.cittaexp.core.port;

import java.util.UUID;

public interface HuskClaimsPort {

    boolean available();

    boolean hasClaimAt(UUID playerUuid);

    ClaimCreationResult createAutoClaim100x100(UUID playerUuid, String world, int centerX, int centerZ);

    boolean expandClaim(UUID cityId, int chunks, long cost);

    record ClaimCreationResult(boolean success, int minX, int minZ, int maxX, int maxZ, String reason) {
    }
}
