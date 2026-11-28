package vn.tmc.central.systemadmin.internal.controller;

import vn.tmc.central.systemadmin.api.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Controller tiếp nhận và xử lý yêu cầu cho System Administration.
 * NGUỒN: main.pdf, FR-SA-01..06; §4.2; §5.1; DECISIONS.md §1, §6, §7.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 */
public final class AdministrationController {
    private final Administration service;

    public AdministrationController(Administration service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    // GET /api/v1/admin/configurations/{key}
    public SystemConfigurationView findConfiguration(String key) {
        return service.findConfiguration(key);
    }

    // GET /api/v1/admin/configurations
    public List<SystemConfigurationView> findAllConfigurations() {
        return service.findAllConfigurations();
    }

    // GET /api/v1/admin/configurations?category={category}
    public List<SystemConfigurationView> findConfigurationsByCategory(String category) {
        return service.findConfigurationsByCategory(category);
    }

    // PUT /api/v1/admin/configurations/{key}
    public SystemConfigurationView updateConfiguration(String key, String value) {
        return service.updateConfiguration(key, value);
    }

    // GET /api/v1/admin/factories
    public List<FactoryView> findAllFactories() {
        return service.findAllFactories();
    }

    // GET /api/v1/admin/factories/{factoryId}
    public FactoryView findFactory(String factoryId) {
        return service.findFactory(factoryId);
    }

    // POST /api/v1/admin/factories
    public FactoryView registerFactory(String id, String name, String zoneId, String address) {
        return service.registerFactory(id, name, zoneId, address);
    }

    // GET /api/v1/admin/factories/{factoryId}/shifts
    public List<ShiftView> findShiftsByFactory(String factoryId) {
        return service.findShiftsByFactory(factoryId);
    }

    // POST /api/v1/admin/shifts
    public ShiftView registerShift(String id, String factoryId, String name, LocalTime startsAt, LocalTime endsAt, int graceLate, int graceEarly) {
        return service.registerShift(id, factoryId, name, startsAt, endsAt, graceLate, graceEarly);
    }

    // GET /api/v1/admin/holidays
    public List<HolidayView> findHolidays(String factoryId, int year) {
        return service.findHolidays(factoryId, year);
    }

    // POST /api/v1/admin/holidays
    public HolidayView registerHoliday(String id, String factoryId, String name, LocalDate date, boolean paid) {
        return service.registerHoliday(id, factoryId, name, date, paid);
    }

    // GET /api/v1/admin/health
    public SystemHealthView getSystemHealth() {
        return service.getSystemHealth();
    }

    // GET /api/v1/admin/devices
    public List<DeviceStatusView> getDeviceStatuses(String factoryId) {
        return service.getDeviceStatuses(factoryId);
    }

    // GET /api/v1/admin/retention-policy
    public RetentionPolicyView getRetentionPolicy() {
        return service.getRetentionPolicy();
    }

    // POST /api/v1/admin/archive/trigger
    public ArchivalResultView triggerDataArchival() {
        return service.triggerDataArchival();
    }

    // POST /api/v1/admin/dsr
    public DataSubjectRequestView submitDataSubjectRequest(String employeeId, String requestType, String reason) {
        return service.submitDataSubjectRequest(employeeId, requestType, reason);
    }

    // PUT /api/v1/admin/dsr/{requestId}
    public DataSubjectRequestView processDataSubjectRequest(String requestId, String status, String processedBy, String notes) {
        return service.processDataSubjectRequest(requestId, status, processedBy, notes);
    }

    // GET /api/v1/admin/dsr
    public List<DataSubjectRequestView> findAllDataSubjectRequests() {
        return service.findAllDataSubjectRequests();
    }

    // GET /api/v1/admin/dsr?employeeId={employeeId}
    public List<DataSubjectRequestView> findDataSubjectRequestsByEmployee(String employeeId) {
        return service.findDataSubjectRequestsByEmployee(employeeId);
    }
}
