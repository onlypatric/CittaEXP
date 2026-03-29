package it.patric.cittaexp.challenges;

import java.util.EnumSet;
import java.util.Set;

public record ChallengeObjectivePolicy(
        ChallengeProgressionRole progressionRole,
        Set<ChallengeMode> allowedModes,
        boolean raceEligible,
        boolean cityXpEnabled
) {

    public ChallengeObjectivePolicy {
        progressionRole = progressionRole == null ? ChallengeProgressionRole.CORE : progressionRole;
        allowedModes = allowedModes == null || allowedModes.isEmpty()
                ? EnumSet.allOf(ChallengeMode.class)
                : EnumSet.copyOf(allowedModes);
    }

    public boolean allowsMode(ChallengeMode mode) {
        if (mode == null) {
            return false;
        }
        boolean allowed = allowedModes.contains(mode);
        if (!allowed) {
            allowed = switch (mode) {
                case DAILY_SPRINT_1830, DAILY_SPRINT_2130, DAILY_RACE_1600, DAILY_RACE_2100 ->
                        allowedModes.contains(ChallengeMode.DAILY_STANDARD)
                                || allowedModes.contains(ChallengeMode.DAILY_RACE_1600)
                                || allowedModes.contains(ChallengeMode.DAILY_RACE_2100);
                case WEEKLY_CLASH -> allowedModes.contains(ChallengeMode.WEEKLY_STANDARD);
                case MONTHLY_CROWN, MONTHLY_EVENT_A, MONTHLY_EVENT_B,
                        MONTHLY_LEDGER_GRAND, MONTHLY_LEDGER_CONTRACT, MONTHLY_LEDGER_MYSTERY ->
                        allowedModes.contains(ChallengeMode.MONTHLY_STANDARD);
                case SEASON_CODEX_ACT_I, SEASON_CODEX_ACT_II, SEASON_CODEX_ACT_III,
                        SEASON_CODEX_ELITE, SEASON_CODEX_HIDDEN_RELIC, SEASONAL_RACE ->
                        allowedModes.contains(ChallengeMode.MONTHLY_STANDARD)
                                || allowedModes.contains(ChallengeMode.SEASONAL_RACE);
                default -> false;
            };
        }
        if (!allowed) {
            return false;
        }
        return !mode.race() || raceEligible;
    }
}
