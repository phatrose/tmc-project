package vn.tmc.central.qrsecurity.api;

import java.util.List;

import vn.tmc.contracts.RevocationChange;

/**
 * PHỤ TRÁCH: Task 6 (mới, theo CONTRACT-V1.md §8).
 * Public API cung cấp danh sách revocation đang hiệu lực để Task 7 tổng hợp
 * vào BootstrapResponse (mục revocations, có lịch sử tại Central để đối soát
 * event đến muộn). QrCredentialService ghi SUB mỗi lần revoke/reissue.
 * "Revision tăng khi policy, gate config, key hoặc revocation đổi" — revision
 * tổng do Task 5/7 quản; module này chỉ cấp dữ liệu thay đổi.
 * PHỐI HỢP: Task 7 (bootstrap), Task 3 (đối soát POST_REVOKE_VIOLATION).
 */
public interface QrRevocationQuery {
    /** Các thay đổi đang hiệu lực (UPSERT chưa bị DELETE theo id). */
    List<RevocationChange> activeRevocations();

    /** Phiên bản hiện tại của dữ liệu revocation; tăng khi có thay đổi. */
    long revision();
}
