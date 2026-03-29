package it.patric.cittaexp.levelsgui;

import it.patric.cittaexp.defense.CityDefenseTier;
import it.patric.cittaexp.levels.CityLevelRepository;
import it.patric.cittaexp.levels.TownLevelRequest;
import it.patric.cittaexp.levels.TownStage;

public record CityLevelRowView(
        int level,
        double requiredXp,
        RowState rowState,
        TownStage stageForLevel,
        TownStage checkpointStage,
        int claimCap,
        int memberCap,
        int spawnerCap,
        boolean regnoExtraClaim,
        boolean actionable,
        boolean staffApprovalRequired,
        boolean spawnRequired,
        double requiredBalance,
        double upgradeCost,
        double monthlyTax,
        CityDefenseTier requiredDefenseTier,
        boolean defenseTierCompleted,
        TownLevelRequest latestRequest
) {
    public enum RowState {
        CURRENT,
        UNLOCKED,
        LOCKED
    }

    public boolean hasPendingRequest() {
        return latestRequest != null && latestRequest.status() == CityLevelRepository.RequestStatus.PENDING;
    }

    public boolean hasReviewedRequest() {
        return latestRequest != null && latestRequest.status() != CityLevelRepository.RequestStatus.PENDING;
    }
}
