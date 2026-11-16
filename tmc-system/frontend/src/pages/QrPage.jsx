import React from 'react';
import ModulePlaceholder from '../components/ModulePlaceholder.jsx';

/**
 * KHUNG TRIỂN KHAI — có component, chưa nối API nghiệp vụ.
 * PHỤ TRÁCH: Task 6.
 * VAI TRÒ: View.
 * NGUỒN: main.pdf, FR-UQ-02..05; FR-QR-01/02.
 * TÀI LIỆU PDF: docs/tasks/07-qr-security.md; docs/REQUIREMENTS.md.
 * TODO[Task 6]: Hiển thị JWS dưới dạng QR, đếm hạn/làm mới; không giữ private key.
 * TODO[Task 6]: Chỉ hiển thị QR trên app/web sau đăng nhập; email/SMS không chứa QR hoặc JWS.
 * PHỐI HỢP: Task 1, Task 2, Task 7.
 * TODO: bổ sung form, state, API, phân quyền và kiểm thử của trang.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Trang phục vụ: QR động của chính người dùng đã đăng nhập.
 * - Cần làm: Lấy token qua /me/qr-token, vẽ QR và đếm expiresAt; hủy timer/request khi rời trang, không giữ QR đã hết hạn.
 * - Lưu ý bàn giao: Bổ sung loading/empty/error và kiểm tra quyền; UI hiện là placeholder, chưa có API nghiệp vụ hoạt động.
 */
export default function QrPage() {
  return <ModulePlaceholder title="QR cá nhân" description="Cấp lại, thu hồi, hiển thị QR động và thời hạn sử dụng." />;
}
