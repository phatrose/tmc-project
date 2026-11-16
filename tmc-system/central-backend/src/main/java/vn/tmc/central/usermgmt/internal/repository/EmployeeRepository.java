package vn.tmc.central.usermgmt.internal.repository;

import vn.tmc.central.usermgmt.internal.model.Employee;
import java.util.Optional;

/** PHỤ TRÁCH: Task 1. TODO: Hoàn thiện contract và nghiệp vụ theo PDF.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Port truy cập dữ liệu cho model tương ứng; implementation persistence sẽ được bổ sung sau.
 * - Cần làm: Chốt cách xử lý ID không tồn tại, transaction và truy vấn có scope; findById trả Optional, save không được âm thầm bỏ lỗi.
 * - Lưu ý bàn giao: Đặt JPA mapping/SQL trong adapter, không để service module khác gọi repository trực tiếp; viết test DB thật.
 */
public interface EmployeeRepository {
    Optional<Employee> findById(String id);
    void save(Employee value);
}
