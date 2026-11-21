package vn.tmc.central.attendanceprocessing.api;

import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: DTO quyết định phê duyệt/từ chối điều chỉnh theo CONTRACT-V1.md §9.
 * POST /api/v1/attendance/adjustments/{id}/decision -> {decision:"APPROVE"|"REJECT", reason:string, version:int}
 */
public record AdjustmentDecision(
        String decision, // "APPROVE" | "REJECT"
        String reason,
        int version
) {
    public AdjustmentDecision {
        Objects.requireNonNull(decision, "decision must not be null");
        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) {
            throw new IllegalArgumentException("decision must be either APPROVE or REJECT, got: " + decision);
        }
        Objects.requireNonNull(reason, "reason must not be null");
        if (version < 1) {
            throw new IllegalArgumentException("version must be at least 1, got: " + version);
        }
    }
}
