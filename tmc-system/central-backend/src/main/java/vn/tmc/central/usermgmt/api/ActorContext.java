package vn.tmc.central.usermgmt.api;

import java.util.List;
import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 1.
 * Ngữ cảnh định danh theo CONTRACT-V1 §3, dùng chung với Task 6.
 * Chỉ tạo từ phiên/adapter đáng tin cậy, không lấy trực tiếp từ request body.
 * employeeId có thể null; các danh sách được sao chép để tránh sửa ngoài ý muốn.
 */
public record ActorContext(
        String userId,
        String employeeId,
        List<Role> roles,
        List<String> permissions,
        List<String> factoryIds,
        List<String> departmentIds,
        String scope) {

    public static final String SCOPE_SELF = "SELF";
    public static final String SCOPE_DEPARTMENT = "DEPARTMENT";
    public static final String SCOPE_FACTORY = "FACTORY";
    public static final String SCOPE_GLOBAL = "GLOBAL";

    public ActorContext {
        Objects.requireNonNull(userId, "userId");
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        factoryIds = factoryIds == null ? List.of() : List.copyOf(factoryIds);
        departmentIds = departmentIds == null ? List.of() : List.copyOf(departmentIds);
        Objects.requireNonNull(scope, "scope");
    }

    public boolean hasPermission(String permission) {
        return permission != null && permissions.contains(permission);
    }

    public boolean hasRole(Role role) {
        return role != null && roles.contains(role);
    }

    /** Auditor vẫn bị cấm mutation khi đồng thời có vai trò khác. */
    public boolean isAuditor() {
        return hasRole(Role.AUDITOR);
    }
}