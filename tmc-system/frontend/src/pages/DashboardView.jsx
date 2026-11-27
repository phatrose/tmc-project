import React, { useEffect, useState } from 'react';
import { request } from '../api.js';
import { routes, canAccess } from '../navigation.js';

function primaryRole(session) {
  const roles = session?.actor?.roles || [];
  if (roles.includes('ADMIN')) return 'ADMIN';
  if (roles.includes('AUDITOR')) return 'AUDITOR';
  if (roles.includes('HR')) return 'HR';
  if (roles.includes('GUARD')) return 'GUARD';
  if (roles.includes('EMPLOYEE')) return 'EMPLOYEE';
  return 'GUEST';
}

const ROLE_COPY = {
  ADMIN: {
    title: 'Trung tâm điều hành hệ thống',
    subtitle: 'Giám sát 2 nhà máy Bình Dương & Long An, trạm cổng Edge và phân quyền toàn hệ thống.',
    primary: { label: 'Quản trị cổng & cấu hình', href: '#/administration' },
    secondary: { label: 'Mở cổng quét QR', href: '#/kiosk' },
  },
  HR: {
    title: 'Điều hành nhân sự & kỳ công',
    subtitle: 'Chốt kỳ công chính thức lúc 06:30 ngày 01 và hoàn tất đối soát HR/Payroll trước 12:00.',
    primary: { label: 'Mở bảng chấm công', href: '#/attendance' },
    secondary: { label: 'Báo cáo & Payroll', href: '#/reports' },
  },
  GUARD: {
    title: 'Trạm kiểm soát ra vào cổng',
    subtitle: 'Quét QR tự động qua camera và xử lý mở cổng thủ công / khẩn cấp có re-auth 60 giây.',
    primary: { label: 'Mở camera quét cổng', href: '#/kiosk' },
    secondary: { label: 'Vận hành cổng (Override)', href: '#/guard' },
  },
  AUDITOR: {
    title: 'Không gian kiểm toán độc lập',
    subtitle: 'Chế độ chỉ đọc — tra cứu Audit Log, lịch sử điều chỉnh công và chốt kỳ. Mọi thao tác sửa đều bị khóa.',
    primary: { label: 'Tra cứu báo cáo & Audit', href: '#/reports' },
    secondary: { label: 'Xem lịch sử chấm công', href: '#/attendance' },
  },
  EMPLOYEE: {
    title: 'Không gian chấm công cá nhân',
    subtitle: 'Hiển thị mã QR động ES256 (hiệu lực 60 giây) để qua cổng và theo dõi công cá nhân.',
    primary: { label: 'Mở mã QR cá nhân', href: '#/qr' },
    secondary: { label: 'Xem bảng công của tôi', href: '#/attendance' },
  },
  GUEST: {
    title: 'Hệ thống kiểm soát cổng & chấm công TMC',
    subtitle: 'Đăng nhập bằng tài khoản được cấp để sử dụng mã QR động, chấm công và bảng điều hành theo vai trò.',
    primary: { label: 'Đăng nhập ngay', href: '#/login' },
    secondary: null,
  },
};

export default function DashboardView({ session, moduleMeta, renderIcon }) {
  const role = primaryRole(session);
  const copy = ROLE_COPY[role] || ROLE_COPY.GUEST;
  const [status, setStatus] = useState(session ? 'loading' : 'idle');
  const [error, setError] = useState('');
  const [live, setLive] = useState({
    employeeCount: null,
    gateCount: null,
    activeGateCount: null,
    eventCount: null,
    pendingAdjustments: null,
    recentEvents: [],
  });

  async function loadLive(signal) {
    if (!session) {
      setStatus('idle');
      return;
    }
    setStatus('loading');
    setError('');
    try {
      const [evRes, adjRes, gateRes, empRes] = await Promise.allSettled([
        request('/attendance/events?page=0&size=8', { signal }),
        request('/attendance/adjustments?page=0&size=20', { signal }),
        request('/admin/gates', { signal }),
        request('/employees?page=0&size=1', { signal }),
      ]);
      const events = evRes.status === 'fulfilled' && Array.isArray(evRes.value?.items) ? evRes.value.items : [];
      const eventTotal = evRes.status === 'fulfilled' ? (evRes.value?.total ?? events.length) : 0;
      const adjustments = adjRes.status === 'fulfilled' && Array.isArray(adjRes.value?.items) ? adjRes.value.items : [];
      const pendingAdj = adjustments.filter((a) => (a.state || a.status) === 'PENDING').length;
      const gates = gateRes.status === 'fulfilled' && Array.isArray(gateRes.value) ? gateRes.value : [];
      const activeGates = gates.filter((g) => g.status === 'ACTIVE').length;
      const empTotal = empRes.status === 'fulfilled' ? (empRes.value?.totalElements ?? empRes.value?.total ?? empRes.value?.items?.length ?? null) : null;

      setLive({
        employeeCount: empTotal,
        gateCount: gates.length || null,
        activeGateCount: gates.length ? activeGates : null,
        eventCount: eventTotal,
        pendingAdjustments: pendingAdj,
        recentEvents: events,
      });
      setStatus('ready');
    } catch (err) {
      if (err?.name === 'AbortError') return;
      setError(err?.message || 'Không tải được dữ liệu tổng quan từ Central API.');
      setStatus('error');
    }
  }

  useEffect(() => {
    const controller = new AbortController();
    loadLive(controller.signal);
    return () => controller.abort();
  }, [session?.actor?.userId]);

  const metrics = (() => {
    if (role === 'ADMIN') {
      return [
        { label: 'Cổng hoạt động', value: live.gateCount != null ? `${live.activeGateCount}/${live.gateCount}` : '4/4', tone: 'success' },
        { label: 'Nhân sự trong phạm vi', value: live.employeeCount != null ? String(live.employeeCount) : '—', tone: 'default' },
        { label: 'Lượt quét ghi nhận', value: live.eventCount != null ? String(live.eventCount) : '0', tone: 'accent' },
        { label: 'Điều chỉnh chờ duyệt', value: live.pendingAdjustments != null ? String(live.pendingAdjustments) : '0', tone: live.pendingAdjustments > 0 ? 'warning' : 'default' },
      ];
    }
    if (role === 'HR') {
      return [
        { label: 'Chốt kỳ công chính thức', value: '06:30 · 01', tone: 'accent' },
        { label: 'Hạn xuất Payroll HR', value: '< 12:00 · 01', tone: 'default' },
        { label: 'Nhân sự quản lý', value: live.employeeCount != null ? String(live.employeeCount) : '—', tone: 'default' },
        { label: 'Điều chỉnh chờ duyệt', value: live.pendingAdjustments != null ? String(live.pendingAdjustments) : '0', tone: live.pendingAdjustments > 0 ? 'warning' : 'success' },
      ];
    }
    if (role === 'GUARD') {
      return [
        { label: 'Trạm cổng trực', value: live.gateCount != null ? `${live.activeGateCount}/${live.gateCount}` : '4/4', tone: 'success' },
        { label: 'Lượt quét ghi nhận', value: live.eventCount != null ? String(live.eventCount) : '0', tone: 'accent' },
        { label: 'Chế độ Kiosk', value: 'Camera tự động', tone: 'success' },
        { label: 'Action Grant Override', value: 'ES256 · 60s', tone: 'default' },
      ];
    }
    if (role === 'AUDITOR') {
      return [
        { label: 'Chế độ kiểm toán', value: 'Chỉ đọc', tone: 'warning' },
        { label: 'Sự kiện quẹt thẻ', value: live.eventCount != null ? String(live.eventCount) : '0', tone: 'accent' },
        { label: 'Điều chỉnh chờ duyệt', value: live.pendingAdjustments != null ? String(live.pendingAdjustments) : '0', tone: 'default' },
        { label: 'Mốc chốt kỳ công', value: '06:30 · 01', tone: 'default' },
      ];
    }
    return [
      { label: 'Mã QR động', value: 'ES256 · 60s', tone: 'accent' },
      { label: 'Lượt quét đã ghi nhận', value: live.eventCount != null ? String(live.eventCount) : '0', tone: 'success' },
      { label: 'Điều chỉnh chờ duyệt', value: live.pendingAdjustments != null ? String(live.pendingAdjustments) : '0', tone: 'default' },
      { label: 'Chốt kỳ tháng', value: '06:30 · 01', tone: 'default' },
    ];
  })();

  const allowedModules = routes.filter((r) => r.task && r.id !== 'login' && canAccess(r, session));

  return (
    <section className="ops-dashboard">
      <div className="ops-hero">
        <div className="ops-hero-copy">
          <h2>{copy.title}</h2>
          <p>{copy.subtitle}</p>
        </div>
        <div className="welcome-actions">
          <a className="primary-link" href={copy.primary.href}>{copy.primary.label} →</a>
          {copy.secondary && (
            <a className="secondary-link" href={copy.secondary.href}>{copy.secondary.label}</a>
          )}
        </div>
      </div>

      {session && (
        <div className="ops-metrics-bar" aria-label="Chỉ số vận hành">
          {metrics.map((m) => (
            <div className="ops-metric" key={m.label}>
              <span className="ops-metric-label">{m.label}</span>
              {status === 'loading' ? (
                <span className="ops-skeleton" aria-hidden="true" />
              ) : (
                <strong className={`ops-metric-value ops-metric-value--${m.tone}`}>{m.value}</strong>
              )}
            </div>
          ))}
        </div>
      )}

      {status === 'error' && (
        <div className="notice error" role="alert">
          <strong>Không đồng bộ được số liệu trực tiếp</strong>
          <p>{error}</p>
          <button type="button" onClick={() => loadLive()} style={{ marginTop: 8 }}>Thử tải lại</button>
        </div>
      )}

      <div className="ops-split">
        <div className="ops-panel">
          <div className="ops-panel-head">
            <h3>Nhật ký quét cổng gần đây</h3>
            {session && (
              <a href="#/attendance" className="ops-panel-link">Xem tất cả →</a>
            )}
          </div>

          {!session ? (
            <div className="ops-empty">
              <p>Đăng nhập để xem nhật ký sự kiện quẹt thẻ và trạng thái vận hành theo vai trò của bạn.</p>
              <a className="primary-link" href="#/login">Đăng nhập ngay →</a>
            </div>
          ) : status === 'loading' ? (
            <div className="ops-skeleton-rows" aria-busy="true">
              <span className="ops-skeleton ops-skeleton--row" />
              <span className="ops-skeleton ops-skeleton--row" />
              <span className="ops-skeleton ops-skeleton--row" />
            </div>
          ) : live.recentEvents.length === 0 ? (
            <div className="ops-empty">
              <p>Chưa có lượt quẹt thẻ nào được ghi nhận trong phiên này.</p>
              {canAccess(routes.find((r) => r.id === 'kiosk'), session) && (
                <a className="ops-inline-btn" href="#/kiosk">Mở cổng quét QR ngay →</a>
              )}
            </div>
          ) : (
            <div className="ops-timeline">
              {live.recentEvents.map((ev) => (
                <div className="ops-timeline-row" key={ev.eventId || ev.id}>
                  <span className={`ops-dir ops-dir--${(ev.direction || 'IN').toLowerCase()}`}>
                    {ev.direction || 'IN'}
                  </span>
                  <div className="ops-timeline-main">
                    <strong>{ev.employeeId || 'Nhân viên'}</strong>
                    <span>Cổng {ev.gateId || '—'} · Nguồn {ev.source || 'ONLINE'}</span>
                  </div>
                  <time className="ops-timeline-time">
                    {ev.occurredAt ? new Date(ev.occurredAt).toLocaleTimeString('vi-VN') : '—'}
                  </time>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="ops-panel">
          <div className="ops-panel-head">
            <h3>Chức năng được cấp quyền ({allowedModules.length})</h3>
          </div>
          <div className="ops-directory">
            {allowedModules.map((item) => {
              const meta = moduleMeta[item.id] || { kind: 'live', badge: 'Sẵn sàng', desc: '' };
              return (
                <a className="ops-dir-item" key={item.id} href={`#/${item.id}`}>
                  <span className="module-card-icon">{renderIcon(item.id)}</span>
                  <div className="ops-dir-copy">
                    <div className="ops-dir-top">
                      <strong>{item.title}</strong>
                      <span className={`module-pill module-pill--${meta.kind}`}>{meta.badge}</span>
                    </div>
                    <span>{meta.desc}</span>
                  </div>
                </a>
              );
            })}
          </div>
        </div>
      </div>
    </section>
  );
}
