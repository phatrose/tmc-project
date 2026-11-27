import React, { useEffect, useRef, useState } from 'react';
import { request } from '../api.js';
import { describeError, describeScanResult, isUuid, newScanId } from '../lib/gateStation.js';
import '../styles/gate-station.css';

/**
 * PHỤ TRÁCH: Task 2. Màn hình quét tại cổng (FR-GS-03; UC-02/03; CONTRACT-V1.md §5).
 * - Đầu đọc QR hoạt động như bàn phím: gõ token rồi Enter vào ô nhập được focus sẵn, hoặc bấm nút Quét.
 * - Trong môi trường preview, gọi POST /api/v1/preview/scans để xác minh QR và ghi nhận sự kiện chấm công thật.
 */
const DEFAULT_GATE_ID = import.meta.env?.VITE_KIOSK_GATE_ID || '00000000-0000-4000-8000-000000000011';
const RESULT_RESET_MS = 6000;
const SCAN_TIMEOUT_MS = 5000;

export default function KioskPage({ session } = {}) {
  const [gateId, setGateId] = useState(DEFAULT_GATE_ID);
  const [phase, setPhase] = useState('idle'); // idle | sending | result | error
  const [view, setView] = useState(null);
  const [elapsedMs, setElapsedMs] = useState(null);
  const [history, setHistory] = useState([]);
  const pending = useRef(null);
  const input = useRef(null);
  const gateValid = isUuid(gateId);

  useEffect(() => { if (phase === 'idle') input.current?.focus(); }, [phase]);
  useEffect(() => {
    if (phase !== 'result') return undefined;
    const timer = setTimeout(() => { setPhase('idle'); setView(null); }, RESULT_RESET_MS);
    return () => clearTimeout(timer);
  }, [phase]);

  async function send(attempt) {
    setPhase('sending');
    const started = performance.now();
    try {
      const result = await request('/preview/scans', {
        method: 'POST',
        timeoutMs: SCAN_TIMEOUT_MS,
        idempotencyKey: attempt.scanId,
        body: { scanId: attempt.scanId, gateId: attempt.gateId, token: attempt.token },
      });
      pending.current = null;
      const ms = Math.round(performance.now() - started);
      setElapsedMs(ms);
      const described = describeScanResult(result);
      setView({ kind: 'scan', ...described });
      setHistory((prev) => [
        {
          id: result.eventId || attempt.scanId,
          time: new Date().toLocaleTimeString('vi-VN'),
          decision: result.decision,
          direction: result.direction || '—',
          detail: described.title,
          ms,
        },
        ...prev.slice(0, 9),
      ]);
      setPhase('result');
    } catch (error) {
      const described = describeError(error);
      pending.current = described.retryable ? attempt : null;
      setElapsedMs(null);
      setView({ kind: 'error', tone: 'danger', ...described });
      setPhase('error');
    }
  }

  function onSubmit(event) {
    event.preventDefault();
    const token = input.current?.value.trim();
    if (input.current) input.current.value = '';
    if (!token || !gateValid || phase === 'sending') return;
    send({ scanId: newScanId(), token, gateId });
  }

  async function scanMyQrNow() {
    if (!gateValid || phase === 'sending') return;
    setPhase('sending');
    try {
      const issued = await request('/qr/issue', { method: 'POST', idempotencyKey: newScanId(), body: {} });
      await send({ scanId: newScanId(), token: issued.token, gateId });
    } catch (error) {
      const described = describeError(error);
      setView({ kind: 'error', tone: 'danger', ...described });
      setPhase('error');
    }
  }

  function dismiss() { pending.current = null; setView(null); setPhase('idle'); }

  const tone = view?.tone || 'idle';
  return <section aria-labelledby="kiosk-title">
    <div className="module-heading">
      <span className="module-label">TRẠM CHẤM CÔNG</span>
      <h1 id="kiosk-title">Quét tại cổng</h1>
      <p>Đưa mã QR vào đầu đọc hoặc bấm nút quét thử bên dưới để ghi nhận phiên chấm công.</p>
    </div>

    <div className="gs-panel">
      <div className="gs-field">
        <label htmlFor="gate-id">Mã cổng của kiosk này (UUID)</label>
        <input id="gate-id" value={gateId} onChange={event => setGateId(event.target.value.trim())}
          autoComplete="off" spellCheck="false" aria-invalid={!gateValid} />
        <p className="gs-hint">Đã điền sẵn mã cổng mặc định. Bạn có thể đổi theo cổng được cấp cho kiosk.</p>
      </div>
    </div>

    <form onSubmit={onSubmit} className="gs-panel">
      <div className="gs-field">
        <label htmlFor="qr-input">Đưa mã QR vào đầu đọc</label>
        <input id="qr-input" ref={input} type="password" autoComplete="off" spellCheck="false"
          disabled={!gateValid || phase === 'sending'} aria-describedby="qr-hint" />
        <p id="qr-hint" className="gs-hint">Đầu đọc sẽ tự nhập mã và nhấn Enter, hoặc dán mã QR rồi bấm Quét.</p>
      </div>
      <div className="gs-row">
        <button type="submit" disabled={!gateValid || phase === 'sending'}>Quét mã QR</button>
        {session && (
          <button type="button" onClick={scanMyQrNow} disabled={!gateValid || phase === 'sending'}>
            Lấy &amp; quét QR của tôi ngay
          </button>
        )}
      </div>
    </form>

    <div className={`gs-result gs-result--${phase === 'sending' ? 'idle' : tone}`} role="status" aria-live="assertive">
      {phase === 'sending' && <h2>Đang xử lý…</h2>}
      {phase === 'idle' && <h2>Sẵn sàng quét</h2>}
      {(phase === 'result' || phase === 'error') && view && <>
        <h2>{view.title}{view.offline && <span className="gs-badge gs-badge--offline">Offline</span>}</h2>
        <p>{view.detail}</p>
        {phase === 'result' && elapsedMs !== null && <p className="gs-meta">Xử lý trong {elapsedMs} ms</p>}
        {phase === 'error' && <div className="gs-row">
          {view.retryable && pending.current && <button type="button" onClick={() => send(pending.current)}>
            Thử lại cùng lượt quét</button>}
          <button type="button" onClick={dismiss}>Đóng</button>
        </div>}
      </>}
    </div>

    {history.length > 0 && (
      <div className="gs-panel">
        <h3>Phiên quét vừa ghi nhận ({history.length})</h3>
        <ul>
          {history.map((item) => (
            <li key={item.id}>
              <strong>{item.time}</strong> · <code>{item.direction}</code> · {item.decision} — {item.detail} ({item.ms} ms)
            </li>
          ))}
        </ul>
      </div>
    )}
  </section>;
}
