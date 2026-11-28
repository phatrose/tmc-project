package vn.tmc.central.attendanceprocessing.internal.service;

/**
 * PHỤ TRÁCH: Task 3.
 * Ném ra khi thiết bị gửi đồng bộ không khớp chứng chỉ / định danh edgeId-factoryId.
 * Ánh xạ HTTP 403 DEVICE_BINDING_MISMATCH theo CONTRACT-V1.md §7 & Ví dụ #5.
 */
public class DeviceBindingException extends RuntimeException {
    public DeviceBindingException(String message) {
        super(message);
    }
}
