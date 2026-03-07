package it.patric.cittaexp.persistence.domain;

import java.util.Objects;
import java.util.UUID;

public record CityRoleRecord(
        UUID cityId,
        String roleKey,
        String displayName,
        int priority,
        String permissionsJson
) {

    public CityRoleRecord {
        cityId = Objects.requireNonNull(cityId, "cityId");
        roleKey = requireText(roleKey, "roleKey");
        displayName = requireText(displayName, "displayName");
        permissionsJson = requireText(permissionsJson, "permissionsJson");
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
