package vn.tmc.edge.gatestation.domain;

import vn.tmc.edge.gatestation.domain.GateStationException.Kind;

import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 2. Đầu vào POST /edge/v1/scans (CONTRACT-V1.md §5): {scanId, gateId, token}.
 * Chỉ kiểm tra hình dạng. Token chưa được tin cậy; không có trong toString để tránh lọt vào log.
 */
public record ScanRequest(UUID scanId, UUID gateId, String token) {
    /** Cùng giới hạn độ dài với JwsVerifier (Task 6) để không nhận token mà verifier chắc chắn từ chối. */
    public static final int MAX_TOKEN_CHARS = 4096;

    public ScanRequest {
        Uuids.requireV4(scanId, "scanId");
        Objects.requireNonNull(gateId, "gateId");
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_CHARS) {
            throw new GateStationException(Kind.VALIDATION_ERROR, "token phải khác rỗng và tối đa " + MAX_TOKEN_CHARS + " ký tự");
        }
    }

    @Override
    public String toString() {
        return "ScanRequest[scanId=" + scanId + ", gateId=" + gateId + ", token=***]";
    }
}
