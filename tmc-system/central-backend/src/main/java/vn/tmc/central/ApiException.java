package vn.tmc.central;

/** PHỤ TRÁCH: Task 8. Adapter HTTP dùng thông báo công khai; không truyền secret vào message. */
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final boolean retryable;
    public ApiException(int status, String code, String publicMessage, boolean retryable) {
        super(publicMessage);
        if (status < 400 || status > 599) throw new IllegalArgumentException("Expected error status");
        this.status = status;
        this.code = code;
        this.retryable = retryable;
    }
    public int status() { return status; }
    public String code() { return code; }
    public boolean retryable() { return retryable; }
}
