package vn.tmc.central.usermgmt.internal.service;

/**
 * PHỤ TRÁCH: Task 1.
 * Ném ra khi version của tài nguyên không khớp (Optimistic Lock Failure).
 * Ánh xạ HTTP 409 VERSION_CONFLICT theo CONTRACT-V1.md §2.
 */
public class VersionConflictException extends RuntimeException {
    public VersionConflictException(String message) {
        super(message);
    }

    public VersionConflictException(int expectedVersion, int actualVersion) {
        super(String.format("Xung đột phiên bản: client gửi version=%d nhưng phiên bản hiện tại là %d",
                expectedVersion, actualVersion));
    }
}
