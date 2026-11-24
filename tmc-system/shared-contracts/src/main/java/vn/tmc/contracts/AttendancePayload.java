package vn.tmc.contracts;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: DTO EVT-01: attendance.event.recorded theo CONTRACT-V1.md §6 & §12.
 * NGUỒN: CONTRACT-V1.md §6:
 * {employeeId:UUID, direction:"IN"|"OUT", source:"ONLINE"|"OFFLINE"|"MANUAL", jti:UUID?, kid:string?, tokenIssuedAt:Instant?, commandId:UUID, operatorId:UUID?, reason:string?}
 * 
 * QUY TẮC BẮT BUỘC:
 * - QR event (ONLINE / OFFLINE): bắt buộc jti, kid, tokenIssuedAt; operatorId = null, reason = null.
 * - MANUAL event: bắt buộc operatorId, reason; jti = null, kid = null, tokenIssuedAt = null.
 */
public record AttendancePayload(
        UUID employeeId,
        Direction direction,
        Source source,
        UUID jti,
        String kid,
        Instant tokenIssuedAt,
        UUID commandId,
        UUID operatorId,
        String reason
) {
    public enum Direction { IN, OUT }
    public enum Source { ONLINE, OFFLINE, MANUAL }

    public AttendancePayload {
        Objects.requireNonNull(employeeId, "employeeId is required");
        Objects.requireNonNull(direction, "direction is required");
        Objects.requireNonNull(commandId, "commandId is required");
        if (source == null) {
            source = Source.ONLINE;
        }

        if (source == Source.MANUAL) {
            if (operatorId == null) {
                throw new IllegalArgumentException("operatorId is required for MANUAL source");
            }
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason is required for MANUAL source");
            }
            if (jti != null || kid != null || tokenIssuedAt != null) {
                throw new IllegalArgumentException("MANUAL event must not have token fields (jti, kid, tokenIssuedAt)");
            }
        } else {
            // ONLINE or OFFLINE QR scan event
            if (jti == null) {
                throw new IllegalArgumentException("jti is required for QR attendance event");
            }
            if (kid == null || kid.isBlank()) {
                throw new IllegalArgumentException("kid is required for QR attendance event");
            }
            if (tokenIssuedAt == null) {
                throw new IllegalArgumentException("tokenIssuedAt is required for QR attendance event");
            }
            if (operatorId != null || reason != null) {
                throw new IllegalArgumentException("QR event must not have operatorId or reason");
            }
        }
    }

    /**
     * Factory cho sự kiện quét mã QR (ONLINE hoặc OFFLINE).
     */
    public static AttendancePayload ofQr(
            UUID employeeId,
            Direction direction,
            Source source,
            UUID jti,
            String kid,
            Instant tokenIssuedAt,
            UUID commandId
    ) {
        return new AttendancePayload(employeeId, direction, source, jti, kid, tokenIssuedAt, commandId, null, null);
    }

    /**
     * Factory cho sự kiện mở cổng thủ công (MANUAL override).
     */
    public static AttendancePayload ofManual(
            UUID employeeId,
            Direction direction,
            UUID operatorId,
            String reason,
            UUID commandId
    ) {
        return new AttendancePayload(employeeId, direction, Source.MANUAL, null, null, null, commandId, operatorId, reason);
    }

    /**
     * Tiện ích chuyển đổi sang Canonical JSON theo RFC 8785 (keys sorted alphabetically).
     */
    public String toCanonicalJson() {
        if (source == Source.MANUAL) {
            return String.format(
                    "{\"commandId\":\"%s\",\"direction\":\"%s\",\"employeeId\":\"%s\",\"operatorId\":\"%s\",\"reason\":\"%s\",\"source\":\"MANUAL\"}",
                    commandId, direction.name(), employeeId, operatorId, escapeJson(reason)
            );
        } else {
            return String.format(
                    "{\"commandId\":\"%s\",\"direction\":\"%s\",\"employeeId\":\"%s\",\"jti\":\"%s\",\"kid\":\"%s\",\"source\":\"%s\",\"tokenIssuedAt\":\"%s\"}",
                    commandId, direction.name(), employeeId, jti, kid, source.name(), tokenIssuedAt.toString()
            );
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
