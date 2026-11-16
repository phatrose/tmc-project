package vn.tmc.central.usermgmt.api;



/**
 * PHỤ TRÁCH: Task 1.
 * Khung triển khai theo docs/DECISIONS.md; chưa có nghiệp vụ hoạt động.
 * Auditor là actor chính thức; quyền cụ thể xem AuditorPermission và DECISIONS.md.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Danh sách vai trò chính thức, trong đó AUDITOR là vai trò độc lập.
 * - Cần làm: Task 1 cần ánh xạ role → permission và data scope ở backend; không dựa riêng vào tên role trên UI.
 * - Lưu ý bàn giao: Auditor chỉ tra cứu/xuất khi được cấp quyền; không có quyền sửa/duyệt công, mở cổng hay cấp QR.
 */
public enum Role { EMPLOYEE, SUPERVISOR, HR, GUARD, ADMIN, EXECUTIVE, AUDITOR }
