package vn.tmc.central.systemadmin.internal.repository;

import vn.tmc.central.systemadmin.internal.model.DataSubjectRequest;
import java.util.List;
import java.util.Optional;

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: Repository Port cho DataSubjectRequest.
 * NGUỒN: main.pdf, FR-SA-06.
 */
public interface DataSubjectRequestRepository {
    Optional<DataSubjectRequest> findById(String id);
    List<DataSubjectRequest> findByEmployeeId(String employeeId);
    List<DataSubjectRequest> findAll();
    void save(DataSubjectRequest request);
    void delete(String id);
}
