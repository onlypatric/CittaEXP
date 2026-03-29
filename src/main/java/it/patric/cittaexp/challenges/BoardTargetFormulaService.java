package it.patric.cittaexp.challenges;

import it.patric.cittaexp.levels.PrincipalStage;

public final class BoardTargetFormulaService {

    public static final String FORMULA_VERSION = "m5-v1";

    public record TargetResult(
            int target,
            double appliedMultiplier,
            String formulaVersion
    ) {
    }

    private final CityChallengeSettings.BoardGeneratorSettings settings;

    public BoardTargetFormulaService(CityChallengeSettings settings) {
        this.settings = settings == null
                ? CityChallengeSettings.BoardGeneratorSettings.defaults()
                : settings.boardGenerator();
    }

    public TargetResult resolve(
            String instanceId,
            ChallengeDefinition definition,
            ChallengeMode mode,
            PrincipalStage stage,
            int baseTemplateTarget
    ) {
        int safeBase = Math.max(1, baseTemplateTarget);
        if (definition == null || mode == null) {
            return new TargetResult(safeBase, 1.0D, FORMULA_VERSION);
        }

        CityChallengeSettings.ObjectiveExceptionSettings exceptionSettings = settings.targetFormula()
                .exceptions()
                .get(definition.objectiveType());
        CityChallengeSettings.TargetBracketSettings bracket = exceptionSettings == null
                ? null
                : exceptionSettings.bracketFor(mode);
        if (bracket != null) {
            int ranged = deterministicSteppedRange(
                    instanceId + ":m5:exception:" + definition.objectiveType().id() + ":" + mode.name(),
                    bracket.min(),
                    bracket.max(),
                    bracket.step()
            );
            return new TargetResult(ranged, 1.0D, FORMULA_VERSION);
        }

        PrincipalStage safeStage = stage == null ? PrincipalStage.AVAMPOSTO : stage;
        double stageMultiplier = settings.targetFormula().stageMultipliers()
                .getOrDefault(safeStage, 1.0D);
        double cycleMultiplier = settings.targetFormula().multiplierForMode(mode);
        double appliedMultiplier = Math.max(0.01D, stageMultiplier) * Math.max(0.01D, cycleMultiplier);
        long raw = Math.max(1L, Math.round(safeBase * appliedMultiplier));
        int snapped = snapToStep((int) Math.min(Integer.MAX_VALUE, raw), definition.targetStep());
        return new TargetResult(Math.max(1, snapped), appliedMultiplier, FORMULA_VERSION);
    }

    private static int deterministicSteppedRange(String seed, int min, int max, int step) {
        int safeStep = Math.max(1, step);
        int safeMin = Math.max(1, Math.min(min, max));
        int safeMax = Math.max(safeMin, Math.max(min, max));
        int buckets = ((safeMax - safeMin) / safeStep) + 1;
        int offset = Math.floorMod(seed == null ? 0 : seed.hashCode(), Math.max(1, buckets));
        int value = safeMin + (offset * safeStep);
        return Math.min(safeMax, Math.max(safeMin, value));
    }

    private static int snapToStep(int value, int step) {
        int safeValue = Math.max(1, value);
        int safeStep = Math.max(1, step);
        if (safeStep <= 1) {
            return safeValue;
        }
        return Math.max(1, ((safeValue + (safeStep / 2)) / safeStep) * safeStep);
    }
}
