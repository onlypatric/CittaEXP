package it.patric.cittaexp.challenges;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TeamPhaseQuestCatalog {

    public record Quest(
            String id,
            String displayName,
            int gatherTarget,
            int buildTarget,
            int bossTarget
    ) {
        public int totalTarget() {
            return Math.max(1, gatherTarget) + Math.max(1, buildTarget) + Math.max(1, bossTarget);
        }
    }

    private static final List<Quest> QUESTS = List.of(
            new Quest("proc_season_team_phase_01", "Conquista Stagionale I", 450, 600, 1),
            new Quest("proc_season_team_phase_02", "Conquista Stagionale II", 600, 800, 1),
            new Quest("proc_season_team_phase_03", "Conquista Stagionale III", 800, 1000, 1),
            new Quest("proc_season_team_phase_04", "Conquista Stagionale IV", 1000, 1200, 1),
            new Quest("proc_season_team_phase_05", "Conquista Stagionale V", 1200, 1400, 1),
            new Quest("proc_season_team_phase_06", "Conquista Stagionale VI", 1400, 1600, 1),
            new Quest("proc_season_team_phase_07", "Conquista Stagionale VII", 1600, 1800, 1),
            new Quest("proc_season_team_phase_08", "Conquista Stagionale VIII", 1800, 2000, 1),
            new Quest("proc_season_team_phase_09", "Conquista Stagionale IX", 2000, 2200, 1),
            new Quest("proc_season_team_phase_10", "Conquista Stagionale X", 2400, 2600, 2)
    );

    private static final Map<String, Quest> BY_ID = indexById();

    private TeamPhaseQuestCatalog() {
    }

    public static List<Quest> quests() {
        return QUESTS;
    }

    public static Quest byId(String challengeId) {
        if (challengeId == null || challengeId.isBlank()) {
            return null;
        }
        return BY_ID.get(challengeId.toLowerCase(java.util.Locale.ROOT));
    }

    private static Map<String, Quest> indexById() {
        Map<String, Quest> map = new LinkedHashMap<>();
        for (Quest quest : QUESTS) {
            map.put(quest.id().toLowerCase(java.util.Locale.ROOT), quest);
        }
        return Map.copyOf(map);
    }
}
