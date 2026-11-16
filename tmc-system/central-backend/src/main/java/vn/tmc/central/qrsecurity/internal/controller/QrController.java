package vn.tmc.central.qrsecurity.internal.controller;

import vn.tmc.central.qrsecurity.api.QrIssuer;
import vn.tmc.contracts.QrTokenResponse;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 6.
 * VAI TRÒ: controller.
 * NGUỒN: main.pdf, §4.2.1; §7.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 * TODO[Task 6]: GET /api/v1/me/qr-token: bearer, chỉ QR của chính người gọi.
 * TODO[Task 6]: GET /api/v1/qr/keys/refresh: mTLS, chỉ public JWKS.
 * TODO[Task 6]: Chốt route quản trị credential/revocation/rotation trước khi thêm.
 * PHỐI HỢP: Task 1, Task 2, Task 7.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: GET /api/v1/me/qr-token phải lấy employeeId từ danh tính đã xác thực.
 * - Cần làm: Ủy quyền cho QrIssuer; bổ sung response/error mapping và không cache/log token trong middleware.
 * - Lưu ý bàn giao: Chưa có HTTP mapping trong sườn; UI không được tự ký hoặc dùng employeeId nhập tay để lấy QR người khác.
 */
public final class QrController {
    private final QrIssuer service;

    public QrController(QrIssuer service) {
        this.service = java.util.Objects.requireNonNull(service);
    }

    // TODO[Task 6]: HTTP mapping, authenticated identity, RBAC/scope and validation.
    // Do not expose employeeId from untrusted input for /me/qr-token.
    public QrTokenResponse issueToken(String employeeId) {
        return service.issueToken(employeeId);
    }
}
