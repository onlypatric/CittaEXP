package it.patric.cittaexp.challenges;

import it.patric.cittaexp.levels.CityLevelService;
import java.util.List;
import java.util.Map;

public record CityProgressionHubSnapshot(
        CityLevelService.LevelStatus levelStatus,
        List<CycleProgressSnapshot> cycleProgress,
        List<CycleTopContributorSnapshot> topContributors,
        WeeklyStreakSnapshot weeklyStreak,
        EventLeaderboardSnapshot monthlyEvent,
        List<ChallengeSnapshot> activeChallenges,
        Map<String, ChallengeRewardPreview> rewardPreviewByInstanceId
) {
}
