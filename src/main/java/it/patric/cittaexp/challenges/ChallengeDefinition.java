package it.patric.cittaexp.challenges;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ChallengeDefinition(
        String id,
        String displayName,
        ChallengeObjectiveType objectiveType,
        int category,
        String bundleSlot,
        String difficultyTag,
        String objectiveFamily,
        int target,
        int targetMin,
        int targetMax,
        int targetStep,
        int weight,
        Set<ChallengeMode> cyclesSupported,
        ChallengeVariantMode variantMode,
        double specificChance,
        Map<ChallengeMode, Double> specificChanceByCycle,
        ChallengeFocusType focusType,
        List<ChallengeFocusOption> focusPool,
        String rewardBundleId,
        boolean enabled
) {

    public ChallengeDefinition {
        bundleSlot = normalizeToken(bundleSlot, "support");
        difficultyTag = normalizeToken(difficultyTag, "medium");
        objectiveFamily = normalizeToken(objectiveFamily, objectiveType == null ? "generic" : objectiveType.id());
        variantMode = variantMode == null ? ChallengeVariantMode.GLOBAL_ONLY : variantMode;
        specificChance = clampChance(specificChance);
        specificChanceByCycle = specificChanceByCycle == null ? Map.of() : Map.copyOf(specificChanceByCycle);
        focusType = focusType == null ? ChallengeFocusType.NONE : focusType;
        focusPool = focusPool == null ? List.of() : List.copyOf(focusPool);
    }

    public int resolveTarget(String instanceSeed) {
        int fallback = Math.max(1, target);
        int min = Math.max(1, targetMin);
        int max = Math.max(min, targetMax);
        int step = Math.max(1, targetStep);
        if (min >= max) {
            return min;
        }
        int slots = (max - min) / step;
        if (slots <= 0) {
            return min;
        }
        int bucketCount = slots + 1;
        int index = Math.floorMod(instanceSeed == null ? 0 : instanceSeed.hashCode(), bucketCount);
        return min + (index * step);
    }

    public double resolveSpecificChance(ChallengeMode mode) {
        Double override = mode == null ? null : specificChanceByCycle.get(mode);
        return clampChance(override == null ? specificChance : override);
    }

    public ChallengeVariantType resolveVariant(ChallengeMode mode, String instanceSeed) {
        return switch (variantMode) {
            case GLOBAL_ONLY -> ChallengeVariantType.GLOBAL;
            case SPECIFIC_ONLY -> focusPool.isEmpty() ? ChallengeVariantType.GLOBAL : ChallengeVariantType.SPECIFIC;
            case MIXED -> {
                if (focusPool.isEmpty()) {
                    yield ChallengeVariantType.GLOBAL;
                }
                int bucket = Math.floorMod(hash("variant", instanceSeed), 10_000);
                int threshold = (int) Math.round(resolveSpecificChance(mode) * 10_000.0D);
                yield bucket < threshold ? ChallengeVariantType.SPECIFIC : ChallengeVariantType.GLOBAL;
            }
        };
    }

    public ChallengeFocusOption resolveFocus(String instanceSeed) {
        if (focusPool.isEmpty()) {
            return null;
        }
        int index = Math.floorMod(hash("focus", instanceSeed), focusPool.size());
        return focusPool.get(index);
    }

    private static int hash(String prefix, String seed) {
        String value = (prefix == null ? "" : prefix) + ':' + (seed == null ? "" : seed);
        return value.hashCode();
    }

    private static String normalizeToken(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static double clampChance(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0D;
        }
        if (value < 0.0D) {
            return 0.0D;
        }
        if (value > 1.0D) {
            return 1.0D;
        }
        return value;
    }
}
