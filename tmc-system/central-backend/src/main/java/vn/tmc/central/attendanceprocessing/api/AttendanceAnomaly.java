package vn.tmc.central.attendanceprocessing.api;

/**
 * PHỤ TRÁCH: Task 3.
 * Các loại bất thường trong chấm công (anomalies).
 */
public enum AttendanceAnomaly {
    CONSECUTIVE_ENTRY,     // Vào liên tiếp mà không có lần ra tương ứng
    CONSECUTIVE_EXIT,      // Ra liên tiếp mà không có lần vào tương ứng
    MISSING_IN,            // Thiếu quẹt vào
    MISSING_OUT,           // Thiếu quẹt ra
    LATE_ARRIVAL,          // Đi làm trễ so với ca
    EARLY_DEPARTURE,       // Về sớm so với ca
    RAPID_REPEAT_SCAN,     // Quét lặp nghiệp vụ trong thời gian ngắn (<30s)
    CROSS_GATE_REPLAY,     // Token jti được tái sử dụng giữa các cổng khác nhau
    POST_REVOKE_VIOLATION  // Quẹt thẻ sau khi token/credential đã bị thu hồi (phát hiện khi reconnect)
}
