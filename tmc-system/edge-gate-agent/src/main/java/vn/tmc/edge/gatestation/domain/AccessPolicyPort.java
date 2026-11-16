package vn.tmc.edge.gatestation.domain;

/** PHỤ TRÁCH: Task 2. TODO: Hoàn thiện contract và nghiệp vụ theo PDF.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận employeeId và gateId; kết quả boolean thể hiện quyền qua cổng theo policy cục bộ.
 * - Cần làm: Bổ sung clock/ca/hướng/context qua contract khi hiện thực; không gọi Central trong luồng scan.
 * - Lưu ý bàn giao: Thiếu hoặc không xác thực được policy phải có kết quả từ chối rõ; kiểm thử khác nhà máy/khu vực/ca.
 */
public interface AccessPolicyPort {
    boolean isAllowed(String employeeId, String gateId);
}
