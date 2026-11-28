package vn.tmc.central.qrsecurity.internal.key;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import vn.tmc.central.qrsecurity.api.QrSecurityException;
import vn.tmc.contracts.QrKeySetEntry;

/**
 * PHỤ TRÁCH: Task 8 — tích hợp với Task 6 (DATA-05).
 * Provider khóa ký ES256 P-256 bền vững lưu ngoài Git (file JSON keystore cục bộ có quyền truy cập hạn chế).
 * - Khởi động lại nạp đúng khóa và kid cũ, không sinh lại mỗi startup, không tái dùng kid.
 * - Hỗ trợ rotation 90 ngày với overlap ≥ 48h (khóa cũ chuyển VERIFY_ONLY, khóa mới ACTIVE, tăng keySetVersion).
 * - Dành cho môi trường local/staging; production có thể thay bằng adapter KMS/HSM qua cùng port QrSigningKeyProvider.
 */
public final class FileBackedSigningKeyProvider implements QrSigningKeyProvider {

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final long ROTATION_LIFETIME_DAYS = 90L;

    public record StoredKey(
            String kid,
            String state,
            Instant notBefore,
            Instant notAfter,
            String publicKeyX509Base64,
            String privateKeyPkcs8Base64
    ) {}

    public record StoredKeyring(long keySetVersion, List<StoredKey> keys) {}

    private final Path keystorePath;
    private final Clock clock;
    private volatile List<QrSigningKeyHandle> keys;
    private volatile long keySetVersion;

    public FileBackedSigningKeyProvider(Path keystorePath, Clock clock) {
        this.keystorePath = Objects.requireNonNull(keystorePath, "keystorePath");
        this.clock = Objects.requireNonNull(clock, "clock");
        loadOrInitialize();
    }

    @Override
    public QrSigningKeyHandle activeSigningKey() {
        return keys.stream()
                .filter(k -> QrKeySetEntry.STATE_ACTIVE.equals(k.state()) && k.privateKey() != null)
                .findFirst()
                .orElseThrow(() -> new QrSecurityException(
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
     * Xoay khóa ký: tạo khóa ACTIVE mới (kid UUIDv4 duy nhất, hạn 90 ngày),
     * chuyển khóa ACTIVE cũ sang VERIFY_ONLY (giữ overlap ≥ 48h), tăng keySetVersion và lưu nguyên tử ra đĩa.
     */
    public synchronized QrSigningKeyHandle rotateKey() {
        Instant now = clock.instant();
        List<QrSigningKeyHandle> updated = new ArrayList<>();
        for (QrSigningKeyHandle k : keys) {
            if (QrKeySetEntry.STATE_ACTIVE.equals(k.state())) {
                Instant graceEnd = now.plus(48, ChronoUnit.HOURS);
                Instant effectiveNotAfter = k.notAfter().isAfter(graceEnd) ? k.notAfter() : graceEnd;
                updated.add(new QrSigningKeyHandle(
                        k.kid(), QrKeySetEntry.STATE_VERIFY_ONLY, k.notBefore(), effectiveNotAfter, k.publicKey(), null));
            } else {
                updated.add(k);
            }
        }
        QrSigningKeyHandle next = generateKeyHandle(now);
        updated.add(next);
        long nextVersion = this.keySetVersion + 1;
        persistAtomically(updated, nextVersion);
        this.keys = List.copyOf(updated);
        this.keySetVersion = nextVersion;
        return next;
    }

    private synchronized void loadOrInitialize() {
        if (Files.exists(keystorePath)) {
            try {
                StoredKeyring ring = JSON.readValue(keystorePath.toFile(), StoredKeyring.class);
                if (ring == null || ring.keys() == null || ring.keys().isEmpty()) {
                    throw new IllegalStateException("Keystore QR rỗng hoặc hỏng: " + keystorePath);
                }
                List<QrSigningKeyHandle> loaded = new ArrayList<>();
                for (StoredKey sk : ring.keys()) {
                    loaded.add(decode(sk));
                }
                this.keys = List.copyOf(loaded);
                this.keySetVersion = ring.keySetVersion();
                return;
            } catch (Exception e) {
                throw new IllegalStateException("Không thể nạp khóa ký QR từ " + keystorePath, e);
            }
        }
        QrSigningKeyHandle initial = generateKeyHandle(clock.instant());
        List<QrSigningKeyHandle> initialList = List.of(initial);
        persistAtomically(initialList, 1L);
        this.keys = initialList;
        this.keySetVersion = 1L;
    }

    private void persistAtomically(List<QrSigningKeyHandle> handles, long version) {
        try {
            Path parent = keystorePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<StoredKey> serialized = handles.stream().map(FileBackedSigningKeyProvider::encode).toList();
            StoredKeyring ring = new StoredKeyring(version, serialized);
            Path tmp = keystorePath.resolveSibling(keystorePath.getFileName() + ".tmp." + UUID.randomUUID());
            JSON.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), ring);
            try {
                Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows NTFS: áp dụng quyền owner-only qua java.io.File API
                tmp.toFile().setReadable(false, false);
                tmp.toFile().setReadable(true, true);
                tmp.toFile().setWritable(false, false);
                tmp.toFile().setWritable(true, true);
            }
            try {
                Files.move(tmp, keystorePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, keystorePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Không thể ghi keystore QR bền vững tại " + keystorePath, e);
        }
    }

    private static QrSigningKeyHandle generateKeyHandle(Instant now) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
            KeyPair pair = generator.generateKeyPair();
            String kid = "qr-" + UUID.randomUUID();
            return new QrSigningKeyHandle(
                    kid,
                    QrKeySetEntry.STATE_ACTIVE,
                    now.minusSeconds(60),
                    now.plus(ROTATION_LIFETIME_DAYS, ChronoUnit.DAYS),
                    (ECPublicKey) pair.getPublic(),
                    pair.getPrivate());
        } catch (Exception e) {
            throw new IllegalStateException("Sinh keypair P-256 thất bại", e);
        }
    }

    private static StoredKey encode(QrSigningKeyHandle h) {
        Base64.Encoder b64 = Base64.getEncoder();
        return new StoredKey(
                h.kid(),
                h.state(),
                h.notBefore(),
                h.notAfter(),
                b64.encodeToString(h.publicKey().getEncoded()),
                h.privateKey() != null ? b64.encodeToString(h.privateKey().getEncoded()) : null);
    }

    private static QrSigningKeyHandle decode(StoredKey sk) throws Exception {
        KeyFactory kf = KeyFactory.getInstance("EC");
        Base64.Decoder b64 = Base64.getDecoder();
        ECPublicKey pub = (ECPublicKey) kf.generatePublic(new X509EncodedKeySpec(b64.decode(sk.publicKeyX509Base64())));
        PrivateKey priv = sk.privateKeyPkcs8Base64() != null
                ? kf.generatePrivate(new PKCS8EncodedKeySpec(b64.decode(sk.privateKeyPkcs8Base64())))
                : null;
        return new QrSigningKeyHandle(sk.kid(), sk.state(), sk.notBefore(), sk.notAfter(), pub, priv);
    }
}
