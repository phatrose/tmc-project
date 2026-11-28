package vn.tmc.central.systemadmin.internal.repository;

import vn.tmc.central.systemadmin.internal.model.SystemConfiguration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: In-memory Repository Implementation cho SystemConfiguration với seed data.
 * NGUỒN: main.pdf, FR-SA-02, FR-SA-03; DECISIONS.md §1, §6, §7.
 */
public final class InMemorySystemConfigurationRepository implements SystemConfigurationRepository {
    private final Map<String, SystemConfiguration> store = new ConcurrentHashMap<>();

    public InMemorySystemConfigurationRepository() {
        // Cấu hình nghiệp vụ cốt lõi theo DECISIONS.md và main.pdf
        seed("system.timezone.default", "Asia/Ho_Chi_Minh", "SYSTEM", "Múi giờ mặc định toàn hệ thống");
        seed("attendance.duplicate-scan-window-seconds", "30", "ATTENDANCE", "Cửa sổ chống quét lặp nghiệp vụ 30 giây (BR-03)");
        seed("attendance.period-closing-time", "06:30", "ATTENDANCE", "Giờ chốt kỳ công chính thức ngày 01 hàng tháng (DECISIONS.md §1)");
        seed("attendance.hr-export-deadline", "12:00", "ATTENDANCE", "Hạn chót hoàn tất xuất HR ngày 01 (DECISIONS.md §1)");
        seed("hr.payroll.endpoint", "https://hr-mock.tmc.internal/api/v1/payroll", "INTEGRATION", "Endpoint hệ thống HR/Payroll ngoài");
        seed("hr.payroll.timeout-ms", "5000", "INTEGRATION", "Timeout gọi HR/Payroll");
        seed("hr.payroll.retry-max-attempts", "3", "INTEGRATION", "Số lần thử lại tối đa gọi HR/Payroll");
        seed("turnstile.default-timeout-ms", "2000", "INTEGRATION", "Timeout tối đa gửi lệnh mở cổng xoay");
        seed("edge.sync.batch-size-max", "50", "INTEGRATION", "Số lượng sự kiện tối đa trong một lô đồng bộ (FR-OS-04)");
        seed("edge.sync.backoff-initial-sec", "5", "INTEGRATION", "Thời gian chờ thử lại ban đầu");
        seed("edge.sync.backoff-max-sec", "300", "INTEGRATION", "Thời gian chờ thử lại tối đa");
        seed("retention.online-years", "2", "RETENTION", "Thời gian lưu trữ dữ liệu trực tuyến tối thiểu (OQ-05)");
        seed("retention.archive-years", "5", "RETENTION", "Thời gian lưu trữ lạnh (Cold Archive)");
        seed("retention.anonymize-inactive-months", "36", "RETENTION", "Thời hạn ẩn danh hồ sơ sau khi nghỉ việc (BR-11)");
    }

    private void seed(String key, String value, String category, String description) {
        store.put(key, new SystemConfiguration(key, value, category, description, Instant.now()));
    }

    @Override
    public Optional<SystemConfiguration> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<SystemConfiguration> findByCategory(String category) {
        List<SystemConfiguration> result = new ArrayList<>();
        for (SystemConfiguration c : store.values()) {
            if (c.category().equalsIgnoreCase(category)) {
                result.add(c);
            }
        }
        return result;
    }

    @Override
    public List<SystemConfiguration> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void save(SystemConfiguration value) {
        store.put(value.key(), value);
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }
}
