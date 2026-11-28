package vn.tmc.edge.gatestation.domain;

import java.time.Instant;

import vn.tmc.contracts.QrClaims;

/**
 * PHỤ TRÁCH: Task 6.
 * Port xác minh Dynamic QR theo CONTRACT-V1.md §10:
 * "QrVerificationPort.verify | rawToken,now → VerifiedQr | 6; chưa phải ALLOW".
 * now là thời điểm tin cậy của Edge (adapter giám sát đồng hồ); khi Edge KHÔNG
 * có thời gian tin cậy, luồng quét phải fail closed với CLOCK_UNTRUSTED trước
 * khi gọi verify (§4: "Không có thời gian tin cậy thì fail closed").
 * Kết quả chỉ là claims đã xác minh chữ ký/claims — CHƯA phải quyết định ALLOW:
 * policy nhà máy/cổng/ca, replay jti và ghi event thuộc use case (Task 2) và
 * LocalEventStorePort (Task 7).
 *
 * Lỗi ném QrVerificationRejectException với reasonCode hợp đồng §5:
 * INVALID_TOKEN, TOKEN_EXPIRED, UNKNOWN_KID, REVOKED, CACHE_UNAVAILABLE.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Token thô đọc từ máy quét; không sửa, không log.
 * - Cần làm: Adapter kiểm ES256/chữ ký/iss/aud/thời gian/revocation từ cache.
 * - Lưu ý bàn giao: Unknown kid từ chối lượt hiện tại, refresh khóa chạy nền
 *   qua mTLS; không gọi Central trong scan.
 */
public interface QrVerificationPort {
    VerifiedQr verify(String token, Instant now);
}
