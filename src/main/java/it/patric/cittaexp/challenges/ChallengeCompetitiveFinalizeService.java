package it.patric.cittaexp.challenges;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ChallengeCompetitiveFinalizeService {

    public List<MonthlyEventService.LeaderboardEntry> leaderboard(
            ChallengeInstance instance,
            Map<Integer, TownChallengeProgress> progressByTown
    ) {
        if (instance == null || progressByTown == null || progressByTown.isEmpty()) {
            return List.of();
        }
        List<MonthlyEventService.LeaderboardEntry> entries = new ArrayList<>();
        for (TownChallengeProgress progress : progressByTown.values()) {
            if (progress == null) {
                continue;
            }
            int value = Math.max(0, progress.progress());
            if (value <= 0) {
                continue;
            }
            entries.add(new MonthlyEventService.LeaderboardEntry(
                    progress.townId(),
                    value,
                    progress.completedAt()
            ));
        }
        entries.sort(Comparator
                .comparingInt(MonthlyEventService.LeaderboardEntry::progress).reversed()
                .thenComparing(entry -> entry.completedAt() == null ? Instant.MAX : entry.completedAt())
                .thenComparingInt(MonthlyEventService.LeaderboardEntry::townId));
        return List.copyOf(entries);
    }
}

