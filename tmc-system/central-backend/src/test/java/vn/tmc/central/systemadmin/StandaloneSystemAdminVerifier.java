package vn.tmc.central.systemadmin;

import vn.tmc.central.systemadmin.api.*;
import vn.tmc.central.systemadmin.internal.model.DeviceStatus;
import vn.tmc.central.systemadmin.internal.model.Gate;
import vn.tmc.central.systemadmin.internal.repository.*;
import vn.tmc.central.systemadmin.internal.service.AdministrationService;
import vn.tmc.central.systemadmin.internal.service.GateConfigurationService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * PHỤ TRÁCH: Task 5.
 * Trình kiểm tra xác thực độc lập (Standalone Verifier) cho toàn bộ chức năng của Task 5
 * Chạy trực tiếp bằng Java mà không cần phụ thuộc thư viện bên ngoài.
 */
public final class StandaloneSystemAdminVerifier {

    private static int testsPassed = 0;
    private static int testsTotal = 0;

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("   TMC TASK 5 - SYSTEM ADMINISTRATION VERIFICATION SUITE   ");
        System.out.println("=================================================================\n");

        verifyGateManagement();
        verifyAdministrationAndConfigs();
        verifyOvernightShiftsAndHolidays();
        verifySystemMonitoringAndHeartbeat();
        verifyRetentionAndDsr();

        System.out.println("\n-----------------------------------------------------------------");
        System.out.printf(" KẾT QUẢ KIỂM THỬ TASK 5: %d/%d TEST ĐẠT (100%% THÀNH CÔNG)\n", testsPassed, testsTotal);
        System.out.println("-----------------------------------------------------------------");
    }

    private static void verifyGateManagement() {
        System.out.println("[TEST GROUP 1] Kiểm tra GateManagement & FR-GS-01:");

        InMemoryGateRepository gateRepo = new InMemoryGateRepository();
        InMemoryFactoryRepository factoryRepo = new InMemoryFactoryRepository();
        InMemoryDeviceStatusRepository deviceRepo = new InMemoryDeviceStatusRepository();
        GateConfigurationService gateService = new GateConfigurationService(gateRepo, factoryRepo, deviceRepo);

        // 1.1 Tìm cổng đã seed
        GateView g1 = gateService.findGate("GATE-BD-01");
        assertTrue("Tìm thấy GATE-BD-01", g1 != null && "FACTORY-BD".equals(g1.factoryId()) && "IN".equals(g1.direction()));

        // 1.2 Lọc cổng theo nhà máy Bình Dương
        List<GateView> bdGates = gateService.findGatesByFactory("FACTORY-BD");
        assertTrue("Lọc cổng Bình Dương (2 cổng)", bdGates.size() == 2);

        // 1.3 Đăng ký cổng mới thành công
        GateView newGate = gateService.registerGate("GATE-BD-03", "FACTORY-BD", "EDGE-BD-01", "Cổng phụ", "IN", "TURNSTILE-BD-03-IN");
        assertTrue("Đăng ký cổng mới thành công", "GATE-BD-03".equals(newGate.id()) && "ACTIVE".equals(newGate.status()));

        // 1.4 Kiểm tra ràng buộc duy nhất trên turnstile_api_id (§5.1 UK)
        boolean duplicateCaught = false;
        try {
            gateService.registerGate("GATE-BD-99", "FACTORY-BD", "EDGE-BD-01", "Cổng lỗi", "IN", "TURNSTILE-BD-01-IN");
        } catch (IllegalStateException e) {
            duplicateCaught = true;
        }
        assertTrue("Từ chối trùng turnstile_api_id theo §5.1 UK", duplicateCaught);

        // 1.5 Cập nhật trạng thái cổng sang MAINTENANCE
        GateView updated = gateService.updateGateStatus("GATE-BD-01", "MAINTENANCE");
        assertTrue("Chuyển trạng thái cổng sang MAINTENANCE", "MAINTENANCE".equals(updated.status()));
    }

    private static void verifyAdministrationAndConfigs() {
        System.out.println("\n[TEST GROUP 2] Kiểm tra Cấu hình hệ thống & Tích hợp (FR-SA-02, FR-SA-03):");

        InMemorySystemConfigurationRepository configRepo = new InMemorySystemConfigurationRepository();
        InMemoryFactoryRepository factoryRepo = new InMemoryFactoryRepository();
        InMemoryShiftRepository shiftRepo = new InMemoryShiftRepository();
        InMemoryHolidayRepository holidayRepo = new InMemoryHolidayRepository();
        InMemoryDataSubjectRequestRepository dsrRepo = new InMemoryDataSubjectRequestRepository();
        InMemoryDeviceStatusRepository deviceRepo = new InMemoryDeviceStatusRepository();
        InMemoryGateRepository gateRepo = new InMemoryGateRepository();

        AdministrationService adminService = new AdministrationService(
            configRepo, factoryRepo, shiftRepo, holidayRepo, dsrRepo, deviceRepo, gateRepo
        );

        // 2.1 Múi giờ nhà máy cho Scheduler chốt kỳ công lúc 06:30 (DECISIONS.md §1)
        ZoneId bdZone = adminService.getFactoryZoneId("FACTORY-BD");
        assertTrue("Timezone nhà máy Bình Dương là Asia/Ho_Chi_Minh", ZoneId.of("Asia/Ho_Chi_Minh").equals(bdZone));

        // 2.2 Cửa sổ chống quét lặp 30 giây (BR-03)
        int window = adminService.getDuplicateScanWindowSeconds();
        assertTrue("Cửa sổ chống quét lặp nghiệp vụ là 30 giây (BR-03)", window == 30);

        // 2.3 Cập nhật cấu hình tích hợp
        SystemConfigurationView updatedConfig = adminService.updateConfiguration("hr.payroll.timeout-ms", "7500");
        assertTrue("Cập nhật timeout HR thành 7500ms", "7500".equals(updatedConfig.value()));
    }

    private static void verifyOvernightShiftsAndHolidays() {
        System.out.println("\n[TEST GROUP 3] Kiểm tra Ca làm việc qua ngày & Lịch nghỉ lễ (DECISIONS.md §1):");

        InMemorySystemConfigurationRepository configRepo = new InMemorySystemConfigurationRepository();
        InMemoryFactoryRepository factoryRepo = new InMemoryFactoryRepository();
        InMemoryShiftRepository shiftRepo = new InMemoryShiftRepository();
        InMemoryHolidayRepository holidayRepo = new InMemoryHolidayRepository();
        InMemoryDataSubjectRequestRepository dsrRepo = new InMemoryDataSubjectRequestRepository();
        InMemoryDeviceStatusRepository deviceRepo = new InMemoryDeviceStatusRepository();
        InMemoryGateRepository gateRepo = new InMemoryGateRepository();

        AdministrationService adminService = new AdministrationService(
            configRepo, factoryRepo, shiftRepo, holidayRepo, dsrRepo, deviceRepo, gateRepo
        );

        // 3.1 Ca đêm 22:00 -> 06:00 tự động nhận diện qua ngày (overnight)
        ShiftView overnight = adminService.registerShift(
            "SHIFT-BD-NIGHT",
            "FACTORY-BD",
            "Ca Đêm Sản Xuất",
            LocalTime.of(22, 0),
            LocalTime.of(6, 0),
            15,
            15
        );
        assertTrue("Tự động nhận diện ca đêm (overnight = true)", overnight.isOvernight());

        // 3.2 Lịch nghỉ lễ năm 2026
        List<HolidayView> holidays = adminService.findHolidays("FACTORY-BD", 2026);
        assertTrue("Danh sách ngày lễ năm 2026 có đầy đủ các dịp lễ lớn", holidays.size() >= 5);
    }

    private static void verifySystemMonitoringAndHeartbeat() {
        System.out.println("\n[TEST GROUP 4] Kiểm tra Giám sát sức khỏe hệ thống & Thiết bị (FR-SA-04, FR-GS-04):");

        InMemorySystemConfigurationRepository configRepo = new InMemorySystemConfigurationRepository();
        InMemoryFactoryRepository factoryRepo = new InMemoryFactoryRepository();
        InMemoryShiftRepository shiftRepo = new InMemoryShiftRepository();
        InMemoryHolidayRepository holidayRepo = new InMemoryHolidayRepository();
        InMemoryDataSubjectRequestRepository dsrRepo = new InMemoryDataSubjectRequestRepository();
        InMemoryDeviceStatusRepository deviceRepo = new InMemoryDeviceStatusRepository();
        InMemoryGateRepository gateRepo = new InMemoryGateRepository();

        AdministrationService adminService = new AdministrationService(
            configRepo, factoryRepo, shiftRepo, holidayRepo, dsrRepo, deviceRepo, gateRepo
        );

        SystemHealthView health = adminService.getSystemHealth();
        assertTrue("Trạng thái toàn hệ thống ban đầu HEALTHY", "HEALTHY".equals(health.status()));
        assertTrue("Edge nodes online: " + health.edgeNodesOnline() + "/" + health.edgeNodesTotal(), health.edgeNodesOnline() == health.edgeNodesTotal());
        assertTrue("Cổng hoạt động: " + health.gatesActive() + "/" + health.gatesTotal(), health.gatesActive() == health.gatesTotal());
    }

    private static void verifyRetentionAndDsr() {
        System.out.println("\n[TEST GROUP 5] Kiểm tra Chính sách lưu trữ & Quyền DSR (FR-SA-05, FR-SA-06, BR-04, BR-11):");

        InMemorySystemConfigurationRepository configRepo = new InMemorySystemConfigurationRepository();
        InMemoryFactoryRepository factoryRepo = new InMemoryFactoryRepository();
        InMemoryShiftRepository shiftRepo = new InMemoryShiftRepository();
        InMemoryHolidayRepository holidayRepo = new InMemoryHolidayRepository();
        InMemoryDataSubjectRequestRepository dsrRepo = new InMemoryDataSubjectRequestRepository();
        InMemoryDeviceStatusRepository deviceRepo = new InMemoryDeviceStatusRepository();
        InMemoryGateRepository gateRepo = new InMemoryGateRepository();

        AdministrationService adminService = new AdministrationService(
            configRepo, factoryRepo, shiftRepo, holidayRepo, dsrRepo, deviceRepo, gateRepo
        );

        // 5.1 Chính sách lưu trữ
        RetentionPolicyView policy = adminService.getRetentionPolicy();
        assertTrue("Lưu trữ online tối thiểu 2 năm (OQ-05)", policy.onlineRetentionYears() == 2);
        assertTrue("Lưu trữ kho lạnh 5 năm", policy.coldArchiveRetentionYears() == 5);
        assertTrue("Ẩn danh sau 36 tháng nghỉ việc (BR-11)", policy.anonymizeInactiveMonths() == 36);

        // 5.2 Kích hoạt lưu trữ
        ArchivalResultView result = adminService.triggerDataArchival();
        assertTrue("Tác vụ lưu trữ chạy thành công", "SUCCESS".equals(result.status()) && result.recordsArchived() > 0);

        // 5.3 Vòng đời yêu cầu DSR
        DataSubjectRequestView req = adminService.submitDataSubjectRequest("EMP-0300", "ERASURE", "Nhân viên nghỉ việc yêu cầu ẩn danh thông tin");
        assertTrue("Tiếp nhận DSR ở trạng thái PENDING", "PENDING".equals(req.status()) && "ERASURE".equals(req.requestType()));

        DataSubjectRequestView completed = adminService.processDataSubjectRequest(req.id(), "COMPLETED", "admin_hr", "Đã duyệt");
        assertTrue("Hoàn thành DSR", "COMPLETED".equals(completed.status()));
        assertTrue("Ghi chú tuân thủ BR-11 và BR-04", completed.notes().contains("BR-11") && completed.notes().contains("BR-04"));
    }

    private static void assertTrue(String testName, boolean condition) {
        testsTotal++;
        if (condition) {
            testsPassed++;
            System.out.println("  ✓ [PASS] " + testName);
        } else {
            System.err.println("  ✗ [FAIL] " + testName);
            throw new AssertionError("Kiểm thử thất bại: " + testName);
        }
    }
}
