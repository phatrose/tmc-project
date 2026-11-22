package vn.tmc.central.usermgmt.api;

import java.time.Instant;

/**
 * PHỤ TRÁCH: Task 1.
 * DTO phản hồi cấp quyền hành động đặc biệt (Action Grant) cho manual/emergency tại Edge.
 * Theo CONTRACT-V1.md §5.
 *
 * @param grant chuỗi JWS ES256 chứa claims ủy quyền (hạn 60 giây)
 * @param expiresAt thời điểm hết hạn
 */
public record ActionGrantResponse(
        String grant,
        Instant expiresAt
) {}
