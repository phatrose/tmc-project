import React from 'react';
import ModulePlaceholder from '../components/ModulePlaceholder.jsx';

/**
 * KHUNG TRIỂN KHAI — có component, chưa nối API nghiệp vụ.
 * PHỤ TRÁCH: Task 2.
 * VAI TRÒ: View.
 * NGUỒN: main.pdf, FR-GS-02/03; UC-02/03.
 * TÀI LIỆU PDF: docs/tasks/02-requirements-rbac.md; docs/REQUIREMENTS.md.
 * TODO[Task 2]: Nhận scan, hiển thị allow/deny/lý do, trạng thái offline; gọi Edge LAN.
 * PHỐI HỢP: Task 1, Task 3, Task 6, Task 7.
 * TODO: bổ sung form, state, API, phân quyền và kiểm thử của trang.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Trang phục vụ: Màn hình scanner tại cổng: nhận QR, trạng thái chờ và kết quả cho phép/từ chối.
 * - Cần làm: Gọi Edge LAN; phân biệt xác minh hợp lệ, lưu event và kết quả thiết bị. Không báo mở thành công chỉ vì HTTP 200.
 * - Lưu ý bàn giao: Bổ sung loading/empty/error và kiểm tra quyền; UI hiện là placeholder, chưa có API nghiệp vụ hoạt động.
 */
export default function KioskPage() {
  return <ModulePlaceholder title="Quét tại cổng" description="Quét QR qua Edge, hiển thị kết quả và trạng thái kết nối." />;
}
