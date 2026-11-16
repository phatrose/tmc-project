package vn.tmc.central.systemadmin.api;

/** PHỤ TRÁCH: Task 5. TODO: Hoàn thiện contract và nghiệp vụ theo PDF.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Model/DTO mô tả dữ liệu với các trường tối thiểu hiện có, không phải schema đầy đủ.
 * - Cần làm: Chốt ID, nullability, trường bắt buộc và quan hệ trước khi thêm validation/migration; tham chiếu checklist dữ liệu trong docs.
 * - Lưu ý bàn giao: Không thêm phụ thuộc UI/HTTP vào model; record không tự thực thi nghiệp vụ hoặc kiểm tra quyền truy cập.
 */
public record SystemConfigurationView(String key, String value) {}
