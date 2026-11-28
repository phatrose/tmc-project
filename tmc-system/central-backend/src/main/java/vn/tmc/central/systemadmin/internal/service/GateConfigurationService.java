package vn.tmc.central.systemadmin.internal.service;

import vn.tmc.central.systemadmin.api.GateManagement;
import vn.tmc.central.systemadmin.api.GateView;
import vn.tmc.central.systemadmin.internal.model.DeviceStatus;
import vn.tmc.central.systemadmin.internal.model.Gate;
import vn.tmc.central.systemadmin.internal.repository.DeviceStatusRepository;
import vn.tmc.central.systemadmin.internal.repository.FactoryRepository;
import vn.tmc.central.systemadmin.internal.repository.GateRepository;
import vn.tmc.central.systemadmin.internal.repository.InMemoryDeviceStatusRepository;
import vn.tmc.central.systemadmin.internal.repository.InMemoryFactoryRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Service quản lý cấu hình cổng, turnstile và trạm kiểm soát.
 * NGUỒN: main.pdf, FR-GS-01..06; §5.1; BR-10.
 * TÀI LIỆU PDF: docs/tasks/02-requirements-rbac.md; docs/REQUIREMENTS.md.
 */
public final class GateConfigurationService implements GateManagement {
    private final GateRepository repository;
    private final FactoryRepository factoryRepository;
    private final DeviceStatusRepository deviceStatusRepository;

    public GateConfigurationService(GateRepository repository) {
        this(repository, new InMemoryFactoryRepository(), new InMemoryDeviceStatusRepository());
    }

    public GateConfigurationService(
        GateRepository repository,
        FactoryRepository factoryRepository,
        DeviceStatusRepository deviceStatusRepository
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.factoryRepository = Objects.requireNonNull(factoryRepository, "factoryRepository must not be null");
        this.deviceStatusRepository = Objects.requireNonNull(deviceStatusRepository, "deviceStatusRepository must not be null");
    }

    @Override
    public GateView findGate(String gateId) {
        Objects.requireNonNull(gateId, "gateId must not be null");
        Gate gate = repository.findById(gateId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cổng với mã: " + gateId));
        return toView(gate);
    }

    @Override
    public List<GateView> findGatesByFactory(String factoryId) {
        Objects.requireNonNull(factoryId, "factoryId must not be null");
        return repository.findByFactoryId(factoryId).stream()
            .map(this::toView)
            .toList();
    }

    @Override
    public List<GateView> findAllGates() {
        return repository.findAll().stream()
            .map(this::toView)
            .toList();
    }

    @Override
    public GateView registerGate(String id, String factoryId, String edgeId, String name, String direction, String turnstileApiId) {
        Objects.requireNonNull(id, "gateId must not be null");
        Objects.requireNonNull(factoryId, "factoryId must not be null");
        Objects.requireNonNull(direction, "direction must not be null");

        if (id.isBlank()) {
            throw new IllegalArgumentException("Mã cổng không được để trống");
        }

        // Kiểm tra nhà máy tồn tại
        if (factoryRepository.findById(factoryId).isEmpty()) {
            throw new IllegalArgumentException("Nhà máy không tồn tại: " + factoryId);
        }

        // Kiểm tra ràng buộc duy nhất (Unique Key) trên turnstile_api_id theo §5.1 / FR-GS-01
        if (turnstileApiId != null && !turnstileApiId.isBlank()) {
            Optional<Gate> existingTurnstile = repository.findByTurnstileApiId(turnstileApiId);
            if (existingTurnstile.isPresent() && !existingTurnstile.get().id().equalsIgnoreCase(id)) {
                throw new IllegalStateException("turnstile_api_id '" + turnstileApiId + "' đã được gán cho cổng: " + existingTurnstile.get().id());
            }
        }

        Gate gate = new Gate(id, factoryId, edgeId, name, direction, turnstileApiId, "ACTIVE");
        repository.save(gate);

        // Khởi tạo trạng thái thiết bị trong hệ thống giám sát
        deviceStatusRepository.save(new DeviceStatus(
            id,
            "GATE",
            name != null && !name.isBlank() ? name : id,
            factoryId,
            "ONLINE",
            Instant.now(),
            "v2.0.4",
            "192.168.10.x",
            null
        ));

        return toView(gate);
    }

    @Override
    public GateView updateGateStatus(String gateId, String newStatus) {
        Objects.requireNonNull(gateId, "gateId must not be null");
        Objects.requireNonNull(newStatus, "newStatus must not be null");

        Gate existing = repository.findById(gateId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cổng: " + gateId));

        String normalizedStatus = newStatus.toUpperCase();
        if (!normalizedStatus.equals("ACTIVE") && !normalizedStatus.equals("INACTIVE") && !normalizedStatus.equals("MAINTENANCE")) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ: " + newStatus + ". Chấp nhận: ACTIVE, INACTIVE, MAINTENANCE");
        }

        Gate updated = new Gate(
            existing.id(),
            existing.factoryId(),
            existing.edgeId(),
            existing.name(),
            existing.direction(),
            existing.turnstileApiId(),
            normalizedStatus
        );
        repository.save(updated);

        // Cập nhật trạng thái giám sát
        deviceStatusRepository.findById(gateId).ifPresent(dev -> {
            String devStatus = normalizedStatus.equals("ACTIVE") ? "ONLINE" : (normalizedStatus.equals("MAINTENANCE") ? "WARNING" : "OFFLINE");
            deviceStatusRepository.save(new DeviceStatus(
                dev.deviceId(),
                dev.deviceType(),
                dev.name(),
                dev.factoryId(),
                devStatus,
                Instant.now(),
                dev.firmwareVersion(),
                dev.ipAddress(),
                normalizedStatus.equals("MAINTENANCE") ? "Thiết bị đang bảo trì" : null
            ));
        });

        return toView(updated);
    }

    @Override
    public void recordGateHeartbeat(String gateId, String ipAddress) {
        Objects.requireNonNull(gateId, "gateId must not be null");
        Gate gate = repository.findById(gateId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cổng: " + gateId));

        deviceStatusRepository.save(new DeviceStatus(
            gate.id(),
            "GATE",
            gate.name(),
            gate.factoryId(),
            "ONLINE",
            Instant.now(),
            "v2.0.4",
            ipAddress != null ? ipAddress : "192.168.10.x",
            null
        ));
    }

    private GateView toView(Gate g) {
        return new GateView(
            g.id(),
            g.factoryId(),
            g.edgeId(),
            g.name(),
            g.direction(),
            g.turnstileApiId(),
            g.status()
        );
    }
}
