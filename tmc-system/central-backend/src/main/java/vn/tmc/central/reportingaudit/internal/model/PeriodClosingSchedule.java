package vn.tmc.central.reportingaudit.internal.model;

import java.time.LocalTime;

/**
 * PHỤ TRÁCH: Task 4.
 * Khung triển khai theo docs/DECISIONS.md; chưa có nghiệp vụ hoạt động.
 *
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Các mốc giờ địa phương dùng cho scheduler chốt công và đối soát Payroll.
 * - Cần làm: Task 4 cần ghép giờ với ngày 01 và timezone của Factory; lập job idempotent và xử lý lần chạy bị trễ.
 * - Lưu ý bàn giao: Hằng số không tự tạo lịch chạy; deadline là trước 12:00, không phải bắt đầu xuất lúc 12:00.
 */
public final class PeriodClosingSchedule {
    public static final LocalTime OFFICIAL_CLOSE = LocalTime.of(6, 30);
    public static final LocalTime PAYROLL_DEADLINE = LocalTime.NOON;
    private PeriodClosingSchedule() {}
    // TODO: scheduler ngày 01 theo timezone nhà máy; phải hoàn tất export TRƯỚC deadline.
    // Ca đêm kết thúc 06:00 ngày 01 thuộc tháng bắt đầu ca; 23:00 chỉ tổng hợp tạm.
}
