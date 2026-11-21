package vn.tmc.central.attendanceprocessing.internal.model;

import java.time.Instant;
import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 3.
 * Bảng: adjustment_histories.
 * Nhật ký kiểm toán điều chỉnh công (ai duyệt/từ chối, lúc nào, ghi chú).
 */
public final class AdjustmentHistory {
    private final String id;
    private final String adjustmentId;
    private final String action; // e.g. "APPROVED", "REJECTED"
    private final String actorId;
    private final Instant timestamp;
    private final String note;

    public AdjustmentHistory(String id, String adjustmentId, String action, String actorId, Instant timestamp, String note) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.adjustmentId = Objects.requireNonNull(adjustmentId, "adjustmentId must not be null");
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.actorId = Objects.requireNonNull(actorId, "actorId must not be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.note = note;
    }

    public String getId() { return id; }
    public String getAdjustmentId() { return adjustmentId; }
    public String getAction() { return action; }
    public String getActorId() { return actorId; }
    public Instant getTimestamp() { return timestamp; }
    public String getNote() { return note; }
}
