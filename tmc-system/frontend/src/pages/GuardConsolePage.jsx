import React, { useCallback, useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { request } from '../api.js';
import { createHttpClient } from '../httpClient.js';
import { getSession, subscribeSession } from '../session.js';
import { EDGE_BASE_URL, describeError, describeScanResult, grantRequestBody, newScanId, overrideRequestBody,
  summarizePresence, validateOverrideForm } from '../lib/gateStation.js';
import '../styles/gate-station.css';

/**
 * PHỤ TRÁCH: Task 2. Bảng điều khiển bảo vệ (FR-GS-05/06, FR-AT-07; UC-04; CONTRACT-V1.md §5 "Re-auth cho override").
 * Luồng mở thủ công/khẩn cấp: nhập lại mật khẩu → Central cấp action grant (60 giây, dùng một lần) → gửi grant kèm
 * lý do tới Edge /overrides. Grant và mật khẩu không được đưa vào state hiển thị: mật khẩu bị xóa ngay sau khi đổi
 * lấy grant, grant chỉ nằm trong ref cho tới khi Edge trả lời. Retry chỉ dùng lại đúng scanId + grant khi lỗi tạm thời
 * và grant còn hạn. Emergency không tạo công giả và cần xác nhận rõ ràng. Hiện diện chỉ là dữ liệu cục bộ của Edge
 * (coverage LOCAL_EDGE), luôn hiển thị asOf và mốc bắt đầu theo dõi.
 * Ẩn nút không thay thế kiểm tra quyền ở backend; Auditor bị chặn thao tác ở cả UI lẫn Edge/Central.
 */
const edge = createHttpClient({ baseUrl: EDGE_BASE_URL, auth: false });
const PRESENCE_REFRESH_MS = 30_000;
const GRANT_SAFETY_MS = 5_000;
const EMPTY_FORM = { mode: 'MANUAL', gateId: '', edgeId: '', employeeId: '', reason: '', password: '', confirmed: false };

export default function GuardConsolePage() {
  const session = useSyncExternalStore(subscribeSession, getSession, () => null);
  const isAuditor = session?.actor?.roles?.includes('AUDITOR') ?? false;
  const [form, setForm] = useState(EMPTY_FORM);
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const [outcome, setOutcome] = useState(null);
  const [canRetry, setCanRetry] = useState(false);
  const attempt = useRef(null); // { scanId, body, expiresAt } — chứa grant, không đưa vào state
  const [gates, setGates] = useState({ state: 'loading', items: [] });
  const [presence, setPresence] = useState({ state: 'loading' });

  const update = (field, value) => setForm(current => ({ ...current, [field]: value }));

  useEffect(() => {
    if (!session) return undefined;
    const controller = new AbortController();
    request('/gates?page=0&size=100', { signal: controller.signal })
      .then(page => setGates({ state: 'ready', items: page.items ?? [] }))
      .catch(error => { if (error.code !== 'CANCELLED') setGates({ state: 'error', items: [], error: describeError(error) }); });
    return () => controller.abort();
  }, [session]);

  const refreshPresence = useCallback(async signal => {
    try {
      const data = await edge('/presence', { signal });
      setPresence({ state: 'ready', data });
    } catch (error) {
      if (error.code !== 'CANCELLED') setPresence({ state: 'error', error: describeError(error) });
    }
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    refreshPresence(controller.signal);
    const timer = setInterval(() => refreshPresence(controller.signal), PRESENCE_REFRESH_MS);
    return () => { controller.abort(); clearInterval(timer); };
  }, [refreshPresence]);

  function pickGate(id) {
    const gate = gates.items.find(item => item.id === id);
    setForm(current => ({ ...current, gateId: gate?.id ?? '', edgeId: gate?.edgeId ?? '' }));
  }

  async function sendToEdge(current) {
    try {
      const result = await edge('/overrides', { method: 'POST', body: current.body,
        idempotencyKey: current.scanId, timeoutMs: 5000 });
      attempt.current = null;
      setCanRetry(false);
      setOutcome({ kind: 'result', view: describeScanResult(result), result });
      refreshPresence();
    } catch (error) {
      const described = describeError(error);
      const retryable = described.retryable && Date.now() < current.expiresAt - GRANT_SAFETY_MS;
      if (!retryable) attempt.current = null;
      setCanRetry(retryable);
      setOutcome({ kind: 'error', view: { tone: 'danger', ...described,
        detail: retryable ? described.detail : `${described.detail} Nếu cần, nhập lại mật khẩu để tạo yêu cầu mới.` } });
    }
  }

  async function onSubmit(event) {
    event.preventDefault();
    const found = validateOverrideForm(form);
    setErrors(found);
    if (Object.keys(found).length > 0 || busy || isAuditor) return;
    setBusy(true);
    setOutcome(null);
    setCanRetry(false);
    try {
      let grant;
      try {
        grant = await request('/auth/action-grants', { method: 'POST', body: grantRequestBody(form) });
      } catch (error) {
        const described = error.status === 401
          ? { title: 'Không xác nhận được mật khẩu', detail: 'Mật khẩu không đúng hoặc phiên đã hết hạn.', retryable: false }
          : describeError(error);
        setOutcome({ kind: 'error', view: { tone: 'danger', ...described } });
        return;
      } finally {
        update('password', ''); // không giữ mật khẩu sau khi đã đổi lấy grant hoặc thất bại
      }
      const scanId = newScanId();
      attempt.current = { scanId, expiresAt: Date.parse(grant.expiresAt),
        body: overrideRequestBody({ scanId, mode: form.mode, gateId: form.gateId, employeeId: form.employeeId,
          reason: form.reason, grant: grant.grant }) };
      await sendToEdge(attempt.current);
    } finally {
      setBusy(false);
    }
  }

  async function onRetry() {
    if (!attempt.current || busy) return;
    setBusy(true);
    try { await sendToEdge(attempt.current); } finally { setBusy(false); }
  }

  const summary = presence.state === 'ready' ? summarizePresence(presence.data) : null;
  const emergency = form.mode === 'EMERGENCY';
  const disabled = busy || isAuditor || !session;

  return <section aria-labelledby="guard-title">
    <h1 id="guard-title">Bảng điều khiển bảo vệ</h1>
    {!session && <p className="notice" role="status">Cần đăng nhập để mở cổng thủ công hoặc khẩn cấp.</p>}
    {isAuditor && <p className="notice" role="status">Tài khoản Auditor chỉ được xem, không được mở cổng.</p>}

    <form className={`gs-panel${emergency ? ' gs-danger-zone' : ''}`} onSubmit={onSubmit} noValidate>
      <h2>Mở cổng thủ công / khẩn cấp</h2>
      <fieldset className="gs-field" disabled={disabled}>
        <legend>Loại thao tác</legend>
        <div className="gs-row">
          <label><input type="radio" name="mode" checked={!emergency} onChange={() => update('mode', 'MANUAL')} /> Mở thủ công (QR lỗi, hết pin)</label>
          <label><input type="radio" name="mode" checked={emergency} onChange={() => update('mode', 'EMERGENCY')} /> Mở khẩn cấp (sơ tán)</label>
        </div>
        {errors.mode && <span className="gs-error">{errors.mode}</span>}
      </fieldset>

      <div className="gs-field">
        <label htmlFor="gate-select">Cổng</label>
        <select id="gate-select" value={form.gateId} onChange={event => pickGate(event.target.value)} disabled={disabled || gates.state !== 'ready'}>
          <option value="">{gates.state === 'loading' ? 'Đang tải danh sách cổng…' : 'Chọn cổng'}</option>
          {gates.items.map(gate => <option key={gate.id} value={gate.id}>{gate.name} ({gate.direction})</option>)}
        </select>
        {gates.state === 'error' && <p className="gs-hint" role="alert">{gates.error.title}. Nhập mã thủ công bên dưới.</p>}
        {gates.state === 'ready' && gates.items.length === 0 && <p className="gs-hint">Chưa có cổng nào được cấu hình.</p>}
      </div>
      <div className="gs-field">
        <label htmlFor="gate-id">Mã cổng (UUID)</label>
        <input id="gate-id" value={form.gateId} onChange={event => update('gateId', event.target.value.trim())} disabled={disabled} autoComplete="off" />
        {errors.gateId && <span className="gs-error">{errors.gateId}</span>}
      </div>
      <div className="gs-field">
        <label htmlFor="edge-id">Mã Edge (UUID)</label>
        <input id="edge-id" value={form.edgeId} onChange={event => update('edgeId', event.target.value.trim())} disabled={disabled} autoComplete="off" />
        {errors.edgeId && <span className="gs-error">{errors.edgeId}</span>}
      </div>
      {!emergency && <div className="gs-field">
        <label htmlFor="employee-id">Mã nhân viên cần mở cổng (UUID)</label>
        <input id="employee-id" value={form.employeeId} onChange={event => update('employeeId', event.target.value.trim())} disabled={disabled} autoComplete="off" />
        {errors.employeeId && <span className="gs-error">{errors.employeeId}</span>}
        <p className="gs-hint">Nhân viên vẫn phải có quyền qua cổng này theo chính sách; mở thủ công không bỏ qua quyền khu vực.</p>
      </div>}
      <div className="gs-field">
        <label htmlFor="reason">Lý do (10–500 ký tự, được ghi vào nhật ký kiểm toán)</label>
        <textarea id="reason" rows={3} value={form.reason} onChange={event => update('reason', event.target.value)} disabled={disabled} />
        {errors.reason && <span className="gs-error">{errors.reason}</span>}
      </div>
      <div className="gs-field">
        <label htmlFor="password">Nhập lại mật khẩu của bạn</label>
        <input id="password" type="password" autoComplete="current-password" value={form.password}
          onChange={event => update('password', event.target.value)} disabled={disabled} />
        {errors.password && <span className="gs-error">{errors.password}</span>}
      </div>
      {emergency && <div className="gs-field">
        <label><input type="checkbox" checked={form.confirmed} onChange={event => update('confirmed', event.target.checked)} disabled={disabled} />
          {' '}Tôi xác nhận mở cổng khẩn cấp. Thao tác này không tạo bản ghi chấm công.</label>
        {errors.confirmed && <span className="gs-error">{errors.confirmed}</span>}
      </div>}
      <div className="gs-row">
        <button type="submit" disabled={disabled}>{busy ? 'Đang xử lý…' : emergency ? 'Mở khẩn cấp' : 'Mở cổng'}</button>
        {canRetry && <button type="button" onClick={onRetry} disabled={busy}>Thử lại cùng yêu cầu</button>}
      </div>
    </form>

    <div className="gs-panel" aria-live="assertive" role="status">
      <h2>Kết quả</h2>
      {!outcome && <p className="gs-hint">Chưa có thao tác nào.</p>}
      {outcome && <div className={`gs-result gs-result--${outcome.view.tone}`}>
        <h2>{outcome.view.title}</h2>
        <p>{outcome.view.detail}</p>
        {outcome.result?.eventId && <p className="gs-meta">Event: {outcome.result.eventId}</p>}
        {outcome.result?.commandId && <p className="gs-meta">Lệnh cổng: {outcome.result.commandId}</p>}
        {outcome.result?.decision === 'EMERGENCY' && <p className="gs-meta">Không tạo công; thao tác đã được ghi nhật ký.</p>}
      </div>}
    </div>

    <div className="gs-panel">
      <h2>Hiện diện tại Edge này</h2>
      {presence.state === 'loading' && <p>Đang tải…</p>}
      {presence.state === 'error' && <p role="alert">{presence.error.title}. {presence.error.detail}</p>}
      {summary && <>
        <p><strong>{summary.inside}</strong> người đang ở trong theo lượt quét gần nhất.</p>
        <p className="gs-hint">Phạm vi: chỉ lượt quét tại Edge này (LOCAL_EDGE), tính từ {presence.data.trackedSince ?? 'không rõ'}; cập nhật lúc {presence.data.asOf}.
          Không thay thế danh sách đầy đủ của nhà máy khi có nhiều Edge hoặc mất kết nối.</p>
        {summary.items.length === 0 ? <p className="gs-hint">Chưa có ai.</p> : <table className="gs-table">
          <thead><tr><th>Nhân viên</th><th>Lần quét gần nhất</th></tr></thead>
          <tbody>{summary.items.map(item => <tr key={item.employeeId}><td>{item.employeeId}</td><td>{item.lastOccurredAt}</td></tr>)}</tbody>
        </table>}
      </>}
      <button type="button" onClick={() => refreshPresence()}>Làm mới</button>
    </div>
  </section>;
}
