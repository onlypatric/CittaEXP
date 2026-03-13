package it.patric.cittaexp.levels;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class StaffApprovalService {

    public record SubmitResult(boolean success, String reason, StaffApprovalRequest request) {
    }

    public record ReviewResult(boolean success, String reason, StaffApprovalRequest request) {
    }

    private final CityLevelStore store;

    public StaffApprovalService(CityLevelStore store) {
        this.store = store;
    }

    public SubmitResult submitStageUpgradeRequest(int townId, TownStage targetStage, UUID requestedBy, String reason) {
        if (hasPendingStageRequest(townId, targetStage)) {
            return new SubmitResult(false, "pending-request-exists", null);
        }
        long requestId = store.nextApprovalRequestId();
        String payload = StaffApprovalRequest.encodePayload(Map.of("targetStage", targetStage.name()));
        StaffApprovalRequest request = new StaffApprovalRequest(
                requestId,
                townId,
                StaffApprovalType.STAGE_UPGRADE,
                payload,
                CityLevelRepository.RequestStatus.PENDING,
                requestedBy,
                null,
                reason,
                null,
                Instant.now(),
                null
        );
        store.upsertApprovalRequest(request);
        store.appendApprovalEvent(request.id(), request.townId(), "created", requestedBy, payload);
        return new SubmitResult(true, "created", request);
    }

    public SubmitResult submitXpBonusRequest(int townId, long xpDeltaScaled, UUID requestedBy, String reason) {
        long requestId = store.nextApprovalRequestId();
        String payload = StaffApprovalRequest.encodePayload(Map.of(
                "xpDeltaScaled", Long.toString(xpDeltaScaled),
                "reason", reason == null ? "" : reason
        ));
        StaffApprovalRequest request = new StaffApprovalRequest(
                requestId,
                townId,
                StaffApprovalType.XP_BONUS,
                payload,
                CityLevelRepository.RequestStatus.PENDING,
                requestedBy,
                null,
                reason,
                null,
                Instant.now(),
                null
        );
        store.upsertApprovalRequest(request);
        store.appendApprovalEvent(request.id(), request.townId(), "created", requestedBy, payload);
        return new SubmitResult(true, "created", request);
    }

    public Optional<StaffApprovalRequest> findRequest(long requestId) {
        return store.findApprovalRequest(requestId);
    }

    public List<StaffApprovalRequest> listRequests(
            Optional<CityLevelRepository.RequestStatus> status,
            Optional<StaffApprovalType> type
    ) {
        return store.listApprovalRequests(status, type);
    }

    public List<StaffApprovalRequest> listStageRequests(Optional<CityLevelRepository.RequestStatus> status) {
        return listRequests(status, Optional.of(StaffApprovalType.STAGE_UPGRADE));
    }

    public boolean hasPendingStageRequest(int townId, TownStage stage) {
        return listStageRequests(Optional.of(CityLevelRepository.RequestStatus.PENDING)).stream()
                .anyMatch(request -> request.townId() == townId && stage.name().equalsIgnoreCase(request.payloadValue("targetStage")));
    }

    public ReviewResult approve(long requestId, UUID reviewer, String note) {
        Optional<StaffApprovalRequest> raw = store.findApprovalRequest(requestId);
        if (raw.isEmpty()) {
            return new ReviewResult(false, "request-not-found", null);
        }
        StaffApprovalRequest request = raw.get();
        if (request.status() != CityLevelRepository.RequestStatus.PENDING) {
            return new ReviewResult(false, "request-not-pending", request);
        }
        StaffApprovalRequest updated = request.withStatus(
                CityLevelRepository.RequestStatus.APPROVED,
                reviewer,
                note,
                Instant.now()
        );
        store.upsertApprovalRequest(updated);
        store.appendApprovalEvent(updated.id(), updated.townId(), "approved", reviewer, note);
        return new ReviewResult(true, "approved", updated);
    }

    public ReviewResult approveXpBonus(long requestId, UUID reviewer, String note, CityLevelService cityLevelService) {
        Optional<StaffApprovalRequest> raw = store.findApprovalRequest(requestId);
        if (raw.isEmpty()) {
            return new ReviewResult(false, "request-not-found", null);
        }
        StaffApprovalRequest request = raw.get();
        if (request.type() != StaffApprovalType.XP_BONUS) {
            return new ReviewResult(false, "request-wrong-type", request);
        }
        if (request.status() != CityLevelRepository.RequestStatus.PENDING) {
            return new ReviewResult(false, "request-not-pending", request);
        }
        String rawDelta = request.payloadValue("xpDeltaScaled");
        long deltaScaled;
        try {
            deltaScaled = Long.parseLong(rawDelta == null ? "0" : rawDelta);
        } catch (NumberFormatException ignored) {
            return new ReviewResult(false, "request-invalid-payload", request);
        }
        if (deltaScaled <= 0L) {
            return new ReviewResult(false, "request-invalid-payload", request);
        }
        cityLevelService.grantXpBonus(request.townId(), deltaScaled, reviewer, "staff-xp-bonus");
        return approve(requestId, reviewer, note);
    }

    public ReviewResult reject(long requestId, UUID reviewer, String reason) {
        Optional<StaffApprovalRequest> raw = store.findApprovalRequest(requestId);
        if (raw.isEmpty()) {
            return new ReviewResult(false, "request-not-found", null);
        }
        StaffApprovalRequest request = raw.get();
        if (request.status() != CityLevelRepository.RequestStatus.PENDING) {
            return new ReviewResult(false, "request-not-pending", request);
        }
        StaffApprovalRequest updated = request.withStatus(
                CityLevelRepository.RequestStatus.REJECTED,
                reviewer,
                reason,
                Instant.now()
        );
        store.upsertApprovalRequest(updated);
        store.appendApprovalEvent(updated.id(), updated.townId(), "rejected", reviewer, reason);
        return new ReviewResult(true, "rejected", updated);
    }

    public ReviewResult cancel(long requestId, UUID actor, String reason) {
        Optional<StaffApprovalRequest> raw = store.findApprovalRequest(requestId);
        if (raw.isEmpty()) {
            return new ReviewResult(false, "request-not-found", null);
        }
        StaffApprovalRequest request = raw.get();
        if (request.status() != CityLevelRepository.RequestStatus.PENDING) {
            return new ReviewResult(false, "request-not-pending", request);
        }
        StaffApprovalRequest updated = request.withStatus(
                CityLevelRepository.RequestStatus.CANCELLED,
                actor,
                reason,
                Instant.now()
        );
        store.upsertApprovalRequest(updated);
        store.appendApprovalEvent(updated.id(), updated.townId(), "cancelled", actor, reason);
        return new ReviewResult(true, "cancelled", updated);
    }

    public Optional<TownStage> resolveTargetStage(StaffApprovalRequest request) {
        if (request.type() != StaffApprovalType.STAGE_UPGRADE) {
            return Optional.empty();
        }
        String raw = request.payloadValue("targetStage");
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(TownStage.valueOf(raw));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public String diagnosticsSummary() {
        Map<StaffApprovalType, Long> byType = listRequests(Optional.empty(), Optional.empty()).stream()
                .collect(Collectors.groupingBy(StaffApprovalRequest::type, Collectors.counting()));
        if (byType.isEmpty()) {
            return "-";
        }
        return byType.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey().name() + ':' + entry.getValue())
                .collect(Collectors.joining(","));
    }
}
