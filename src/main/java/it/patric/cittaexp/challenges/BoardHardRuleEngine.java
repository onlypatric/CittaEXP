package it.patric.cittaexp.challenges;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BoardHardRuleEngine {

    public record Context(
            ChallengeMode mode,
            int boardTargetCount,
            Set<String> previousCycleFamilies,
            int resourceContributionSelectionsInWindow,
            List<ChallengeDefinition> pool
    ) {
    }

    public record Decision(
            boolean allowed,
            String reasonCode
    ) {
    }

    private final CityChallengeSettings.BoardHardRulesSettings settings;
    private final ChallengeTaxonomyService taxonomyService;

    public BoardHardRuleEngine(CityChallengeSettings settings, ChallengeTaxonomyService taxonomyService) {
        this.settings = settings == null
                ? CityChallengeSettings.BoardHardRulesSettings.defaults()
                : settings.boardGenerator().hardRules();
        this.taxonomyService = taxonomyService;
    }

    public Decision evaluate(
            ChallengeDefinition candidate,
            Context context,
            List<ChallengeDefinition> selected
    ) {
        if (candidate == null) {
            return deny("candidate-null");
        }
        if (context == null || context.mode() == null) {
            return allow();
        }
        if (settings.excludeEconomyFromRace()
                && context.mode().race()
                && candidate.objectiveType() == ChallengeObjectiveType.ECONOMY_ADVANCED) {
            return deny("race-economy");
        }

        if (settings.noConsecutiveFamily()) {
            String family = normalizeFamily(candidate.objectiveFamily());
            if (!family.isBlank() && context.previousCycleFamilies() != null && context.previousCycleFamilies().contains(family)) {
                return deny("family-consecutive");
            }
        }

        if ((candidate.objectiveType() == ChallengeObjectiveType.RESOURCE_CONTRIBUTION
                || candidate.objectiveType() == ChallengeObjectiveType.VAULT_DELIVERY)
                && context.resourceContributionSelectionsInWindow() >= settings.resourceContributionMaxInWindow()) {
            return deny("resource-window-limit");
        }

        int maxPassive = Math.max(0, settings.maxPassivePerBoard());
        if (maxPassive > 0) {
            int passiveCount = passiveCount(selected);
            if (isPassive(candidate)) {
                passiveCount++;
            }
            if (passiveCount > maxPassive) {
                return deny("max-passive");
            }
        }

        if (settings.banDailyPlaytimeXpPair() && context.mode() == ChallengeMode.DAILY_STANDARD) {
            boolean hasPlaytime = containsObjective(selected, ChallengeObjectiveType.PLAYTIME_MINUTES)
                    || candidate.objectiveType() == ChallengeObjectiveType.PLAYTIME_MINUTES;
            boolean hasXpPickup = containsObjective(selected, ChallengeObjectiveType.XP_PICKUP)
                    || candidate.objectiveType() == ChallengeObjectiveType.XP_PICKUP;
            if (hasPlaytime && hasXpPickup) {
                return deny("daily-playtime-xp-pair");
            }
        }

        int minSkillful = Math.max(0, settings.minSkillfulPerBoard());
        if (minSkillful > 0) {
            int selectedSkillful = skillfulCount(selected) + (isSkillful(candidate) ? 1 : 0);
            int remainingSlots = Math.max(0, context.boardTargetCount() - (selected.size() + 1));
            int remainingSkillfulPotential = remainingSkillfulPotential(context.pool(), candidate);
            if (selectedSkillful + remainingSkillfulPotential < minSkillful) {
                return deny("min-skillful");
            }
        }

        return allow();
    }

    private int remainingSkillfulPotential(List<ChallengeDefinition> pool, ChallengeDefinition chosen) {
        if (pool == null || pool.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ChallengeDefinition definition : pool) {
            if (definition == null || definition == chosen) {
                continue;
            }
            if (isSkillful(definition)) {
                count++;
            }
        }
        return count;
    }

    private int passiveCount(List<ChallengeDefinition> selected) {
        if (selected == null || selected.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ChallengeDefinition definition : selected) {
            if (definition != null && isPassive(definition)) {
                count++;
            }
        }
        return count;
    }

    private int skillfulCount(List<ChallengeDefinition> selected) {
        if (selected == null || selected.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ChallengeDefinition definition : selected) {
            if (definition != null && isSkillful(definition)) {
                count++;
            }
        }
        return count;
    }

    private boolean containsObjective(List<ChallengeDefinition> selected, ChallengeObjectiveType objectiveType) {
        if (selected == null || selected.isEmpty() || objectiveType == null) {
            return false;
        }
        for (ChallengeDefinition definition : selected) {
            if (definition != null && definition.objectiveType() == objectiveType) {
                return true;
            }
        }
        return false;
    }

    private boolean isPassive(ChallengeDefinition definition) {
        if (definition == null || definition.objectiveType() == null) {
            return false;
        }
        return taxonomyService.policyFor(definition.objectiveType()).progressionRole() == ChallengeProgressionRole.SUPPORT;
    }

    private boolean isSkillful(ChallengeDefinition definition) {
        return !isPassive(definition);
    }

    private static String normalizeFamily(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static Decision allow() {
        return new Decision(true, "-");
    }

    private static Decision deny(String reason) {
        return new Decision(false, reason == null || reason.isBlank() ? "denied" : reason);
    }
}
