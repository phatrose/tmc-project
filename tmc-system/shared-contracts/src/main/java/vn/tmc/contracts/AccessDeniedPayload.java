package vn.tmc.contracts;

import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: DTO EVT-02: access.attempt.denied theo CONTRACT-V1.md §6.
 * Payload JSON canonical: {employeeId:UUID?, reasonCode:string, jti:UUID?, kid:string?, source:"ONLINE"|"OFFLINE"}
 */
public record AccessDeniedPayload(
        UUID employeeId,
        String reasonCode,
        UUID jti,
        String kid,
        Source source
) {
    public enum Source { ONLINE, OFFLINE }

    public AccessDeniedPayload {
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode is required");
        }
        if (source == null) {
            source = Source.ONLINE;
        }
    }
}
