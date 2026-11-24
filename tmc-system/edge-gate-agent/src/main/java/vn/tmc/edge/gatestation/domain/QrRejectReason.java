package vn.tmc.edge.gatestation.domain;

/**
 * PHỤ TRÁCH: Task 6 (mới). Mã lý do từ chối phần XÁC MINH QR, trích từ danh
 * mục reasonCode của CONTRACT-V1.md §5 (ScanResult):
 * INVALID_TOKEN, TOKEN_EXPIRED, UNKNOWN_KID, REVOKED, CACHE_UNAVAILABLE.
 * Các mã còn lại của luồng scan (OUTSIDE_POLICY, DUPLICATE_SCAN, GATE_DISABLED,
 * CLOCK_UNTRUSTED, EMERGENCY...) do use case Task 2 và adapter quyết, không do
 * verifier phát. Không tạo mã ngoài hợp đồng.
 */
public enum QrRejectReason {
    /** Cấu trúc JWS sai, sai alg (không ES256), sai iss/aud, sai lifetime,
     * sai thời điểm phát hành/hiệu lực ngoài skew 5s, chữ ký sai. */
    INVALID_TOKEN,
    /** now >= exp + 5 giây (skew đã tính). */
    TOKEN_EXPIRED,
    /** kid chưa có trong cache khóa tin cậy, hoặc khóa không trong cửa sổ
     * notBefore/notAfter tại thời điểm xác minh. */
    UNKNOWN_KID,
    /** token/key bị thu hồi theo cache revocation SUB/JTI/KID hoặc state khóa. */
    REVOKED,
    /** Cache khóa/revocation không có dữ liệu khả dụng — fail closed. */
    CACHE_UNAVAILABLE
}
