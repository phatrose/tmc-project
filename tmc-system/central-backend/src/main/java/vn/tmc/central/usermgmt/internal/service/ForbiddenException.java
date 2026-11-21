package vn.tmc.central.usermgmt.internal.service;

/**
 * PHỤ TRÁCH: Task 1.
 * Ném ra khi người dùng không đủ quyền hoặc vượt ngoài phạm vi (Scope Mismatch).
 * Ánh xạ HTTP 403 FORBIDDEN theo CONTRACT-V1.md §2, §3.
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
