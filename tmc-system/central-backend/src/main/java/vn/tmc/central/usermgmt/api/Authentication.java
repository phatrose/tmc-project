package vn.tmc.central.usermgmt.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * PHỤ TRÁCH: Task 1.
 * Contract quản lý xác thực phiên đăng nhập và cấp Action Grant.
 * Theo CONTRACT-V1.md §3, §5, §10.
 */
public interface Authentication {

    /**
     * Xác thực thông tin đăng nhập và tạo phiên làm việc mới (15 phút).
     *
     * @param request thông tin gồm username và password
     * @return đối tượng Session gồm accessToken và ActorContext
     */
    Session authenticate(LoginRequest request);

    /**
     * Tra cứu thông tin ActorContext của phiên hiện tại từ bearer token.
     *
     * @param accessToken token phiên
     * @return ActorContext tương ứng
     */
    ActorContext getActorContext(String accessToken);

    /**
     * Đăng xuất và thu hồi phiên làm việc.
     *
     * @param accessToken token phiên cần thu hồi
     */
    void logout(String accessToken);

    /**
     * Xác thực lại mật khẩu cho các thao tác quản trị nhạy cảm (hạn 5 phút).
     */
    default ReauthResponse reauth(ActorContext actor, ReauthRequest request) {
        throw new UnsupportedOperationException("Re-authentication is not configured");
    }

    /**
     * Thu hồi toàn bộ phiên đang hoạt động của một tài khoản (khi khóa hoặc đổi quyền).
     */
    default void revokeSessionsForUser(String userId) {}

    /**
     * Cấp Action Grant (ES256, hạn 60s) để thực hiện override manual/emergency tại Edge.
     *
     * @param actor người dùng yêu cầu
     * @param request thông tin cấp grant gồm mật khẩu xác thực lại, action, edgeId, gateId, reason
     * @return ActionGrantResponse chứa token grant và expiresAt
     */
    ActionGrantResponse issueActionGrant(ActorContext actor, ActionGrantRequest request);

    // ─────────────────────────── Requests ─────────────────────────────────────

    record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {}

    record ReauthRequest(
            @NotBlank String password
    ) {}

    record ReauthResponse(
            Instant reauthenticatedUntil
    ) {}

    record ActionGrantRequest(
            @NotBlank String password,
            @NotBlank String action, // "MANUAL" | "EMERGENCY"
            @NotBlank String edgeId,
            @NotBlank String gateId,
            String employeeId,
            @NotBlank @Size(min = 10, max = 500) String reason
    ) {}
}
