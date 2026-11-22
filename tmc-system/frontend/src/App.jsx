import React, { useEffect, useState, useSyncExternalStore } from 'react';
import EmployeePage from './pages/EmployeePage.jsx';
import QrPage from './pages/QrPage.jsx';
import KioskPage from './pages/KioskPage.jsx';
import GuardConsolePage from './pages/GuardConsolePage.jsx';
import AttendancePage from './pages/AttendancePage.jsx';
import ReportsPage from './pages/ReportsPage.jsx';
import AdministrationPage from './pages/AdministrationPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import { routes, canAccess, routeFromHash } from './navigation.js';
import { getSession, subscribeSession } from './session.js';
import { logout } from './api.js';
import RequestState from './components/RequestState.jsx';

// PHỤ TRÁCH: Task 8. Hash routing hỗ trợ back/forward; các trang do chủ module triển khai.
const components = { employees: EmployeePage, qr: QrPage, kiosk: KioskPage,
  guard: GuardConsolePage, attendance: AttendancePage, reports: ReportsPage,
  administration: AdministrationPage, login: LoginPage };
export default function App() {
  const [hash, setHash] = useState(window.location.hash);
  const [logoutError, setLogoutError] = useState(null);
  const [loggingOut, setLoggingOut] = useState(false);
  const session = useSyncExternalStore(subscribeSession, getSession, () => null);
  const route = routeFromHash(hash);
  useEffect(() => {
    const changed = () => { setHash(window.location.hash); setLogoutError(null); };
    window.addEventListener('hashchange', changed);
    return () => window.removeEventListener('hashchange', changed);
  }, []);
  useEffect(() => { document.title = `${route?.title || 'Không tìm thấy'} · TMC`; }, [route]);
  const ActivePage = route && components[route.id];
  async function signOut() {
    setLoggingOut(true); setLogoutError(null);
    try { await logout(); window.location.hash = '/login'; }
    catch (error) { setLogoutError(error); }
    finally { setLoggingOut(false); }
  }
  return <div className="layout">
    <a className="skip-link" href="#content" onClick={event => {
      event.preventDefault(); document.getElementById('content')?.focus();
    }}>Đến nội dung chính</a>
    <aside>
      <a className="brand" href="#/home">TMC<span>Quản lý chấm công</span></a>
      <nav aria-label="Các chức năng">{routes.map(item =>
        <a key={item.id} href={`#/${item.id}`} aria-current={route?.id === item.id ? 'page' : undefined}>
          {item.title}{!canAccess(item, session) && <span className="locked" aria-label="Cần quyền truy cập"> · Khóa</span>}
        </a>)}</nav>
      <div className="session"><p>{session ? 'Đã đăng nhập' : 'Chưa đăng nhập'}</p>
        {session && <button onClick={signOut} disabled={loggingOut}>{loggingOut ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>}
      </div>
    </aside>
    <main id="content" tabIndex={-1}>
      {logoutError && <RequestState error={logoutError} />}
      {!route ? <section><h1>Không tìm thấy trang</h1><a href="#/home">Về tổng quan</a></section>
      : route.id === 'home' ? <section>
        <p className="eyebrow">TMC · Nền tảng nhóm</p><h1>Không gian làm việc</h1>
        <p>Chọn chức năng từ menu để bắt đầu.</p>
        <div className="notice"><strong>Ứng dụng đang được triển khai.</strong>
          <p>Các trang nghiệp vụ hiện là khung chờ từng module hoàn thiện. Chưa có dữ liệu mẫu hoặc phiên đăng nhập mô phỏng.</p></div>
        <div className="module-grid">{routes.filter(r => r.task && r.id !== 'login').map(item =>
          <a className="module-card" key={item.id} href={`#/${item.id}`}><small>Task {item.task}</small><h2>{item.title}</h2><span>Xem chức năng →</span></a>)}</div>
      </section>
      : !canAccess(route, session) ? <section><h1>{route.title}</h1><p role="status">
        {session ? 'Phiên hiện tại chưa có quyền truy cập chức năng này.' : 'Bạn cần đăng nhập để truy cập chức năng này.'}</p>
        {!session && <a href="#/login">Đến trang đăng nhập</a>}</section>
      : <><p className="eyebrow">Task {route.task} · {route.title}</p>
        {route.device && <p className="notice">Kiosk cần chứng chỉ thiết bị được cấp riêng. Phiên đăng nhập người dùng không thay thế danh tính kiosk.</p>}
        <ActivePage /></>}
    </main>
  </div>;
}
