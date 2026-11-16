import React from 'react';
import ModulePlaceholder from '../components/ModulePlaceholder.jsx';

/**
 * KHUNG TRIỂN KHAI — có component, chưa nối API nghiệp vụ.
 * PHỤ TRÁCH: Task 1.
 * VAI TRÒ: View.
 * NGUỒN: main.pdf, §2.3, §4.2.1.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 * TODO[Task 1]: Chốt luồng bearer access token/session với backend trước khi nối form; không account Basic demo.
 * PHỐI HỢP: Task 1, Task 4, Task 8.
 * TODO: bổ sung form, state, API, phân quyền và kiểm thử của trang.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Trang phục vụ: Form đăng nhập và trạng thái phiên của người dùng.
 * - Cần làm: Chốt token/session với Authentication và HTTP client; xử lý sai credential/khóa tài khoản/phiên hết hạn, không hardcode tài khoản.
 * - Lưu ý bàn giao: Bổ sung loading/empty/error và kiểm tra quyền; UI hiện là placeholder, chưa có API nghiệp vụ hoạt động.
 */
export default function LoginPage() {
  return <ModulePlaceholder title="Đăng nhập" description="Đăng nhập và kiểm tra quyền truy cập theo vai trò." />;
}
