package vn.tmc.central.attendanceprocessing.internal.service;

import vn.tmc.central.attendanceprocessing.api.AttendanceQueries;
import vn.tmc.central.attendanceprocessing.api.AttendanceView;
import vn.tmc.central.attendanceprocessing.internal.repository.AttendanceRepository;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: service.
 * NGUỒN: main.pdf, FR-AT-01..04/06/07; §5.2.
 * TÀI LIỆU PDF: docs/tasks/02-requirements-rbac.md; docs/REQUIREMENTS.md.
 * TODO[Task 3]: Tính công từ event bất biến, ngày bắt đầu ca, bất thường và hiện diện.
 * TODO[Task 3]: Tách denied attempt/command result; không xem UNLOCK_ACCEPTED là passage.
 * TODO[Task 3]: Tiếp nhận sync theo contract mục PDF 4/5, không rollback cả batch chỉ vì một event lỗi.
 * PHỐI HỢP: Task 2, Task 4, Task 7.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận yêu cầu đọc/tính công theo nhân viên và scope đã được xác thực.
 * - Cần làm: Hoàn thiện xử lý IN/OUT, ca qua ngày, bất thường và bản ghi dẫn xuất từ event bất biến.
 * - Lưu ý bàn giao: Ca đêm kết thúc 06:00 ngày 01 thuộc kỳ tháng trước; không tính denied attempt hoặc ACK mở cổng thành giờ công.
 */
public final class AttendanceProcessingService implements AttendanceQueries {
    private final AttendanceRepository repository;

    public AttendanceProcessingService(AttendanceRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }

    // TODO: use repository after authorization and business rules are implemented.
    @Override
    public AttendanceView findAttendance(String employeeId) {
        throw new UnsupportedOperationException("TODO: findAttendance");
    }
}
