package it.patric.cittaexp.challenges;

import it.patric.cittaexp.levels.PrincipalStage;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public record AtlasFamilyDefinition(
        String id,
        String displayName,
        AtlasChapter chapter,
        ChallengeObjectiveType objectiveType,
        ChallengeFocusType focusType,
        Set<String> focusKeys,
        PrincipalStage minStage,
        Map<AtlasTier, Integer> targets,
        Map<AtlasTier, ChallengeRewardSpec> rewards,
        boolean enabled
) {

    public int target(AtlasTier tier) {
        if (tier == null || targets == null) {
            return 0;
        }
        return Math.max(0, targets.getOrDefault(tier, 0));
    }

    public ChallengeRewardSpec reward(AtlasTier tier) {
        if (tier == null || rewards == null) {
            return ChallengeRewardSpec.EMPTY;
        }
        ChallengeRewardSpec spec = rewards.get(tier);
        return spec == null ? ChallengeRewardSpec.EMPTY : spec;
    }

    public boolean supportsTier(AtlasTier tier) {
        return target(tier) > 0;
    }

    public Map<AtlasTier, Integer> normalizedTargets() {
        Map<AtlasTier, Integer> copy = new EnumMap<>(AtlasTier.class);
        if (targets != null) {
            copy.putAll(targets);
        }
        return copy;
    }
}
