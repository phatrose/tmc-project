package vn.tmc.central.qrsecurity.internal.key;

import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 6 (mới). Handle khóa ký QR tại Central.
 * privateKey chỉ tồn tại ở Central qua KMS/HSM/Vault; Edge chỉ nhận public
 * material (x/y) qua QrKeySet. state dùng đúng bộ giá trị QrKeySetEntry
 * (ACTIVE/VERIFY_ONLY/REVOKED); VERIFY_ONLY tương ứng GRACE phía quản trị —
 * đã phân phối cho Edge nhưng Central chưa dùng để ký.
 */
public record QrSigningKeyHandle(
        String kid,
        String state,
        Instant notBefore,
        Instant notAfter,
        ECPublicKey publicKey,
        PrivateKey privateKey) {

    public QrSigningKeyHandle {
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
