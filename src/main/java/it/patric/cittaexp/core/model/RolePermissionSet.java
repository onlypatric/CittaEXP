package it.patric.cittaexp.core.model;

public record RolePermissionSet(
        boolean canInvite,
        boolean canKick,
        boolean canManageMembers,
        boolean canManageRoles,
        boolean canRequestUpgrade,
        boolean canExpandClaims,
        boolean canManageSettings
) {
}
