import React from 'react';
import ModulePlaceholder from '../components/ModulePlaceholder.jsx';

/**
 * KHUNG TRIỂN KHAI — có component, chưa nối API nghiệp vụ.
 * PHỤ TRÁCH: Task 2.
 * VAI TRÒ: View.
 * NGUỒN: main.pdf, FR-GS-04..06; UC-04.
 * TÀI LIỆU PDF: docs/tasks/02-requirements-rbac.md; docs/REQUIREMENTS.md.
 * TODO[Task 2]: Heartbeat, manual override có lý do/re-auth, emergency và hiện diện sơ tán.
 * PHỐI HỢP: Task 1, Task 3, Task 6, Task 7.
 * TODO: bổ sung form, state, API, phân quyền và kiểm thử của trang.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Trang phục vụ: Theo dõi cổng, manual override và emergency theo quyền bảo vệ.
 * - Cần làm: Manual cần nhân viên/lý do/re-auth; emergency không tạo công giả. Hiển thị timeout/thiết bị lỗi và audit.
 * - Lưu ý bàn giao: Bổ sung loading/empty/error và kiểm tra quyền; UI hiện là placeholder, chưa có API nghiệp vụ hoạt động.
 */
export default function GuardConsolePage() {
  return <ModulePlaceholder title="Bảng điều khiển bảo vệ" description="Theo dõi cổng, mở thủ công có lý do và xử lý khẩn cấp." />;
}
