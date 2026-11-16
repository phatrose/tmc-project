package vn.tmc.central.usermgmt.internal.service;

import vn.tmc.central.usermgmt.api.Authentication;

/**
 * PHỤ TRÁCH: Task 1.
 * Khung triển khai theo docs/DECISIONS.md; chưa có nghiệp vụ hoạt động.
 *
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận username/password từ luồng đăng nhập; kết quả token/session còn phải chốt kiểu với frontend.
 * - Cần làm: Bổ sung credential store, kiểm tra khóa tài khoản, password hash, phiên và role/scope qua user-mgmt.
 * - Lưu ý bàn giao: Không log credential hoặc hardcode tài khoản demo; không coi chuỗi trả về là phiên hợp lệ khi chưa hiện thực.
 */
public final class AuthenticationService implements Authentication {
    @Override
    public String authenticate(String username, char[] password) {
        throw new UnsupportedOperationException("TODO: authenticate and issue session/token");
    }
}
