package vn.tmc.central.usermgmt.api;

import java.util.List;

/**
 * PHỤ TRÁCH: Task 1.
 * DTO tài khoản người dùng theo CONTRACT-V1.md §4.
 * Tuyệt đối không trả mật khẩu hoặc hash ra ngoài wire.
 */
public record AccountView(
        String id,
        String username,
        String employeeId,
        List<Role> roles,
        List<String> factoryIds,
        List<String> departmentIds,
        String scope,
        boolean active,
        int version
) {}
