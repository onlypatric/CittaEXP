package it.patric.cittaexp.core.port;

import java.util.concurrent.CompletableFuture;
import java.util.UUID;

public interface HuskClaimsPort {

    boolean available();

    boolean hasClaimAt(UUID playerUuid);

    CompletableFuture<ClaimCreationResult> createAutoClaim100x100Async(
            UUID playerUuid,
            String world,
            int centerX,
            int centerZ
    );

    CompletableFuture<Boolean> expandClaimAsync(String world, int centerX, int centerZ, int chunks);

    CompletableFuture<Boolean> deleteClaimAtAsync(String world, int blockX, int blockZ);

    record ClaimCreationResult(boolean success, int minX, int minZ, int maxX, int maxZ, String reason) {
    }
}
