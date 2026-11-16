package vn.tmc.central.attendanceprocessing.internal.service;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: service.
 * NGUỒN: main.pdf, FR-AT-05; BR-04/05; UC-05.
 * TÀI LIỆU PDF: docs/tasks/02-requirements-rbac.md; docs/REQUIREMENTS.md.
 * TODO[Task 3]: Yêu cầu/duyệt/từ chối có lý do và lịch sử.
 * TODO[Task 3]: Người tạo không tự duyệt; HR quyết định cuối; không sửa event gốc.
 * PHỐI HỢP: Task 2, Task 4, Task 7.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận eventId, operatorId và lý do điều chỉnh; danh tính phải lấy từ phiên đã xác thực.
 * - Cần làm: Bổ sung request/approval DTO và lịch sử; người tạo không tự duyệt, Auditor không được duyệt.
 * - Lưu ý bàn giao: Lưu điều chỉnh riêng và audit, không UPDATE/DELETE event gốc; kiểm thử trái quyền và lý do rỗng.
 */
public final class AttendanceAdjustmentService {
    public void requestAdjustment(String eventId, String operatorId, String reason) {
        throw new UnsupportedOperationException("TODO: requestAdjustment");
    }
}
