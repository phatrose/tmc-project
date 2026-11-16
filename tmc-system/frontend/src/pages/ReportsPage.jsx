import React from 'react';
import ModulePlaceholder from '../components/ModulePlaceholder.jsx';

/**
 * KHUNG TRIỂN KHAI — có component, chưa nối API nghiệp vụ.
 * PHỤ TRÁCH: Task 4.
 * VAI TRÒ: View.
 * NGUỒN: main.pdf, FR-RP-01..06; UC-05.
 * TÀI LIỆU PDF: docs/tasks/02-requirements-rbac.md; docs/REQUIREMENTS.md.
 * TODO[Task 4]: Báo cáo/dashboard, chốt kỳ, xuất HR/đối soát, tải CSV/Excel/PDF và tra cứu audit.
 * PHỐI HỢP: Task 3, Task 5, Task 8.
 * TODO: bổ sung form, state, API, phân quyền và kiểm thử của trang.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Trang phục vụ: Báo cáo công, chốt kỳ, export Payroll và tra cứu audit.
 * - Cần làm: Hiển thị kỳ/nhà máy/trạng thái batch; phân biệt xuất xong với đã đối soát. Export audit cần quyền riêng.
 * - Lưu ý bàn giao: Bổ sung loading/empty/error và kiểm tra quyền; UI hiện là placeholder, chưa có API nghiệp vụ hoạt động.
 */
export default function ReportsPage() {
  return <ModulePlaceholder title="Báo cáo và kiểm toán" description="Báo cáo theo kỳ, xuất HR, đối soát và tra cứu audit." />;
}
