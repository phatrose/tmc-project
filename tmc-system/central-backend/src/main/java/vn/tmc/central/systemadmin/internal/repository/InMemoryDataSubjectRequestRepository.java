package vn.tmc.central.systemadmin.internal.repository;

import vn.tmc.central.systemadmin.internal.model.DataSubjectRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: In-memory Repository Implementation cho DataSubjectRequest.
 * NGUỒN: main.pdf, FR-SA-06; BR-11.
 */
public final class InMemoryDataSubjectRequestRepository implements DataSubjectRequestRepository {
    private final Map<String, DataSubjectRequest> store = new ConcurrentHashMap<>();

    public InMemoryDataSubjectRequestRepository() {
        // Seed ví dụ 1 yêu cầu mẫu
        save(new DataSubjectRequest(
            "DSR-2026-001",
            "EMP-0099",
            "ACCESS",
            "COMPLETED",
            "Yêu cầu trích xuất bản ghi chấm công cá nhân năm 2026",
            Instant.now().minusSeconds(86400),
            "admin_system",
            Instant.now().minusSeconds(3600),
            "Đã xuất file dữ liệu theo đúng phạm vi cho nhân viên."
        ));
    }

    @Override
    public Optional<DataSubjectRequest> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<DataSubjectRequest> findByEmployeeId(String employeeId) {
        List<DataSubjectRequest> result = new ArrayList<>();
        for (DataSubjectRequest r : store.values()) {
            if (r.employeeId().equalsIgnoreCase(employeeId)) {
                result.add(r);
            }
        }
        return result;
    }

    @Override
    public List<DataSubjectRequest> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void save(DataSubjectRequest request) {
        store.put(request.id(), request);
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }
}
