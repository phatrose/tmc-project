package vn.tmc.edge.gatestation.domain;

/**
 * PHỤ TRÁCH: Task 2.
 * Lỗi cấp HTTP của luồng quét theo CONTRACT-V1.md §2 (ApiError). Chỉ dùng cho lỗi request/hạ tầng;
 * DENY nghiệp vụ là ScanResult 200 và không bao giờ đi qua exception này.
 * Message phải an toàn để trả cho client: không token, không PII, không stack trace.
 */
public final class GateStationException extends RuntimeException {
    /** code + HTTP status + retryable đúng bảng ApiError trong contract. */
    public enum Kind {
        VALIDATION_ERROR(400, false),
        MALFORMED_JSON(400, false),
        DEVICE_BINDING_MISMATCH(403, false),
        NOT_FOUND(404, false),
        IDEMPOTENCY_CONFLICT(409, false),
        STORAGE_UNAVAILABLE(503, true),
        DEPENDENCY_UNAVAILABLE(503, true),
        INTERNAL_ERROR(500, true);

        private final int status;
        private final boolean retryable;

        Kind(int status, boolean retryable) {
            this.status = status;
            this.retryable = retryable;
        }

        public int status() {
            return status;
        }

        public boolean retryable() {
            return retryable;
        }
    }

    private final Kind kind;

    public GateStationException(Kind kind, String safeMessage) {
        this(kind, safeMessage, null);
    }

    public GateStationException(Kind kind, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.kind = java.util.Objects.requireNonNull(kind, "kind");
    }

    public Kind kind() {
        return kind;
    }
}
