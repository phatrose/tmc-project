package vn.tmc.central.attendanceprocessing.api;

import vn.tmc.central.usermgmt.api.ActorContext;
import vn.tmc.contracts.Page;

import java.util.List;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: Port xử lý quy trình điều chỉnh chấm công theo CONTRACT-V1.md §9 & §10.
 */
public interface AttendanceAdjustmentPort {
    /**
     * Tạo yêu cầu điều chỉnh công mới theo CONTRACT-V1.md §9.
     */
    Adjustment createAdjustment(ActorContext context, AdjustmentRequest request);

    /**
     * Phê duyệt hoặc từ chối điều chỉnh (attendance.approve và khác người tạo; cấm Auditor).
     */
    Adjustment decideAdjustment(ActorContext context, UUID adjustmentId, AdjustmentDecision decision);

    /**
     * Truy vấn danh sách điều chỉnh theo scope.
     */
    Page<Adjustment> queryAdjustments(ActorContext context, UUID employeeId, String state, int page, int size);

    // Tiện ích tương thích ngược
    AttendanceAdjustmentView requestAdjustment(AdjustmentRequestCommand command);
    AttendanceAdjustmentView reviewAdjustment(AdjustmentReviewCommand command);
    List<AttendanceAdjustmentView> queryAdjustments(String employeeId, AdjustmentStatus status);
}
