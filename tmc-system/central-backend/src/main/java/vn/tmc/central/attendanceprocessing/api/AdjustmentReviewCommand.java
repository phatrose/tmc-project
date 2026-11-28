package vn.tmc.central.attendanceprocessing.api;

import vn.tmc.central.usermgmt.api.Role;

/**
 * PHỤ TRÁCH: Task 3.
 * Command phê duyệt hoặc từ chối yêu cầu điều chỉnh công.
 */
public record AdjustmentReviewCommand(
        String adjustmentId,
        String reviewerId,
        Role reviewerRole,
        boolean approved,
        String reviewNote
) {
    public AdjustmentReviewCommand {
        if (adjustmentId == null || adjustmentId.isBlank()) {
            throw new IllegalArgumentException("adjustmentId is required");
        }
        if (reviewerId == null || reviewerId.isBlank()) {
            throw new IllegalArgumentException("reviewerId is required");
        }
        if (reviewerRole == null) {
            throw new IllegalArgumentException("reviewerRole is required");
        }
    }
}
