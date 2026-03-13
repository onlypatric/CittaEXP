package it.patric.cittaexp.claimsettings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.william278.husktowns.claim.Claim;

public final class ClaimRulesDialogSession {

    private final UUID citySessionId;
    private final int townId;
    private final Claim.Type claimType;
    private final LinkedHashMap<String, Boolean> flagStates;

    public ClaimRulesDialogSession(
            UUID citySessionId,
            int townId,
            Claim.Type claimType,
            Map<String, Boolean> flagStates
    ) {
        this.citySessionId = citySessionId;
        this.townId = townId;
        this.claimType = claimType;
        this.flagStates = new LinkedHashMap<>(flagStates);
    }

    public UUID citySessionId() {
        return citySessionId;
    }

    public int townId() {
        return townId;
    }

    public Claim.Type claimType() {
        return claimType;
    }

    public Map<String, Boolean> flagStates() {
        return flagStates;
    }

    public boolean toggle(String flagId) {
        boolean next = !flagStates.getOrDefault(flagId, false);
        flagStates.put(flagId, next);
        return next;
    }
}
