package vn.tmc.contracts;

import java.time.Instant;
import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 6 (mới, theo CONTRACT-V1.md §8).
 * Một khóa công khai ký QR trong KeySet phân phối cho Edge qua bootstrap hoặc
 * GET /api/v1/qr/keys/refresh (mTLS). x/y là tọa độ đường cong P-256 dạng
 * base64url, KHÔNG chứa tham số riêng d (private key chỉ ở Central/KMS).
 * state: ACTIVE (Central đang ký), VERIFY_ONLY (đã phân phối, chỉ xác minh —
 * ánh xạ trạng thái GRACE tại Central theo docs/tasks/07-qr-security.md),
 * REVOKED (bị thu hồi, Edge từ chối ngay trong cache).
 * PHỐI HỢP: Task 7 nhúng QrKeySet vào BootstrapResponse có kiểu theo §8;
 * Task 6 chủ trì shape này theo mục 1 của contract.
 */
public record QrKeySetEntry(
        String kid,
        String kty,
        String crv,
        String alg,
        String use,
        String x,
        String y,
        String state,
        Instant notBefore,
        Instant notAfter) {

    public static final String STATE_ACTIVE = "ACTIVE";
    public static final String STATE_VERIFY_ONLY = "VERIFY_ONLY";
    public static final String STATE_REVOKED = "REVOKED";

    public QrKeySetEntry {
        Objects.requireNonNull(kid, "kid");
        Objects.requireNonNull(kty, "kty");
        Objects.requireNonNull(crv, "crv");
        Objects.requireNonNull(alg, "alg");
        Objects.requireNonNull(use, "use");
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(notBefore, "notBefore");
        Objects.requireNonNull(notAfter, "notAfter");
    }
}
