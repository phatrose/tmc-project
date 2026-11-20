package vn.tmc.edge.gatestation.api;

import java.time.Instant;
import java.util.List;

import vn.tmc.contracts.RevocationChange;

/**
 * PHỤ TRÁCH: Task 6 (mới). Public API cache thu hồi tại Edge cho JwsVerifier.
 * Task 7 nạp danh sách RevocationChange từ bootstrap/refresh (đã xác thực mTLS,
 * commit bền vững trước khi áp dụng revision). Ngữ nghĩa kiểm tra (§8):
 * - SUB: từ chối token có tokenIssuedAt (iat) <= revokedBefore;
 * - JTI/KID: áp dụng mọi token khớp, revokedBefore = null;
 * - DELETE theo id: bỏ khỏi tập active, không xóa lịch sử Central.
 * Offline dùng snapshot hợp lệ cũ (cửa sổ rủi ro chấp nhận); reconnect đối
 * soát POST_REVOKE_VIOLATION thuộc Task 3.
 */
public interface QrRevocationCachePort {

    /** true khi token bị thu hồi theo dữ liệu active hiện có. */
    boolean isRevoked(String subject, String jti, String kid, Instant tokenIssuedAt);

    /** Thay thế nguyên tử tập active từ danh sách đã xác thực. */
    void replaceAll(List<RevocationChange> changes);

    /** Số thay đổi active hiện tại (quan sát vận hành). */
    int size();
}
