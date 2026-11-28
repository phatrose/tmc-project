package vn.tmc.central.attendanceprocessing.internal.model;

import vn.tmc.central.attendanceprocessing.api.DailyAttendance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: Entity bảng daily_attendance_records theo CONTRACT-V1.md §9.
 * 
 * QUY TẮC BẮT BUỘC (CONTRACT-V1 §9):
 * - Thiếu IN/OUT thì workedMinutes = null, không coi 0 giờ đã xác nhận.
 * - version: số nguyên quản lý optimistic concurrency.
 */
public final class DailyAttendanceRecord {
    private final String id;
    private final UUID employeeId;
    private final UUID factoryId;
    private final LocalDate workDate;
    private UUID shiftId;
    private String shiftCode;
    private Instant firstIn;
    private Instant lastOut;
    private Integer workedMinutes;
    private int lateMinutes;
    private int earlyMinutes;
    private boolean isMissingSwipe;
    private boolean isAdjusted;
    private final List<String> flags;
    private String status;
    private int version;

    public DailyAttendanceRecord(
            String id,
            UUID employeeId,
            UUID factoryId,
            LocalDate workDate,
            UUID shiftId,
            String shiftCode
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.factoryId = Objects.requireNonNull(factoryId, "factoryId must not be null");
        this.workDate = Objects.requireNonNull(workDate, "workDate must not be null");
        this.shiftId = shiftId;
        this.shiftCode = shiftCode != null ? shiftCode : "SHIFT_1";
        this.flags = new ArrayList<>();
        this.status = "PENDING";
        this.version = 1;
        this.workedMinutes = null;
    }

    public String getId() { return id; }
    public UUID getEmployeeId() { return employeeId; }
    public UUID getFactoryId() { return factoryId; }
    public LocalDate getWorkDate() { return workDate; }
    public UUID getShiftId() { return shiftId; }
    public void setShiftId(UUID shiftId) { this.shiftId = shiftId; }
    public String getShiftCode() { return shiftCode; }
    public void setShiftCode(String shiftCode) { this.shiftCode = shiftCode; }
    public Instant getFirstIn() { return firstIn; }
    public void setFirstIn(Instant firstIn) { this.firstIn = firstIn; }
    public Instant getLastOut() { return lastOut; }
    public void setLastOut(Instant lastOut) { this.lastOut = lastOut; }
    public Integer getWorkedMinutes() { return workedMinutes; }
    public void setWorkedMinutes(Integer workedMinutes) { this.workedMinutes = workedMinutes; }
    public double getHoursWorked() { return workedMinutes != null ? workedMinutes / 60.0 : 0.0; }
    public boolean isLate() { return lateMinutes > 0; }
    public int getLateMinutes() { return lateMinutes; }
    public void setLateMinutes(int lateMinutes) { this.lateMinutes = lateMinutes; }
    public boolean isEarlyLeave() { return earlyMinutes > 0; }
    public int getEarlyMinutes() { return earlyMinutes; }
    public void setEarlyMinutes(int earlyMinutes) { this.earlyMinutes = earlyMinutes; }
    public boolean isMissingSwipe() { return isMissingSwipe; }
    public void setMissingSwipe(boolean missingSwipe) { this.isMissingSwipe = missingSwipe; }
    public boolean isAdjusted() { return isAdjusted; }
    public void setAdjusted(boolean adjusted) { isAdjusted = adjusted; }
    public List<String> getFlags() { return Collections.unmodifiableList(flags); }
    public void addFlag(String flag) { if (flag != null && !this.flags.contains(flag)) this.flags.add(flag); }
    public void removeFlag(String flag) { if (flag != null) this.flags.remove(flag); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public void incrementVersion() { this.version++; }

    public DailyAttendance toContractDto() {
        return new DailyAttendance(
                employeeId,
                factoryId,
                shiftId,
                workDate,
                firstIn,
                lastOut,
                workedMinutes,
                lateMinutes,
                earlyMinutes,
                flags,
                version
        );
    }
}
