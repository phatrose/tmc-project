package vn.tmc.central.systemadmin.api;

/** PHỤ TRÁCH: Task 5. TODO: Hoàn thiện contract và nghiệp vụ theo PDF.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Contract công khai cho caller của module; input/output đã có kiểu để nhóm thống nhất trước tích hợp.
 * - Cần làm: Bổ sung quy ước null/not-found, lỗi quyền và lỗi dependency; caller phải dùng interface thay vì import internal implementation.
 * - Lưu ý bàn giao: Không trả JPA entity hoặc lộ repository qua API; thay đổi chữ ký cần thống nhất với task sử dụng.
 */
public interface Administration {
    SystemConfigurationView findConfiguration(String key);
}
