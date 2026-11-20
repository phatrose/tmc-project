package vn.tmc.edge.gatestation.api;

import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 6 (mới). Khóa công khai đã dựng được từ QrKeySetEntry và
 * đang tin cậy tại Edge. Chỉ có public material (không tham số riêng d);
 * state đúng bộ giá trị QrKeySetEntry: ACTIVE/VERIFY_ONLY/REVOKED.
 * Key REVOKED bị từ chối ngay trong cache (§8); khóa ngoài cửa sổ
 * notBefore/notAfter coi như UNKNOWN_KID tại thời điểm xác minh.
 */
public record QrTrustedKey(String kid, String state, Instant notBefore, Instant notAfter, ECPublicKey publicKey) {

    public QrTrustedKey {
        Objects.requireNonNull(kid, "kid");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(notBefore, "notBefore");
        Objects.requireNonNull(notAfter, "notAfter");
        Objects.requireNonNull(publicKey, "publicKey");
        if (!notBefore.isBefore(notAfter)) {
            throw new IllegalArgumentException("notBefore phải trước notAfter");
        }
    }
}
