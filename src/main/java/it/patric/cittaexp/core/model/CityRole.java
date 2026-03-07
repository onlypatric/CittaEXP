package it.patric.cittaexp.core.model;

import java.util.Objects;
import java.util.UUID;

public record CityRole(
        UUID cityId,
        String roleKey,
        String displayName,
        int priority,
        RolePermissionSet permissions,
        long updatedAtEpochMilli
) {

    public CityRole {
        cityId = Objects.requireNonNull(cityId, "cityId");
        roleKey = requireText(roleKey, "roleKey");
        displayName = requireText(displayName, "displayName");
        permissions = Objects.requireNonNull(permissions, "permissions");
        if (updatedAtEpochMilli < 0L) {
            throw new IllegalArgumentException("updatedAtEpochMilli must be >= 0");
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
