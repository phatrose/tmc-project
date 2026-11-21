package vn.tmc.central.usermgmt.internal.service;

/**
 * PHỤ TRÁCH: Task 1.
 * Ném ra khi không tìm thấy nhân viên theo ID.
 */
public class EmployeeNotFoundException extends RuntimeException {
    public EmployeeNotFoundException(String employeeId) {
        super("Không tìm thấy nhân viên với ID: " + employeeId);
    }
}
