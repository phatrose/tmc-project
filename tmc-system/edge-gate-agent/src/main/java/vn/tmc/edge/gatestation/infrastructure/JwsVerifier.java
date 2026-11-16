package vn.tmc.edge.gatestation.infrastructure;

import vn.tmc.edge.gatestation.domain.QrVerificationPort;
import vn.tmc.edge.gatestation.domain.VerifiedQr;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 6.
 * VAI TRÒ: Adapter / Controller.
 * NGUỒN: main.pdf, §7.2–7.4.
 * TÀI LIỆU PDF: docs/tasks/07-qr-security.md; docs/REQUIREMENTS.md.
 * TODO[Task 6]: Allowlist ES256, trusted kid, chữ ký, iss/aud, iat/nbf/exp và TTL/skew.
 * TODO[Task 6]: Kiểm tra revocation cache; trả claims để use case kiểm tra policy/reserve replay; không giữ private key.
 * TODO[Task 6]: Unknown kid: từ chối lượt hiện tại; refresh khóa chạy nền qua mTLS, không chờ Central.
 * PHỐI HỢP: Task 1, Task 2, Task 7.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận token, đọc khóa công khai tin cậy và cache thu hồi tại Edge.
 * - Cần làm: Trả VerifiedQr chỉ sau khi chữ ký và claims hợp lệ; không giữ private key hoặc log JWS.
 * - Lưu ý bàn giao: Unknown kid từ chối lượt hiện tại, refresh chạy nền; replay/policy do use case và store phối hợp kiểm tra.
 */
public final class JwsVerifier implements QrVerificationPort {
    @Override
    public VerifiedQr verify(String token) {
        throw new UnsupportedOperationException("TODO: verify");
    }
}
