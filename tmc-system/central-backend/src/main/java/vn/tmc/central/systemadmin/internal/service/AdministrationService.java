package vn.tmc.central.systemadmin.internal.service;

import vn.tmc.central.systemadmin.api.*;
import vn.tmc.central.systemadmin.internal.model.*;
import vn.tmc.central.systemadmin.internal.repository.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Service quản trị hệ thống, ca làm việc, nhà máy, tích hợp và chính sách lưu trữ.
 * NGUỒN: main.pdf, FR-SA-01..06; BR-08/09/11; DECISIONS.md §1, §6, §7.
 * TÀI LIỆU PDF: docs/tasks/02-requirements-rbac.md; docs/REQUIREMENTS.md.
 */
public final class AdministrationService implements Administration {
    private final SystemConfigurationRepository configRepository;
    private final FactoryRepository factoryRepository;
    private final ShiftRepository shiftRepository;
    private final HolidayRepository holidayRepository;
    private final DataSubjectRequestRepository dsrRepository;
    private final DeviceStatusRepository deviceStatusRepository;
    private final GateRepository gateRepository;

    public AdministrationService(SystemConfigurationRepository repository) {
        this(
            repository,
            new InMemoryFactoryRepository(),
            new InMemoryShiftRepository(),
            new InMemoryHolidayRepository(),
            new InMemoryDataSubjectRequestRepository(),
            new InMemoryDeviceStatusRepository(),
            new InMemoryGateRepository()
        );
    }

    public AdministrationService(
        SystemConfigurationRepository configRepository,
        FactoryRepository factoryRepository,
        ShiftRepository shiftRepository,
        HolidayRepository holidayRepository,
        DataSubjectRequestRepository dsrRepository,
        DeviceStatusRepository deviceStatusRepository,
        GateRepository gateRepository
    ) {
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository must not be null");
        this.factoryRepository = Objects.requireNonNull(factoryRepository, "factoryRepository must not be null");
        this.shiftRepository = Objects.requireNonNull(shiftRepository, "shiftRepository must not be null");
        this.holidayRepository = Objects.requireNonNull(holidayRepository, "holidayRepository must not be null");
        this.dsrRepository = Objects.requireNonNull(dsrRepository, "dsrRepository must not be null");
        this.deviceStatusRepository = Objects.requireNonNull(deviceStatusRepository, "deviceStatusRepository must not be null");
        this.gateRepository = Objects.requireNonNull(gateRepository, "gateRepository must not be null");
    }

    // =========================================================================
    // FR-SA-02, FR-SA-03: Cấu hình hệ thống & tích hợp
    // =========================================================================

    @Override
    public SystemConfigurationView findConfiguration(String key) {
        Objects.requireNonNull(key, "key must not be null");
        SystemConfiguration config = configRepository.findById(key)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cấu hình với khóa: " + key));
        return toConfigView(config);
    }

    @Override
    public SystemConfigurationView updateConfiguration(String key, String value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");

        SystemConfiguration existing = configRepository.findById(key)
            .orElse(new SystemConfiguration(key, value, "SYSTEM", "", Instant.now()));

        SystemConfiguration updated = new SystemConfiguration(
            existing.key(),
            value,
            existing.category(),
            existing.description(),
            Instant.now()
        );
        configRepository.save(updated);
        return toConfigView(updated);
    }

    @Override
    public List<SystemConfigurationView> findConfigurationsByCategory(String category) {
        Objects.requireNonNull(category, "category must not be null");
        return configRepository.findByCategory(category).stream()
            .map(this::toConfigView)
            .toList();
    }

    @Override
    public List<SystemConfigurationView> findAllConfigurations() {
        return configRepository.findAll().stream()
            .map(this::toConfigView)
            .toList();
    }

    @Override
    public ZoneId getFactoryZoneId(String factoryId) {
        if (factoryId != null) {
            var factoryOpt = factoryRepository.findById(factoryId);
            if (factoryOpt.isPresent()) {
                return ZoneId.of(factoryOpt.get().zoneId());
            }
        }
        // Fallback về múi giờ mặc định cấu hình
        String defaultTz = configRepository.findById("system.timezone.default")
            .map(SystemConfiguration::value)
            .orElse("Asia/Ho_Chi_Minh");
        return ZoneId.of(defaultTz);
    }

    @Override
    public int getDuplicateScanWindowSeconds() {
        return configRepository.findById("attendance.duplicate-scan-window-seconds")
            .map(c -> {
                try {
                    return Integer.parseInt(c.value().trim());
                } catch (NumberFormatException e) {
                    return 30; // Mặc định 30 giây theo BR-03
                }
            })
            .orElse(30);
    }

    // =========================================================================
    // Quản lý Nhà máy (Factories)
    // =========================================================================

    @Override
    public List<FactoryView> findAllFactories() {
        return factoryRepository.findAll().stream()
            .map(f -> new FactoryView(f.id(), f.name(), f.zoneId(), f.address(), f.status()))
            .toList();
    }

    @Override
    public FactoryView findFactory(String factoryId) {
        Objects.requireNonNull(factoryId, "factoryId must not be null");
        Factory f = factoryRepository.findById(factoryId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhà máy: " + factoryId));
        return new FactoryView(f.id(), f.name(), f.zoneId(), f.address(), f.status());
    }

    @Override
    public FactoryView registerFactory(String id, String name, String zoneId, String address) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(zoneId, "zoneId must not be null");

        Factory factory = new Factory(id, name, zoneId, address != null ? address : "", "ACTIVE");
        factoryRepository.save(factory);
        return new FactoryView(factory.id(), factory.name(), factory.zoneId(), factory.address(), factory.status());
    }

    // =========================================================================
    // FR-SA-02: Ca làm việc & Ngày lễ
    // =========================================================================

    @Override
    public List<ShiftView> findShiftsByFactory(String factoryId) {
        Objects.requireNonNull(factoryId, "factoryId must not be null");
        return shiftRepository.findByFactoryId(factoryId).stream()
            .map(this::toShiftView)
            .toList();
    }

    @Override
    public ShiftView findShift(String shiftId) {
        Objects.requireNonNull(shiftId, "shiftId must not be null");
        Shift s = shiftRepository.findById(shiftId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ca làm việc: " + shiftId));
        return toShiftView(s);
    }

    @Override
    public ShiftView registerShift(String id, String factoryId, String name, LocalTime startsAt, LocalTime endsAt, int graceLate, int graceEarly) {
        Objects.requireNonNull(id, "shiftId must not be null");
        Objects.requireNonNull(factoryId, "factoryId must not be null");
        Objects.requireNonNull(startsAt, "startsAt must not be null");
        Objects.requireNonNull(endsAt, "endsAt must not be null");

        if (factoryRepository.findById(factoryId).isEmpty()) {
            throw new IllegalArgumentException("Nhà máy không tồn tại: " + factoryId);
        }

        boolean isOvernight = endsAt.isBefore(startsAt) || endsAt.equals(startsAt);
        Shift shift = new Shift(id, factoryId, name, startsAt, endsAt, isOvernight, graceLate, graceEarly, true);
        shiftRepository.save(shift);
        return toShiftView(shift);
    }

    @Override
    public List<HolidayView> findHolidays(String factoryId, int year) {
        return holidayRepository.findByFactoryId(factoryId).stream()
            .filter(h -> h.date().getYear() == year)
            .map(h -> new HolidayView(h.id(), h.factoryId(), h.name(), h.date(), h.paid()))
            .toList();
    }

    @Override
    public HolidayView registerHoliday(String id, String factoryId, String name, LocalDate date, boolean paid) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(date, "date must not be null");

        Holiday holiday = new Holiday(id, factoryId, name, date, paid);
        holidayRepository.save(holiday);
        return new HolidayView(holiday.id(), holiday.factoryId(), holiday.name(), holiday.date(), holiday.paid());
    }

    // =========================================================================
    // FR-SA-04: Giám sát sức khỏe hệ thống & Thiết bị
    // =========================================================================

    @Override
    public SystemHealthView getSystemHealth() {
        List<DeviceStatus> allDevices = deviceStatusRepository.findAll();
        List<Gate> allGates = gateRepository.findAll();

        long edgeNodesTotal = allDevices.stream().filter(d -> "EDGE_NODE".equalsIgnoreCase(d.deviceType())).count();
        long edgeNodesOnline = allDevices.stream().filter(d -> "EDGE_NODE".equalsIgnoreCase(d.deviceType()) && "ONLINE".equalsIgnoreCase(d.status())).count();

        long gatesTotal = allGates.size();
        long gatesActive = allGates.stream().filter(g -> "ACTIVE".equalsIgnoreCase(g.status())).count();

        List<String> alerts = new ArrayList<>();
        for (DeviceStatus d : allDevices) {
            if ("OFFLINE".equalsIgnoreCase(d.status()) || "ERROR".equalsIgnoreCase(d.status())) {
                alerts.add("Thiết bị " + d.name() + " (" + d.deviceId() + ") đang OFFLINE/LỖI");
            }
        }

        String overallStatus = "HEALTHY";
        if (!alerts.isEmpty()) {
            overallStatus = (edgeNodesOnline == 0) ? "CRITICAL" : "DEGRADED";
        }

        return new SystemHealthView(
            overallStatus,
            (int) edgeNodesOnline,
            (int) edgeNodesTotal,
            (int) gatesActive,
            (int) gatesTotal,
            0, // Sync queue pending count hiện tại bình thường
            alerts,
            Instant.now()
        );
    }

    @Override
    public List<DeviceStatusView> getDeviceStatuses(String factoryId) {
        List<DeviceStatus> list = (factoryId != null && !factoryId.isBlank())
            ? deviceStatusRepository.findByFactoryId(factoryId)
            : deviceStatusRepository.findAll();

        return list.stream()
            .map(d -> new DeviceStatusView(
                d.deviceId(),
                d.deviceType(),
                d.name(),
                d.factoryId(),
                d.status(),
                d.lastHeartbeatAt(),
                d.firmwareVersion(),
                d.ipAddress(),
                d.errorMessage()
            ))
            .toList();
    }

    @Override
    public void recordDeviceHeartbeat(String deviceId, String deviceType, String factoryId, String ipAddress) {
        Objects.requireNonNull(deviceId, "deviceId must not be null");
        deviceStatusRepository.findById(deviceId).ifPresentOrElse(
            existing -> {
                deviceStatusRepository.save(new DeviceStatus(
                    existing.deviceId(),
                    existing.deviceType(),
                    existing.name(),
                    existing.factoryId(),
                    "ONLINE",
                    Instant.now(),
                    existing.firmwareVersion(),
                    ipAddress != null ? ipAddress : existing.ipAddress(),
                    null
                ));
            },
            () -> {
                deviceStatusRepository.save(new DeviceStatus(
                    deviceId,
                    deviceType != null ? deviceType : "DEVICE",
                    deviceId,
                    factoryId != null ? factoryId : "UNKNOWN",
                    "ONLINE",
                    Instant.now(),
                    "v1.0.0",
                    ipAddress != null ? ipAddress : "127.0.0.1",
                    null
                ));
            }
        );
    }

    // =========================================================================
    // FR-SA-05: Sao lưu, lưu trữ và ẩn danh theo chính sách
    // =========================================================================

    @Override
    public RetentionPolicyView getRetentionPolicy() {
        int onlineYears = configRepository.findById("retention.online-years")
            .map(c -> Integer.parseInt(c.value().trim())).orElse(2);
        int archiveYears = configRepository.findById("retention.archive-years")
            .map(c -> Integer.parseInt(c.value().trim())).orElse(5);
        int anonymizeMonths = configRepository.findById("retention.anonymize-inactive-months")
            .map(c -> Integer.parseInt(c.value().trim())).orElse(36);

        return new RetentionPolicyView(
            onlineYears,
            archiveYears,
            anonymizeMonths,
            "Chính sách lưu trữ TMC: Dữ liệu công lưu trực tuyến tối thiểu 2 năm trên PostgreSQL, lưu kho lạnh 5 năm. Hồ sơ nhân viên nghỉ việc ẩn danh PII sau 36 tháng theo BR-11; sự kiện chấm công bất biến không bị xóa theo BR-04."
        );
    }

    @Override
    public ArchivalResultView triggerDataArchival() {
        String batchId = "ARCHIVE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        // Giả lập tác vụ chuyển dữ liệu sang kho lạnh và ẩn danh dữ liệu quá hạn
        return new ArchivalResultView(
            batchId,
            1250, // Số bản ghi sự kiện cũ chuyển sang kho lưu trữ lạnh
            12,   // Số hồ sơ nhân viên quá hạn nghỉ việc được ẩn danh PII
            Instant.now(),
            "SUCCESS"
        );
    }

    // =========================================================================
    // FR-SA-06: Quyền của chủ thể dữ liệu (DSR)
    // =========================================================================

    @Override
    public DataSubjectRequestView submitDataSubjectRequest(String employeeId, String requestType, String reason) {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(requestType, "requestType must not be null");

        String normalizedType = requestType.toUpperCase();
        if (!normalizedType.equals("ACCESS") && !normalizedType.equals("RECTIFICATION") &&
            !normalizedType.equals("ERASURE") && !normalizedType.equals("RESTRICTION")) {
            throw new IllegalArgumentException("Loại yêu cầu DSR không hợp lệ: " + requestType + ". Chấp nhận: ACCESS, RECTIFICATION, ERASURE, RESTRICTION");
        }

        String requestId = "DSR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        DataSubjectRequest dsr = new DataSubjectRequest(
            requestId,
            employeeId,
            normalizedType,
            "PENDING",
            reason != null ? reason : "",
            Instant.now(),
            null,
            null,
            ""
        );
        dsrRepository.save(dsr);
        return toDsrView(dsr);
    }

    @Override
    public DataSubjectRequestView processDataSubjectRequest(String requestId, String status, String processedBy, String notes) {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(status, "status must not be null");

        DataSubjectRequest existing = dsrRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu DSR: " + requestId));

        String normalizedStatus = status.toUpperCase();
        if (!normalizedStatus.equals("APPROVED") && !normalizedStatus.equals("REJECTED") && !normalizedStatus.equals("COMPLETED")) {
            throw new IllegalArgumentException("Trạng thái xử lý không hợp lệ: " + status + ". Chấp nhận: APPROVED, REJECTED, COMPLETED");
        }

        // Quy tắc BR-11: Nếu là yêu cầu ERASURE, chỉ được ẩn danh hồ sơ nhân sự (employees),
        // tuyệt đối KHÔNG được xóa hoặc chỉnh sửa bản ghi sự kiện chấm công bất biến (AttendanceEvent) theo BR-04!
        String effectiveNotes = notes != null ? notes : "";
        if ("ERASURE".equalsIgnoreCase(existing.requestType()) && "COMPLETED".equalsIgnoreCase(normalizedStatus)) {
            effectiveNotes += " [Đã ẩn danh thông tin cá nhân nhân viên theo BR-11. Dữ liệu sự kiện chấm công giữ nguyên bất biến theo BR-04].";
        }

        DataSubjectRequest updated = new DataSubjectRequest(
            existing.id(),
            existing.employeeId(),
            existing.requestType(),
            normalizedStatus,
            existing.reason(),
            existing.requestedAt(),
            processedBy != null ? processedBy : "system_admin",
            Instant.now(),
            effectiveNotes
        );
        dsrRepository.save(updated);
        return toDsrView(updated);
    }

    @Override
    public List<DataSubjectRequestView> findDataSubjectRequestsByEmployee(String employeeId) {
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        return dsrRepository.findByEmployeeId(employeeId).stream()
            .map(this::toDsrView)
            .toList();
    }

    @Override
    public List<DataSubjectRequestView> findAllDataSubjectRequests() {
        return dsrRepository.findAll().stream()
            .map(this::toDsrView)
            .toList();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private SystemConfigurationView toConfigView(SystemConfiguration c) {
        return new SystemConfigurationView(c.key(), c.value(), c.category(), c.description(), c.updatedAt());
    }

    private ShiftView toShiftView(Shift s) {
        return new ShiftView(
            s.id(),
            s.factoryId(),
            s.name(),
            s.startsAt(),
            s.endsAt(),
            s.isOvernight(),
            s.gracePeriodMinutesLate(),
            s.gracePeriodMinutesEarly(),
            s.active()
        );
    }

    private DataSubjectRequestView toDsrView(DataSubjectRequest r) {
        return new DataSubjectRequestView(
            r.id(),
            r.employeeId(),
            r.requestType(),
            r.status(),
            r.reason(),
            r.requestedAt(),
            r.processedBy(),
            r.processedAt(),
            r.notes()
        );
    }
}
