package vn.tmc.central.systemadmin.api;

import java.time.ZoneId;
import java.util.List;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Public API Port cho module System Administration.
 * NGUỒN: main.pdf, FR-SA-01..06; BR-08/09/11; DECISIONS.md §1.
 * TÀI LIỆU PDF: docs/tasks/03-modules-solid.md; docs/REQUIREMENTS.md.
 */
public interface Administration {
    // FR-SA-02, FR-SA-03: Cấu hình hệ thống và tích hợp
    SystemConfigurationView findConfiguration(String key);
    SystemConfigurationView updateConfiguration(String key, String value);
    List<SystemConfigurationView> findConfigurationsByCategory(String category);
    List<SystemConfigurationView> findAllConfigurations();

    // FR-SA-02, DECISIONS.md §1: Timezone nhà máy và cửa sổ chống quét lặp
    ZoneId getFactoryZoneId(String factoryId);
    int getDuplicateScanWindowSeconds();

    // Quản lý nhà máy
    List<FactoryView> findAllFactories();
    FactoryView findFactory(String factoryId);
    FactoryView registerFactory(String id, String name, String zoneId, String address);

    // FR-SA-02: Ca làm việc và ngày lễ
    List<ShiftView> findShiftsByFactory(String factoryId);
    ShiftView findShift(String shiftId);
    ShiftView registerShift(String id, String factoryId, String name, java.time.LocalTime startsAt, java.time.LocalTime endsAt, int graceLate, int graceEarly);

    List<HolidayView> findHolidays(String factoryId, int year);
    HolidayView registerHoliday(String id, String factoryId, String name, java.time.LocalDate date, boolean paid);

    // FR-SA-04: Giám sát sức khỏe hệ thống và thiết bị
    SystemHealthView getSystemHealth();
    List<DeviceStatusView> getDeviceStatuses(String factoryId);
    void recordDeviceHeartbeat(String deviceId, String deviceType, String factoryId, String ipAddress);

    // FR-SA-05: Sao lưu, lưu trữ và xóa/ẩn danh dữ liệu
    RetentionPolicyView getRetentionPolicy();
    ArchivalResultView triggerDataArchival();

    // FR-SA-06: Quyền của chủ thể dữ liệu (DSR)
    DataSubjectRequestView submitDataSubjectRequest(String employeeId, String requestType, String reason);
    DataSubjectRequestView processDataSubjectRequest(String requestId, String status, String processedBy, String notes);
    List<DataSubjectRequestView> findDataSubjectRequestsByEmployee(String employeeId);
    List<DataSubjectRequestView> findAllDataSubjectRequests();
}
