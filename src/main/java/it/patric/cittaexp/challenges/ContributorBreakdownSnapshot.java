package it.patric.cittaexp.challenges;

import java.util.List;
import java.util.UUID;

public record ContributorBreakdownSnapshot(
        String instanceId,
        int townId,
        int totalContribution,
        double carryRatio,
        List<Entry> topContributors
) {
    public record Entry(
            UUID playerId,
            int contribution
    ) {
    }
}
