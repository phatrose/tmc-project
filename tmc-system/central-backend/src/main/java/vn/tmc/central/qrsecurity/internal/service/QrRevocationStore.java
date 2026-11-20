package vn.tmc.central.qrsecurity.internal.service;

import java.util.List;

import vn.tmc.contracts.RevocationChange;

/**
 * PHỤ TRÁCH: Task 6 (mới). Port lưu thay đổi thu hồi QR tại Central.
 * Service ghi SUB khi revoke/reissue (revokedBefore = cutoff) và JTI/KID khi
 * cần thu hồi khẩn cấp. Adapter production lưu bảng qr_revocation có lịch sử
 * (không xóa history) để Task 3 đối soát POST_REVOKE_VIOLATION và Task 7
 * phân phối qua bootstrap.
 * Lịch sử tại Central là bắt buộc theo §8: "Revocations phải có lịch sử tại
 * Central để đối soát event đến muộn."
 */
public interface QrRevocationStore {

    /** Ghi một thay đổi; id trùng lặp với UPSERT mới hơn là lỗi dữ liệu. */
    void append(RevocationChange change);

    /** Các thay đổi đang hiệu lực (UPSERT chưa bị DELETE theo id). */
    List<RevocationChange> activeChanges();

    /** Số thay đổi đã ghi (lịch sử); dùng làm revision nội bộ tăng đơn điệu. */
    long count();
}
