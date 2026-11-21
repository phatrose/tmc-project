package vn.tmc.contracts;

/**
 * PHỤ TRÁCH: Task 8. Wire error theo CONTRACT-V1 §2, dùng chung bởi HTTP adapter và frontend.
 * requestId là UUIDv4 của request; message chỉ chứa thông tin công khai, không stack trace/secret.
 * retryable không tự cho phép retry mutation; caller phải giữ idempotency key và kiểm tra outcome.
 * Scan DENY và ACK từng event là kết quả nghiệp vụ, không chuyển thành DTO lỗi HTTP này.
 */
public record ApiError(String code, String message, String requestId, boolean retryable) {}
