package it.patric.cittaexp.manageclaim;

import net.william278.husktowns.claim.Claim;

public record CitySectionMapCell(
        int chunkX,
        int chunkZ,
        State state,
        String ownerName,
        Claim.Type claimType,
        String reasonCode
) {

    public enum State {
        OWN_CLAIM,
        OTHER_CLAIM,
        WILDERNESS_CLAIMABLE,
        BLOCKED_HUSKCLAIMS,
        BLOCKED_POLICY
    }
}
