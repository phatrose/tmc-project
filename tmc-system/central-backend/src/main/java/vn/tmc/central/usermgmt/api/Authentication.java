package vn.tmc.central.usermgmt.api;



/**
 * PHỤ TRÁCH: Task 1.
 * Khung triển khai theo docs/DECISIONS.md; chưa có nghiệp vụ hoạt động.
 *
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Contract đăng nhập giữa controller và service thuộc user-mgmt.
 * - Cần làm: Thống nhất token/session DTO, expiry và lỗi xác thực với Task 8 trước khi nối LoginPage.
 * - Lưu ý bàn giao: char[] password là dữ liệu nhạy cảm; quy định vòng đời/xóa bộ nhớ và không đưa vào log hoặc audit.
 */
public interface Authentication {
    // TODO: chốt credential/session DTO và cơ chế bearer với Task 8; không log secret.
    String authenticate(String username, char[] password);
}
