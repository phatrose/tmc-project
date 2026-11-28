package vn.tmc.contracts;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 6 (mới, theo CONTRACT-V1.md §8).
 * KeySet = {version, generatedAt, trustedUntil, keys:[QrKeySetEntry]}.
 * Central trả cho Edge qua GET /api/v1/qr/keys/refresh (mTLS) và đồng thời là
 * thành phần keySet của BootstrapResponse (Task 7 tổng hợp theo §8).
 * FULL bootstrap cấp trustedUntil = generatedAt + 48h; refresh FULL khi còn
 * dưới 36h; không tự gia hạn trust khi mất WAN.
 * Lưu ý: chỉ chứa public key material; không có tham số riêng d.
 */
public record QrKeySet(long version, Instant generatedAt, Instant trustedUntil, List<QrKeySetEntry> keys) {

    public QrKeySet {
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(trustedUntil, "trustedUntil");
        keys = List.copyOf(keys);
    }

    public static final long TRUST_WINDOW_HOURS = 48;
    public static final long REFRESH_FULL_BEFORE_HOURS = 36;
    public static final long ROTATION_PERIOD_DAYS = 90;
    public static final long ROTATION_OVERLAP_MIN_HOURS = 48;
}
