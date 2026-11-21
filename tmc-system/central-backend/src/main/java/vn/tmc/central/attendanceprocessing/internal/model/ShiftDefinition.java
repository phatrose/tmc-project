package vn.tmc.central.attendanceprocessing.internal.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 3.
 * Định nghĩa ca làm việc và các quy tắc thời gian:
 * - Ca ngày (Ca 1): 06:00 - 14:00
 * - Ca chiều (Ca 2): 14:00 - 22:00
 * - Ca đêm (Ca 3): 22:00 - 06:00 ngày hôm sau
 * 
 * QUY TẮC QUAN TRỌNG TỪ DECISIONS.md §1:
 * - Ca đêm bắt đầu cuối tháng trước, kết thúc 06:00 ngày 01, vẫn thuộc tháng trước!
 * - workDate của ca đêm là ngày bắt đầu ca (ngày cuối tháng trước).
 * - Tổng hợp và chốt chính thức bắt đầu 06:30 ngày 01.
 */
public record ShiftDefinition(
        String shiftCode,
        String name,
        LocalTime startTime,
        LocalTime endTime,
        boolean isCrossDay,
        int toleranceMinutes // dung sai cho phép đi trễ / về sớm không bị phạt (e.g. 15 phút)
) {
    public static final ShiftDefinition SHIFT_1 = new ShiftDefinition("SHIFT_1", "Ca Sáng", LocalTime.of(6, 0), LocalTime.of(14, 0), false, 15);
    public static final ShiftDefinition SHIFT_2 = new ShiftDefinition("SHIFT_2", "Ca Chiều", LocalTime.of(14, 0), LocalTime.of(22, 0), false, 15);
    public static final ShiftDefinition SHIFT_NIGHT = new ShiftDefinition("SHIFT_NIGHT", "Ca Đêm", LocalTime.of(22, 0), LocalTime.of(6, 0), true, 15);

    /**
     * Xác định workDate và ca làm việc từ thời điểm sự kiện và hướng quẹt (IN / OUT).
     * 
     * Hướng IN:
     * - 05:00 - 13:59: Ca 1 (ngày date)
     * - 14:00 - 21:29: Ca 2 (ngày date)
     * - 21:30 - 23:59: Ca Đêm (ngày date)
     * - 00:00 - 04:59: Ca Đêm vào muộn (ngày date.minusDays(1))
     * 
     * Hướng OUT:
     * - 00:00 - 08:00: Ca Đêm tan ca (ngày date.minusDays(1))
     * - 08:01 - 16:00: Ca 1 (ngày date)
     * - 16:01 - 23:59: Ca 2 (ngày date)
     */
    public static ShiftAssignment determineShiftAndWorkDate(ZonedDateTime eventTime, boolean isCheckIn) {
        Objects.requireNonNull(eventTime, "eventTime must not be null");
        LocalDate date = eventTime.toLocalDate();
        LocalTime time = eventTime.toLocalTime();

        if (isCheckIn) {
            if (time.isAfter(LocalTime.of(21, 29))) {
                // Vào ca đêm trước nửa đêm -> workDate là ngày date
                return new ShiftAssignment(SHIFT_NIGHT, date);
            }
            if (time.isBefore(LocalTime.of(5, 0))) {
                // Vào ca đêm sau nửa đêm (rạng sáng) -> ca bắt đầu từ hôm trước
                return new ShiftAssignment(SHIFT_NIGHT, date.minusDays(1));
            }
            if (time.isBefore(LocalTime.of(14, 0))) {
                // Ca sáng 06:00 - 14:00 (cho phép vào từ 05:00)
                return new ShiftAssignment(SHIFT_1, date);
            }
            // Ca chiều 14:00 - 22:00
            return new ShiftAssignment(SHIFT_2, date);
        } else {
            // Check-OUT
            if (time.isBefore(LocalTime.of(8, 0)) || time.equals(LocalTime.of(8, 0))) {
                // Ra ca đêm (kết thúc 06:00 sáng hôm sau, cho phép ra tới 08:00) -> thuộc ca đêm bắt đầu từ ngày hôm trước!
                return new ShiftAssignment(SHIFT_NIGHT, date.minusDays(1));
            }
            if (time.isBefore(LocalTime.of(16, 0)) || time.equals(LocalTime.of(16, 0))) {
                // Ra ca sáng (kết thúc 14:00, cho phép ra tới 16:00)
                return new ShiftAssignment(SHIFT_1, date);
            }
            // Ra ca chiều (kết thúc 22:00)
            return new ShiftAssignment(SHIFT_2, date);
        }
    }

    public record ShiftAssignment(ShiftDefinition shift, LocalDate workDate) {}
}
