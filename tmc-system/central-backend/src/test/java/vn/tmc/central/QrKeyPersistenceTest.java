package vn.tmc.central;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.tmc.central.qrsecurity.internal.key.FileBackedSigningKeyProvider;
import vn.tmc.contracts.QrKeySetEntry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHỤ TRÁCH: Task 8 — tích hợp với Task 6 (DATA-05).
 * Kiểm thử khóa ký QR ES256 bền vững ngoài Git:
 * 1. Sống nguyên qua restart (nạp lại đúng kid và public key, không sinh lại mỗi startup).
 * 2. Rotation sinh kid mới duy nhất, giữ khóa cũ VERIFY_ONLY (overlap ≥ 48h), tăng keySetVersion và sống qua restart.
 */
class QrKeyPersistenceTest {

    @Test
    void signingKeySurvivesRestartAndSupportsRotationWithOverlap(@TempDir Path tempDir) {
        Path keystoreFile = tempDir.resolve("qr-signing-keys.json");
        Clock clock = Clock.fixed(Instant.parse("2026-11-28T04:00:00Z"), ZoneOffset.UTC);

        // Lần khởi động 1: tự khởi tạo khóa ACTIVE đầu tiên
        FileBackedSigningKeyProvider instance1 = new FileBackedSigningKeyProvider(keystoreFile, clock);
        var key1 = instance1.activeSigningKey();
        assertNotNull(key1.privateKey());
        assertEquals(QrKeySetEntry.STATE_ACTIVE, key1.state());
        assertEquals(1L, instance1.keySetVersion());

        // Lần khởi động 2 (giả lập restart): phải nạp lại ĐÚNG kid và public key cũ
        FileBackedSigningKeyProvider instance2 = new FileBackedSigningKeyProvider(keystoreFile, clock);
        var reloadedKey1 = instance2.activeSigningKey();
        assertEquals(key1.kid(), reloadedKey1.kid());
        assertArrayEquals(key1.publicKey().getEncoded(), reloadedKey1.publicKey().getEncoded());
        assertEquals(1L, instance2.keySetVersion());

        // Rotation: tạo kid mới ACTIVE, chuyển kid cũ sang VERIFY_ONLY, tăng version lên 2
        var key2 = instance2.rotateKey();
        assertNotEquals(key1.kid(), key2.kid());
        assertEquals(QrKeySetEntry.STATE_ACTIVE, key2.state());
        assertEquals(2L, instance2.keySetVersion());

        // Lần khởi động 3 (sau restart hậu rotation): đủ cả 2 khóa, kid2 ACTIVE và kid1 VERIFY_ONLY
        FileBackedSigningKeyProvider instance3 = new FileBackedSigningKeyProvider(keystoreFile, clock);
        assertEquals(2L, instance3.keySetVersion());
        assertEquals(key2.kid(), instance3.activeSigningKey().kid());
        assertEquals(2, instance3.allKeys().size());
        var oldKeyAfterRestart = instance3.allKeys().stream()
                .filter(k -> k.kid().equals(key1.kid())).findFirst().orElseThrow();
        assertEquals(QrKeySetEntry.STATE_VERIFY_ONLY, oldKeyAfterRestart.state());
        assertNull(oldKeyAfterRestart.privateKey(), "Khóa VERIFY_ONLY không giữ private key ký mới");
    }
}
