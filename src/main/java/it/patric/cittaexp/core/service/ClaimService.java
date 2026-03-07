package it.patric.cittaexp.core.service;

import it.patric.cittaexp.core.model.ClaimBinding;
import java.util.UUID;

public interface ClaimService {

    ClaimBinding ensureInitialClaim100x100(UUID cityId, UUID leaderUuid, String world, int centerX, int centerZ);

    ClaimBinding expand(UUID cityId, UUID actorUuid, int chunks, String reason);
}
