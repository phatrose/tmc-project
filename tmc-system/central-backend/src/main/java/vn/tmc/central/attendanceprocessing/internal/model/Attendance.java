package vn.tmc.central.attendanceprocessing.internal.model;

/**
 * PHỤ TRÁCH: Task 3.
 * Model tương thích ngược cho attendance.
 */
public record Attendance(String eventId, String employeeId, String direction) {}
