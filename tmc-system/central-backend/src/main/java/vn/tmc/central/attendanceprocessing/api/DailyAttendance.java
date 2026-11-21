package vn.tmc.central.attendanceprocessing.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: DTO bản ghi tổng hợp công ngày theo CONTRACT-V1.md §9.
 * DailyAttendance = {employeeId:UUID, factoryId:UUID, shiftId:UUID?, workDate:LocalDate,
 *   firstIn:Instant?, lastOut:Instant?, workedMinutes:Integer?, lateMinutes:int,
 *   earlyMinutes:int, flags:List<String>, version:int}
 * 
 * QUY TẮC BẮT BUỘC (CONTRACT-V1 §9):
 * - Thiếu IN/OUT thì workedMinutes = null, không coi 0 giờ đã xác nhận.
 * - version: số nguyên quản lý optimistic concurrency.
 */
public record DailyAttendance(
        UUID employeeId,
        UUID factoryId,
        UUID shiftId,
        LocalDate workDate,
        Instant firstIn,
        Instant lastOut,
        Integer workedMinutes,
        int lateMinutes,
        int earlyMinutes,
        List<String> flags,
        int version
) {
    public DailyAttendance {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(factoryId, "factoryId must not be null");
        Objects.requireNonNull(workDate, "workDate must not be null");
        flags = flags != null ? Collections.unmodifiableList(flags) : List.of();
    }
}
