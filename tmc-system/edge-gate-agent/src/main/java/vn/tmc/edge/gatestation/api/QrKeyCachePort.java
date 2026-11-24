package vn.tmc.edge.gatestation.api;

import java.util.Optional;

import vn.tmc.contracts.QrKeySet;

/**
 * PHỤ TRÁCH: Task 6 (mới). Public API của gate-station cho cache khóa công khai
 * tin cậy. Task 7 nạp/refresh qua đây sau bootstrap hoặc GET /qr/keys/refresh
 * (mTLS) — KHÔNG tải khóa từ URL trong token (CONTRACT-V1.md §4).
 * Cập nhật phải nguyên tử: validate toàn bộ QrKeySet rồi mới thay thế snapshot
 * đang dùng; revision lùi hoặc bundle sai thì từ chối, giữ snapshot cũ.
 * JwsVerifier (Task 6) tra khóa qua implementation của port này; quét KHÔNG
 * gọi Central trong lượt scan — unknown kid chỉ từ chối lượt hiện tại.
 * PHỐI HỢP: Task 7 (BootstrapWorker nạp KeySet), Task 6 (verifier đọc).
 */
public interface QrKeyCachePort {

    Optional<QrTrustedKey> findByKid(String kid);

    /** Có ít nhất một khóa không phải REVOKED để xác minh; false → fail closed. */
    boolean hasUsableKeys();

    /** Thay thế nguyên tử snapshot khóa từ KeySet đã xác thực mTLS. */
    void replaceAll(QrKeySet keySet);

    /** Phiên bản KeySet hiện tại; -1 khi chưa từng nạp (chưa ready offline). */
    long revision();
}
