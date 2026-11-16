import React from 'react';
import ModulePlaceholder from '../components/ModulePlaceholder.jsx';

/**
 * KHUNG TRIỂN KHAI — có component, chưa nối API nghiệp vụ.
 * PHỤ TRÁCH: Task 1.
 * VAI TRÒ: View.
 * NGUỒN: main.pdf, FR-UQ-01/06.
 * TÀI LIỆU PDF: docs/tasks/02-requirements-rbac.md; docs/REQUIREMENTS.md.
 * TODO[Task 1]: Hồ sơ nhân viên, quyền theo nhà máy/khu vực/ca; thao tác QR qua API.
 * PHỐI HỢP: Task 2, Task 5, Task 6.
 * TODO: bổ sung form, state, API, phân quyền và kiểm thử của trang.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Trang phục vụ: Hồ sơ nhân viên, tài khoản/vai trò và quyền khu vực.
 * - Cần làm: Hoàn thiện form, validation và quyền theo scope; gọi API user-mgmt, chỉ tham chiếu nhà máy/ca từ system-admin.
 * - Lưu ý bàn giao: Bổ sung loading/empty/error và kiểm tra quyền; UI hiện là placeholder, chưa có API nghiệp vụ hoạt động.
 */
export default function EmployeePage() {
  return <ModulePlaceholder title="Nhân sự" description="Hồ sơ nhân viên, tài khoản, vai trò và quyền khu vực." />;
}
