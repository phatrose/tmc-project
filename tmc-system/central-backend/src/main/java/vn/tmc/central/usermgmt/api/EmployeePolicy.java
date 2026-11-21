package vn.tmc.central.usermgmt.api;

import java.time.Instant;
import java.util.List;

/**
 * PHỤ TRÁCH: Task 1.
 * Chính sách phân quyền khu vực/ca làm việc của nhân viên.
 * Theo CONTRACT-V1.md §4.
 *
 * @param employeeId mã ID nhân viên
 * @param assignments danh sách phân quyền ca/cổng
 * @param version số phiên bản chính sách phục vụ optimistic locking
 */
public record EmployeePolicy(
        String employeeId,
        List<Assignment> assignments,
        int version
) {
    /**
     * Một phân vùng quyền gồm các cổng, ca làm và khoảng thời gian hiệu lực.
     */
    public record Assignment(
            List<String> gateIds,
            List<String> shiftIds,
            Instant validFrom,
            Instant validUntil
    ) {}
}
