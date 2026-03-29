package it.patric.cittaexp.levelsgui;

import it.patric.cittaexp.levels.TownStage;
import it.patric.cittaexp.levels.PrincipalStage;
import it.patric.cittaexp.levels.SubTier;

public record CityLevelSummaryView(
        int townId,
        String townName,
        int currentLevel,
        int earnedLevel,
        double currentXp,
        TownStage currentStage,
        PrincipalStage principalStage,
        SubTier subTier,
        int stageSealsEarned,
        int stageSealsRequired,
        int claimCap,
        int memberCap,
        int spawnerCap,
        double currentBalance,
        TownStage nextStage,
        int nextRequiredLevel,
        double requiredBalance,
        double upgradeCost,
        double nextMonthlyTax,
        boolean staffApprovalRequired,
        double nextRequiredXp
) {
}
