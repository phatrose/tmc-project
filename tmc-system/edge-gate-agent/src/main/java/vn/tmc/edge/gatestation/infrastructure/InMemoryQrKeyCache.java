package vn.tmc.edge.gatestation.infrastructure;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import vn.tmc.contracts.JwsCompact;
import vn.tmc.contracts.JwsHeader;
import vn.tmc.contracts.QrKeySet;
import vn.tmc.contracts.QrKeySetEntry;
import vn.tmc.edge.gatestation.api.QrKeyCachePort;
import vn.tmc.edge.gatestation.api.QrTrustedKey;

/**
 * PHỤ TRÁCH: Task 6 (mới). Cache khóa công khai in-memory cho JwsVerifier.
 * replaceAll validate toàn bộ KeySet (kty/crv/alg/use, tọa độ nằm trên đường
 * cong) rồi mới swap snapshot — revision lùi hoặc bundle hỏng giữ nguyên cache
 * cũ (fail closed theo §8). KeySet rỗng (keys=[]) vẫn được chấp nhận như một
 * snapshot hợp lệ; hasUsableKeys() sẽ false và luồng quét fail closed cho tới
 * khi nạp được khóa thật (fixture FULL bootstrap của contract vẫn không được
 * báo ready offline).
 * Task 7 gọi replaceAll sau khi commit bền vững; class không gọi mạng.
 */
public final class InMemoryQrKeyCache implements QrKeyCachePort {

    private volatile Snapshot snapshot = Snapshot.EMPTY;

    private static final class Snapshot {
        static final Snapshot EMPTY = new Snapshot(Map.of(), -1);

        final Map<String, QrTrustedKey> byKid;
        final long revision;

        Snapshot(Map<String, QrTrustedKey> byKid, long revision) {
            this.byKid = byKid;
            this.revision = revision;
        }
    }

    @Override
    public Optional<QrTrustedKey> findByKid(String kid) {
        if (kid == null || kid.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshot.byKid.get(kid));
    }

    @Override
    public boolean hasUsableKeys() {
        return snapshot.byKid.values().stream()
                .anyMatch(key -> !QrKeySetEntry.STATE_REVOKED.equals(key.state()));
    }

    @Override
    public void replaceAll(QrKeySet keySet) {
        if (keySet == null || keySet.keys() == null) {
            throw new IllegalArgumentException("QrKeySet và keys bắt buộc");
        }
        if (keySet.version() < snapshot.revision && snapshot.revision >= 0) {
            // revision lùi là tín hiệu cache thiếu/hỏng: không áp dụng (§8)
            throw new IllegalArgumentException("KeySet revision lùi: từ chối áp dụng");
        }
        Map<String, QrTrustedKey> built = new HashMap<>();
        for (QrKeySetEntry entry : keySet.keys()) {
            validateShape(entry);
            QrTrustedKey key = toTrustedKey(entry);
            QrTrustedKey existing = built.putIfAbsent(entry.kid(), key);
            if (existing != null) {
                throw new IllegalArgumentException("KeySet có kid trùng: " + entry.kid());
            }
        }
        snapshot = new Snapshot(Map.copyOf(built), keySet.version());
    }

    @Override
    public long revision() {
        return snapshot.revision;
    }

    private static void validateShape(QrKeySetEntry entry) {
        if (!JwsHeader.KEY_TYPE.equals(entry.kty())) {
            throw new IllegalArgumentException("kty phải là EC: " + entry.kid());
        }
        if (!JwsHeader.CURVE.equals(entry.crv())) {
            throw new IllegalArgumentException("crv phải là P-256: " + entry.kid());
        }
        if (!JwsHeader.ALGORITHM.equals(entry.alg())) {
            throw new IllegalArgumentException("alg phải là ES256: " + entry.kid());
        }
        if (!JwsHeader.USE.equals(entry.use())) {
            throw new IllegalArgumentException("use phải là sig: " + entry.kid());
        }
        if (!QrKeySetEntry.STATE_ACTIVE.equals(entry.state())
                && !QrKeySetEntry.STATE_VERIFY_ONLY.equals(entry.state())
                && !QrKeySetEntry.STATE_REVOKED.equals(entry.state())) {
            throw new IllegalArgumentException("state khóa không hợp lệ: " + entry.kid());
        }
    }

    private static QrTrustedKey toTrustedKey(QrKeySetEntry entry) {
        byte[] x = Base64.getUrlDecoder().decode(entry.x());
        byte[] y = Base64.getUrlDecoder().decode(entry.y());
        // p256PublicKeyFrom ném IllegalArgumentException khi điểm không nằm trên
        // đường cong — KeySet lỗi sẽ từ chối nguyên vẹn trước khi swap.
        return new QrTrustedKey(entry.kid(), entry.state(), entry.notBefore(), entry.notAfter(),
                JwsCompact.p256PublicKeyFrom(x, y));
    }
}
