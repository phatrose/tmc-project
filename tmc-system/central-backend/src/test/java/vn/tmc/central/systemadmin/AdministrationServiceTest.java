package vn.tmc.central.systemadmin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.tmc.central.systemadmin.api.*;
import vn.tmc.central.systemadmin.internal.repository.*;
import vn.tmc.central.systemadmin.internal.service.AdministrationService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHỤ TRÁCH: Task 5.
 * Kiểm thử đơn vị cho AdministrationService (FR-SA-01..06, BR-03, BR-08, BR-11, DECISIONS.md §1).
 */
class AdministrationServiceTest {
    private InMemorySystemConfigurationRepository configRepo;
    private InMemoryFactoryRepository factoryRepo;
    private InMemoryShiftRepository shiftRepo;
    private InMemoryHolidayRepository holidayRepo;
    private InMemoryDataSubjectRequestRepository dsrRepo;
    private InMemoryDeviceStatusRepository deviceRepo;
    private InMemoryGateRepository gateRepo;
    private AdministrationService adminService;

    @BeforeEach
    void setUp() {
        configRepo = new InMemorySystemConfigurationRepository();
        factoryRepo = new InMemoryFactoryRepository();
        shiftRepo = new InMemoryShiftRepository();
        holidayRepo = new InMemoryHolidayRepository();
        dsrRepo = new InMemoryDataSubjectRequestRepository();
        deviceRepo = new InMemoryDeviceStatusRepository();
        gateRepo = new InMemoryGateRepository();

        adminService = new AdministrationService(
            configRepo,
            factoryRepo,
            shiftRepo,
            holidayRepo,
            dsrRepo,
            deviceRepo,
            gateRepo
        );
    }

    @Test
    @DisplayName("findConfiguration: tìm thấy cấu hình hệ thống đã seed")
    void testFindConfiguration_Success() {
        SystemConfigurationView tzConfig = adminService.findConfiguration("system.timezone.default");
        assertNotNull(tzConfig);
        assertEquals("Asia/Ho_Chi_Minh", tzConfig.value());

        SystemConfigurationView windowConfig = adminService.findConfiguration("attendance.duplicate-scan-window-seconds");
        assertNotNull(windowConfig);
        assertEquals("30", windowConfig.value());
    }

    @Test
    @DisplayName("updateConfiguration: cập nhật giá trị cấu hình thành công")
    void testUpdateConfiguration_Success() {
        SystemConfigurationView updated = adminService.updateConfiguration("hr.payroll.timeout-ms", "8000");
        assertEquals("8000", updated.value());

        SystemConfigurationView inService = adminService.findConfiguration("hr.payroll.timeout-ms");
        assertEquals("8000", inService.value());
    }

    @Test
    @DisplayName("getFactoryZoneId: trả về đúng Timezone của nhà máy phục vụ chốt kỳ công lúc 06:30 ngày 01 (DECISIONS.md §1)")
    void testGetFactoryZoneId() {
        ZoneId bdZone = adminService.getFactoryZoneId("FACTORY-BD");
        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), bdZone);

        ZoneId laZone = adminService.getFactoryZoneId("FACTORY-LA");
        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), laZone);
    }

    @Test
    @DisplayName("getDuplicateScanWindowSeconds: trả về 30 giây theo BR-03")
    void testGetDuplicateScanWindowSeconds() {
        int window = adminService.getDuplicateScanWindowSeconds();
        assertEquals(30, window);
    }

    @Test
    @DisplayName("registerShift: tự động nhận diện ca đêm qua ngày (overnight) khi endsAt <= startsAt")
    void testRegisterShift_OvernightDetection() {
        // Ca đêm 22:00 -> 06:00
        ShiftView overnightShift = adminService.registerShift(
            "SHIFT-BD-NIGHT-TEST",
            "FACTORY-BD",
            "Ca Đêm Test",
            LocalTime.of(22, 0),
            LocalTime.of(6, 0),
            15,
            15
        );

        assertNotNull(overnightShift);
        assertTrue(overnightShift.isOvernight(), "Ca làm từ 22:00 đến 06:00 hôm sau phải có isOvernight = true");

        // Ca ngày 08:00 -> 17:00
        ShiftView dayShift = adminService.registerShift(
            "SHIFT-BD-DAY-TEST",
            "FACTORY-BD",
            "Ca Ngày Test",
            LocalTime.of(8, 0),
            LocalTime.of(17, 0),
            15,
            15
        );
        assertFalse(dayShift.isOvernight(), "Ca làm từ 08:00 đến 17:00 cùng ngày phải có isOvernight = false");
    }

    @Test
    @DisplayName("findHolidays: lọc ngày nghỉ lễ theo năm và nhà máy")
    void testFindHolidays() {
        List<HolidayView> holidays = adminService.findHolidays("FACTORY-BD", LocalDate.now().getYear());
        assertFalse(holidays.isEmpty());
        assertTrue(holidays.stream().anyMatch(h -> h.name().contains("Quốc khánh")));
    }

    @Test
    @DisplayName("getSystemHealth: tổng hợp trạng thái sức khỏe thiết bị và cổng")
    void testGetSystemHealth() {
        SystemHealthView health = adminService.getSystemHealth();
        assertNotNull(health);
        assertTrue(health.edgeNodesTotal() > 0);
        assertTrue(health.gatesTotal() > 0);
        assertEquals("HEALTHY", health.status());
    }

    @Test
    @DisplayName("getRetentionPolicy: trả về chính sách 2 năm trực tuyến, 5 năm kho lạnh, 36 tháng ẩn danh (BR-11)")
    void testGetRetentionPolicy() {
        RetentionPolicyView policy = adminService.getRetentionPolicy();
        assertNotNull(policy);
        assertEquals(2, policy.onlineRetentionYears());
        assertEquals(5, policy.coldArchiveRetentionYears());
        assertEquals(36, policy.anonymizeInactiveMonths());
    }

    @Test
    @DisplayName("triggerDataArchival: thực hiện tác vụ lưu trữ và ẩn danh thành công")
    void testTriggerDataArchival() {
        ArchivalResultView result = adminService.triggerDataArchival();
        assertNotNull(result);
        assertEquals("SUCCESS", result.status());
        assertTrue(result.recordsArchived() > 0);
    }

    @Test
    @DisplayName("submitDataSubjectRequest & processDataSubjectRequest: tuân thủ BR-11 (chỉ ẩn danh hồ sơ nhân viên, không sửa event bất biến theo BR-04)")
    void testDataSubjectRequest_Lifecycle() {
        // Tạo yêu cầu xóa/ẩn danh dữ liệu (ERASURE)
        DataSubjectRequestView req = adminService.submitDataSubjectRequest(
            "EMP-0123",
            "ERASURE",
            "Nhân viên đã nghỉ việc yêu cầu xóa dữ liệu cá nhân"
        );

        assertNotNull(req);
        assertEquals("PENDING", req.status());
        assertEquals("ERASURE", req.requestType());

        // Phê duyệt và hoàn thành
        DataSubjectRequestView processed = adminService.processDataSubjectRequest(
            req.id(),
            "COMPLETED",
            "hr_admin",
            "Đã xử lý yêu cầu."
        );

        assertEquals("COMPLETED", processed.status());
        assertEquals("hr_admin", processed.processedBy());
        assertTrue(processed.notes().contains("BR-11"), "Ghi chú xử lý phải thể hiện tuân thủ BR-11");
        assertTrue(processed.notes().contains("BR-04"), "Ghi chú xử lý phải thể hiện tính bất biến của event theo BR-04");
    }
}
