package vn.tmc.central.qrsecurity.internal.service;

import vn.tmc.central.qrsecurity.api.QrIssuer;
import vn.tmc.contracts.QrTokenResponse;
import vn.tmc.central.qrsecurity.internal.repository.QrCredentialRepository;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 6.
 * VAI TRÒ: service.
 * NGUỒN: main.pdf, FR-UQ-02..05; FR-QR-01..08; §7.
 * TÀI LIỆU PDF: docs/tasks/07-qr-security.md; docs/REQUIREMENTS.md.
 * TODO[Task 6]: JWS ES256, TTL mặc định 60 giây; kid ở JOSE protected header.
 * TODO[Task 6]: Issue/reissue/revoke credential, ZXing, rotation mặc định 90 ngày có overlap.
 * TODO[Task 6]: Private key chỉ qua KMS/HSM/Vault port; không lưu từng token hoặc phát unsigned token.
 * PHỐI HỢP: Task 1, Task 2, Task 7.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Cấp token cho nhân viên đã đăng nhập, còn hoạt động và có credential hợp lệ.
 * - Cần làm: Ký ES256 với kid ở header, 7 claims, TTL 60 giây và jti mới; kiểm tra quyền bằng policy cache tại Edge.
 * - Lưu ý bàn giao: Không lấy employeeId tùy ý từ client cho /me; QR/JWS chỉ trả cho màn hình app/web, không gửi email/SMS.
 */
public final class QrCredentialService implements QrIssuer {
    private final QrCredentialRepository repository;

    public QrCredentialService(QrCredentialRepository repository) {
        this.repository = java.util.Objects.requireNonNull(repository);
    }

    // TODO: use repository after authorization and business rules are implemented.
    @Override
    public QrTokenResponse issueToken(String employeeId) {
        throw new UnsupportedOperationException("TODO: issueToken");
    }
}
