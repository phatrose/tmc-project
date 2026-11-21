package vn.tmc.central.usermgmt.api;

import java.time.Instant;

/**
 * PHỤ TRÁCH: Task 1.
 * Thông tin phiên đăng nhập trả về sau khi xác thực thành công.
 * Theo CONTRACT-V1.md §3.
 *
 * @param accessToken mã phiên Bearer opaque (hạn 15 phút)
 * @param tokenType luôn là "Bearer"
 * @param expiresAt thời điểm hết hạn của phiên (UTC)
 * @param actor ngữ cảnh định danh tin cậy của người dùng
 */
public record Session(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        ActorContext actor
) {}
