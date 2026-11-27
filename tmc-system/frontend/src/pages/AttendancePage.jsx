import React, { useState, useMemo, useEffect } from 'react';
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

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: View — Chấm công, sự kiện bất biến, điều chỉnh & giả lập Edge Sync.
 * NGUỒN: main.pdf, FR-AT-03..07; DECISIONS.md §1, §6, §8.
 */

const USERS = [
  { id: 'EMP001', name: 'Nguyễn Văn An', role: 'EMPLOYEE', roleTitle: 'Nhân viên', dept: 'Phân xưởng A' },
  { id: 'SUP001', name: 'Trần Thị Bình', role: 'SUPERVISOR', roleTitle: 'Tổ trưởng / Quản lý', dept: 'Phân xưởng A' },
  { id: 'HR001', name: 'Lê Hoàng Cường', role: 'HR', roleTitle: 'Chuyên viên Nhân sự', dept: 'Phòng Nhân sự' },
  { id: 'AUD001', name: 'Phạm Minh Đức', role: 'AUDITOR', roleTitle: 'Kiểm toán viên (Auditor)', dept: 'Ban Kiểm toán' }
];

export default function AttendancePage({ session } = {}) {
  const [currentUser, setCurrentUser] = useState(USERS[0]);
  const [activeTab, setActiveTab] = useState('daily');
  const [apiStatus, setApiStatus] = useState('idle');
  const [apiMessage, setApiMessage] = useState('');

  const [events, setEvents] = useState([
    {
      eventId: '018f45a1-0001-7000-8000-000000000001',
      occurredAt: '2026-10-31T22:00:00Z',
      direction: 'IN',
      source: 'ONLINE',
      gateId: 'GATE-01',
      factoryId: 'F01',
      employeeId: 'EMP001',
      jti: 'jti-oct31-in',
      flags: [],
      payloadSha256: 'a1b2c3d4e5f6...31a'
    },
    {
      eventId: '018f45a1-0002-7000-8000-000000000002',
      occurredAt: '2026-11-01T06:00:00Z',
      direction: 'OUT',
      source: 'ONLINE',
      gateId: 'GATE-01',
      factoryId: 'F01',
      employeeId: 'EMP001',
      jti: 'jti-nov01-out',
      flags: [],
      payloadSha256: 'f6e5d4c3b2a1...01b'
    },
    {
      eventId: '018f45a1-0003-7000-8000-000000000003',
      occurredAt: '2026-11-10T06:30:00Z',
      direction: 'IN',
      source: 'ONLINE',
      gateId: 'GATE-02',
      factoryId: 'F01',
      employeeId: 'EMP001',
      jti: 'jti-nov10-in',
      flags: ['LATE_ARRIVAL'],
      payloadSha256: '778899aabbcc...10a'
    },
    {
      eventId: '018f45a1-0004-7000-8000-000000000004',
      occurredAt: '2026-11-10T14:00:00Z',
      direction: 'OUT',
      source: 'ONLINE',
      gateId: 'GATE-02',
      factoryId: 'F01',
      employeeId: 'EMP001',
      jti: 'jti-nov10-out',
      flags: [],
      payloadSha256: '112233445566...10b'
    },
    {
      eventId: '018f45a1-0005-7000-8000-000000000005',
      occurredAt: '2026-11-12T06:00:00Z',
      direction: 'IN',
      source: 'ONLINE',
      gateId: 'GATE-01',
      factoryId: 'F01',
      employeeId: 'EMP001',
      jti: 'jti-nov12-in1',
      flags: [],
      payloadSha256: '445566778899...12a'
    },
    {
      eventId: '018f45a1-0006-7000-8000-000000000006',
      occurredAt: '2026-11-12T08:00:00Z',
      direction: 'IN',
      source: 'ONLINE',
      gateId: 'GATE-01',
      factoryId: 'F01',
      employeeId: 'EMP001',
      jti: 'jti-nov12-in2',
      flags: ['CONSECUTIVE_ENTRY'],
      payloadSha256: '998877665544...12b'
    }
  ]);

  const [dailyRecords, setDailyRecords] = useState([
    {
      id: 'DAY-EMP001-2026-10-31',
      employeeId: 'EMP001',
      workDate: '2026-10-31',
      shiftCode: 'SHIFT_NIGHT',
      shiftName: 'Ca Đêm (22:00 - 06:00)',
      firstIn: '2026-10-31 22:00:00',
      lastOut: '2026-11-01 06:00:00',
      hoursWorked: 8.0,
      isLate: false,
      lateMinutes: 0,
      isEarlyLeave: false,
      earlyMinutes: 0,
      isMissingSwipe: false,
      isAdjusted: false,
      anomalyFlags: [],
      note: 'Ca đêm kết thúc 06:00 ngày 01/11, vẫn tính vào tháng 10 (DECISIONS.md §1)'
    },
    {
      id: 'DAY-EMP001-2026-11-10',
      employeeId: 'EMP001',
      workDate: '2026-11-10',
      shiftCode: 'SHIFT_1',
      shiftName: 'Ca Sáng (06:00 - 14:00)',
      firstIn: '2026-11-10 06:30:00',
      lastOut: '2026-11-10 14:00:00',
      hoursWorked: 7.5,
      isLate: true,
      lateMinutes: 30,
      isEarlyLeave: false,
      earlyMinutes: 0,
      isMissingSwipe: false,
      isAdjusted: false,
      anomalyFlags: ['LATE_ARRIVAL'],
      note: 'Đi trễ 30 phút'
    },
    {
      id: 'DAY-EMP001-2026-11-12',
      employeeId: 'EMP001',
      workDate: '2026-11-12',
      shiftCode: 'SHIFT_1',
      shiftName: 'Ca Sáng (06:00 - 14:00)',
      firstIn: '2026-11-12 06:00:00',
      lastOut: null,
      hoursWorked: null,
      isLate: false,
      lateMinutes: 0,
      isEarlyLeave: false,
      earlyMinutes: 0,
      isMissingSwipe: true,
      isAdjusted: false,
      anomalyFlags: ['CONSECUTIVE_ENTRY', 'MISSING_OUT'],
      note: 'Vào 2 lần liên tiếp, thiếu quẹt ra'
    }
  ]);

  const [adjustments, setAdjustments] = useState([
    {
      id: 'ADJ-202611-001',
      employeeId: 'EMP001',
      workDate: '2026-11-10',
      adjustmentType: 'EXCUSE_LATE',
      adjustmentTypeName: 'Miễn trừ đi trễ',
      requestedTime: null,
      adjustedHours: 8.0,
      reason: 'Xe đưa đón công nhân gặp sự cố trên quốc lộ, có biên bản xác nhận của đội xe',
      status: 'APPROVED',
      requestedBy: 'EMP001',
      requestedAt: '2026-11-10 15:30:00',
      reviewedBy: 'HR001',
      reviewedAt: '2026-11-11 09:15:00',
      reviewNote: 'Đã xác thực danh sách xe đưa đón hợp lệ'
    },
    {
      id: 'ADJ-202611-002',
      employeeId: 'EMP001',
      workDate: '2026-11-12',
      adjustmentType: 'ADD_OUT',
      adjustmentTypeName: 'Bổ sung giờ ra',
      requestedTime: '2026-11-12 14:00:00',
      adjustedHours: 8.0,
      reason: 'Cổng 1 bảo trì đột xuất lúc tan ca, đã báo bảo vệ trực cổng',
      status: 'PENDING',
      requestedBy: 'EMP001',
      requestedAt: '2026-11-12 14:30:00',
      reviewedBy: null,
      reviewedAt: null,
      reviewNote: null
    }
  ]);

  const [formWorkDate, setFormWorkDate] = useState('2026-11-12');
  const [formType, setFormType] = useState('ADD_OUT');
  const [formTime, setFormTime] = useState('14:00');
  const [formReason, setFormReason] = useState('');
  const [formError, setFormError] = useState('');
  const [formSuccess, setFormSuccess] = useState('');
  const [selectedMonth, setSelectedMonth] = useState('2026-11');
  const [simulatorStatus, setSimulatorStatus] = useState('');

  useEffect(() => {
    if (!session) return undefined;
    const controller = new AbortController();
    setApiStatus('loading');
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
            ? `Đã đồng bộ từ Central API (${liveEvents.length} sự kiện, ${liveRecords.length} ngày công, ${liveAdjustments.length} điều chỉnh).`
            : 'Đã kết nối Central API — chưa có dữ liệu công thực tế trong kỳ này.'
        );
      })
      .catch((err) => {
        if (err?.name === 'AbortError') return;
        setApiStatus('error');
        setApiMessage(`Không thể tải dữ liệu chấm công từ Central API (${err?.message || 'lỗi kết nối'}).`);
      });
    return () => controller.abort();
  }, [session, selectedMonth]);

  const kpiData = useMemo(() => {
    const filtered = dailyRecords.filter((r) => r.workDate.startsWith(selectedMonth));
    const totalHours = filtered.reduce((acc, r) => acc + (r.hoursWorked || 0), 0);
    const completedDays = filtered.filter((r) => !r.isMissingSwipe).length;
    const lateDays = filtered.filter((r) => r.isLate).length;
    const earlyDays = filtered.filter((r) => r.isEarlyLeave).length;
    const anomalyCount = filtered.reduce((acc, r) => acc + (r.anomalyFlags ? r.anomalyFlags.length : 0), 0);
    return { totalHours, completedDays, lateDays, earlyDays, anomalyCount };
  }, [dailyRecords, selectedMonth]);

  const handleCreateAdjustment = async (e) => {
    e.preventDefault();
    setFormError('');
    setFormSuccess('');
    if (!formReason.trim() || formReason.trim().length < 10) {
      setFormError('Lý do điều chỉnh phải dài tối thiểu 10 ký tự theo CONTRACT-V1 §9.');
      return;
    }
    const proposedDirection = formType === 'ADD_IN' ? 'IN' : formType === 'ADD_OUT' ? 'OUT' : null;
    const proposedOccurredAt = new Date(`${formWorkDate}T${formTime}:00+07:00`).toISOString();
    try {
      const created = await attendanceApi.createAdjustment(
        {
          eventId: null,
          proposedOccurredAt,
          proposedDirection,
          reason: formReason.trim(),
        },
        crypto.randomUUID()
      );
      setAdjustments((prev) => [created, ...prev]);
      setFormReason('');
      setFormSuccess(`Đã gửi yêu cầu điều chỉnh lên hệ thống (trạng thái ${created.state || created.status || 'PENDING'}).`);
    } catch (err) {
      setFormError(err?.message || 'Không thể gửi yêu cầu điều chỉnh lên Central API.');
    }
  };

  const handleReviewAdjustment = async (adjId, approved) => {
    const adj = adjustments.find((a) => a.id === adjId);
    if (!adj) return;
    if (currentUser.role === 'AUDITOR') {
      window.alert('Auditor chỉ có quyền tra cứu, cấm phê duyệt hoặc từ chối điều chỉnh công.');
      return;
    }
    const requester = String(adj.requestedBy || adj.employeeId || '');
    if (requester && requester.toLowerCase() === String(currentUser.id || '').toLowerCase()) {
      window.alert('Người tạo không được tự phê duyệt yêu cầu điều chỉnh của chính mình.');
      return;
    }
    if (currentUser.role !== 'SUPERVISOR' && currentUser.role !== 'HR' && currentUser.role !== 'ADMIN') {
      window.alert('Chỉ Quản lý (Supervisor), Nhân sự (HR) hoặc Quản trị viên mới có quyền duyệt.');
      return;
    }
    const reviewNote = window.prompt(approved ? 'Nhập ghi chú phê duyệt:' : 'Nhập lý do từ chối:');
    if (reviewNote === null) return;
    try {
      const updated = await attendanceApi.decideAdjustment(adjId, {
        decision: approved ? 'APPROVE' : 'REJECT',
        reason: reviewNote.trim() || (approved ? 'Đã xác nhận và phê duyệt' : 'Không đủ căn cứ phê duyệt'),
        version: adj.version || 1,
      });
      setAdjustments((prev) => prev.map((a) => (a.id === adjId ? updated : a)));
    } catch (err) {
      window.alert(`Không thể cập nhật quyết định: ${err?.message || 'lỗi hệ thống'}`);
    }
  };

  const runSimulatorScenario = (scenario) => {
    const nowIso = new Date().toISOString();
    switch (scenario) {
      case 'NIGHT_SHIFT_MONTH_BOUNDARY':
        setEvents((prev) => [
          {
            eventId: `018f-sim-out-${Date.now()}`,
            occurredAt: '2026-11-01T06:00:00Z',
            direction: 'OUT',
            source: 'ONLINE',
            gateId: 'GATE-01',
            factoryId: 'F01',
            employeeId: 'EMP001',
            jti: 'jti-sim-nov01',
            flags: [],
            payloadSha256: 'sha256-sim-nov01'
          },
          {
            eventId: `018f-sim-in-${Date.now()}`,
            occurredAt: '2026-10-31T22:00:00Z',
            direction: 'IN',
            source: 'ONLINE',
            gateId: 'GATE-01',
            factoryId: 'F01',
            employeeId: 'EMP001',
            jti: 'jti-sim-oct31',
            flags: [],
            payloadSha256: 'sha256-sim-oct31'
          },
          ...prev
        ]);
        setSimulatorStatus('Ca đêm 31/10 22:00 → 01/11 06:00 đã được quy về kỳ công tháng 10/2026 (ACCEPTED).');
        break;
      case 'DUPLICATE_RETRY':
        setSimulatorStatus('Idempotency: gửi lại eventId trùng SHA-256 hash → trả DUPLICATE, không chèn bản ghi mới.');
        break;
      case 'RAPID_REPEAT':
        setEvents((prev) => [
          {
            eventId: `018f-sim-${Date.now()}`,
            occurredAt: nowIso,
            direction: 'IN',
            source: 'ONLINE',
            gateId: 'GATE-01',
            factoryId: 'F01',
            employeeId: 'EMP001',
            jti: `jti-rapid-${Date.now()}`,
            flags: ['RAPID_REPEAT_SCAN'],
            payloadSha256: 'sha256-rapid'
          },
          ...prev
        ]);
        setSimulatorStatus('Quét lặp < 30 giây → lưu bất biến và gắn cờ RAPID_REPEAT_SCAN.');
        break;
      case 'CONSECUTIVE_IN':
        setEvents((prev) => [
          {
            eventId: `018f-sim-${Date.now()}`,
            occurredAt: nowIso,
            direction: 'IN',
            source: 'ONLINE',
            gateId: 'GATE-01',
            factoryId: 'F01',
            employeeId: 'EMP001',
            jti: `jti-in-${Date.now()}`,
            flags: ['CONSECUTIVE_ENTRY'],
            payloadSha256: 'sha256-consec'
          },
          ...prev
        ]);
        setSimulatorStatus('Quét vào liên tiếp chưa có lượt ra → lưu bất biến và gắn cờ CONSECUTIVE_ENTRY.');
        break;
      default:
        break;
    }
  };

  return (
    <section>
      <div role="status" className="notice">
        <strong>
          {apiStatus === 'connected' ? 'Chế độ kết hợp (Central API + mô phỏng cục bộ).' : 'Màn hình mô phỏng.'}
        </strong>{' '}
        {apiMessage || 'Dữ liệu minh họa và thao tác tạo/duyệt điều chỉnh ở trang này chạy cục bộ trên trình duyệt, chưa ghi đè dữ liệu production.'}
      </div>

      <div className="module-heading">
        <div>
          <h1>Chấm công &amp; Tiếp nhận Sự kiện</h1>
          <p>Theo dõi bảng công ngày, nhật ký sự kiện bất biến (UTC), yêu cầu điều chỉnh và kịch bản ca đêm qua tháng.</p>
        </div>
        <div className="tmc-field" style={{ minWidth: 260 }}>
          <label htmlFor="sim-user">Vai trò giả lập</label>
          <select
            id="sim-user"
            value={currentUser.id}
            onChange={(e) => setCurrentUser(USERS.find((u) => u.id === e.target.value))}
          >
            {USERS.map((u) => (
              <option key={u.id} value={u.id}>
                {u.name} — {u.roleTitle}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="tmc-kpi-grid">
        <div className="tmc-kpi">
          <div className="tmc-kpi-label">Tổng giờ công ({selectedMonth})</div>
          <div className="tmc-kpi-value tmc-kpi-value--accent">{kpiData.totalHours.toFixed(1)} h</div>
        </div>
        <div className="tmc-kpi">
          <div className="tmc-kpi-label">Ca hoàn thành</div>
          <div className="tmc-kpi-value tmc-kpi-value--success">{kpiData.completedDays} ca</div>
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
          Bảng công ngày
        </button>
        <button role="tab" aria-selected={activeTab === 'events'} onClick={() => setActiveTab('events')}>
          Sự kiện bất biến
        </button>
        <button role="tab" aria-selected={activeTab === 'adjustments'} onClick={() => setActiveTab('adjustments')}>
          Điều chỉnh & phê duyệt
        </button>
        <button role="tab" aria-selected={activeTab === 'simulator'} onClick={() => setActiveTab('simulator')}>
          Giả lập Edge Sync
        </button>
      </div>

      {activeTab === 'daily' && (
        <div className="tmc-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12, marginBottom: 16 }}>
            <h3>Bảng chấm công chi tiết theo ngày</h3>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <label htmlFor="att-month" style={{ fontSize: 13, fontWeight: 600 }}>Kỳ công:</label>
              <select id="att-month" value={selectedMonth} onChange={(e) => setSelectedMonth(e.target.value)}>
                <option value="2026-11">Tháng 11/2026</option>
                <option value="2026-10">Tháng 10/2026</option>
              </select>
            </div>
          </div>

          <div className="data-table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Ngày công</th>
                  <th>Ca làm việc</th>
                  <th>Quẹt vào</th>
                  <th>Quẹt ra</th>
                  <th>Giờ công</th>
                  <th>Trạng thái</th>
                  <th>Bất thường & ghi chú</th>
                </tr>
              </thead>
              <tbody>
                {dailyRecords
                  .filter((r) => r.workDate.startsWith(selectedMonth))
                  .map((r) => (
                    <tr key={r.id}>
                      <td className="mono"><strong>{formatDateVi(r.workDate)}</strong></td>
                      <td>{r.shiftName}</td>
                      <td className="mono">
                        {r.firstIn ? formatDateTimeVi(r.firstIn) : <span className="tmc-badge tmc-badge--danger">Thiếu quẹt</span>}
                        {r.isLate && <div style={{ fontSize: 12.5, color: 'var(--danger-ink)' }}>+{r.lateMinutes} phút trễ</div>}
                      </td>
                      <td className="mono">
                        {r.lastOut ? formatDateTimeVi(r.lastOut) : <span className="tmc-badge tmc-badge--danger">Thiếu quẹt</span>}
                        {r.isEarlyLeave && <div style={{ fontSize: 12.5, color: 'var(--danger-ink)' }}>-{r.earlyMinutes} phút sớm</div>}
                      </td>
                      <td>
                        <strong>{r.hoursWorked === null ? 'Chưa đủ dữ liệu' : `${r.hoursWorked.toFixed(1)} giờ`}</strong>
                      </td>
                      <td>
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                          {r.isAdjusted && <span className="tmc-badge tmc-badge--accent">Đã điều chỉnh</span>}
                          {r.isMissingSwipe ? (
                            <span className="tmc-badge tmc-badge--danger">Thiếu quẹt</span>
                          ) : r.isLate ? (
                            <span className="tmc-badge tmc-badge--warning">Đi trễ</span>
                          ) : (
                            <span className="tmc-badge tmc-badge--success">Đúng giờ</span>
                          )}
                        </div>
                      </td>
                      <td>
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginBottom: 4 }}>
                          {r.anomalyFlags?.map((f) => (
                            <span key={f} className="tmc-badge tmc-badge--warning" title={f}>{anomalyText(f)}</span>
                          ))}
                        </div>
                        <small style={{ color: 'var(--muted)', fontSize: 13 }}>{r.note}</small>
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {activeTab === 'events' && (
        <div className="tmc-card">
          <h3>Nhật ký sự kiện quẹt thẻ bất biến (attendance_events)</h3>
          <p style={{ color: 'var(--muted)', fontSize: 13.5, marginBottom: 16 }}>
            Mỗi lượt quẹt tại Edge được đồng bộ lên Central dạng append-only, kiểm tra SHA-256 và không bao giờ bị sửa đổi.
          </p>
          <div className="data-table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Thời gian (UTC)</th>
                  <th>Hướng</th>
                  <th>Cổng / Nhà máy</th>
                  <th>Nhân viên</th>
                  <th>Nguồn</th>
                  <th>Token JTI</th>
                  <th>Cờ bất thường</th>
                  <th>Payload SHA-256</th>
                </tr>
              </thead>
              <tbody>
                {events.map((e) => (
                  <tr key={e.eventId}>
                    <td className="mono">{formatDateTimeVi(e.occurredAt, true)}</td>
                    <td>
                      <span className={`tmc-badge ${e.direction === 'IN' ? 'tmc-badge--accent' : 'tmc-badge--warning'}`}>
                        {e.direction === 'IN' ? 'VÀO (IN)' : 'RA (OUT)'}
                      </span>
                    </td>
                    <td>{e.gateId} ({e.factoryId})</td>
                    <td><strong>{e.employeeId}</strong></td>
                    <td><span className="tmc-badge tmc-badge--neutral">{e.source}</span></td>
                    <td className="mono">{e.jti}</td>
                    <td>
                      {e.flags?.length > 0 ? (
                        e.flags.map((f) => <span key={f} className="tmc-badge tmc-badge--danger" title={f}>{anomalyText(f)}</span>)
                      ) : (
                        <span className="tmc-badge tmc-badge--success">Bình thường</span>
                      )}
                    </td>
                    <td className="mono" style={{ fontSize: 12.5, color: 'var(--muted)' }}>{e.payloadSha256}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {activeTab === 'adjustments' && (
        <>
          <div className="tmc-card">
            <h3>Tạo yêu cầu điều chỉnh công</h3>
            <p style={{ color: 'var(--muted)', fontSize: 13.5 }}>
              Người gửi: <strong>{currentUser.name}</strong> ({currentUser.roleTitle}). Người tạo không thể tự duyệt yêu cầu của mình.
            </p>
            {formError && <div className="notice error" role="alert">{formError}</div>}
            {formSuccess && <div className="notice" style={{ background: 'var(--success-bg)', borderColor: '#b7e4c7', color: 'var(--success-ink)' }}>{formSuccess}</div>}

            <form onSubmit={handleCreateAdjustment} style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 14, marginTop: 14 }}>
              <div className="tmc-field">
                <label htmlFor="adj-date">Ngày làm việc</label>
                <input id="adj-date" type="date" value={formWorkDate} onChange={(e) => setFormWorkDate(e.target.value)} required />
              </div>
              <div className="tmc-field">
                <label htmlFor="adj-type">Loại điều chỉnh</label>
                <select id="adj-type" value={formType} onChange={(e) => setFormType(e.target.value)}>
                  <option value="ADD_OUT">Bổ sung giờ ra</option>
                  <option value="ADD_IN">Bổ sung giờ vào</option>
                  <option value="EXCUSE_LATE">Miễn trừ đi trễ</option>
                  <option value="EXCUSE_EARLY">Miễn trừ về sớm</option>
                  <option value="MODIFY_HOURS">Chỉnh sửa giờ công</option>
                </select>
              </div>
              <div className="tmc-field">
                <label htmlFor="adj-time">Giờ đề xuất</label>
                <input id="adj-time" type="time" value={formTime} onChange={(e) => setFormTime(e.target.value)} />
              </div>
              <div className="tmc-field" style={{ gridColumn: '1 / -1' }}>
                <label htmlFor="adj-reason">Lý do điều chỉnh (tối thiểu 10 ký tự)</label>
                <textarea
                  id="adj-reason"
                  rows={2}
                  value={formReason}
                  onChange={(e) => setFormReason(e.target.value)}
                  placeholder="Ví dụ: Thiết bị cổng bảo trì đột xuất lúc tan ca, đã báo bảo vệ trực cổng…"
                />
              </div>
              <div style={{ gridColumn: '1 / -1' }}>
                <button type="submit" className="tmc-btn-primary">Gửi yêu cầu điều chỉnh</button>
              </div>
            </form>
          </div>

          <div className="tmc-card">
            <h3>Danh sách yêu cầu điều chỉnh & Phê duyệt</h3>
            {currentUser.role === 'AUDITOR' && (
              <div className="notice" style={{ marginTop: 10 }}>
                <strong>Chế độ Auditor:</strong> Kiểm toán viên chỉ được tra cứu lịch sử, cấm phê duyệt hoặc từ chối điều chỉnh.
              </div>
            )}
            <div className="data-table-scroll" style={{ marginTop: 12 }}>
              <table>
                <thead>
                  <tr>
                    <th>Mã yêu cầu</th>
                    <th>Ngày công</th>
                    <th>Loại</th>
                    <th>Lý do</th>
                    <th>Người gửi</th>
                    <th>Trạng thái</th>
                    <th>Người duyệt</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {adjustments.map((adj) => {
                    const isCreator = adj.requestedBy.toLowerCase() === currentUser.id.toLowerCase();
                    const isAuditor = currentUser.role === 'AUDITOR';
                    const canApprove = !isCreator && !isAuditor && adj.status === 'PENDING';
                    return (
                      <tr key={adj.id}>
                        <td className="mono"><strong>{adj.id}</strong></td>
                        <td className="mono">{adj.workDate}</td>
                        <td>{adj.adjustmentTypeName}</td>
                        <td style={{ maxWidth: 260 }}>{adj.reason}</td>
                        <td>
                          {adj.requestedBy}
                          {isCreator && <small style={{ display: 'block', color: 'var(--accent)' }}>(Bạn)</small>}
                        </td>
                        <td>
                          <span className={`tmc-badge ${adj.status === 'APPROVED' ? 'tmc-badge--success' : adj.status === 'REJECTED' ? 'tmc-badge--danger' : 'tmc-badge--warning'}`}>
                            {adj.status}
                          </span>
                        </td>
                        <td>
                          {adj.reviewedBy ? (
                            <div>
                              <strong>{adj.reviewedBy}</strong>
                              <small style={{ display: 'block', color: 'var(--muted)' }}>{adj.reviewNote}</small>
                            </div>
                          ) : '—'}
                        </td>
                        <td>
                          {adj.status === 'PENDING' ? (
                            <div style={{ display: 'flex', gap: 6 }}>
                              <button
                                className="tmc-btn-primary"
                                style={{ padding: '6px 10px', fontSize: 12 }}
                                disabled={!canApprove}
                                onClick={() => handleReviewAdjustment(adj.id, true)}
                              >
                                Duyệt
                              </button>
                              <button
                                className="tmc-btn-danger-ghost"
                                disabled={!canApprove}
                                onClick={() => handleReviewAdjustment(adj.id, false)}
                              >
                                Từ chối
                              </button>
                            </div>
                          ) : (
                            <span style={{ color: 'var(--muted)', fontSize: 12 }}>Đã xử lý</span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}

      {activeTab === 'simulator' && (
        <div className="tmc-card">
          <h3>Trình giả lập tiếp nhận sự kiện từ Edge</h3>
          <p style={{ color: 'var(--muted)', fontSize: 13.5, marginBottom: 16 }}>
            Kiểm thử nhanh các kịch bản nghiệp vụ: ca đêm qua tháng, idempotency retry và phát hiện bất thường.
          </p>
          <div className="module-grid" style={{ marginBottom: 16 }}>
            <div className="tmc-card" style={{ marginBottom: 0 }}>
              <h3>Ca đêm qua ranh giới tháng</h3>
              <p style={{ fontSize: 13, color: 'var(--muted)' }}>31/10 22:00 → 01/11 06:00 quy về kỳ công tháng 10.</p>
              <button className="tmc-btn-secondary" onClick={() => runSimulatorScenario('NIGHT_SHIFT_MONTH_BOUNDARY')}>
                Chạy kịch bản ca đêm
              </button>
            </div>
            <div className="tmc-card" style={{ marginBottom: 0 }}>
              <h3>Idempotency Retry</h3>
              <p style={{ fontSize: 13, color: 'var(--muted)' }}>Gửi lại eventId trùng SHA-256 → trả DUPLICATE.</p>
              <button className="tmc-btn-secondary" onClick={() => runSimulatorScenario('DUPLICATE_RETRY')}>
                Chạy kịch bản retry
              </button>
            </div>
            <div className="tmc-card" style={{ marginBottom: 0 }}>
              <h3>Quét lặp &lt; 30 giây</h3>
              <p style={{ fontSize: 13, color: 'var(--muted)' }}>Cùng nhân viên quẹt 2 lần trong 30s → RAPID_REPEAT_SCAN.</p>
              <button className="tmc-btn-secondary" onClick={() => runSimulatorScenario('RAPID_REPEAT')}>
                Chạy kịch bản quét nhanh
              </button>
            </div>
            <div className="tmc-card" style={{ marginBottom: 0 }}>
              <h3>Vào 2 lần liên tiếp</h3>
              <p style={{ fontSize: 13, color: 'var(--muted)' }}>Quẹt IN liên tiếp chưa có OUT → CONSECUTIVE_ENTRY.</p>
              <button className="tmc-btn-secondary" onClick={() => runSimulatorScenario('CONSECUTIVE_IN')}>
                Chạy kịch bản vào liên tiếp
              </button>
            </div>
          </div>
          {simulatorStatus && (
            <div className="notice" style={{ background: 'var(--accent-soft)', borderColor: '#b3e3db', color: 'var(--accent-ink)', marginBottom: 0 }}>
              {simulatorStatus}
            </div>
          )}
        </div>
      )}
    </section>
  );
}
