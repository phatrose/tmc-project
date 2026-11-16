import React, { useState } from 'react';
import EmployeePage from './pages/EmployeePage.jsx';
import QrPage from './pages/QrPage.jsx';
import KioskPage from './pages/KioskPage.jsx';
import GuardConsolePage from './pages/GuardConsolePage.jsx';
import AttendancePage from './pages/AttendancePage.jsx';
import ReportsPage from './pages/ReportsPage.jsx';
import AdministrationPage from './pages/AdministrationPage.jsx';
import LoginPage from './pages/LoginPage.jsx';


/**
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Khung điều hướng giữa các trang React; activeId hiện chỉ điều khiển màn hình đang chọn.
 * - Cần làm: Bổ sung router/session và route guard phối hợp Task 1; giữ từng trang nghiệp vụ trong file riêng.
 * - Lưu ý bàn giao: Ẩn menu không thay kiểm tra quyền backend; chưa lưu token hoặc cấp quyền từ trạng thái menu.
 */

// PHỤ TRÁCH: Task 8. TODO: router và route guard sau khi chốt auth.
const pages = [
  { id: 'EmployeePage', title: 'Nhân sự', component: EmployeePage },
  { id: 'QrPage', title: 'QR cá nhân', component: QrPage },
  { id: 'KioskPage', title: 'Quét tại cổng', component: KioskPage },
  { id: 'GuardConsolePage', title: 'Bảng điều khiển bảo vệ', component: GuardConsolePage },
  { id: 'AttendancePage', title: 'Chấm công', component: AttendancePage },
  { id: 'ReportsPage', title: 'Báo cáo và kiểm toán', component: ReportsPage },
  { id: 'AdministrationPage', title: 'Quản trị', component: AdministrationPage },
  { id: 'LoginPage', title: 'Đăng nhập', component: LoginPage }
];
export default function App() {
  const [activeId, setActiveId] = useState(pages[0].id);
  const ActivePage = pages.find(page => page.id === activeId).component;
  return <div className="layout">
    <aside><h2>TMC</h2><p>Khung ứng dụng</p>
      <nav aria-label="Các chức năng">{pages.map(page =>
        <button key={page.id} aria-current={page.id === activeId ? 'page' : undefined}
          onClick={() => setActiveId(page.id)}>{page.title}</button>
      )}</nav>
    </aside>
    <main><ActivePage /></main>
  </div>;
}
