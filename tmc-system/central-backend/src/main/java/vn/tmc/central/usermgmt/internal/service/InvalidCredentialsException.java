package vn.tmc.central.usermgmt.internal.service;

/**
 * PHỤ TRÁCH: Task 1.
 * Ném ra khi thông tin đăng nhập không hợp lệ (sai username hoặc password).
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Tên đăng nhập hoặc mật khẩu không chính xác.");
    }

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
