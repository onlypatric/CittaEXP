package it.patric.cittaexp.levelsgui;

import it.patric.cittaexp.defense.CityDefenseService;
import it.patric.cittaexp.levels.CityLevelRequestService;
import it.patric.cittaexp.levels.CityLevelService;
import it.patric.cittaexp.levels.CityLevelCurve;
import it.patric.cittaexp.levels.CityLevelSettings;
import it.patric.cittaexp.levels.TownLevelRequest;
import it.patric.cittaexp.levels.TownStage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class CityLevelTimelineBuilder {

    private static final int TIMELINE_MAX_LEVEL = 250;

    public record Timeline(
            CityLevelSummaryView summary,
            List<CityLevelRowView> rows
    ) {
    }

    private final CityLevelService cityLevelService;
    private final CityLevelRequestService requestService;
    private final CityDefenseService cityDefenseService;

    public CityLevelTimelineBuilder(
            CityLevelService cityLevelService,
            CityLevelRequestService requestService,
            CityDefenseService cityDefenseService
    ) {
        this.cityLevelService = cityLevelService;
        this.requestService = requestService;
        this.cityDefenseService = cityDefenseService;
    }

    public Timeline build(int townId) {
        CityLevelService.LevelStatus status = cityLevelService.statusForTown(townId, true);
        CityLevelSettings settings = cityLevelService.settings();
        int maxLevel = Math.min(TIMELINE_MAX_LEVEL, settings.levelCap());

        CityLevelSummaryView summary = new CityLevelSummaryView(
                status.townId(),
                status.townName(),
                status.level(),
                status.earnedLevel(),
                status.xp(),
                status.stage(),
                status.principalStage(),
                status.subTier(),
                status.stageSealsEarned(),
                status.stageSealsRequired(),
                status.claimCap(),
                status.memberCap(),
                status.spawnerCap(),
                status.currentBalance(),
                status.nextStage(),
                status.nextRequiredLevel(),
                status.requiredBalance(),
                status.upgradeCost(),
                status.nextMonthlyTax(),
                status.staffApprovalRequired(),
                status.nextRequiredXp()
        );

        List<CityLevelRowView> rows = new ArrayList<>(maxLevel);
        Map<TownStage, CityLevelSettings.StageSpec> specs = specsMap(settings);
        for (int level = 1; level <= maxLevel; level++) {
            TownStage stageForLevel = stageForLevel(level, specs);
            TownStage checkpointStage = checkpointAtLevel(level, specs);
            CityLevelSettings.StageSpec stageSpec = specs.get(stageForLevel);

            CityLevelRowView.RowState rowState = level == status.level()
                    ? CityLevelRowView.RowState.CURRENT
                    : (level < status.level() ? CityLevelRowView.RowState.UNLOCKED : CityLevelRowView.RowState.LOCKED);

            boolean isCheckpoint = checkpointStage != null;
            boolean actionable = level == status.level() + 1 && level <= status.earnedLevel();
            CityLevelSettings.StageSpec checkpointSpec = isCheckpoint ? specs.get(checkpointStage) : null;
            TownLevelRequest latestRequest = isCheckpoint ? requestService.latestRequestForTownStage(townId, checkpointStage).orElse(null) : null;
            boolean defenseTierCompleted = checkpointSpec == null
                    || checkpointSpec.requiredDefenseTier() == null
                    || cityDefenseService.hasCompletedTier(townId, checkpointSpec.requiredDefenseTier());

            boolean regnoExtraClaim = stageForLevel.principalStage() == it.patric.cittaexp.levels.PrincipalStage.REGNO
                    && level > TownStage.REGNO_III.requiredLevel();
            rows.add(new CityLevelRowView(
                    level,
                    requiredXpForLevel(level, settings),
                    rowState,
                    stageForLevel,
                    checkpointStage,
                    resolveClaimCap(stageForLevel, level, settings, specs),
                    stageSpec.memberCap(),
                    stageSpec.spawnerCap(),
                    regnoExtraClaim,
                    actionable,
                    checkpointSpec != null && checkpointSpec.staffApprovalRequired(),
                    checkpointStage == TownStage.VILLAGGIO_I,
                    checkpointStage == null ? 0.0D : cityLevelService.effectiveRequiredBalance(checkpointStage),
                    checkpointStage == null ? 0.0D : cityLevelService.effectiveUpgradeCost(checkpointStage),
                    checkpointStage == null ? 0.0D : cityLevelService.effectiveMonthlyTax(checkpointStage),
                    checkpointSpec == null ? null : checkpointSpec.requiredDefenseTier(),
                    defenseTierCompleted,
                    latestRequest
            ));
        }

        return new Timeline(summary, rows);
    }

    private Map<TownStage, CityLevelSettings.StageSpec> specsMap(CityLevelSettings settings) {
        Map<TownStage, CityLevelSettings.StageSpec> map = new EnumMap<>(TownStage.class);
        for (TownStage stage : TownStage.values()) {
            map.put(stage, settings.spec(stage));
        }
        return map;
    }

    private TownStage stageForLevel(int level, Map<TownStage, CityLevelSettings.StageSpec> specs) {
        TownStage result = TownStage.AVAMPOSTO_I;
        for (TownStage stage : TownStage.values()) {
            if (level >= specs.get(stage).requiredLevel()) {
                result = stage;
            }
        }
        return result;
    }

    private TownStage checkpointAtLevel(int level, Map<TownStage, CityLevelSettings.StageSpec> specs) {
        for (TownStage stage : TownStage.values()) {
            if (specs.get(stage).requiredLevel() == level) {
                return stage;
            }
        }
        return null;
    }

    private double requiredXpForLevel(int level, CityLevelSettings settings) {
        long requiredXpScaled = CityLevelCurve.requiredXpScaledForLevel(
                level,
                settings.xpPerLevel(),
                settings.xpScale(),
                settings.curveExponent()
        );
        return cityLevelService.toXp(requiredXpScaled);
    }

    private int resolveClaimCap(
            TownStage stage,
            int level,
            CityLevelSettings settings,
            Map<TownStage, CityLevelSettings.StageSpec> specs
    ) {
        int base = specs.get(stage).claimCap();
        if (!(stage.principalStage() == it.patric.cittaexp.levels.PrincipalStage.REGNO
                && stage.subTier() == it.patric.cittaexp.levels.SubTier.III)) {
            return base;
        }
        int extraUntilLevel = Math.min(settings.levelCap(), Math.min(settings.regnoExtraClaimMaxLevel(), 250));
        int extraLevels = Math.max(0, Math.min(level, extraUntilLevel) - TownStage.REGNO_III.requiredLevel());
        return base + extraLevels;
    }
}
