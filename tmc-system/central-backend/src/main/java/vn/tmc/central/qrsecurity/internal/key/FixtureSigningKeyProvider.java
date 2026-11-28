package vn.tmc.central.qrsecurity.internal.key;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import vn.tmc.central.qrsecurity.api.QrSecurityException;
import vn.tmc.contracts.QrKeySetEntry;

/**
 * PHỤ TRÁCH: Task 6 (mới). Fixture/demo provider cho sườn và kiểm thử.
 * KHÔNG DÙNG CHO PRODUCTION: private key sinh bằng KeyPairGenerator nằm trong
 * bộ nhớ tiến trình, không qua KMS/HSM. Adapter production thay thế bean này,
 * đọc khóa từ secret store theo QrSigningKeyProvider (xem handover Task 6).
 * Xây dựng state VERIFY_ONLY/REVOKED để kiểm thử rotation và overlap.
 */
public final class FixtureSigningKeyProvider implements QrSigningKeyProvider {

    private final List<QrSigningKeyHandle> keys;
    private final long keySetVersion;

    public FixtureSigningKeyProvider(List<QrSigningKeyHandle> keys, long keySetVersion) {
        if (keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("Cần ít nhất một khóa trong provider");
        }
        this.keys = List.copyOf(keys);
        this.keySetVersion = keySetVersion;
    }

    @Override
    public QrSigningKeyHandle activeSigningKey() {
        Optional<QrSigningKeyHandle> active = keys.stream()
                .filter(k -> QrKeySetEntry.STATE_ACTIVE.equals(k.state()) && k.privateKey() != null)
                .findFirst();
        return active.orElseThrow(() -> new QrSecurityException(
                "DEPENDENCY_UNAVAILABLE", 503, true, "Không có khóa ACTIVE để ký QR"));
    }

    @Override
    public List<QrSigningKeyHandle> allKeys() {
        return keys;
    }

    @Override
    public long keySetVersion() {
        return keySetVersion;
    }

    /**
     * Sinh handle mới với keypair P-256; chỉ dùng cho fixture/test/demo.
     * privateKey giữ lại cho ký; publicKey dùng khi dựng QrKeySet.
     */
    public static QrSigningKeyHandle generateHandle(String kid, String state, Instant notBefore, Instant notAfter) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
            KeyPair pair = generator.generateKeyPair();
            return new QrSigningKeyHandle(kid, state, notBefore, notAfter,
                    (ECPublicKey) pair.getPublic(), pair.getPrivate());
        } catch (Exception e) {
            throw new IllegalStateException("Sinh keypair P-256 thất bại", e);
        }
    }
}
