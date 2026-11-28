import React, { useState, useMemo, useEffect, useRef } from 'react';
import { request } from '../api.js';
import { createAttendanceApi } from './attendanceApi.js';

const attendanceApi = createAttendanceApi(request);

const ANOMALY_LABELS = {
  LATE_ARRIVAL: 'Đi trễ',
  EARLY_LEAVE: 'Về sớm',
  MISSING_OUT: 'Thiếu quẹt ra',
  MISSING_IN: 'Thiếu quẹt vào',
  CONSECUTIVE_ENTRY: 'Quẹt vào liên tiếp',
  RAPID_REPEAT_SCAN: 'Quét lặp nhanh (<30s)',
  POST_REVOKE_VIOLATION: 'Quẹt sau thu hồi',
};

function formatDateVi(isoDate) {
  if (!isoDate || !/^\d{4}-\d{2}-\d{2}/.test(isoDate)) return isoDate || '—';
  const [y, m, d] = isoDate.slice(0, 10).split('-');
  return `${d}/${m}/${y}`;
}

function formatDateTimeVi(raw, withTz = false) {
  if (!raw) return '—';
  const parsed = new Date(raw.includes('T') ? raw : raw.replace(' ', 'T') + 'Z');
  if (Number.isNaN(parsed.getTime())) return raw;
  const formatted = parsed.toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
    timeZone: withTz ? 'UTC' : undefined,
  });
  return withTz ? `${formatted} (UTC)` : formatted;
}

function anomalyText(code) {
  return ANOMALY_LABELS[code] ? `${ANOMALY_LABELS[code]} (${code})` : code;
}

function currentYearMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

/**
 * PHỤ TRÁCH: Task 3 / Task 8 — tích hợp với Task 3.
 * VAI TRÒ: View Chấm công kết nối trực tiếp Central API (không dùng dữ liệu mẫu hoặc vai trò giả lập).
 * NGUỒN: CONTRACT-V1.md §9; DECISIONS.md §1, §8.
 */
export default function AttendancePage({ session } = {}) {
  const actor = session?.actor || null;
  const roles = actor?.roles || [];
  const permissions = actor?.permissions || [];
  const isAuditor = roles.includes('AUDITOR');
  const canApprove = !isAuditor && permissions.includes('attendance.approve');

  const [activeTab, setActiveTab] = useState('daily');
  const [selectedMonth, setSelectedMonth] = useState(currentYearMonth);
  const [reloadNonce, setReloadNonce] = useState(0);

  const [apiStatus, setApiStatus] = useState('idle');
  const [apiMessage, setApiMessage] = useState('');

  const [events, setEvents] = useState([]);
  const [dailyRecords, setDailyRecords] = useState([]);
  const [adjustments, setAdjustments] = useState([]);

  const [formWorkDate, setFormWorkDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [formDirection, setFormDirection] = useState('OUT');
  const [formTime, setFormTime] = useState('17:00');
  const [formReason, setFormReason] = useState('');
  const [formError, setFormError] = useState('');
  const [formSuccess, setFormSuccess] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Giữ ổn định Idempotency-Key cho cùng payload khi retry sau lỗi mạng/503 (FE-ATT-08)
  const pendingCreateKeyRef = useRef({ fingerprint: null, key: null });

  useEffect(() => {
    if (!session) return undefined;
    const controller = new AbortController();
    setApiStatus('loading');
    setApiMessage('');

    const [year, month] = selectedMonth.split('-').map(Number);
    const fromDate = `${selectedMonth}-01`;
    const nextMonth = month === 12 ? `${year + 1}-01-01` : `${year}-${String(month + 1).padStart(2, '0')}-01`;

    Promise.all([
      attendanceApi.listEvents({ page: 0, size: 50, signal: controller.signal }),
      attendanceApi.listRecords({ from: fromDate, to: nextMonth, page: 0, size: 50, signal: controller.signal }),
      attendanceApi.listAdjustments({ page: 0, size: 50, signal: controller.signal }),
    ])
      .then(([evPage, recPage, adjPage]) => {
        const liveEvents = Array.isArray(evPage?.items) ? evPage.items : [];
        const liveRecords = Array.isArray(recPage?.items) ? recPage.items : [];
        const liveAdjustments = Array.isArray(adjPage?.items) ? adjPage.items : [];
        setEvents(liveEvents);
        setDailyRecords(liveRecords);
        setAdjustments(liveAdjustments);
        setApiStatus('connected');
        const totalLive = liveEvents.length + liveRecords.length + liveAdjustments.length;
        setApiMessage(
          totalLive > 0
            ? `Đã tải từ Central API (${liveEvents.length} sự kiện, ${liveRecords.length} ngày công, ${liveAdjustments.length} điều chỉnh).`
            : 'Đã kết nối Central API — chưa có dữ liệu chấm công trong kỳ này.'
        );
      })
      .catch((err) => {
        if (err?.name === 'AbortError') return;
        setApiStatus('error');
        setApiMessage(err?.message || 'Không thể tải dữ liệu chấm công từ Central API.');
      });

    return () => controller.abort();
  }, [session, selectedMonth, reloadNonce]);

  const kpiData = useMemo(() => {
    const totalMinutes = dailyRecords.reduce((acc, r) => acc + (typeof r.workedMinutes === 'number' ? r.workedMinutes : 0), 0);
    const completedDays = dailyRecords.filter((r) => r.firstIn && r.lastOut).length;
    const lateDays = dailyRecords.filter((r) => (r.lateMinutes || 0) > 0).length;
    const earlyDays = dailyRecords.filter((r) => (r.earlyMinutes || 0) > 0).length;
    const anomalyCount = dailyRecords.reduce((acc, r) => acc + (Array.isArray(r.flags) ? r.flags.length : 0), 0);
    return { totalHours: totalMinutes / 60, completedDays, lateDays, earlyDays, anomalyCount };
  }, [dailyRecords]);

  const handleCreateAdjustment = async (e) => {
    e.preventDefault();
    setFormError('');
    setFormSuccess('');
    if (isAuditor) {
      setFormError('Auditor là vai trò chỉ đọc, không được gửi yêu cầu điều chỉnh công.');
      return;
    }
    const trimmedReason = formReason.trim();
    if (trimmedReason.length < 10 || trimmedReason.length > 500) {
      setFormError('Lý do điều chỉnh phải dài từ 10 đến 500 ký tự theo CONTRACT-V1 §9.');
      return;
    }

    const body = {
      eventId: null,
      proposedOccurredAt: new Date(`${formWorkDate}T${formTime}:00+07:00`).toISOString(),
      proposedDirection: formDirection || null,
      reason: trimmedReason,
    };
    const fingerprint = JSON.stringify(body);
    if (pendingCreateKeyRef.current.fingerprint !== fingerprint || !pendingCreateKeyRef.current.key) {
      pendingCreateKeyRef.current = { fingerprint, key: crypto.randomUUID() };
    }

    setSubmitting(true);
    try {
      const created = await attendanceApi.createAdjustment(body, pendingCreateKeyRef.current.key);
      pendingCreateKeyRef.current = { fingerprint: null, key: null };
      setAdjustments((prev) => [created, ...prev.filter((a) => a.id !== created.id)]);
      setFormReason('');
      setFormSuccess(`Đã gửi yêu cầu điều chỉnh lên hệ thống (trạng thái ${created.state || 'PENDING'}).`);
    } catch (err) {
      if (err?.status === 409) {
        setFormError('Xung đột yêu cầu (409): dữ liệu hoặc khóa idempotency đã thay đổi. Vui lòng tải lại danh sách.');
        pendingCreateKeyRef.current = { fingerprint: null, key: null };
      } else {
        setFormError(err?.message || 'Không thể gửi yêu cầu điều chỉnh lên Central API.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleReviewAdjustment = async (adj, approved) => {
    if (!canApprove) return;
    if (String(adj.createdBy || '') === String(actor?.userId || '')) {
      window.alert('Người tạo không được tự phê duyệt yêu cầu điều chỉnh của chính mình.');
      return;
    }
    const reviewNote = window.prompt(approved ? 'Nhập ghi chú phê duyệt:' : 'Nhập lý do từ chối:');
    if (reviewNote === null) return;
    try {
      const updated = await attendanceApi.decideAdjustment(adj.id, {
        decision: approved ? 'APPROVE' : 'REJECT',
        reason: reviewNote.trim() || (approved ? 'Đã xác nhận và phê duyệt' : 'Không đủ căn cứ phê duyệt'),
        version: adj.version || 1,
      });
      setAdjustments((prev) => prev.map((a) => (a.id === adj.id ? updated : a)));
      setReloadNonce((n) => n + 1);
    } catch (err) {
      if (err?.status === 409) {
        window.alert('Xung đột phiên bản (409): yêu cầu này vừa được người khác xử lý. Đang tải lại dữ liệu mới nhất.');
        setReloadNonce((n) => n + 1);
      } else {
        window.alert(`Không thể cập nhật quyết định: ${err?.message || 'lỗi hệ thống'}`);
      }
    }
  };

  return (
    <section>
      <div className="module-heading">
        <div>
          <span className="module-label">Chấm công</span>
          <h1>Chấm công &amp; Sự kiện Quẹt cổng</h1>
          <p>Dữ liệu bảng công ngày, nhật ký sự kiện bất biến (UTC) và quy trình điều chỉnh/phê duyệt công.</p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span className={`tmc-badge ${apiStatus === 'error' ? 'tmc-badge--danger' : 'tmc-badge--success'}`}>
            {apiStatus === 'loading' ? 'Đang tải…' : apiStatus === 'error' ? 'Lỗi kết nối' : 'Central API'}
          </span>
          <button type="button" className="tmc-btn-secondary" onClick={() => setReloadNonce((n) => n + 1)}>
            Tải lại
          </button>
        </div>
      </div>

      {apiMessage && (
        <div
          role={apiStatus === 'error' ? 'alert' : 'status'}
          className={`notice ${apiStatus === 'error' ? 'error' : ''}`}
        >
          {apiMessage}
        </div>
      )}

      <div className="tmc-kpi-grid">
        <div className="tmc-kpi">
          <div className="tmc-kpi-label">Tổng giờ công ({selectedMonth})</div>
          <div className="tmc-kpi-value tmc-kpi-value--accent">{kpiData.totalHours.toFixed(1)} h</div>
        </div>
        <div className="tmc-kpi">
          <div className="tmc-kpi-label">Ngày đủ IN/OUT</div>
          <div className="tmc-kpi-value tmc-kpi-value--success">{kpiData.completedDays} ngày</div>
        </div>
        <div className="tmc-kpi">
          <div className="tmc-kpi-label">Lượt đi trễ</div>
          <div className="tmc-kpi-value tmc-kpi-value--danger">{kpiData.lateDays}</div>
        </div>
        <div className="tmc-kpi">
          <div className="tmc-kpi-label">Lượt về sớm</div>
          <div className="tmc-kpi-value tmc-kpi-value--danger">{kpiData.earlyDays}</div>
        </div>
        <div className="tmc-kpi">
          <div className="tmc-kpi-label">Cờ bất thường</div>
          <div className="tmc-kpi-value tmc-kpi-value--warning">{kpiData.anomalyCount}</div>
        </div>
      </div>

      <div className="tmc-tabs" role="tablist" aria-label="Phân mục chấm công">
        <button role="tab" aria-selected={activeTab === 'daily'} onClick={() => setActiveTab('daily')}>
          Bảng công ngày ({dailyRecords.length})
        </button>
        <button role="tab" aria-selected={activeTab === 'events'} onClick={() => setActiveTab('events')}>
          Sự kiện bất biến ({events.length})
        </button>
        <button role="tab" aria-selected={activeTab === 'adjustments'} onClick={() => setActiveTab('adjustments')}>
          Điều chỉnh &amp; phê duyệt ({adjustments.length})
        </button>
      </div>

      {activeTab === 'daily' && (
        <div className="tmc-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12, marginBottom: 16 }}>
            <h3>Bảng chấm công chi tiết theo ngày</h3>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <label htmlFor="att-month" style={{ fontSize: 14, fontWeight: 500 }}>Kỳ công:</label>
              <input
                id="att-month"
                type="month"
                value={selectedMonth}
                onChange={(e) => e.target.value && setSelectedMonth(e.target.value)}
              />
            </div>
          </div>

          <div className="data-table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Ngày công</th>
                  <th>Nhân viên</th>
                  <th>Quẹt vào</th>
                  <th>Quẹt ra</th>
                  <th>Giờ công</th>
                  <th>Đi trễ / Về sớm</th>
                  <th>Cờ bất thường</th>
                </tr>
              </thead>
              <tbody>
                {dailyRecords.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="td-empty">
                      Chưa có bảng công ngày nào trong kỳ {selectedMonth}.
                    </td>
                  </tr>
                ) : (
                  dailyRecords.map((r) => (
                    <tr key={`${r.employeeId}-${r.workDate}`}>
                      <td className="mono"><strong>{formatDateVi(r.workDate)}</strong></td>
                      <td className="mono">{r.employeeId}</td>
                      <td className="mono">
                        {r.firstIn ? formatDateTimeVi(r.firstIn) : <span className="tmc-badge tmc-badge--danger">Thiếu vào</span>}
                      </td>
                      <td className="mono">
                        {r.lastOut ? formatDateTimeVi(r.lastOut) : <span className="tmc-badge tmc-badge--danger">Thiếu ra</span>}
                      </td>
                      <td>
                        <strong>
                          {typeof r.workedMinutes === 'number'
                            ? `${(r.workedMinutes / 60).toFixed(1)} giờ`
                            : 'Chưa đủ dữ liệu'}
                        </strong>
                      </td>
                      <td>
                        {(r.lateMinutes || 0) > 0 && <span className="tmc-badge tmc-badge--warning">+{r.lateMinutes}p trễ</span>}{' '}
                        {(r.earlyMinutes || 0) > 0 && <span className="tmc-badge tmc-badge--warning">-{r.earlyMinutes}p sớm</span>}
                        {(r.lateMinutes || 0) === 0 && (r.earlyMinutes || 0) === 0 && (
                          <span className="tmc-badge tmc-badge--success">Đúng giờ</span>
                        )}
                      </td>
                      <td>
                        {Array.isArray(r.flags) && r.flags.length > 0 ? (
                          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                            {r.flags.map((f) => (
                              <span key={f} className="tmc-badge tmc-badge--warning" title={f}>{anomalyText(f)}</span>
                            ))}
                          </div>
                        ) : '—'}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {activeTab === 'events' && (
        <div className="tmc-card">
          <h3>Nhật ký sự kiện quẹt thẻ bất biến</h3>
          <p style={{ color: 'var(--muted)', marginBottom: 16 }}>
            Mỗi lượt quẹt được lưu dạng append-only, kiểm tra SHA-256 và không bị sửa đổi.
          </p>
          <div className="data-table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Thời gian (UTC)</th>
                  <th>Loại sự kiện</th>
                  <th>Cổng / Nhà máy</th>
                  <th>Nhận lúc (UTC)</th>
                  <th>Cờ bất thường</th>
                  <th>Payload SHA-256</th>
                </tr>
              </thead>
              <tbody>
                {events.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="td-empty">
                      Chưa có sự kiện quẹt cổng nào được ghi nhận trong phạm vi của bạn.
                    </td>
                  </tr>
                ) : (
                  events.map((item) => {
                    const env = item.envelope || item;
                    return (
                      <tr key={env.eventId}>
                        <td className="mono">{formatDateTimeVi(env.occurredAt, true)}</td>
                        <td className="mono">{env.eventType}</td>
                        <td className="mono">{env.gateId}</td>
                        <td className="mono">{formatDateTimeVi(item.receivedAt, true)}</td>
                        <td>
                          {Array.isArray(item.flags) && item.flags.length > 0 ? (
                            item.flags.map((f) => (
                              <span key={f} className="tmc-badge tmc-badge--danger" title={f}>{anomalyText(f)}</span>
                            ))
                          ) : (
                            <span className="tmc-badge tmc-badge--success">Bình thường</span>
                          )}
                        </td>
                        <td className="mono" style={{ color: 'var(--muted)' }}>
                          {env.payloadSha256 ? `${env.payloadSha256.slice(0, 16)}…` : '—'}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {activeTab === 'adjustments' && (
        <>
          {!isAuditor && (
            <div className="tmc-card">
              <h3>Tạo yêu cầu điều chỉnh công</h3>
              <p style={{ color: 'var(--muted)' }}>
                Phạm vi phiên hiện tại: <strong>{actor?.scope || '—'}</strong> ({roles.join(', ') || '—'}). Người tạo không thể tự phê duyệt yêu cầu của mình.
              </p>
              {formError && <div className="notice error" role="alert">{formError}</div>}
              {formSuccess && (
                <div className="notice" style={{ background: 'var(--success-bg)', borderColor: '#b7e4c7', color: 'var(--success-ink)' }}>
                  {formSuccess}
                </div>
              )}

              <form onSubmit={handleCreateAdjustment} style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 14, marginTop: 14 }}>
                <div className="tmc-field">
                  <label htmlFor="adj-date">Ngày làm việc</label>
                  <input id="adj-date" type="date" value={formWorkDate} onChange={(e) => setFormWorkDate(e.target.value)} required />
                </div>
                <div className="tmc-field">
                  <label htmlFor="adj-dir">Hướng đề xuất</label>
                  <select id="adj-dir" value={formDirection} onChange={(e) => setFormDirection(e.target.value)}>
                    <option value="OUT">Bổ sung giờ ra (OUT)</option>
                    <option value="IN">Bổ sung giờ vào (IN)</option>
                  </select>
                </div>
                <div className="tmc-field">
                  <label htmlFor="adj-time">Giờ đề xuất (UTC+7)</label>
                  <input id="adj-time" type="time" value={formTime} onChange={(e) => setFormTime(e.target.value)} required />
                </div>
                <div className="tmc-field" style={{ gridColumn: '1 / -1' }}>
                  <label htmlFor="adj-reason">Lý do điều chỉnh (10–500 ký tự)</label>
                  <textarea
                    id="adj-reason"
                    rows={2}
                    value={formReason}
                    onChange={(e) => setFormReason(e.target.value)}
                    placeholder="Ví dụ: Thiết bị cổng bảo trì đột xuất lúc tan ca, đã báo bảo vệ trực cổng…"
                  />
                </div>
                <div style={{ gridColumn: '1 / -1' }}>
                  <button type="submit" className="tmc-btn-primary" disabled={submitting}>
                    {submitting ? 'Đang gửi…' : 'Gửi yêu cầu điều chỉnh'}
                  </button>
                </div>
              </form>
            </div>
          )}

          <div className="tmc-card">
            <h3>Danh sách yêu cầu điều chỉnh &amp; Phê duyệt</h3>
            {isAuditor && (
              <div className="notice" style={{ marginTop: 10 }}>
                <strong>Chế độ Auditor:</strong> Kiểm toán viên chỉ được tra cứu lịch sử, cấm gửi hoặc phê duyệt điều chỉnh.
              </div>
            )}
            <div className="data-table-scroll" style={{ marginTop: 12 }}>
              <table>
                <thead>
                  <tr>
                    <th>Mã yêu cầu</th>
                    <th>Giờ đề xuất</th>
                    <th>Hướng</th>
                    <th>Lý do</th>
                    <th>Trạng thái</th>
                    <th>Quyết định</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {adjustments.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="td-empty">
                        Chưa có yêu cầu điều chỉnh công nào.
                      </td>
                    </tr>
                  ) : (
                    adjustments.map((adj) => {
                      const isCreator = String(adj.createdBy || '') === String(actor?.userId || '');
                      const canAct = canApprove && !isCreator && adj.state === 'PENDING';
                      return (
                        <tr key={adj.id}>
                          <td className="mono"><strong>{String(adj.id).slice(0, 8)}…</strong></td>
                          <td className="mono">{formatDateTimeVi(adj.proposedOccurredAt)}</td>
                          <td>{adj.proposedDirection || '—'}</td>
                          <td style={{ maxWidth: 280 }}>{adj.reason}</td>
                          <td>
                            <span className={`tmc-badge ${adj.state === 'APPROVED' ? 'tmc-badge--success' : adj.state === 'REJECTED' ? 'tmc-badge--danger' : 'tmc-badge--warning'}`}>
                              {adj.state}
                            </span>
                          </td>
                          <td>{adj.decisionReason || '—'}</td>
                          <td>
                            {adj.state === 'PENDING' && canAct ? (
                              <div style={{ display: 'flex', gap: 6 }}>
                                <button type="button" className="tmc-btn-primary" onClick={() => handleReviewAdjustment(adj, true)}>
                                  Duyệt
                                </button>
                                <button type="button" className="tmc-btn-danger-ghost" onClick={() => handleReviewAdjustment(adj, false)}>
                                  Từ chối
                                </button>
                              </div>
                            ) : (
                              <span style={{ color: 'var(--muted)' }}>
                                {adj.state !== 'PENDING' ? 'Đã xử lý' : isCreator ? 'Không tự duyệt' : 'Chỉ xem'}
                              </span>
                            )}
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </section>
  );
}
