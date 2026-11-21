package vn.tmc.central.usermgmt.api;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: Ngữ cảnh danh tính tin cậy sau xác thực theo CONTRACT-V1.md §3.
 * Shape: {userId:UUID, employeeId:UUID?, roles:Role[], permissions:string[], factoryIds:UUID[], departmentIds:UUID[], scope:"SELF"|"DEPARTMENT"|"FACTORY"|"GLOBAL"}
 */
public record ActorContext(
        UUID userId,
        UUID employeeId,
        List<Role> roles,
        List<String> permissions,
        List<UUID> factoryIds,
        List<UUID> departmentIds,
        String scope
) {
    public ActorContext {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(roles, "roles must not be null");
        Objects.requireNonNull(permissions, "permissions must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        roles = Collections.unmodifiableList(roles);
        permissions = Collections.unmodifiableList(permissions);
        factoryIds = factoryIds != null ? Collections.unmodifiableList(factoryIds) : List.of();
        departmentIds = departmentIds != null ? Collections.unmodifiableList(departmentIds) : List.of();
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
