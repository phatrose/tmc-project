package vn.tmc.central.usermgmt.api;

import java.util.List;
import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 1 — file được tạo bởi Task 6 theo CONTRACT-V1.md §3/§10.
 * Contract §10 yêu cầu QrIssuer.issue(ActorContext); để nhánh Task 6 biên dịch
 * được, Task 6 dựng record đúng shape §3. Task 1 là chủ trì ActorContext: khi
 * hiện thực Session/ActorContext thật (kèm Authorization.require), Task 1 có
 * thể thay thế/điều chỉnh file này trong PR của mình; Task 6 sẽ theo đổi.
 *
 * Shape theo CONTRACT-V1.md §3:
 * ActorContext = {userId:UUID, employeeId:UUID?, roles:Role[], permissions:string[],
 *                 factoryIds:UUID[], departmentIds:UUID[], scope:...}
 * Đây là dữ liệu tin cậy do adapter/phiên đăng nhập tạo, KHÔNG deserialize từ
 * request body rồi coi là đã xác thực. Scope hiệu lực chỉ có một giá trị.
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

    /** Auditor bị cấm mutation kể cả khi phiên có role khác (CONTRACT-V1.md §3). */
    public boolean isAuditor() {
        return hasRole(Role.AUDITOR);
    }
}
