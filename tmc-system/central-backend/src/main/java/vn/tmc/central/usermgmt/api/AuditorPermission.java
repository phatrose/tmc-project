package vn.tmc.central.usermgmt.api;



/**
 * PHỤ TRÁCH: Task 1.
 * Khung triển khai theo docs/DECISIONS.md; chưa có nghiệp vụ hoạt động.
 * TODO: xuất audit cần quyền cấp riêng và data scope.
 * Cấm sửa event, duyệt công, mở cổng, cấp/thu hồi QR, đổi tài khoản/quyền/cấu hình, xóa audit.
 * Enum chỉ là contract; chưa phải cơ chế kiểm soát truy cập.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Quyền đọc audit/manual override/lịch sử điều chỉnh/lịch sử kỳ; export cần cấp riêng.
 * - Cần làm: Áp dụng kiểm tra quyền và data scope ở service/controller trước khi đọc hoặc xuất.
 * - Lưu ý bàn giao: Enum chỉ mô tả contract; không tự cấp quyền cho mọi Auditor và không thay cơ chế authorization.
 */
public enum AuditorPermission {
    READ_AUDIT_LOG, SEARCH_AUDIT_LOG, READ_MANUAL_OVERRIDE,
    READ_ADJUSTMENT_HISTORY, READ_PERIOD_HISTORY, EXPORT_AUDIT_REPORT
}
