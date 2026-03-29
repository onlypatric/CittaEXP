package it.patric.cittaexp.levels;

import it.patric.cittaexp.defense.CityDefenseService;
import it.patric.cittaexp.integration.husktowns.HuskTownsApiHook;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import net.william278.husktowns.town.Member;
import org.bukkit.entity.Player;

public final class CityLevelRequestService {

    public record RequestResult(boolean success, String reason, TownLevelRequest request) {
    }

    private final HuskTownsApiHook huskTownsApiHook;
    private final StaffApprovalService approvalService;
    private final CityLevelService cityLevelService;
    private final CityLevelSettings settings;
    private final CityDefenseService cityDefenseService;

    public CityLevelRequestService(
            HuskTownsApiHook huskTownsApiHook,
            StaffApprovalService approvalService,
            CityLevelService cityLevelService,
            CityLevelSettings settings,
            CityDefenseService cityDefenseService
    ) {
        this.huskTownsApiHook = huskTownsApiHook;
        this.approvalService = approvalService;
        this.cityLevelService = cityLevelService;
        this.settings = settings;
        this.cityDefenseService = cityDefenseService;
    }

    public RequestResult createRequest(Player player) {
        Optional<Member> member = huskTownsApiHook.getUserTown(player);
        if (member.isEmpty()) {
            return new RequestResult(false, "no-town", null);
        }
        if (!member.get().town().getMayor().equals(player.getUniqueId())) {
            return new RequestResult(false, "not-mayor", null);
        }

        CityLevelService.LevelUnlockCheck check = cityLevelService.nextUnlockCheck(member.get().town().getId(), true);
        TownStage nextStage = check.checkpointStage();
        if (check.targetLevel() <= 0 || nextStage == null) {
            return new RequestResult(false, check.blockingReason() == null ? "request-not-required" : check.blockingReason(), null);
        }
        if (!check.staffApprovalRequired()) {
            return new RequestResult(false, "request-not-required", null);
        }
        if (!check.xpReached()) {
            return new RequestResult(false, "level-too-low", null);
        }
        if (!check.defenseTierCompleted()) {
            return new RequestResult(false, "defense-tier-required", null);
        }
        if (!check.spawnPresent()) {
            return new RequestResult(false, "town-spawn-required", null);
        }
        if (!check.bankBalanceSatisfied()) {
            return new RequestResult(false, "insufficient-balance-requirement", null);
        }
        if (approvalService.hasPendingStageRequest(check.townId(), nextStage)) {
            return new RequestResult(false, "pending-request-exists", null);
        }

        StaffApprovalService.SubmitResult submit = approvalService.submitStageUpgradeRequest(
                check.townId(),
                nextStage,
                player.getUniqueId(),
                "auto-manual-checkpoint-" + nextStage.name().toLowerCase(Locale.ROOT)
        );
        if (!submit.success() || submit.request() == null) {
            return new RequestResult(false, submit.reason(), null);
        }
        return new RequestResult(true, "created", toTownLevelRequest(submit.request()));
    }

    public List<TownLevelRequest> listRequests(Optional<CityLevelRepository.RequestStatus> status) {
        return approvalService.listStageRequests(status).stream()
                .map(this::toTownLevelRequest)
                .collect(Collectors.toList());
    }

    public Optional<TownLevelRequest> latestRequestForTownStage(int townId, TownStage stage) {
        if (townId <= 0 || stage == null) {
            return Optional.empty();
        }
        return approvalService.listStageRequests(Optional.empty()).stream()
                .filter(request -> request.townId() == townId)
                .filter(request -> approvalService.resolveTargetStage(request).filter(stage::equals).isPresent())
                .findFirst()
                .map(this::toTownLevelRequest);
    }

    public RequestResult approve(Player staff, long requestId, String note) {
        Optional<StaffApprovalRequest> raw = approvalService.findRequest(requestId);
        if (raw.isEmpty() || raw.get().type() != StaffApprovalType.STAGE_UPGRADE) {
            return new RequestResult(false, "request-not-found", null);
        }
        StaffApprovalRequest request = raw.get();
        if (request.status() != CityLevelRepository.RequestStatus.PENDING) {
            return new RequestResult(false, "request-not-pending", toTownLevelRequest(request));
        }

        Optional<TownStage> targetStage = approvalService.resolveTargetStage(request);
        if (targetStage.isEmpty()) {
            return new RequestResult(false, "request-invalid-payload", toTownLevelRequest(request));
        }

        CityLevelService.UpgradeAttempt upgrade = cityLevelService.applyStageUpgrade(
                staff,
                request.townId(),
                targetStage.get(),
                "staff-approval"
        );
        if (!upgrade.success()) {
            return new RequestResult(false, "upgrade-failed-" + upgrade.reason(), toTownLevelRequest(request));
        }

        StaffApprovalService.ReviewResult review = approvalService.approve(requestId, staff.getUniqueId(), note);
        if (!review.success()) {
            return new RequestResult(false, review.reason(), toTownLevelRequest(request));
        }
        return new RequestResult(true, "approved", toTownLevelRequest(review.request()));
    }

    public RequestResult reject(Player staff, long requestId, String reason) {
        StaffApprovalService.ReviewResult review = approvalService.reject(requestId, staff.getUniqueId(), reason);
        if (!review.success()) {
            return new RequestResult(false, review.reason(), review.request() == null ? null : toTownLevelRequest(review.request()));
        }
        return new RequestResult(true, "rejected", toTownLevelRequest(review.request()));
    }

    private TownLevelRequest toTownLevelRequest(StaffApprovalRequest request) {
        TownStage stage = approvalService.resolveTargetStage(request).orElse(TownStage.AVAMPOSTO_I);
        return new TownLevelRequest(
                request.id(),
                request.townId(),
                stage,
                request.status(),
                request.requestedBy(),
                request.reviewedBy(),
                request.reason(),
                request.note(),
                request.createdAt(),
                request.reviewedAt()
        );
    }
}
