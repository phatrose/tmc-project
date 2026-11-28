import React, { useEffect, useRef, useState } from 'react';
import { createReportMutation } from './reportsApi.js';
import './ReportsPage.css';

const uuid4 = '[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}';
const displayTime = value => value ? new Date(value).toLocaleString('vi-VN') : '—';
const utc = value => value ? new Date(value).toISOString() : undefined;
const emptyPage = { items: [], page: 0, size: 20, totalElements: 0 };
const tomorrow = () => { const date = new Date(); date.setDate(date.getDate() + 1); return date.toLocaleDateString('sv-SE'); };

function Failure({ error }) {
  return error ? <div className="t4-error" role="alert">{error.message || 'Không thể hoàn tất yêu cầu.'}
    {error.requestId && <small>Mã tra cứu: {error.requestId}</small>}</div> : null;
}

function Pager({ value, onPage, busy }) {
  return <div className="t4-pager">
    <span>{value.totalElements} dòng · Trang {value.page + 1}</span>
    <button type="button" disabled={busy || value.page === 0} onClick={() => onPage(value.page - 1)}>Trước</button>
    <button type="button" disabled={busy || (value.page + 1) * value.size >= value.totalElements}
      onClick={() => onPage(value.page + 1)}>Sau</button>
  </div>;
}

const DEFAULT_FACTORY_ID = '00000000-0000-4000-8000-000000000023';
const sevenDaysAgoLocal = () => { const d = new Date(Date.now() - 7 * 86400000); return `${d.toLocaleDateString('sv-SE')}T00:00`; };
const tomorrowLocal = () => `${tomorrow()}T00:00`;

export default function ReportsPage({ actor = null, api = null } = {}) {
  const allowed = permission => actor?.permissions?.includes(permission);
  const payrollAllowed = actor?.roles?.includes('HR') && !actor.roles.includes('AUDITOR') && allowed('payroll.export');
  const currentMonth = new Date().toLocaleDateString('sv-SE').slice(0, 7);
  const [tab, setTab] = useState(allowed('report.read') ? 'attendance' : 'audit');
  const [factoryId, setFactoryId] = useState(actor?.factoryIds?.[0] || DEFAULT_FACTORY_ID);
  const [period, setPeriod] = useState(currentMonth);
  const [from, setFrom] = useState(`${currentMonth}-01`);
  const [to, setTo] = useState(tomorrow);
  const [employeeId, setEmployeeId] = useState('');
  const [records, setRecords] = useState(emptyPage);
  const [auditRows, setAuditRows] = useState(emptyPage);
  const [history, setHistory] = useState(emptyPage);
  const [actorId, setActorId] = useState('');
  const [action, setAction] = useState('');
  const [auditFrom, setAuditFrom] = useState(sevenDaysAgoLocal);
  const [auditTo, setAuditTo] = useState(tomorrowLocal);
  const [format, setFormat] = useState('CSV');
  const [reason, setReason] = useState('');
  const [version, setVersion] = useState(1);
  const [batchId, setBatchId] = useState('');
  const [batch, setBatch] = useState(null);
  const [dashboard, setDashboard] = useState(null);
  const [jobs, setJobs] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState('');
  const mutation = useRef(createReportMutation());
  const requests = useRef(new Set());
  const filterVersion = useRef(0);

  useEffect(() => {
    for (const controller of requests.current) controller.abort();
    requests.current.clear();
    filterVersion.current++;
    mutation.current = createReportMutation();
    setFactoryId(actor?.factoryIds?.[0] || DEFAULT_FACTORY_ID);
    setRecords(emptyPage); setAuditRows(emptyPage); setHistory(emptyPage); setJobs([]); setBatch(null);
    setError(null); setNotice(''); setBusy(false);
    return () => { for (const controller of requests.current) controller.abort(); };
  }, [actor?.userId]);

  useEffect(() => { filterVersion.current++; setRecords(emptyPage); setDashboard(null); }, [factoryId, from, to, employeeId]);
  useEffect(() => { filterVersion.current++; setAuditRows(emptyPage); }, [actorId, action, auditFrom, auditTo]);

  useEffect(() => {
    if (!api) return undefined;
    const pending = jobs.filter(job => ['QUEUED', 'RUNNING'].includes(job.state) && !job.pollStopped);
    if (!pending.length) return undefined;
    const controller = new AbortController();
    requests.current.add(controller);
    const timer = setTimeout(async () => {
      for (const job of pending) {
        if (controller.signal.aborted) break;
        if (job.nextPollAt && job.nextPollAt > Date.now()) continue;
        try {
          const updated = await api.exportStatus(job.kind, job.id, controller.signal);
          if (!controller.signal.aborted) setJobs(previous => previous.map(item => item.id === job.id
            ? { ...updated, kind: job.kind, pollError: null, nextPollAt: Date.now() + 3000 } : item));
        } catch (failure) {
          if (!controller.signal.aborted) setJobs(previous => previous.map(item => item.id === job.id
            ? { ...item, pollError: failure, pollStopped: !failure.retryable,
              nextPollAt: Date.now() + Math.max(3, failure.retryAfter || 3) * 1000 } : item));
          break;
        }
      }
      requests.current.delete(controller);
    }, Math.max(0, Math.min(...pending.map(job => job.nextPollAt || Date.now() + 3000)) - Date.now()));
    return () => { clearTimeout(timer); controller.abort(); requests.current.delete(controller); };
  }, [jobs]);

  async function perform(work) {
    const controller = new AbortController();
    requests.current.add(controller);
    setBusy(true); setError(null); setNotice('');
    try { await work(controller.signal); }
    catch (failure) { if (!controller.signal.aborted) setError(failure); }
    finally { requests.current.delete(controller); if (!controller.signal.aborted) setBusy(false); }
  }

  function loadAttendance(page = 0) {
    const revision = ++filterVersion.current;
    return perform(async signal => {
      const data = await api.attendance({ factoryId, employeeId, from, to, page, size: 20 }, signal);
      if (!signal.aborted && revision === filterVersion.current) setRecords(data);
    });
  }

  function loadAudit(page = 0) {
    const revision = ++filterVersion.current;
    return perform(async signal => {
      const data = await api.audit({ actorId, action, from: utc(auditFrom), to: utc(auditTo), page, size: 20 }, signal);
      if (!signal.aborted && revision === filterVersion.current) setAuditRows(data);
    });
  }

  const mutate = (kind, body, work) => mutation.current(kind, period, body, work);

  function loadHistory(page = 0) {
    return perform(async signal => {
      const result = await api.history(period, { factoryId, page, size: 20 }, signal);
      if (!signal.aborted) setHistory(result);
    });
  }

  function createExport(kind) {
    const body = kind === 'AUDIT' ? { factoryId, from: utc(auditFrom), to: utc(auditTo), format }
      : { factoryId, period, format };
    if (kind === 'AUDIT' && (!body.from || !body.to)) {
      setError({ message: 'Chọn đủ thời gian bắt đầu và kết thúc để xuất audit.' }); return;
    }
    return perform(async signal => {
      const result = await mutate(kind, body, key => kind === 'AUDIT'
        ? api.exportAudit(body, key, signal) : api.exportAttendance(body, key, signal));
      if (!signal.aborted) setJobs(previous => [{ ...result, kind }, ...previous.filter(job => job.id !== result.id)]);
    });
  }

  function download(job) {
    return perform(async signal => {
      const blob = await api.exportFile(job.kind, job.id, signal);
      if (signal.aborted) return;
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url; anchor.download = `tmc-${job.id}.${job.format.toLowerCase()}`;
      document.body.appendChild(anchor); anchor.click(); anchor.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    });
  }

  function closePeriod(reopen = false) {
    const body = reopen ? { factoryId, reason, version: Number(version) } : { factoryId, reason };
    return perform(async signal => {
      const result = await mutate(reopen ? 'REOPEN' : 'CLOSE', body, key => reopen
        ? api.reopen(period, body, key, signal) : api.close(period, body, key, signal));
      if (signal.aborted) return;
      if (reopen) { setVersion(result.version); setNotice(`Kỳ đã mở lại, version ${result.version}.`); }
      else { setBatch(result); setBatchId(result.id); setNotice('Đã tiếp nhận yêu cầu chốt kỳ. Theo dõi batch để xem kết quả HR.'); }
    });
  }

  if (!actor) return <section className="t4-reports"><h1>Báo cáo và kiểm toán</h1><p>Cần đăng nhập để sử dụng.</p></section>;
  if (!api) return <section className="t4-reports"><h1>Báo cáo và kiểm toán</h1><p>Dịch vụ báo cáo chưa khả dụng.</p></section>;

  return <section className="t4-reports">
    <header><h1>Báo cáo và kiểm toán</h1><p>Tra cứu công, xuất tệp, theo dõi kỳ Payroll và nhật ký kiểm toán.</p></header>
    <div className="t4-tabs" role="tablist" aria-label="Loại dữ liệu">
      {allowed('report.read') && <button role="tab" aria-selected={tab === 'attendance'} onClick={() => { setTab('attendance'); setError(null); }}>Báo cáo công</button>}
      {(allowed('audit.read') || allowed('audit.export')) && <button role="tab" aria-selected={tab === 'audit'} onClick={() => { setTab('audit'); setError(null); }}>Audit</button>}
      {(actor.roles.includes('HR') || allowed('audit.read')) && <button role="tab" aria-selected={tab === 'payroll'} onClick={() => { setTab('payroll'); setError(null); }}>Kỳ Payroll</button>}
    </div>
    <Failure error={error} />
    {busy && <p role="status">Đang xử lý…</p>}
    {notice && <p className="t4-notice" role="status">{notice}</p>}
    <div className="t4-filters">
      <label>Nhà máy<input value={factoryId} onChange={event => setFactoryId(event.target.value)} list="t4-factories"
        pattern={uuid4} required placeholder="UUID nhà máy" /></label>
      <datalist id="t4-factories">
        {(actor.factoryIds.length ? actor.factoryIds : [DEFAULT_FACTORY_ID]).map(id => <option key={id} value={id} />)}
      </datalist>
      <label>Kỳ xuất<input type="month" value={period} onChange={event => setPeriod(event.target.value)} required /></label>
      <label>Định dạng<select value={format} onChange={event => setFormat(event.target.value)}>
        <option>CSV</option><option>XLSX</option><option>PDF</option>
      </select></label>
    </div>

    {tab === 'attendance' && <div role="tabpanel">
      <form className="t4-filters" onSubmit={event => { event.preventDefault(); loadAttendance(); }}>
        <label>Từ ngày<input type="date" value={from} onChange={event => setFrom(event.target.value)} required /></label>
        <label>Đến trước ngày<input type="date" value={to} onChange={event => setTo(event.target.value)} required /></label>
        <label>Nhân viên<input value={employeeId} onChange={event => setEmployeeId(event.target.value)} pattern={uuid4} placeholder="Tất cả trong phạm vi" /></label>
        <button disabled={busy}>Tra cứu</button>
        {allowed('report.export') && <button type="button" disabled={busy || !factoryId || !period} onClick={() => createExport('ATTENDANCE')}>Xuất kỳ đã chọn</button>}
        <button type="button" disabled={busy || !factoryId || !from} onClick={() => perform(async signal => {
          const result = await api.dashboard(factoryId, from, signal); if (!signal.aborted) setDashboard(result);
        })}>Tải tổng quan ngày bắt đầu</button>
      </form>
      {dashboard && <div className="t4-summary">
        <span>Dự kiến: {dashboard.expectedCount}</span><span>Hiện diện: {dashboard.presentCount}</span>
        <span>Vắng: {dashboard.absentCount}</span><span>Bất thường: {dashboard.anomalyCount}</span>
        <small>Cập nhật {displayTime(dashboard.asOf)} · Edge offline: {dashboard.offlineEdgeIds.length}</small>
      </div>}
      <div className="t4-table"><table><thead><tr><th>Ngày công</th><th>Nhân viên</th><th>Ca</th><th>IN / OUT</th>
        <th>Phút công</th><th>Trễ / Sớm</th><th>Cờ</th></tr></thead><tbody>
        {records.items.map(row => <tr key={`${row.employeeId}-${row.workDate}-${row.shiftId}`}>
          <td>{row.workDate}</td><td className="t4-id">{row.employeeId}</td><td className="t4-id">{row.shiftId || '—'}</td>
          <td>{displayTime(row.firstIn)}<br />{displayTime(row.lastOut)}</td>
          <td>{row.workedMinutes === null ? 'Chưa đủ dữ liệu' : row.workedMinutes}</td>
          <td>{row.lateMinutes} / {row.earlyMinutes}</td><td>{row.flags.join(', ') || '—'}</td>
        </tr>)}</tbody></table></div>
      {!records.items.length && !busy && <p>Chưa có dòng công để hiển thị.</p>}
      <Pager value={records} busy={busy} onPage={loadAttendance} />
    </div>}

    {tab === 'audit' && <div role="tabpanel">
      <form className="t4-filters" onSubmit={event => { event.preventDefault(); loadAudit(); }}>
        <label>Actor<input value={actorId} onChange={event => setActorId(event.target.value)} pattern={uuid4} placeholder="UUID tùy chọn" /></label>
        <label>Hành động<input value={action} onChange={event => setAction(event.target.value)} placeholder="Ví dụ PERIOD_CLOSE" /></label>
        <label>Từ thời điểm<input type="datetime-local" value={auditFrom} onChange={event => setAuditFrom(event.target.value)} /></label>
        <label>Đến trước thời điểm<input type="datetime-local" value={auditTo} onChange={event => setAuditTo(event.target.value)} /></label>
        {allowed('audit.read') && <button disabled={busy}>Tra cứu audit</button>}
        {allowed('audit.export') && <button type="button" disabled={busy || !factoryId} onClick={() => createExport('AUDIT')}>Xuất audit nhà máy đã chọn</button>}
      </form>
      <p>Tra cứu theo phạm vi phiên; bỏ trống thời gian để xem 7 ngày gần nhất. Nhà máy phía trên áp dụng cho yêu cầu xuất tệp.</p>
      <div className="t4-table"><table><thead><tr><th>Thời gian</th><th>Actor / Thiết bị</th><th>Nhà máy</th>
        <th>Hành động</th><th>Tài nguyên</th><th>Kết quả</th><th>Lý do / Correlation</th></tr></thead><tbody>
        {auditRows.items.map(row => <tr key={row.id}><td>{displayTime(row.occurredAt)}</td>
          <td className="t4-id">{row.actorId || row.deviceId || '—'}</td><td className="t4-id">{row.factoryId || '—'}</td>
          <td>{row.action}</td><td className="t4-id">{row.resourceType}<br />{row.resourceId}</td><td>{row.result}</td>
          <td>{row.reason || '—'}<small className="t4-id">{row.correlationId}</small></td></tr>)}</tbody></table></div>
      {!auditRows.items.length && !busy && <p>Chưa có audit để hiển thị.</p>}
      <Pager value={auditRows} busy={busy} onPage={loadAudit} />
    </div>}

    {tab === 'payroll' && <div role="tabpanel">
      <p>Chốt chính thức từ 06:30 ngày 01 theo giờ nhà máy; đối soát HR trước 12:00. Ca đêm thuộc tháng bắt đầu ca.</p>
      {payrollAllowed && <form className="t4-filters" onSubmit={event => { event.preventDefault(); closePeriod(); }}>
        <label>Lý do<input value={reason} onChange={event => setReason(event.target.value)} required /></label>
        <button disabled={busy || !factoryId || !period}>Yêu cầu chốt kỳ</button>
        <label>Version hiện tại<input type="number" min="1" value={version} onChange={event => setVersion(event.target.value)} required /></label>
        <button type="button" disabled={busy || !factoryId || !reason || !period} onClick={() => closePeriod(true)}>Mở lại kỳ</button>
      </form>}
      <form className="t4-filters" onSubmit={event => { event.preventDefault(); perform(async signal => {
        const result = await api.batch(batchId, signal); if (!signal.aborted) setBatch(result);
      }); }}>
        <label>Batch ID<input value={batchId} onChange={event => setBatchId(event.target.value)} pattern={uuid4} required /></label>
        <button disabled={busy || !actor.roles.includes('HR')}>Đọc trạng thái batch</button>
        <button type="button" disabled={busy || !factoryId || !period} onClick={() => loadHistory()}>Xem lịch sử kỳ</button>
      </form>
      {batch && <div className="t4-summary"><span>Trạng thái: {batch.state}</span>
        <span>HR nhận: {batch.acceptedCount} / {batch.recordCount}</span><span>Version kỳ: {batch.periodVersion}</span>
        {batch.errorCode && <span>Lỗi: {batch.errorCode}</span>}<small className="t4-id">Checksum: {batch.checksum}</small></div>}
      <div className="t4-table"><table><thead><tr><th>Thời gian</th><th>Thao tác</th><th>Actor</th><th>Version</th><th>Lý do</th></tr></thead>
        <tbody>{history.items.map(item => <tr key={item.id}><td>{displayTime(item.occurredAt)}</td><td>{item.action}</td>
          <td className="t4-id">{item.actorId || 'Hệ thống'}</td><td>{item.version}</td><td>{item.reason}</td></tr>)}</tbody></table></div>
      {!history.items.length && !busy && <p>Chưa có lịch sử kỳ để hiển thị.</p>}
      <Pager value={history} busy={busy} onPage={loadHistory} />
    </div>}

    {!!jobs.length && <section className="t4-jobs"><h2>Tệp xuất trong phiên này</h2>
      {jobs.map(job => <article key={job.id}><strong>{job.kind === 'AUDIT' ? 'Audit' : 'Báo cáo công'} · {job.format}</strong>
        <span>{job.state}</span><small>Tạo {displayTime(job.createdAt)}{job.expiresAt && ` · Hết hạn ${displayTime(job.expiresAt)}`}</small>
        {job.errorCode && <p role="alert">Không tạo được tệp: {job.errorCode}</p>}<Failure error={job.pollError} />
        {job.pollError && <button type="button" disabled={busy} onClick={() => perform(async signal => {
          const updated = await api.exportStatus(job.kind, job.id, signal);
          if (!signal.aborted) setJobs(previous => previous.map(item => item.id === job.id ? { ...updated, kind: job.kind } : item));
        })}>Đọc lại trạng thái</button>}
        <button type="button" disabled={busy || job.state !== 'READY' || Date.parse(job.expiresAt) <= Date.now()}
          onClick={() => download(job)}>Tải tệp</button>
      </article>)}
    </section>}
  </section>;
}
