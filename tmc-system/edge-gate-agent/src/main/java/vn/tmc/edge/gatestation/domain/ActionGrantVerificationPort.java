package vn.tmc.edge.gatestation.domain;

import vn.tmc.edge.edgesync.api.EventPayload.CommandMode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 2 (khai báo port); adapter ES256 thuộc Task 6 (mật mã/JWS), grant do Task 1 cấp.
 * Xác minh action grant của Central (CONTRACT-V1.md §5 "Re-auth cho override"): ES256, aud=tmc-edge-action,
 * hạn 60 giây, chữ ký bằng khóa tin cậy, thời gian/skew như QR.
 * Lỗi ném QrVerificationRejectException với reasonCode §5 (INVALID_TOKEN, TOKEN_EXPIRED, UNKNOWN_KID,
 * REVOKED, CACHE_UNAVAILABLE). Port chỉ xác minh; reserve jti loại ACTION nằm trong LocalEventStorePort.
 */
public interface ActionGrantVerificationPort {
    VerifiedGrant verify(String grant, Instant now);

    /** Claims đã xác minh của grant: operatorId là sub (userId), employeeId null khi EMERGENCY. */
    record VerifiedGrant(UUID jti, UUID operatorId, CommandMode action, UUID edgeId, UUID gateId,
                         UUID employeeId, String reason, Instant expiresAt) {
        public VerifiedGrant {
            Objects.requireNonNull(jti, "jti");
            Objects.requireNonNull(operatorId, "operatorId");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(edgeId, "edgeId");
            Objects.requireNonNull(gateId, "gateId");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }
}
