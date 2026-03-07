package it.patric.cittaexp.persistence.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import it.patric.cittaexp.core.model.InvitationStatus;
import it.patric.cittaexp.core.model.JoinRequestStatus;
import it.patric.cittaexp.persistence.domain.AuditEventRecord;
import it.patric.cittaexp.persistence.domain.CapitalStateRecord;
import it.patric.cittaexp.persistence.domain.CityInvitationRecord;
import it.patric.cittaexp.persistence.domain.CityMemberRecord;
import it.patric.cittaexp.persistence.domain.CityRecord;
import it.patric.cittaexp.persistence.domain.CityRoleRecord;
import it.patric.cittaexp.persistence.domain.CityTreasuryLedgerRecord;
import it.patric.cittaexp.persistence.domain.CityViceRecord;
import it.patric.cittaexp.persistence.domain.ClaimBindingRecord;
import it.patric.cittaexp.persistence.domain.FreezeCaseRecord;
import it.patric.cittaexp.persistence.domain.JoinRequestRecord;
import it.patric.cittaexp.persistence.domain.MemberClaimPermissionRecord;
import it.patric.cittaexp.persistence.domain.MonthlyCycleStateRecord;
import it.patric.cittaexp.persistence.domain.RankingSnapshotRecord;
import it.patric.cittaexp.persistence.domain.TaxPolicyRecord;
import java.util.UUID;
import java.util.Locale;

final class OutboxEventCodec {

    private static final Gson GSON = new Gson();

    String cityUpsertPayload(CityRecord city, int expectedRevision) {
        JsonObject root = new JsonObject();
        root.addProperty("cityId", city.cityId().toString());
        root.addProperty("name", city.name());
        root.addProperty("tag", city.tag());
        root.addProperty("leaderUuid", city.leaderUuid().toString());
        root.addProperty("tier", city.tier().name());
        root.addProperty("status", city.status().name());
        root.addProperty("capital", city.capital());
        root.addProperty("frozen", city.frozen());
        root.addProperty("treasuryBalance", city.treasuryBalance());
        root.addProperty("memberCount", city.memberCount());
        root.addProperty("maxMembers", city.maxMembers());
        root.addProperty("createdAt", city.createdAtEpochMilli());
        root.addProperty("updatedAt", city.updatedAtEpochMilli());
        root.addProperty("expectedRevision", expectedRevision);
        return GSON.toJson(root);
    }

    String cityHardDeletePayload(UUID cityId) {
        JsonObject root = new JsonObject();
        root.addProperty("cityId", cityId.toString());
        return GSON.toJson(root);
    }

    String memberUpsertPayload(CityMemberRecord member) {
        JsonObject root = new JsonObject();
        root.addProperty("cityId", member.cityId().toString());
        root.addProperty("playerUuid", member.playerUuid().toString());
        root.addProperty("roleKey", member.roleKey());
        root.addProperty("joinedAt", member.joinedAtEpochMilli());
        root.addProperty("active", member.active());
        return GSON.toJson(root);
    }

    String memberDeletePayload(java.util.UUID cityId, java.util.UUID playerUuid) {
        JsonObject root = new JsonObject();
        root.addProperty("cityId", cityId.toString());
        root.addProperty("playerUuid", playerUuid.toString());
        return GSON.toJson(root);
    }

    String roleUpsertPayload(CityRoleRecord role) {
        JsonObject root = new JsonObject();
        root.addProperty("cityId", role.cityId().toString());
        root.addProperty("roleKey", role.roleKey());
        root.addProperty("displayName", role.displayName());
        root.addProperty("priority", role.priority());
        root.addProperty("permissionsJson", role.permissionsJson());
        return GSON.toJson(root);
    }

    String roleDeletePayload(java.util.UUID cityId, String roleKey) {
        JsonObject root = new JsonObject();
        root.addProperty("cityId", cityId.toString());
        root.addProperty("roleKey", roleKey);
        return GSON.toJson(root);
    }

    String invitationUpsertPayload(CityInvitationRecord invitation) {
        JsonObject root = new JsonObject();
        root.addProperty("invitationId", invitation.invitationId().toString());
        root.addProperty("cityId", invitation.cityId().toString());
        root.addProperty("invitedPlayerUuid", invitation.invitedPlayerUuid().toString());
        root.addProperty("invitedByUuid", invitation.invitedByUuid().toString());
        root.addProperty("status", invitation.status().name());
        root.addProperty("createdAt", invitation.createdAtEpochMilli());
        root.addProperty("expiresAt", invitation.expiresAtEpochMilli());
        root.addProperty("updatedAt", invitation.updatedAtEpochMilli());
        return GSON.toJson(root);
    }

    String invitationStatusPayload(UUID invitationId, InvitationStatus status, long updatedAtEpochMilli) {
        JsonObject root = new JsonObject();
        root.addProperty("invitationId", invitationId.toString());
        root.addProperty("status", status.name());
        root.addProperty("updatedAt", updatedAtEpochMilli);
        return GSON.toJson(root);
    }

    String joinRequestUpsertPayload(JoinRequestRecord request) {
        JsonObject root = new JsonObject();
        root.addProperty("requestId", request.requestId().toString());
        root.addProperty("cityId", request.cityId().toString());
        root.addProperty("playerUuid", request.playerUuid().toString());
        root.addProperty("status", request.status().name());
        root.addProperty("message", request.message());
        root.addProperty("reviewedByUuid", request.reviewedByUuid() == null ? "" : request.reviewedByUuid().toString());
        root.addProperty("requestedAt", request.requestedAtEpochMilli());
        root.addProperty("reviewedAt", request.reviewedAtEpochMilli());
        return GSON.toJson(root);
    }

    String joinRequestStatusPayload(UUID requestId, JoinRequestStatus status, UUID reviewedByUuid, long reviewedAtEpochMilli) {
        JsonObject root = new JsonObject();
        root.addProperty("requestId", requestId.toString());
        root.addProperty("status", status.name());
        root.addProperty("reviewedByUuid", reviewedByUuid == null ? "" : reviewedByUuid.toString());
        root.addProperty("reviewedAt", reviewedAtEpochMilli);
        return GSON.toJson(root);
    }

    String freezeCaseUpsertPayload(FreezeCaseRecord freezeCase) {
        JsonObject root = new JsonObject();
        root.addProperty("caseId", freezeCase.caseId().toString());
        root.addProperty("cityId", freezeCase.cityId().toString());
        root.addProperty("reason", freezeCase.reason().name());
        root.addProperty("details", freezeCase.details());
        root.addProperty("active", freezeCase.active());
        root.addProperty("openedBy", freezeCase.openedBy() == null ? "" : freezeCase.openedBy().toString());
        root.addProperty("closedBy", freezeCase.closedBy() == null ? "" : freezeCase.closedBy().toString());
        root.addProperty("openedAt", freezeCase.openedAtEpochMilli());
        root.addProperty("closedAt", freezeCase.closedAtEpochMilli());
        return GSON.toJson(root);
    }

    String freezeCaseClosePayload(UUID caseId, UUID closedBy, long closedAtEpochMilli) {
        JsonObject root = new JsonObject();
        root.addProperty("caseId", caseId.toString());
        root.addProperty("closedBy", closedBy == null ? "" : closedBy.toString());
        root.addProperty("closedAt", closedAtEpochMilli);
        return GSON.toJson(root);
    }

    String claimBindingUpsertPayload(ClaimBindingRecord claimBinding) {
        JsonObject root = new JsonObject();
        root.addProperty("cityId", claimBinding.cityId().toString());
        root.addProperty("worldName", claimBinding.worldName());
        root.addProperty("minX", claimBinding.minX());
        root.addProperty("minZ", claimBinding.minZ());
        root.addProperty("maxX", claimBinding.maxX());
        root.addProperty("maxZ", claimBinding.maxZ());
        root.addProperty("area", claimBinding.area());
        root.addProperty("createdAt", claimBinding.createdAtEpochMilli());
        root.addProperty("updatedAt", claimBinding.updatedAtEpochMilli());
        return GSON.toJson(root);
    }

    String cityViceUpsertPayload(CityViceRecord viceRecord) {
        JsonObject root = new JsonObject();
        root.addProperty("cityId", viceRecord.cityId().toString());
        root.addProperty("viceUuid", viceRecord.viceUuid() == null ? "" : viceRecord.viceUuid().toString());
        root.addProperty("updatedAt", viceRecord.updatedAtEpochMilli());
        return GSON.toJson(root);
    }

    String cityViceClearPayload(UUID cityId, long updatedAtEpochMilli) {
        JsonObject root = new JsonObject();
        root.addProperty("cityId", cityId.toString());
        root.addProperty("updatedAt", updatedAtEpochMilli);
        return GSON.toJson(root);
    }

    String memberClaimPermissionsUpsertPayload(MemberClaimPermissionRecord record) {
        JsonObject root = new JsonObject();
        root.addProperty("cityId", record.cityId().toString());
        root.addProperty("playerUuid", record.playerUuid().toString());
        root.addProperty("permAccess", record.access());
        root.addProperty("permContainer", record.container());
        root.addProperty("permBuild", record.build());
        root.addProperty("updatedBy", record.updatedBy() == null ? "" : record.updatedBy().toString());
        root.addProperty("updatedAt", record.updatedAtEpochMilli());
        return GSON.toJson(root);
    }

    String memberClaimPermissionsDeletePayload(UUID cityId, UUID playerUuid) {
        JsonObject root = new JsonObject();
        root.addProperty("cityId", cityId.toString());
        root.addProperty("playerUuid", playerUuid.toString());
        return GSON.toJson(root);
    }

    String taxPolicyUpsertPayload(TaxPolicyRecord record) {
        JsonObject root = new JsonObject();
        root.addProperty("policyId", record.policyId());
        root.addProperty("borgoMonthlyCost", record.borgoMonthlyCost());
        root.addProperty("villaggioMonthlyCost", record.villaggioMonthlyCost());
        root.addProperty("regnoMonthlyCost", record.regnoMonthlyCost());
        root.addProperty("regnoShopMonthlyExtra", record.regnoShopMonthlyExtra());
        root.addProperty("capitaleMonthlyBonus", record.capitaleMonthlyBonus());
        root.addProperty("dueDayOfMonth", record.dueDayOfMonth());
        root.addProperty("timezoneId", record.timezoneId());
        root.addProperty("updatedAt", record.updatedAtEpochMilli());
        return GSON.toJson(root);
    }

    String ledgerAppendPayload(CityTreasuryLedgerRecord record) {
        JsonObject root = new JsonObject();
        root.addProperty("entryId", record.entryId().toString());
        root.addProperty("cityId", record.cityId().toString());
        root.addProperty("entryType", record.entryType().name());
        root.addProperty("amount", record.amount());
        root.addProperty("resultingBalance", record.resultingBalance());
        root.addProperty("reason", record.reason());
        root.addProperty("actorUuid", record.actorUuid() == null ? "" : record.actorUuid().toString());
        root.addProperty("occurredAt", record.occurredAtEpochMilli());
        return GSON.toJson(root);
    }

    String capitalStateUpsertPayload(CapitalStateRecord record) {
        JsonObject root = new JsonObject();
        root.addProperty("capitalSlot", record.capitalSlot());
        root.addProperty("cityId", record.cityId().toString());
        root.addProperty("assignedAt", record.assignedAtEpochMilli());
        root.addProperty("updatedAt", record.updatedAtEpochMilli());
        root.addProperty("sourceRankingVersion", record.sourceRankingVersion());
        return GSON.toJson(root);
    }

    String capitalStateClearPayload(String capitalSlot) {
        JsonObject root = new JsonObject();
        root.addProperty("capitalSlot", capitalSlot);
        return GSON.toJson(root);
    }

    String rankingSnapshotUpsertPayload(RankingSnapshotRecord record) {
        JsonObject root = new JsonObject();
        root.addProperty("cityId", record.cityId().toString());
        root.addProperty("rank", record.rank());
        root.addProperty("score", record.score());
        root.addProperty("sourceVersion", record.sourceVersion());
        root.addProperty("fetchedAt", record.fetchedAtEpochMilli());
        return GSON.toJson(root);
    }

    String rankingSnapshotDeletePayload(UUID cityId) {
        JsonObject root = new JsonObject();
        root.addProperty("cityId", cityId.toString());
        return GSON.toJson(root);
    }

    String monthlyCycleStateUpsertPayload(MonthlyCycleStateRecord record) {
        JsonObject root = new JsonObject();
        root.addProperty("cycleKey", record.cycleKey());
        root.addProperty("lastProcessedMonth", record.lastProcessedMonth());
        root.addProperty("lastRunAt", record.lastRunAtEpochMilli());
        root.addProperty("lastStatus", record.lastStatus());
        root.addProperty("lastError", record.lastError());
        return GSON.toJson(root);
    }

    String auditAppendPayload(AuditEventRecord event) {
        JsonObject root = new JsonObject();
        root.addProperty("eventId", event.eventId().toString());
        root.addProperty("aggregateType", event.aggregateType());
        root.addProperty("aggregateId", event.aggregateId());
        root.addProperty("eventType", event.eventType());
        root.addProperty("actorUuid", event.actorUuid() == null ? "" : event.actorUuid().toString());
        root.addProperty("payloadJson", event.payloadJson());
        root.addProperty("occurredAt", event.occurredAtEpochMilli());
        return GSON.toJson(root);
    }

    JsonObject parse(String payloadJson) {
        return GSON.fromJson(payloadJson, JsonObject.class);
    }

    static String normalizeKey(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}
