import React from 'react';
import ModulePlaceholder from '../components/ModulePlaceholder.jsx';

/**
 * KHUNG TRIỂN KHAI — có component, chưa nối API nghiệp vụ.
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: View.
 * NGUỒN: main.pdf, FR-AT-03..07.
 * TÀI LIỆU PDF: docs/tasks/02-requirements-rbac.md; docs/REQUIREMENTS.md.
 * TODO[Task 3]: Lịch sử/công cá nhân, bất thường, yêu cầu/phê duyệt điều chỉnh; giữ scope theo role.
 * PHỐI HỢP: Task 2, Task 4, Task 7.
 * TODO: bổ sung form, state, API, phân quyền và kiểm thử của trang.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Trang phục vụ: Lịch sử công, bất thường và yêu cầu/phê duyệt điều chỉnh theo scope.
 * - Cần làm: Tách người tạo/người duyệt, nhập lý do và xem lịch sử; không cho sửa event gốc hoặc để Auditor duyệt.
 * - Lưu ý bàn giao: Bổ sung loading/empty/error và kiểm tra quyền; UI hiện là placeholder, chưa có API nghiệp vụ hoạt động.
 */
export default function AttendancePage() {
  return <ModulePlaceholder title="Chấm công" description="Lịch sử vào/ra, tính công, bất thường và duyệt điều chỉnh." />;
}
