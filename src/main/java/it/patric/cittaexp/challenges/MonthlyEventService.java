package it.patric.cittaexp.challenges;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MonthlyEventService {

    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT);

    public record Window(
            ChallengeMode mode,
            String cycleKey,
            String windowKey,
            Instant startAt,
            Instant endAt
    ) {
    }

    public record LeaderboardEntry(
            int townId,
            int progress,
            Instant completedAt
    ) {
    }

    public record Diagnostics(
            boolean windowActive,
            int leaderboardSize,
            int rewardGrants,
            String lastFinalizeError
    ) {
    }

    public Window resolveActiveWindow(LocalDateTime now, ZoneId timezone, LocalTime windowAStart, LocalTime windowBStart) {
        LocalDate date = now.toLocalDate();
        if (date.getDayOfMonth() <= 15) {
            LocalDate startDate = date.withDayOfMonth(1);
            LocalDate endDate = date.withDayOfMonth(16);
            LocalDateTime start = startDate.atTime(windowAStart);
            LocalDateTime end = endDate.atTime(windowBStart);
            return new Window(
                    ChallengeMode.MONTHLY_EVENT_A,
                    startDate.format(MONTH_KEY) + "-A",
                    "monthly-event-a",
                    start.atZone(timezone).toInstant(),
                    end.atZone(timezone).toInstant()
            );
        }
        LocalDate startDate = date.withDayOfMonth(16);
        LocalDate endDate = date.plusMonths(1).withDayOfMonth(1);
        LocalDateTime start = startDate.atTime(windowBStart);
        LocalDateTime end = endDate.atTime(windowAStart);
        return new Window(
                ChallengeMode.MONTHLY_EVENT_B,
                date.format(MONTH_KEY) + "-B",
                "monthly-event-b",
                start.atZone(timezone).toInstant(),
                end.atZone(timezone).toInstant()
        );
    }

    public List<LeaderboardEntry> leaderboardFor(
            ChallengeInstance instance,
            Map<Integer, TownChallengeProgress> progressByTown
    ) {
        if (instance == null || progressByTown == null || progressByTown.isEmpty()) {
            return List.of();
        }
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (TownChallengeProgress progress : progressByTown.values()) {
            if (progress == null) {
                continue;
            }
            int value = Math.max(0, progress.progress());
            if (value <= 0) {
                continue;
            }
            entries.add(new LeaderboardEntry(progress.townId(), value, progress.completedAt()));
        }
        entries.sort(Comparator
                .comparingInt(LeaderboardEntry::progress).reversed()
                .thenComparing(entry -> entry.completedAt() == null ? Instant.MAX : entry.completedAt())
                .thenComparingInt(LeaderboardEntry::townId));
        return List.copyOf(entries);
    }
}
