package it.patric.cittaexp.core.service;

import it.patric.cittaexp.core.model.CityRole;
import it.patric.cittaexp.core.model.RolePermissionSet;
import java.util.List;
import java.util.UUID;

public interface RoleService {

    CityRole createRole(UUID cityId, UUID actorUuid, String roleKey, String displayName, int priority, RolePermissionSet permissions);

    CityRole updateRole(UUID cityId, UUID actorUuid, String roleKey, String displayName, int priority, RolePermissionSet permissions);

    void deleteRole(UUID cityId, UUID actorUuid, String roleKey);

    List<CityRole> listRoles(UUID cityId);
}
