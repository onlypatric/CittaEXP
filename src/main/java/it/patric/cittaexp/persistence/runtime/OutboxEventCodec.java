package it.patric.cittaexp.persistence.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import it.patric.cittaexp.persistence.domain.CityMemberRecord;
import it.patric.cittaexp.persistence.domain.CityRecord;
import it.patric.cittaexp.persistence.domain.CityRoleRecord;
import java.util.Locale;

final class OutboxEventCodec {

    private static final Gson GSON = new Gson();

    String cityUpsertPayload(CityRecord city, int expectedRevision) {
        JsonObject root = new JsonObject();
        root.addProperty("cityId", city.cityId().toString());
        root.addProperty("name", city.name());
        root.addProperty("tag", city.tag());
        root.addProperty("leaderUuid", city.leaderUuid().toString());
        root.addProperty("capital", city.capital());
        root.addProperty("frozen", city.frozen());
        root.addProperty("treasuryBalance", city.treasuryBalance());
        root.addProperty("createdAt", city.createdAtEpochMilli());
        root.addProperty("updatedAt", city.updatedAtEpochMilli());
        root.addProperty("expectedRevision", expectedRevision);
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

    JsonObject parse(String payloadJson) {
        return GSON.fromJson(payloadJson, JsonObject.class);
    }

    static String normalizeKey(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}
