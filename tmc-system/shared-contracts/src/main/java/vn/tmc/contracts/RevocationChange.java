package vn.tmc.contracts;

import java.time.Instant;
import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 6 (mới, theo CONTRACT-V1.md §8).
 * RevocationChange = {id, op:"UPSERT"|"DELETE", kind:"SUB"|"JTI"|"KID", value,
 * revokedBefore:Instant?, effectiveAt:Instant}.
 * Ý nghĩa kiểm tra tại Edge:
 * - SUB: từ chối token có tokenIssuedAt (iat) <= revokedBefore; JTI/KID áp dụng
 *   mọi token khớp, revokedBefore = null.
 * - value là employeeId/jti/kid tương ứng kind.
 * - DELETE theo id chỉ xóa khỏi active cache của Edge, không xóa lịch sử Central
 *   (đối soát POST_REVOKE_VIOLATION thuộc Task 3).
 * - Reissue phát iat sau cutoff (chờ sang giây tiếp nếu cần).
 * Task 6 chủ trì shape; Task 7 phân phối qua bootstrap (revocations), Task 3
 * đối soát occurredAt/tokenIssuedAt với effectiveAt.
 */
public record RevocationChange(
        String id,
        String op,
        String kind,
        String value,
        Instant revokedBefore,
        Instant effectiveAt) {

    public static final String OP_UPSERT = "UPSERT";
    public static final String OP_DELETE = "DELETE";
    public static final String KIND_SUB = "SUB";
    public static final String KIND_JTI = "JTI";
    public static final String KIND_KID = "KID";

    public RevocationChange {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(op, "op");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        // revokedBefore bắt buộc null với JTI/KID, bắt buộc có giá trị với SUB
        if (KIND_SUB.equals(kind) && revokedBefore == null) {
            throw new IllegalArgumentException("SUB cần revokedBefore");
        }
        if (!KIND_SUB.equals(kind) && revokedBefore != null) {
            throw new IllegalArgumentException("JTI/KID để revokedBefore null");
        }
    }
}
