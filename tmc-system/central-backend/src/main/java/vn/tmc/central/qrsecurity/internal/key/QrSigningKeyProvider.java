package vn.tmc.central.qrsecurity.internal.key;

import java.util.List;

/**
 * PHỤ TRÁCH: Task 6 (mới). Port khóa ký QR của Central.
 * "Private key chỉ qua KMS/HSM/Vault port" (TODO của QrCredentialService):
 * production adapter đọc khóa từ secret store và cấp handle; không lưu private
 * key trong code, config thường hoặc database.
 * Rotation mặc định 90 ngày với overlap ít nhất 48h (FR-QR-05, CONTRACT-V1.md
 * §8): (1) tạo khóa mới, state VERIFY_ONLY để Edge pre-publish; (2) khi Edge đã
 * lưu khóa mới, chuyển state ACTIVE để chuyển ký; (3) khóa cũ VERIFY_ONLY
 * (GRACE) ít nhất 48h trước khi REVOKED. Scheduler rotation sẽ phối hợp Task 5.
 */
public interface QrSigningKeyProvider {

    /** Khóa đang dùng để ký QR; phải có privateKey và state ACTIVE. */
    QrSigningKeyHandle activeSigningKey();

    /** Toàn bộ khóa trong tập tin cậy, gồm VERIFY_ONLY/REVOKED để dựng QrKeySet. */
    List<QrSigningKeyHandle> allKeys();

    /** Phiên bản KeySet; tăng khi tập khóa đổi (bootstrap/refresh phân biệt). */
    long keySetVersion();
}
