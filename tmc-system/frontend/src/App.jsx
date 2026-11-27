import React, { useEffect, useState, useSyncExternalStore } from 'react';
import EmployeePage from './pages/EmployeePage.jsx';
import QrPage from './pages/QrPage.jsx';
import KioskPage from './pages/KioskPage.jsx';
import GuardConsolePage from './pages/GuardConsolePage.jsx';
import AttendancePage from './pages/AttendancePage.jsx';
import ReportsPage from './pages/ReportsPage.jsx';
import AdministrationPage from './pages/AdministrationPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import DashboardView from './pages/DashboardView.jsx';
import { routes, canAccess, routeFromHash } from './navigation.js';
import { getSession, subscribeSession, getAccessToken } from './session.js';
import { logout, request } from './api.js';
import { createReportsApi } from './pages/reportsApi.js';
import RequestState from './components/RequestState.jsx';
import AppErrorBoundary from './components/AppErrorBoundary.jsx';

const reportsApi = createReportsApi(request, async (path, { signal } = {}) => {
  if (!/^\/(reports|audit)\/exports\/[0-9a-f-]+\/file$/.test(path)) throw new Error('Invalid export path');
  const response = await fetch('/api/v1' + path, {
    signal,
    credentials: 'omit',
    redirect: 'error',
    cache: 'no-store',
    headers: { Authorization: `Bearer ${getAccessToken()}`, 'X-Request-Id': crypto.randomUUID() },
  });
  if (!response.ok) throw new Error('Không tải được báo cáo: ' + response.status);
  return response.blob();
});

const components = {
  employees: EmployeePage,
  qr: QrPage,
  kiosk: KioskPage,
  guard: GuardConsolePage,
  attendance: AttendancePage,
  reports: ReportsPage,
  administration: AdministrationPage,
  login: LoginPage,
};

const MODULE_META = {
  employees: { kind: 'live', badge: 'Đã kết nối', desc: 'Quản lý hồ sơ nhân sự, tìm kiếm theo nhà máy và phân quyền.' },
  qr: { kind: 'live', badge: 'Đã kết nối', desc: 'Mã QR động ES256 hạn 60 giây để quét ra vào cổng.' },
  reports: { kind: 'live', badge: 'Đã kết nối', desc: 'Báo cáo chấm công, xuất CSV/XLSX/PDF, kỳ Payroll và kiểm toán.' },
  kiosk: { kind: 'live', badge: 'Camera tự động', desc: 'Màn hình quét QR tự động tại cổng, ghi nhận phiên IN/OUT tức thời.' },
  guard: { kind: 'edge', badge: 'Edge LAN', desc: 'Mở cổng thủ công / khẩn cấp có re-auth và theo dõi hiện diện.' },
  attendance: { kind: 'live', badge: 'Đã kết nối', desc: 'Bảng công ngày, sự kiện quẹt thẻ bất biến và điều chỉnh công.' },
  administration: { kind: 'live', badge: 'Đã kết nối', desc: 'Nhà máy, cổng turnstile, ca làm việc, cấu hình và DSR.' },
};

function NavIcon({ id }) {
  const props = { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.75, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': 'true' };
  switch (id) {
    case 'home':
      return <svg {...props}><rect x="3" y="3" width="7" height="7" rx="1.5" /><rect x="14" y="3" width="7" height="7" rx="1.5" /><rect x="3" y="14" width="7" height="7" rx="1.5" /><rect x="14" y="14" width="7" height="7" rx="1.5" /></svg>;
    case 'employees':
      return <svg {...props}><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M22 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" /></svg>;
    case 'qr':
      return <svg {...props}><rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" /><rect x="3" y="14" width="7" height="7" rx="1" /><path d="M14 14h3v3h-3zM20 14v3h-1M14 20h7v-1" /></svg>;
    case 'kiosk':
      return <svg {...props}><path d="M3 7V5a2 2 0 0 1 2-2h2" /><path d="M17 3h2a2 2 0 0 1 2 2v2" /><path d="M21 17v2a2 2 0 0 1-2 2h-2" /><path d="M7 21H5a2 2 0 0 1-2-2v-2" /><line x1="7" y1="12" x2="17" y2="12" /></svg>;
    case 'guard':
      return <svg {...props}><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" /></svg>;
    case 'attendance':
      return <svg {...props}><circle cx="12" cy="12" r="9" /><polyline points="12 7 12 12 15 14" /></svg>;
    case 'reports':
      return <svg {...props}><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><polyline points="14 2 14 8 20 8" /><line x1="8" y1="13" x2="16" y2="13" /><line x1="8" y1="17" x2="13" y2="17" /></svg>;
    case 'administration':
      return <svg {...props}><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9c.26.6.85 1 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" /></svg>;
    default:
      return <svg {...props}><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" /><polyline points="10 17 15 12 10 7" /><line x1="15" y1="12" x2="3" y2="12" /></svg>;
  }
}

function formatRemaining(expiresAt, now) {
  if (!expiresAt) return '';
  const sec = Math.max(0, Math.floor((Date.parse(expiresAt) - now) / 1000));
  const m = Math.floor(sec / 60);
  const s = String(sec % 60).padStart(2, '0');
  return `${m}:${s}`;
}

// PHỤ TRÁCH: Task 8. Hash routing hỗ trợ back/forward; các trang do chủ module triển khai.
export default function App() {
  const [hash, setHash] = useState(window.location.hash);
  const [logoutError, setLogoutError] = useState(null);
  const [loggingOut, setLoggingOut] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  const session = useSyncExternalStore(subscribeSession, getSession, () => null);
  const route = routeFromHash(hash);

  useEffect(() => {
    const changed = () => { setHash(window.location.hash); setLogoutError(null); };
    window.addEventListener('hashchange', changed);
    return () => window.removeEventListener('hashchange', changed);
  }, []);

  useEffect(() => {
    if (!session) return undefined;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [session]);

  useEffect(() => {
    document.title = `${route?.title || 'Không tìm thấy'} · TMC`;
  }, [route]);

  const ActivePage = route && components[route.id];
  const showStandaloneLogin = !session && route?.id !== 'kiosk';

  async function signOut() {
    setLoggingOut(true);
    setLogoutError(null);
    try {
      await logout();
      window.location.hash = '/login';
    } catch (error) {
      setLogoutError(error);
    } finally {
      setLoggingOut(false);
    }
  }

  if (showStandaloneLogin) {
    return (
      <main id="content" className="login-standalone" tabIndex={-1}>
        <AppErrorBoundary key="login:guest">
          <LoginPage
            onLoginSuccess={() => {
              window.location.hash = '/home';
            }}
          />
        </AppErrorBoundary>
      </main>
    );
  }

  return (
    <div className="layout">
      <a
        className="skip-link"
        href="#content"
        onClick={(event) => {
          event.preventDefault();
          document.getElementById('content')?.focus();
        }}
      >
        Đến nội dung chính
      </a>

      <aside className="sidebar">
        <a className="brand" href="#/home">
          <span className="brand-mark">TMC</span>
          <span className="brand-text">
            <strong>TMC System</strong>
            <span>Kiểm soát cổng &amp; Chấm công</span>
          </span>
        </a>

        <nav aria-label="Các chức năng">
          {routes
            .filter((item) => (item.id !== 'login' || !session) && canAccess(item, session))
            .map((item) => (
              <a
                key={item.id}
                href={`#/${item.id}`}
                aria-current={route?.id === item.id ? 'page' : undefined}
              >
                <NavIcon id={item.id} />
                <span>{item.title}</span>
              </a>
            ))}
        </nav>

        <div className="session">
          <div className="session-meta">
            <span className={`session-dot${session ? ' session-dot--online' : ''}`}>
              {session ? 'Đang hoạt động' : 'Chưa đăng nhập'}
            </span>
            {session && (
              <span className="session-timer" title="Thời gian còn lại của phiên">
                {formatRemaining(session.expiresAt, now)}
              </span>
            )}
          </div>
          {session ? (
            <button onClick={signOut} disabled={loggingOut}>
              {loggingOut ? 'Đang đăng xuất…' : 'Đăng xuất'}
            </button>
          ) : (
            <button onClick={() => { window.location.hash = '/login'; }}>
              Đăng nhập
            </button>
          )}
        </div>
      </aside>

      <main id="content" tabIndex={-1}>
        <header className="topbar">
          <h1>{route?.title || 'Không tìm thấy trang'}</h1>
          <div className="account-badge">
            <span className="avatar">{session ? (session.actor.roles?.[0]?.slice(0, 2) || 'TV') : 'KH'}</span>
            <div>
              <strong>{session ? `Phạm vi: ${session.actor.scope}` : 'Khách'}</strong>
              <small>{session ? session.actor.roles.join(' · ') : 'Chưa đăng nhập'}</small>
            </div>
          </div>
        </header>

        <div className="main-body">
          {logoutError && <RequestState error={logoutError} />}

          {!route ? (
            <section className="state-card">
              <h2>Không tìm thấy trang</h2>
              <p>Đường dẫn bạn truy cập không tồn tại trong hệ thống.</p>
              <a className="primary-link" href="#/home">Về tổng quan</a>
            </section>
          ) : route.id === 'home' ? (
            <DashboardView
              session={session}
              moduleMeta={MODULE_META}
              renderIcon={(id) => <NavIcon id={id} />}
            />
          ) : !canAccess(route, session) ? (
            <section className="state-card">
              <h2>{route.title}</h2>
              <p role="status">
                {session
                  ? 'Phiên hiện tại chưa có quyền truy cập chức năng này.'
                  : 'Bạn cần đăng nhập để truy cập chức năng này.'}
              </p>
              {!session && (
                <a className="primary-link" href="#/login">Đến trang đăng nhập →</a>
              )}
            </section>
          ) : (
            <AppErrorBoundary key={`${route.id}:${session?.actor.userId || 'guest'}`}>
              <div className={`workspace-page page-${route.id}`}>
                <ActivePage session={session} actor={session?.actor} api={reportsApi} />
              </div>
            </AppErrorBoundary>
          )}
        </div>
      </main>
    </div>
  );
}
