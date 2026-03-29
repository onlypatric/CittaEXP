package it.patric.cittaexp.challenges;

import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChallengeBroadcastFormatterTest {

    @Test
    void renderRewardUsesUnifiedChatFrame() {
        ChallengeInstance instance = new ChallengeInstance(
                "inst-1",
                7,
                "2026-W12",
                ChallengeCycleType.WEEKLY,
                "weekly",
                "boss_kill",
                "Abbatti i signori del varco nelle rovine orientali",
                ChallengeObjectiveType.BOSS_KILL,
                0,
                5,
                5,
                7,
                1.0D,
                3,
                3,
                2,
                "v1",
                ChallengeVariantType.GLOBAL,
                ChallengeFocusType.NONE,
                null,
                null,
                null,
                null,
                null,
                ChallengeMode.WEEKLY_STANDARD,
                ChallengeInstanceStatus.ACTIVE,
                null,
                null,
                java.time.Instant.now(),
                java.time.Instant.now(),
                java.time.Instant.now(),
                null
        );
        ChallengeRewardSpec spec = new ChallengeRewardSpec(
                120.0D,
                80.0D,
                java.util.List.of(),
                java.util.List.of(),
                Map.of(Material.IRON_INGOT, 16),
                0.0D
        );

        String rendered = ChallengeBroadcastFormatter.renderReward(
                "challenge_complete_broadcast",
                instance,
                "Repubblica delle Mura D'Oriente",
                instance.cycleKey(),
                spec
        );

        String[] lines = rendered.split("\\n");
        assertTrue(lines.length >= 8);
        assertTrue(lines[0].contains("SFIDA COMPLETATA"));
        assertTrue(lines[1].contains("signori del varco"));
        assertTrue(lines[2].contains("Obiettivo completato"));
        assertTrue(lines[3].contains("Repubblica delle Mura D'Oriente"));
        assertTrue(lines[4].contains("Stagione: 2026-W12"));
        assertTrue(rendered.contains("Obiettivo: sconfiggi 5 boss maggiori"));
        assertTrue(rendered.contains("+120 XP citta"));
        assertTrue(rendered.contains("+80 Monete citta"));
        assertTrue(rendered.contains("16x Iron Ingot"));
        assertTrue(lines[lines.length - 1].contains("—————————————"));
        assertTrue(rendered.contains("Obiettivo completato"));
    }

    @Test
    void renderCityLevelUpUsesUnifiedChatFrame() {
        String rendered = ChallengeBroadcastFormatter.renderCityLevelUp("Bastione di Test", 42);

        String[] lines = rendered.split("\\n");
        assertTrue(lines.length >= 5);
        assertTrue(lines[0].contains("ASCESA CITTADINA"));
        assertTrue(lines[1].contains("Livello citta"));
        assertTrue(lines[2].contains("raggiunto il livello 42"));
        assertTrue(rendered.contains("Citta': Bastione di Test"));
        assertTrue(lines[lines.length - 1].contains("—————————————"));
    }

    @Test
    void handlesRewardKeyCoversMilestonesAndBroadcasts() {
        assertTrue(ChallengeBroadcastFormatter.handlesRewardKey("challenge_complete_broadcast"));
        assertTrue(ChallengeBroadcastFormatter.handlesRewardKey("race_winner_broadcast"));
        assertTrue(ChallengeBroadcastFormatter.handlesRewardKey("milestone_season_final"));
    }
}
