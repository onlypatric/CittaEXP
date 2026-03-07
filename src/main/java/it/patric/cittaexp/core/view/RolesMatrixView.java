package it.patric.cittaexp.core.view;

import java.util.List;
import java.util.UUID;

public record RolesMatrixView(UUID cityId, List<RoleRow> rows) {

    public record RoleRow(
            String roleKey,
            String displayName,
            int priority,
            boolean canInvite,
            boolean canKick,
            boolean canManageMembers,
            boolean canManageRoles,
            boolean canRequestUpgrade,
            boolean canExpandClaims,
            boolean canManageSettings
    ) {
    }
}
