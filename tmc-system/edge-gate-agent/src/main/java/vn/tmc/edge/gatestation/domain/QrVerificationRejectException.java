package vn.tmc.edge.gatestation.domain;

/**
 * PHỤ TRÁCH: Task 6 (mới). Lỗi từ chối QR mang reasonCode hợp đồng §5.
 * Message an toàn để log: KHÔNG chứa token thô, chữ ký hoặc PII; chỉ mô tả
 * bước kiểm tra thất bại. Use case Task 2 bắt exception này để trả ScanResult
 * DENY với reasonCode tương ứng, không biến thành HTTP 5xx.
 */
public final class QrVerificationRejectException extends RuntimeException {

    private final QrRejectReason reason;

    public QrVerificationRejectException(QrRejectReason reason, String safeDetail) {
        super(reason + ": " + safeDetail);
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
    }

    public QrRejectReason reason() {
        return reason;
    }
}
