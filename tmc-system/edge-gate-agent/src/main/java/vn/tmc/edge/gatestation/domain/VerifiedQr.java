package vn.tmc.edge.gatestation.domain;

import java.time.Instant;

import vn.tmc.contracts.QrClaims;

/**
 * PHỤ TRÁCH: Task 6.
 * Kết quả xác minh QR: claims đã qua chữ ký/iss/aud/thời gian/revocation, kèm
 * kid. Chưa đồng nghĩa được phép mở cổng — use case còn phải kiểm policy,
 * reserve replay jti và ghi event trong cùng transaction (Task 2/7).
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Chỉ verifier tin cậy (JwsVerifier) được tạo record này.
 * - Cần làm: dùng employeeId() để tra policy, jti() để reserve replay,
 *   replayReservationExpiresAt() cho ReplayReservation (expiresAt = exp+5s, §10).
 * - Lưu ý bàn giao: Không giữ token thô trong record để tránh rò rỉ vào log.
 */
public record VerifiedQr(QrClaims claims, String kid) {

    public VerifiedQr {
        if (claims == null) {
            throw new IllegalArgumentException("claims bắt buộc");
        }
        if (kid == null || kid.isBlank()) {
            throw new IllegalArgumentException("kid bắt buộc");
        }
    }

    public String employeeId() {
        return claims.sub();
    }

    public String jti() {
        return claims.jti();
    }

    /** tokenIssuedAt cho AttendancePayload (Task 3 thêm trường này vào payload). */
    public Instant tokenIssuedAt() {
        return claims.issuedAt();
    }

    /** Hạn reserve replay jti: exp + skew 5 giây (CONTRACT-V1.md §10). */
    public Instant replayReservationExpiresAt() {
        return claims.expiresAt().plusSeconds(QrClaims.MAX_CLOCK_SKEW_SECONDS);
    }
}
