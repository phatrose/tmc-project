package vn.tmc.central.usermgmt.internal.service;

/**
 * PHỤ TRÁCH: Task 1.
 * Ném ra khi tài khoản bị khóa do vi phạm hoặc hành động quản trị.
 */
public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String username) {
        super("Tài khoản '" + username + "' đã bị khóa.");
    }
}
