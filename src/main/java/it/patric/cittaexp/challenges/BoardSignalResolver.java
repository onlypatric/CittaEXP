package it.patric.cittaexp.challenges;

import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.levels.PrincipalStage;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class BoardSignalResolver {

    public record SignalContext(
            int townId,
            ChallengeMode mode,
            String cycleKey,
            PrincipalStage stage,
            ChallengeSeasonResolver.Season season,
            AtlasChapter atlasLowestChapter,
            AtlasChapter atlasMostActiveChapter14d,
            Set<String> previousCycleFamilies,
            int resourceContributionSelectionsLastWindow
    ) {
    }

    public record SignalScore(
            double seasonTheme,
            double atlasLowest,
            double cityAffinity14d,
            boolean seasonHit,
            boolean atlasLowHit,
            boolean affinityHit
    ) {
    }

    public record EffectiveWeights(
            double seasonThemeWeight,
            double atlasLowestWeight,
            double cityAffinity14dWeight
    ) {
    }

    private final CityChallengeSettings.BoardGeneratorSettings boardSettings;
    private final ZoneId timezone;
    private final CityLevelService cityLevelService;
    private final CityAtlasService atlasService;

    public BoardSignalResolver(
            CityChallengeSettings settings,
            CityLevelService cityLevelService,
            CityAtlasService atlasService
    ) {
        this.boardSettings = settings == null
                ? CityChallengeSettings.BoardGeneratorSettings.defaults()
                : settings.boardGenerator();
        this.timezone = settings == null ? ZoneId.of("Europe/Rome") : settings.timezone();
        this.cityLevelService = cityLevelService;
        this.atlasService = atlasService;
    }

    public SignalContext resolve(
            int townId,
            ChallengeMode mode,
            String cycleKey,
            List<CityChallengeRepository.SelectionHistoryEntry> selectionHistory
    ) {
        PrincipalStage stage = PrincipalStage.AVAMPOSTO;
        AtlasChapter lowestChapter = null;
        AtlasChapter activeChapter = null;
        int resourceRecentCount = 0;
        Set<String> previousCycleFamilies = Set.of();
        if (townId > 0) {
            stage = cityLevelService.statusForTown(townId, false).principalStage();
            lowestChapter = atlasService.leastCompletedChapter(townId).orElse(null);
            activeChapter = atlasService.mostActiveChapterLastDays(townId, 14).orElse(null);
            previousCycleFamilies = previousCycleFamilies(selectionHistory, townId, mode, cycleKey);
            resourceRecentCount = resourceContributionSelections(selectionHistory, townId,
                    Math.max(1, boardSettings.hardRules().resourceContributionWindowDays()));
        }
        return new SignalContext(
                townId,
                mode,
                cycleKey,
                stage,
                ChallengeSeasonResolver.seasonFor(LocalDate.now(timezone)),
                lowestChapter,
                activeChapter,
                Set.copyOf(previousCycleFamilies),
                resourceRecentCount
        );
    }

    public SignalScore score(ChallengeDefinition definition, SignalContext context) {
        if (definition == null || context == null) {
            return new SignalScore(0.0D, 0.0D, 0.0D, false, false, false);
        }
        Optional<AtlasChapter> candidateChapter = atlasService.primaryChapterForObjective(definition.objectiveType());
        Set<String> objectiveFamilies = atlasService.atlasFamilyIdsForObjective(definition.objectiveType());
        CityChallengeSettings.SeasonThemeSettings theme = boardSettings.seasonThemes().get(context.season());

        boolean seasonHit = false;
        if (theme != null) {
            if (candidateChapter.isPresent() && theme.preferredChapters().contains(candidateChapter.get())) {
                seasonHit = true;
            }
            if (!seasonHit && matchesAnyFamily(theme.preferredFamilies(), definition.objectiveFamily(), objectiveFamilies)) {
                seasonHit = true;
            }
        }

        boolean atlasLowHit = context.atlasLowestChapter() != null
                && candidateChapter.isPresent()
                && candidateChapter.get() == context.atlasLowestChapter();
        boolean affinityHit = context.atlasMostActiveChapter14d() != null
                && candidateChapter.isPresent()
                && candidateChapter.get() == context.atlasMostActiveChapter14d();

        return new SignalScore(
                seasonHit ? 1.0D : 0.0D,
                atlasLowHit ? 1.0D : 0.0D,
                affinityHit ? 1.0D : 0.0D,
                seasonHit,
                atlasLowHit,
                affinityHit
        );
    }

    public EffectiveWeights effectiveWeights(ChallengeSeasonResolver.Season season) {
        CityChallengeSettings.BoardWeightsSettings base = boardSettings.weights();
        CityChallengeSettings.SeasonThemeSettings theme = boardSettings.seasonThemes().get(season);
        double seasonWeight = theme == null || theme.seasonThemeWeightOverride() == null
                ? base.seasonThemeWeight()
                : theme.seasonThemeWeightOverride();
        double atlasLowestWeight = theme == null || theme.atlasLowestWeightOverride() == null
                ? base.atlasLowestWeight()
                : theme.atlasLowestWeightOverride();
        double affinityWeight = theme == null || theme.cityAffinityWeightOverride() == null
                ? base.cityAffinity14dWeight()
                : theme.cityAffinityWeightOverride();
        return new EffectiveWeights(
                Math.max(0.0D, seasonWeight),
                Math.max(0.0D, atlasLowestWeight),
                Math.max(0.0D, affinityWeight)
        );
    }

    private static boolean matchesAnyFamily(
            Set<String> configured,
            String objectiveFamily,
            Set<String> atlasFamilies
    ) {
        if (configured == null || configured.isEmpty()) {
            return false;
        }
        if (objectiveFamily != null && configured.contains(objectiveFamily.trim().toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (atlasFamilies == null || atlasFamilies.isEmpty()) {
            return false;
        }
        for (String family : atlasFamilies) {
            if (family != null && configured.contains(family.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> previousCycleFamilies(
            List<CityChallengeRepository.SelectionHistoryEntry> history,
            int townId,
            ChallengeMode mode,
            String cycleKey
    ) {
        if (history == null || history.isEmpty() || mode == null) {
            return Set.of();
        }
        String previousCycle = null;
        for (CityChallengeRepository.SelectionHistoryEntry entry : history) {
            if (entry == null || entry.townId() == null || entry.townId() != townId || entry.mode() == null) {
                continue;
            }
            if (entry.mode().cycleType() != mode.cycleType()) {
                continue;
            }
            if (entry.cycleKey() == null || entry.cycleKey().equalsIgnoreCase(cycleKey)) {
                continue;
            }
            previousCycle = entry.cycleKey();
            break;
        }
        if (previousCycle == null) {
            return Set.of();
        }
        Set<String> families = new LinkedHashSet<>();
        for (CityChallengeRepository.SelectionHistoryEntry entry : history) {
            if (entry == null || entry.townId() == null || entry.townId() != townId || entry.mode() == null) {
                continue;
            }
            if (entry.mode().cycleType() != mode.cycleType()) {
                continue;
            }
            if (!Objects.equals(previousCycle, entry.cycleKey())) {
                continue;
            }
            if (entry.objectiveFamily() == null || entry.objectiveFamily().isBlank()) {
                continue;
            }
            families.add(entry.objectiveFamily().trim().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(families);
    }

    private static int resourceContributionSelections(
            List<CityChallengeRepository.SelectionHistoryEntry> history,
            int townId,
            int windowDays
    ) {
        if (history == null || history.isEmpty() || townId <= 0) {
            return 0;
        }
        long minEpoch = java.time.Instant.now().minusSeconds(Math.max(1, windowDays) * 86_400L).getEpochSecond();
        int count = 0;
        for (CityChallengeRepository.SelectionHistoryEntry entry : history) {
            if (entry == null || entry.townId() == null || entry.townId() != townId) {
                continue;
            }
            if (entry.selectedAt() == null || entry.selectedAt().getEpochSecond() < minEpoch) {
                continue;
            }
            String family = entry.objectiveFamily() == null ? "" : entry.objectiveFamily().trim().toLowerCase(Locale.ROOT);
            String challengeId = entry.challengeId() == null ? "" : entry.challengeId().trim().toLowerCase(Locale.ROOT);
            if ("resource_contribution".equals(challengeId)
                    || "proc_resources".equals(challengeId)
                    || ("resource_grind".equals(family) && challengeId.startsWith("resource"))) {
                count++;
            }
        }
        return Math.max(0, count);
    }
}
