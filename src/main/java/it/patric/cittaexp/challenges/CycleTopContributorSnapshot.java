package it.patric.cittaexp.challenges;

import java.util.List;
import java.util.UUID;

public record CycleTopContributorSnapshot(
        ChallengeCycleType cycleType,
        List<Entry> topContributors,
        double carryRatio
) {
    public record Entry(
            UUID playerId,
            int contribution
    ) {
    }
}
