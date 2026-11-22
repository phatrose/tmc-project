package vn.tmc.central.systemadmin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.tmc.central.systemadmin.api.GateView;
import vn.tmc.central.systemadmin.internal.model.DeviceStatus;
import vn.tmc.central.systemadmin.internal.model.Factory;
import vn.tmc.central.systemadmin.internal.model.Gate;
import vn.tmc.central.systemadmin.internal.repository.InMemoryDeviceStatusRepository;
import vn.tmc.central.systemadmin.internal.repository.InMemoryFactoryRepository;
import vn.tmc.central.systemadmin.internal.repository.InMemoryGateRepository;
import vn.tmc.central.systemadmin.internal.service.GateConfigurationService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHỤ TRÁCH: Task 5.
 * Kiểm thử đơn vị cho GateConfigurationService (FR-GS-01..06, §5.1, BR-10).
 */
class GateConfigurationServiceTest {
    private InMemoryGateRepository gateRepo;
    private InMemoryFactoryRepository factoryRepo;
    private InMemoryDeviceStatusRepository deviceRepo;
    private GateConfigurationService gateService;

    @BeforeEach
    void setUp() {
        gateRepo = new InMemoryGateRepository();
        factoryRepo = new InMemoryFactoryRepository();
        deviceRepo = new InMemoryDeviceStatusRepository();
        gateService = new GateConfigurationService(gateRepo, factoryRepo, deviceRepo);
    }

    @Test
    @DisplayName("findGate: tìm thấy cổng đã seed trong hệ thống")
    void testFindGate_Success() {
        GateView gate = gateService.findGate("GATE-BD-01");
        assertNotNull(gate);
        assertEquals("GATE-BD-01", gate.id());
        assertEquals("FACTORY-BD", gate.factoryId());
        assertEquals("IN", gate.direction());
        assertEquals("ACTIVE", gate.status());
    }

    @Test
    @DisplayName("findGate: ném ngoại lệ khi cổng không tồn tại")
    void testFindGate_NotFound() {
        assertThrows(IllegalArgumentException.class, () -> gateService.findGate("GATE-NON-EXISTENT"));
    }

    @Test
    @DisplayName("findGatesByFactory: lọc chính xác danh sách cổng theo nhà máy")
    void testFindGatesByFactory() {
        List<GateView> bdGates = gateService.findGatesByFactory("FACTORY-BD");
        assertFalse(bdGates.isEmpty());
        assertTrue(bdGates.stream().allMatch(g -> "FACTORY-BD".equalsIgnoreCase(g.factoryId())));

        List<GateView> laGates = gateService.findGatesByFactory("FACTORY-LA");
        assertFalse(laGates.isEmpty());
        assertTrue(laGates.stream().allMatch(g -> "FACTORY-LA".equalsIgnoreCase(g.factoryId())));
    }

    @Test
    @DisplayName("registerGate: đăng ký cổng mới thành công khi dữ liệu hợp lệ")
    void testRegisterGate_Success() {
        GateView newGate = gateService.registerGate(
            "GATE-BD-03",
            "FACTORY-BD",
            "EDGE-BD-01",
            "Cổng phụ kho thành phẩm",
            "IN",
            "TURNSTILE-BD-03-IN"
        );

        assertNotNull(newGate);
        assertEquals("GATE-BD-03", newGate.id());
        assertEquals("FACTORY-BD", newGate.factoryId());
        assertEquals("IN", newGate.direction());
        assertEquals("TURNSTILE-BD-03-IN", newGate.turnstileApiId());
        assertEquals("ACTIVE", newGate.status());

        // Kiểm tra lưu vào repository
        assertTrue(gateRepo.findById("GATE-BD-03").isPresent());
    }

    @Test
    @DisplayName("registerGate: từ chối nếu nhà máy không tồn tại")
    void testRegisterGate_UnknownFactory() {
        assertThrows(IllegalArgumentException.class, () -> gateService.registerGate(
            "GATE-TEST-01",
            "FACTORY-UNKNOWN",
            "EDGE-01",
            "Cổng thử nghiệm",
            "IN",
            "TURNSTILE-TEST-01"
        ));
    }

    @Test
    @DisplayName("registerGate: bắt buộc kiểm tra ràng buộc duy nhất trên turnstile_api_id (§5.1 UK)")
    void testRegisterGate_DuplicateTurnstileApiId_ThrowsException() {
        // TURNSTILE-BD-01-IN đã được seed cho GATE-BD-01
        assertThrows(IllegalStateException.class, () -> gateService.registerGate(
            "GATE-BD-99",
            "FACTORY-BD",
            "EDGE-BD-01",
            "Cổng trùng thiết bị",
            "IN",
            "TURNSTILE-BD-01-IN"
        ));
    }

    @Test
    @DisplayName("updateGateStatus: cập nhật trạng thái cổng hợp lệ (ACTIVE, MAINTENANCE, INACTIVE)")
    void testUpdateGateStatus_Success() {
        GateView updated = gateService.updateGateStatus("GATE-BD-01", "MAINTENANCE");
        assertEquals("MAINTENANCE", updated.status());

        GateView inRepo = gateService.findGate("GATE-BD-01");
        assertEquals("MAINTENANCE", inRepo.status());
    }

    @Test
    @DisplayName("updateGateStatus: từ chối trạng thái không hợp lệ")
    void testUpdateGateStatus_InvalidStatus() {
        assertThrows(IllegalArgumentException.class, () ->
            gateService.updateGateStatus("GATE-BD-01", "UNKNOWN_STATUS")
        );
    }

    @Test
    @DisplayName("recordGateHeartbeat: cập nhật nhịp tim thiết bị thành công")
    void testRecordGateHeartbeat() {
        assertDoesNotThrow(() -> gateService.recordGateHeartbeat("GATE-BD-01", "192.168.10.88"));
    }
}
