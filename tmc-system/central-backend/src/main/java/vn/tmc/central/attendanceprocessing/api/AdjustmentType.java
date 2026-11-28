package vn.tmc.central.attendanceprocessing.api;

/**
 * PHỤ TRÁCH: Task 3.
 * Loại điều chỉnh chấm công.
 */
public enum AdjustmentType {
    ADD_IN,          // Bổ sung quẹt vào bị thiếu
    ADD_OUT,         // Bổ sung quẹt ra bị thiếu
    MODIFY_HOURS,    // Chỉnh sửa tổng số giờ làm việc
    EXCUSE_LATE,     // Miễn trừ / giải trình đi trễ
    EXCUSE_EARLY     // Miễn trừ / giải trình về sớm
}
