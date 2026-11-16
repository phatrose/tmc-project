package vn.tmc.central.reportingaudit.internal.service;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 4.
 * VAI TRÒ: service.
 * NGUỒN: main.pdf, FR-RP-05; BR-08; NFR-AUD-01.
 * TÀI LIỆU PDF: docs/tasks/02-requirements-rbac.md; docs/REQUIREMENTS.md.
 * TODO[Task 4]: Audit append-only cho thao tác nhạy cảm, từ chối, override và lỗi sync.
 * TODO[Task 4]: Actor/device, thời gian, kết quả, correlation ID; không log JWS thô/secret.
 * PHỐI HỢP: Task 3, Task 5, Task 8.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận actorId, hành động và correlationId; bổ sung tài nguyên/thời gian/kết quả theo contract audit.
 * - Cần làm: Ghi append-only, tối thiểu dữ liệu nhạy cảm; cung cấp tìm kiếm theo actor/thời gian/hành động với data scope.
 * - Lưu ý bàn giao: Không cho Auditor hoặc API thông thường sửa/xóa audit; không ghi mật khẩu, private key hoặc JWS.
 */
public final class AuditService {
    public void append(String actorId, String action, String correlationId) {
        throw new UnsupportedOperationException("TODO: append");
    }
}
