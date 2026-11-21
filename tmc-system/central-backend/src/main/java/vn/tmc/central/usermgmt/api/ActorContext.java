package vn.tmc.central.usermgmt.api;

import java.util.List;

/**
 * PHỤ TRÁCH: Task 1.
 * Ngữ cảnh định danh và thẩm quyền của người dùng sau khi xác thực.
 * Theo CONTRACT-V1.md §3.
 *
 * @param userId ID tài khoản người dùng (UUIDv4)
 * @param employeeId ID nhân viên liên kết (nullable)
 * @param roles danh sách vai trò
 * @param permissions danh sách quyền hạn chuẩn
 * @param factoryIds danh sách nhà máy được gán
 * @param departmentIds danh sách phòng ban được gán
 * @param scope phạm vi thẩm quyền: "SELF" | "DEPARTMENT" | "FACTORY" | "GLOBAL"
 */
public record ActorContext(
        String userId,
        String employeeId,
        List<Role> roles,
        List<String> permissions,
        List<String> factoryIds,
        List<String> departmentIds,
        String scope
) {}
