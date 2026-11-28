package vn.tmc.central.systemadmin.internal.model;

import java.time.LocalTime;
import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Domain Model cho Shift (Ca làm việc).
 * NGUỒN: main.pdf, FR-SA-02; §5.1; DECISIONS.md §1.
 */
public record Shift(
    String id,
    String factoryId,
    String name,
    LocalTime startsAt,
    LocalTime endsAt,
    boolean isOvernight,
    int gracePeriodMinutesLate,
    int gracePeriodMinutesEarly,
    boolean active
) {
    public Shift {
        Objects.requireNonNull(id, "shiftId must not be null");
        Objects.requireNonNull(factoryId, "factoryId must not be null");
        Objects.requireNonNull(startsAt, "startsAt must not be null");
        Objects.requireNonNull(endsAt, "endsAt must not be null");
        if (name == null || name.isBlank()) {
            name = id;
        }
        // Tự động nhận diện ca đêm qua ngày nếu kết thúc <= bắt đầu
        if (endsAt.isBefore(startsAt) || endsAt.equals(startsAt)) {
            isOvernight = true;
        }
        if (gracePeriodMinutesLate < 0) gracePeriodMinutesLate = 0;
        if (gracePeriodMinutesEarly < 0) gracePeriodMinutesEarly = 0;
    }

    public Shift(String id, String factoryId, LocalTime startsAt, LocalTime endsAt) {
        this(id, factoryId, id, startsAt, endsAt, endsAt.isBefore(startsAt) || endsAt.equals(startsAt), 15, 15, true);
    }
}
