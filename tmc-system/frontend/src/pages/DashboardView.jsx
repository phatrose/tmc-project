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

const ROLE_META = {
  ADMIN: {
    greeting: 'Quản trị viên',
    subtitle: 'Điều hành kiểm soát cổng, nhân sự và cấu hình toàn hệ thống TMC.',
    primary: { label: 'Quản trị hệ thống', href: '#/administration' },
    priorityIds: ['administration', 'employees', 'reports', 'kiosk'],
  },
  HR: {
    greeting: 'Nhân sự',
    subtitle: 'Theo dõi chấm công, xử lý điều chỉnh và chuẩn bị chốt kỳ công (06:30 ngày 01).',
    primary: { label: 'Mở bảng chấm công', href: '#/attendance' },
    priorityIds: ['attendance', 'reports', 'employees', 'qr'],
  },
  GUARD: {
    greeting: 'Bảo vệ cổng',
    subtitle: 'Giám sát trạm cổng, quét QR tự động và xử lý mở cổng có xác thực lại.',
    primary: { label: 'Mở cổng quét QR', href: '#/kiosk' },
    priorityIds: ['kiosk', 'guard', 'qr', 'attendance'],
  },
  AUDITOR: {
    greeting: 'Kiểm toán viên',
    subtitle: 'Chế độ chỉ đọc — tra cứu Audit Log, lịch sử điều chỉnh và chốt kỳ công.',
    primary: { label: 'Mở báo cáo & Audit', href: '#/reports' },
    priorityIds: ['reports', 'attendance', 'kiosk'],
  },
  EMPLOYEE: {
    greeting: 'Nhân viên',
    subtitle: 'Sử dụng mã QR động ES256 (60 giây) để qua cổng và theo dõi công cá nhân.',
    primary: { label: 'Mở QR cá nhân', href: '#/qr' },
    priorityIds: ['qr', 'attendance', 'kiosk'],
  },
  GUEST: {
    greeting: 'Khách',
    subtitle: 'Đăng nhập để truy cập bảng điều hành chấm công và kiểm soát ra vào TMC.',
    primary: { label: 'Đăng nhập', href: '#/login' },
    priorityIds: [],
  },
};

function shortId(value) {
  if (!value) return '—';
  const str = String(value);
  return str.length > 14 ? `${str.slice(0, 8)}…` : str;
}

function RingGauge({ value, total, color = 'var(--accent)' }) {
  const r = 18;
  const c = 2 * Math.PI * r;
  const ratio = total > 0 ? Math.min(1, Math.max(0, value / total)) : 0;
  const dash = ratio * c;
  return (
    <svg className="hc-ring" viewBox="0 0 44 44" aria-hidden="true">
      <circle cx="22" cy="22" r={r} fill="none" stroke="var(--border)" strokeWidth="4" />
      <circle
        cx="22"
        cy="22"
        r={r}
        fill="none"
        stroke={color}
        strokeWidth="4"
        strokeDasharray={`${dash} ${c}`}
        strokeLinecap="round"
        transform="rotate(-90 22 22)"
      />
      <text x="22" y="25.5" textAnchor="middle" className="hc-ring-text">
        {value}
      </text>
    </svg>
  );
}

function DonutChart({ segments, totalLabel }) {
  const r = 36;
  const c = 2 * Math.PI * r;
  const total = segments.reduce((sum, s) => sum + s.value, 0);
  let offset = 0;
  return (
    <svg className="hc-donut" viewBox="0 0 96 96" aria-hidden="true">
      <circle cx="48" cy="48" r={r} fill="none" stroke="var(--surface-soft)" strokeWidth="11" />
      {total > 0 && segments.map((seg) => {
        const len = (seg.value / total) * c;
        const el = (
          <circle
            key={seg.label}
            cx="48"
            cy="48"
            r={r}
            fill="none"
            stroke={seg.color}
            strokeWidth="11"
            strokeDasharray={`${len} ${c}`}
            strokeDashoffset={-offset}
            transform="rotate(-90 48 48)"
          />
        );
        offset += len;
        return el;
      })}
      <text x="48" y="47" textAnchor="middle" className="hc-donut-num">{total}</text>
      <text x="48" y="60" textAnchor="middle" className="hc-donut-sub">{totalLabel}</text>
    </svg>
  );
}

export default function DashboardView({ session, moduleMeta, renderIcon }) {
  const role = primaryRole(session);
  const copy = ROLE_META[role] || ROLE_META.GUEST;
  const showOrgGateMetrics = role === 'ADMIN' || role === 'HR' || role === 'GUARD' || role === 'AUDITOR';
  const [status, setStatus] = useState(session ? 'loading' : 'idle');
  const [error, setError] = useState('');
  const [live, setLive] = useState({
    employeeCount: 0,
    gates: [],
    eventTotal: 0,
    recentEvents: [],
    pendingAdjustments: [],
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
        showOrgGateMetrics ? request('/admin/gates', { signal }) : Promise.resolve([]),
        showOrgGateMetrics ? request('/employees?page=0&size=1', { signal }) : Promise.resolve(null),
      ]);
      const events = evRes.status === 'fulfilled' && Array.isArray(evRes.value?.items) ? evRes.value.items : [];
      const eventTotal = evRes.status === 'fulfilled' ? (evRes.value?.total ?? events.length) : 0;
      const adjustments = adjRes.status === 'fulfilled' && Array.isArray(adjRes.value?.items) ? adjRes.value.items : [];
      const pendingAdj = adjustments.filter((a) => (a.state || a.status) === 'PENDING');
      const gates = gateRes.status === 'fulfilled' && Array.isArray(gateRes.value) ? gateRes.value : [];
      const empTotal = empRes.status === 'fulfilled' && empRes.value
        ? (empRes.value?.totalElements ?? empRes.value?.total ?? empRes.value?.items?.length ?? 0)
        : 0;

      setLive({
        employeeCount: empTotal,
        gates,
        eventTotal,
        recentEvents: events,
        pendingAdjustments: pendingAdj,
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
  }, [session?.actor?.userId, showOrgGateMetrics]);

  // Guest view: gọn gàng, không hiển thị 6 khối rỗng toàn số 0
  if (!session) {
    return (
      <section className="hc-dashboard">
        <div className="hc-greeting">
          <div>
            <h2>Chào mừng đến với hệ thống kiểm soát cổng &amp; chấm công TMC</h2>
            <p>{copy.subtitle}</p>
          </div>
          <div className="hc-greeting-actions">
            <a className="primary-link" href="#/login">Đăng nhập</a>
          </div>
        </div>
        <div className="hc-card">
          <div className="hc-card-head">
            <h3>Mốc vận hành kỳ công TMC</h3>
          </div>
          <ul className="hc-schedule-list">
            <li>
              <span className="hc-dot" style={{ background: 'var(--accent)' }} />
              <div>
                <strong>Chốt kỳ công chính thức</strong>
                <span>06:30 ngày 01 hàng tháng</span>
              </div>
            </li>
            <li>
              <span className="hc-dot" style={{ background: 'var(--success-ink)' }} />
              <div>
                <strong>Hạn đối soát &amp; xuất HR/Payroll</strong>
                <span>Trước 12:00 ngày 01 hàng tháng</span>
              </div>
            </li>
            <li>
              <span className="hc-dot" style={{ background: 'var(--warning-ink)' }} />
              <div>
                <strong>Ca đêm cuối tháng (22:00–06:00)</strong>
                <span>Thuộc kỳ công tháng bắt đầu ca</span>
              </div>
            </li>
          </ul>
        </div>
      </section>
    );
  }

  const allowedModules = routes.filter((r) => r.task && r.id !== 'login' && canAccess(r, session));
  const quickModules = [
    ...copy.priorityIds.map((id) => allowedModules.find((m) => m.id === id)).filter(Boolean),
    ...allowedModules.filter((m) => !copy.priorityIds.includes(m.id)),
  ].slice(0, 4);

  const totalGates = live.gates.length;
  const activeGates = live.gates.filter((g) => g.status === 'ACTIVE').length;
  const inactiveGates = Math.max(0, totalGates - activeGates);
  const inGates = live.gates.filter((g) => g.direction === 'IN').length;
  const outGates = live.gates.filter((g) => g.direction === 'OUT').length;

  const inEvents = live.recentEvents.filter((e) => e.direction === 'IN').length;
  const outEvents = live.recentEvents.filter((e) => e.direction === 'OUT').length;
  const onlineEvents = live.recentEvents.filter((e) => (e.source || 'ONLINE') === 'ONLINE').length;
  const offlineEvents = Math.max(0, live.recentEvents.length - onlineEvents);

  const factoryGroups = live.gates.reduce((acc, g) => {
    const key = g.factoryId || 'Khác';
    acc[key] = (acc[key] || 0) + 1;
    return acc;
  }, {});
  const factoryBars = Object.entries(factoryGroups).map(([name, count]) => ({
    label: name.replace(/^FACTORY-/, 'Nhà máy '),
    count,
    pct: totalGates > 0 ? Math.round((count / totalGates) * 100) : 0,
  }));

  const isLoading = status === 'loading';

  return (
    <section className="hc-dashboard">
      {/* Top Greeting Strip */}
      <div className="hc-greeting">
        <div>
          <h2>Xin chào {copy.greeting}, chào mừng đến với TMC Access Control</h2>
          <p>{copy.subtitle}</p>
        </div>
        <div className="hc-greeting-actions">
          <a className="primary-link" href={copy.primary.href}>{copy.primary.label}</a>
        </div>
      </div>

      {status === 'error' && (
        <div className="notice error" role="alert">
          <strong>Không đồng bộ được số liệu trực tiếp</strong>
          <p>{error}</p>
          <button type="button" onClick={() => loadLive()} style={{ marginTop: 8 }}>Thử tải lại</button>
        </div>
      )}

      {/* Main 2-Column Enterprise Grid */}
      <div className="hc-layout">
        <div className="hc-main">
          {/* Strip 1: Tabular Operational Counters (không dùng vòng tròn 100% trang trí) */}
          <section className="hc-card" aria-label="Tổng quan nhân sự và xác thực">
            <div className="hc-card-head">
              <h3>Nhân sự &amp; Xác thực</h3>
              <span className="hc-card-meta">Phạm vi: {session.actor.scope}</span>
            </div>
            <div className="hc-kpi-row">
              {showOrgGateMetrics && (
                <div className="hc-kpi-cell">
                  <span className="hc-kpi-label">Nhân sự trong phạm vi</span>
                  {isLoading ? <span className="ops-skeleton" /> : <strong className="hc-kpi-num">{live.employeeCount}</strong>}
                  <span className="hc-kpi-sub">Hồ sơ đang quản lý</span>
                </div>
              )}
              <div className="hc-kpi-cell">
                <span className="hc-kpi-label">Lượt quét ghi nhận</span>
                {isLoading ? <span className="ops-skeleton" /> : <strong className="hc-kpi-num hc-kpi-num--success">{live.eventTotal}</strong>}
                <span className="hc-kpi-sub">Sự kiện bất biến</span>
              </div>
              <div className="hc-kpi-cell">
                <span className="hc-kpi-label">Điều chỉnh chờ duyệt</span>
                {isLoading ? <span className="ops-skeleton" /> : (
                  <strong className={`hc-kpi-num${live.pendingAdjustments.length > 0 ? ' hc-kpi-num--warning' : ''}`}>
                    {live.pendingAdjustments.length}
                  </strong>
                )}
                <span className="hc-kpi-sub">Trạng thái PENDING</span>
              </div>
              <div className="hc-kpi-cell">
                <span className="hc-kpi-label">Mã QR động ES256</span>
                <strong className="hc-kpi-num hc-kpi-num--accent">60s</strong>
                <span className="hc-kpi-sub">Dùng một lần (chống replay)</span>
              </div>
            </div>
          </section>

          {/* Strip 2: Device & Gate Status (chỉ hiển thị cho vai trò có phạm vi cổng/quản trị) */}
          {showOrgGateMetrics && (
            <section className="hc-card" aria-label="Trạng thái cổng và thiết bị">
              <div className="hc-card-head">
                <h3>Trạng thái Cổng &amp; Thiết bị</h3>
                {canAccess(routes.find((r) => r.id === 'administration'), session) && (
                  <a href="#/administration" className="ops-panel-link">Quản lý cổng →</a>
                )}
              </div>
              {isLoading ? (
                <div className="ops-skeleton-rows" aria-busy="true">
                  <span className="ops-skeleton ops-skeleton--row" />
                </div>
              ) : (
                <div className="hc-stat-row">
                  <div className="hc-kpi-cell">
                    <span className="hc-kpi-label">Tổng trạm cổng</span>
                    <strong className="hc-kpi-num">{totalGates}</strong>
                    <span className="hc-kpi-sub">Tạm dừng: {inactiveGates}</span>
                  </div>
                  <div className="hc-stat-item">
                    <RingGauge value={activeGates} total={totalGates} color="var(--success-ink)" />
                    <div>
                      <strong>Đang hoạt động</strong>
                      <span>{activeGates}/{totalGates} cổng</span>
                    </div>
                  </div>
                  <div className="hc-stat-item">
                    <RingGauge value={inGates} total={totalGates} color="var(--accent)" />
                    <div>
                      <strong>Cổng chiều Vào (IN)</strong>
                      <span>{inGates}/{totalGates} cổng</span>
                    </div>
                  </div>
                  <div className="hc-stat-item">
                    <RingGauge value={outGates} total={totalGates} color="var(--ink-soft)" />
                    <div>
                      <strong>Cổng chiều Ra (OUT)</strong>
                      <span>{outGates}/{totalGates} cổng</span>
                    </div>
                  </div>
                </div>
              )}
            </section>
          )}

          {/* Middle Analytics Row */}
          <div className={showOrgGateMetrics ? 'hc-mid-grid' : ''}>
            <section className="hc-card" aria-label="Thống kê lượt quét chấm công">
              <div className="hc-card-head">
                <h3>Thống kê Lượt quét</h3>
                <span className="hc-card-meta">Phiên hiện tại</span>
              </div>
              {isLoading ? (
                <div className="ops-skeleton-rows" aria-busy="true">
                  <span className="ops-skeleton ops-skeleton--row" />
                  <span className="ops-skeleton ops-skeleton--row" />
                </div>
              ) : (
                <div className="hc-donut-wrap">
                  <DonutChart
                    totalLabel="Lượt quét"
                    segments={[
                      { label: 'Chiều IN', value: inEvents, color: 'var(--success-ink)' },
                      { label: 'Chiều OUT', value: outEvents, color: 'var(--accent)' },
                    ]}
                  />
                  <ul className="hc-legend">
                    <li><span className="hc-dot" style={{ background: 'var(--success-ink)' }} />Chiều vào (IN)<strong>{inEvents}</strong></li>
                    <li><span className="hc-dot" style={{ background: 'var(--accent)' }} />Chiều ra (OUT)<strong>{outEvents}</strong></li>
                    <li><span className="hc-dot" style={{ background: 'var(--ink-soft)' }} />Nguồn Online<strong>{onlineEvents}</strong></li>
                    <li><span className="hc-dot" style={{ background: 'var(--warning-ink)' }} />Nguồn Offline<strong>{offlineEvents}</strong></li>
                  </ul>
                </div>
              )}
            </section>

            {showOrgGateMetrics && (
              <section className="hc-card" aria-label="Phân bổ cổng theo nhà máy">
                <div className="hc-card-head">
                  <h3>Phân bổ Cổng theo Nhà máy</h3>
                  <span className="hc-card-meta">{totalGates} cổng</span>
                </div>
                {isLoading ? (
                  <div className="ops-skeleton-rows" aria-busy="true">
                    <span className="ops-skeleton ops-skeleton--row" />
                    <span className="ops-skeleton ops-skeleton--row" />
                  </div>
                ) : factoryBars.length === 0 ? (
                  <div className="ops-empty">
                    <p>Chưa có dữ liệu phân bổ cổng cho phiên này.</p>
                  </div>
                ) : (
                  <div className="hc-bars">
                    {factoryBars.map((bar) => (
                      <div className="hc-bar-col" key={bar.label}>
                        <span className="hc-bar-val">{bar.count}</span>
                        <div className="hc-bar-track">
                          <div className="hc-bar-fill" style={{ height: `${Math.max(12, bar.pct)}%` }} />
                        </div>
                        <span className="hc-bar-lbl">{bar.label}</span>
                      </div>
                    ))}
                  </div>
                )}
              </section>
            )}
          </div>

          {/* Bottom Table: Recent Gate Events */}
          <section className="hc-card" aria-label="Nhật ký quét cổng gần đây">
            <div className="hc-card-head">
              <h3>Nhật ký Quét cổng gần đây</h3>
              {canAccess(routes.find((r) => r.id === 'attendance'), session) && (
                <a href="#/attendance" className="ops-panel-link">Xem bảng công →</a>
              )}
            </div>
            {isLoading ? (
              <div className="ops-skeleton-rows" aria-busy="true">
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
              <div className="hc-table-wrap">
                <table className="gs-table">
                  <thead>
                    <tr>
                      <th>Thời gian</th>
                      <th>Nhân viên</th>
                      <th>Cổng</th>
                      <th>Chiều</th>
                      <th>Nguồn</th>
                    </tr>
                  </thead>
                  <tbody>
                    {live.recentEvents.map((ev) => (
                      <tr key={ev.eventId || ev.id}>
                        <td className="mono">{ev.occurredAt ? new Date(ev.occurredAt).toLocaleTimeString('vi-VN') : '—'}</td>
                        <td className="mono" title={ev.employeeId || ''}>{shortId(ev.employeeId)}</td>
                        <td className="mono" title={ev.gateId || ''}>{shortId(ev.gateId)}</td>
                        <td>
                          <span className={`ops-dir ops-dir--${(ev.direction || 'IN').toLowerCase()}`}>
                            {ev.direction || 'IN'}
                          </span>
                        </td>
                        <td>{ev.source || 'ONLINE'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </div>

        {/* Right Utility Rail (≤4 lối tắt ưu tiên, chờ duyệt, mốc kỳ công) */}
        <aside className="hc-rail">
          <section className="hc-card" aria-label="Thao tác nhanh">
            <div className="hc-card-head">
              <h3>Thao tác nhanh</h3>
              <span className="hc-card-meta">Ưu tiên theo vai trò</span>
            </div>
            <div className="hc-quick-grid">
              {quickModules.map((item) => (
                <a className="hc-quick-tile" key={item.id} href={`#/${item.id}`}>
                  <span className="hc-quick-icon">{renderIcon(item.id)}</span>
                  <span className="hc-quick-label">
                    <strong>{item.title}</strong>
                    <small>{moduleMeta[item.id]?.badge || 'Sẵn sàng'}</small>
                  </span>
                </a>
              ))}
            </div>
          </section>

          <section className="hc-card" aria-label="Yêu cầu chờ xử lý">
            <div className="hc-card-head">
              <h3>
                Chờ xử lý
                {live.pendingAdjustments.length > 0 && (
                  <span className="hc-count-badge">{live.pendingAdjustments.length}</span>
                )}
              </h3>
              {canAccess(routes.find((r) => r.id === 'attendance'), session) && (
                <a href="#/attendance" className="ops-panel-link">Xem tất cả</a>
              )}
            </div>
            {isLoading ? (
              <div className="ops-skeleton-rows" aria-busy="true">
                <span className="ops-skeleton ops-skeleton--row" />
              </div>
            ) : live.pendingAdjustments.length === 0 ? (
              <p className="hc-rail-empty">Không có yêu cầu điều chỉnh công nào đang chờ duyệt.</p>
            ) : (
              <ul className="hc-pending-list">
                {live.pendingAdjustments.slice(0, 5).map((adj) => (
                  <li key={adj.id}>
                    <strong title={adj.employeeId || ''}>{shortId(adj.employeeId)} · {adj.workDate || '—'}</strong>
                    <span>{adj.reason || 'Yêu cầu điều chỉnh công'}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="hc-card" aria-label="Mốc vận hành kỳ công">
            <div className="hc-card-head">
              <h3>Mốc vận hành kỳ công</h3>
            </div>
            <ul className="hc-schedule-list">
              <li>
                <span className="hc-dot" style={{ background: 'var(--accent)' }} />
                <div>
                  <strong>Chốt kỳ công chính thức</strong>
                  <span>06:30 ngày 01 hàng tháng</span>
                </div>
              </li>
              <li>
                <span className="hc-dot" style={{ background: 'var(--success-ink)' }} />
                <div>
                  <strong>Hạn đối soát &amp; xuất HR/Payroll</strong>
                  <span>Trước 12:00 ngày 01 hàng tháng</span>
                </div>
              </li>
              <li>
                <span className="hc-dot" style={{ background: 'var(--warning-ink)' }} />
                <div>
                  <strong>Ca đêm cuối tháng (22:00–06:00)</strong>
                  <span>Thuộc kỳ công tháng bắt đầu ca</span>
                </div>
              </li>
            </ul>
          </section>
        </aside>
      </div>
    </section>
  );
}
