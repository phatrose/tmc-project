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
  kiosk: { kind: 'edge', badge: 'Edge LAN', desc: 'Màn hình quét QR tại cổng, hoạt động độc lập cả khi mất WAN.' },
  guard: { kind: 'edge', badge: 'Edge LAN', desc: 'Mở cổng thủ công / khẩn cấp có re-auth và theo dõi hiện diện.' },
  attendance: { kind: 'live', badge: 'Đã kết nối', desc: 'Bảng công ngày, sự kiện quẹt thẻ, điều chỉnh công và giả lập sync.' },
  administration: { kind: 'live', badge: 'Đã kết nối', desc: 'Nhà máy, cổng turnstile, ca làm việc, cấu hình và DSR.' },
};

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
            <span>Kiểm soát cổng & Chấm công</span>
          </span>
        </a>

        <p className="nav-caption">Không gian làm việc</p>
        <nav aria-label="Các chức năng">
          {routes
            .filter((item) => item.id !== 'login' || !session)
            .map((item) => (
              <a
                key={item.id}
                href={`#/${item.id}`}
                aria-current={route?.id === item.id ? 'page' : undefined}
              >
                <span>{item.title}</span>
                {!canAccess(item, session) && (
                  <span className="locked" aria-label="Cần quyền truy cập">Khóa</span>
                )}
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
          <div>
            <span className="breadcrumb">TMC / Không gian làm việc</span>
            <h1>{route?.title || 'Không tìm thấy trang'}</h1>
          </div>
          <div className="account-badge">
            <span className="avatar">{session ? 'TV' : 'KH'}</span>
            <div>
              <strong>{session ? `Phạm vi: ${session.actor.scope}` : 'Khách'}</strong>
              <small>{session ? session.actor.roles.join(' · ') : 'Chưa đăng nhập'}</small>
            </div>
          </div>
        </header>

        {logoutError && <RequestState error={logoutError} />}

        {!route ? (
          <section className="state-card">
            <h2>Không tìm thấy trang</h2>
            <p>Đường dẫn bạn truy cập không tồn tại trong hệ thống.</p>
            <a className="primary-link" href="#/home">Về tổng quan</a>
          </section>
        ) : route.id === 'home' ? (
          <section>
            <div className="welcome-panel">
              <p className="eyebrow">HỆ THỐNG CHẤM CÔNG & KIỂM SOÁT RA VÀO</p>
              <h2>Mọi nghiệp vụ, một không gian thống nhất.</h2>
              <p>
                Quản lý nhân sự, cấp phát mã QR động ES256, giám sát trạm cổng Edge và tổng hợp báo cáo — kiểm toán minh bạch.
              </p>
              <div className="welcome-actions">
                <a className="primary-link" href={session ? '#/qr' : '#/login'}>
                  {session ? 'Mở QR cá nhân →' : 'Đăng nhập để bắt đầu →'}
                </a>
                {session && (
                  <a className="secondary-link" href="#/employees">
                    Danh sách nhân sự
                  </a>
                )}
              </div>
            </div>

            <div className="notice">
              <strong>Trạng thái tích hợp hiện tại</strong>
              <p>
                Đăng nhập, nhân sự, cấp QR và báo cáo/kiểm toán đã nối backend Central. Trang chấm công và quản trị hiện chạy dữ liệu mô phỏng trên trình duyệt; luồng thiết bị Edge LAN cần chứng chỉ mTLS riêng.
              </p>
            </div>

            <div className="module-grid">
              {routes
                .filter((r) => r.task && r.id !== 'login')
                .map((item) => {
                  const meta = MODULE_META[item.id] || { kind: 'live', badge: 'Chức năng', desc: '' };
                  return (
                    <a className="module-card" key={item.id} href={`#/${item.id}`}>
                      <div className="module-card-top">
                        <small>Task {item.task}</small>
                        <span className={`module-pill module-pill--${meta.kind}`}>{meta.badge}</span>
                      </div>
                      <h2>{item.title}</h2>
                      <p>{meta.desc}</p>
                      <span className="module-cta">
                        {meta.kind === 'sim' ? 'Xem bản mô phỏng →' : 'Mở chức năng →'}
                      </span>
                    </a>
                  );
                })}
            </div>
          </section>
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
          <>
            {route.device && (
              <p className="notice">
                Kiosk cần chứng chỉ thiết bị mTLS được cấp riêng. Phiên đăng nhập người dùng không thay thế danh tính kiosk.
              </p>
            )}
            <AppErrorBoundary key={`${route.id}:${session?.actor.userId || 'guest'}`}>
              <div className={`workspace-page page-${route.id}`}>
                <ActivePage session={session} actor={session?.actor} api={reportsApi} />
              </div>
            </AppErrorBoundary>
          </>
        )}
      </main>
    </div>
  );
}
