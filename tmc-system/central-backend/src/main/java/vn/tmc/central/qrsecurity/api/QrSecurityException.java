package vn.tmc.central.qrsecurity.api;

/**
 * PHỤ TRÁCH: Task 6 (mới). Lỗi nghiệp vụ QR mang code hợp đồng theo
 * CONTRACT-V1.md §2 (ApiError): controller/exception handler của Task 8 map
 * sang {"code", "message", "requestId", "retryable"}; domain không dùng null
 * để giấu lỗi. message hiển thị cho người dùng, không chứa token/JWS/secret.
 * PHỐI HỢP: Task 8 (ApiError mapping), Task 1 (scope/permission cùng quy ước).
 */
public final class QrSecurityException extends RuntimeException {

    private final String code;
    private final int httpStatus;
    private final boolean retryable;

    public QrSecurityException(String code, int httpStatus, boolean retryable, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public static QrSecurityException validation(String message) {
        return new QrSecurityException("VALIDATION_ERROR", 400, false, message);
    }

    public static QrSecurityException unauthenticated(String message) {
        return new QrSecurityException("UNAUTHENTICATED", 401, false, message);
    }

    public static QrSecurityException forbidden(String message) {
        return new QrSecurityException("FORBIDDEN", 403, false, message);
    }

    public static QrSecurityException scopeMismatch(String message) {
        return new QrSecurityException("SCOPE_MISMATCH", 403, false, message);
    }

    public static QrSecurityException deviceBindingMismatch(String message) {
        return new QrSecurityException("DEVICE_BINDING_MISMATCH", 403, false, message);
    }

    public static QrSecurityException notFound(String message) {
        return new QrSecurityException("NOT_FOUND", 404, false, message);
    }

    public static QrSecurityException invalidState(String message) {
        return new QrSecurityException("INVALID_STATE", 409, false, message);
    }

    public static QrSecurityException dependencyUnavailable(String message) {
        return new QrSecurityException("DEPENDENCY_UNAVAILABLE", 503, true, message);
    }

    public String code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public boolean retryable() {
        return retryable;
    }
}
