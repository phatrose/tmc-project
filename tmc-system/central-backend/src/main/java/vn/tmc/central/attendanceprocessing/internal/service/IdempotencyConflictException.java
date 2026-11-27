package vn.tmc.central.attendanceprocessing.internal.service;

/**
 * PHỤ TRÁCH: Task 3.
 * Ném ra khi tái sử dụng syncBatchId hoặc Idempotency-Key với nội dung khác.
 * Ánh xạ HTTP 409 IDEMPOTENCY_CONFLICT theo CONTRACT-V1.md §2, §7.
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
