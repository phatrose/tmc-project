package vn.tmc.central.usermgmt.api;

import java.time.Instant;

/**
 * PHỤ TRÁCH: Task 1.
 * DTO đại diện cho nhân viên theo CONTRACT-V1.md §4.
 *
 * @param id mã UUID định danh nhân viên
 * @param employeeCode mã nhân viên nghiệp vụ (unique, 1-64 ký tự)
 * @param displayName tên hiển thị của nhân viên (1-200 ký tự)
 * @param factoryId mã UUID nhà máy trực thuộc
 * @param departmentId mã UUID phòng ban trực thuộc (nullable)
 * @param active trạng thái kích hoạt (false = xóa mềm / nghỉ việc)
 * @param version số phiên bản phục vụ optimistic locking (bắt đầu từ 1)
 * @param updatedAt thời điểm cập nhật lần cuối (UTC ISO 8601)
 */
public record EmployeeView(
        String id,
        String employeeCode,
        String displayName,
        String factoryId,
        String departmentId,
        boolean active,
        int version,
        Instant updatedAt
) {}
