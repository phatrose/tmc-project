import React from 'react';
import ModulePlaceholder from '../components/ModulePlaceholder.jsx';

/**
 * KHUNG TRIỂN KHAI — có component, chưa nối API nghiệp vụ.
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: View.
 * NGUỒN: main.pdf, FR-SA-01..06.
 * TÀI LIỆU PDF: docs/tasks/02-requirements-rbac.md; docs/REQUIREMENTS.md.
 * TODO[Task 5]: Nhà máy/cổng/ca, cấu hình, giám sát, backup/restore/archive; gọi quyền user-mgmt, không sở hữu tài khoản.
 * PHỐI HỢP: Task 1, Task 4, Task 8.
 * TODO: bổ sung form, state, API, phân quyền và kiểm thử của trang.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Trang phục vụ: Nhà máy, cổng/thiết bị, ca làm việc và cấu hình vận hành.
 * - Cần làm: Hoàn thiện form cấu hình và hành động nhạy cảm có re-auth/audit; dùng quyền từ user-mgmt.
 * - Lưu ý bàn giao: Bổ sung loading/empty/error và kiểm tra quyền; UI hiện là placeholder, chưa có API nghiệp vụ hoạt động.
 */
export default function AdministrationPage() {
  return <ModulePlaceholder title="Quản trị" description="Nhà máy, cổng, ca làm việc, cấu hình và vận hành hệ thống." />;
}
