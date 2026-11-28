package vn.tmc.central.qrsecurity.internal.repository;

import java.util.Optional;

import vn.tmc.central.qrsecurity.internal.model.QrCredential;

/**
 * PHỤ TRÁCH: Task 6.
 * Port dữ liệu credential QR; adapter JPA/PostgreSQL sẽ hiện thực sau
 * (migration + unique constraint một ACTIVE trên employeeId).
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Service gọi port này; module khác KHÔNG gọi trực tiếp.
 * - Cần làm: findActiveByEmployeeId trả credential ACTIVE duy nhất;
 *   save là upsert; replaceActive thu hồi cũ + tạo mới TRONG MỘT transaction
 *   (yêu cầu nguyên tử của reissue, CONTRACT-V1.md §4: "thu hồi cũ nguyên tử").
 * - Lưu ý bàn giao: Lỗi DB phải truyền lên caller; không nuốt lỗi, không trả
 *   null thay Optional.
 */
public interface QrCredentialRepository {

    Optional<QrCredential> findById(String id);

    Optional<QrCredential> findActiveByEmployeeId(String employeeId);

    Optional<QrCredential> findLatestByEmployeeId(String employeeId);

    void save(QrCredential credential);

    /**
     * Thao tác nguyên tử cho reissue: thu hồi credential cũ và ghi credential
     * mới trong cùng transaction. Adapter bắt buộc dùng một transaction; nếu
     * ghi mới thất bại thì thu hồi cũng phải rollback.
     */
    void replaceActive(QrCredential revokedOld, QrCredential createdNew);
}
