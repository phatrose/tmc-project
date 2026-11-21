package vn.tmc.central.usermgmt.api;

import java.util.List;

/**
 * PHỤ TRÁCH: Task 1.
 * Port phát hành JWT bearer token cho phiên đăng nhập người dùng.
 * Triển khai cụ thể thuộc qr-security (Task 6) hoặc security infrastructure (Task 8).
 */
public interface TokenIssuerPort {

    /**
     * Phát hành JWT bearer token.
     *
     * @param userId ID người dùng
     * @param role vai trò
     * @param scopes danh sách quyền/scope
     * @return JWT string
     */
    String issueToken(String userId, Role role, List<String> scopes);
}
