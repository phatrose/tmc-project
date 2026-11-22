import React, { useEffect, useRef, useState } from 'react';
import { createHttpClient } from '../httpClient.js';
import { EDGE_BASE_URL, describeError, describeScanResult, isUuid, newScanId } from '../lib/gateStation.js';
import '../styles/gate-station.css';

/**
 * PHỤ TRÁCH: Task 2. Màn hình quét tại cổng (FR-GS-03; UC-02/03; CONTRACT-V1.md §5).
 * - Đầu đọc QR hoạt động như bàn phím: gõ token rồi Enter vào ô nhập được focus sẵn.
 * - Gọi Edge LAN POST /scans bằng mTLS của kiosk (chứng chỉ nằm ở trình duyệt/thiết bị, không phải token người dùng).
 * - Phân biệt ba loại kết quả: ALLOW (xem commandStatus, KHÔNG báo "đã qua cổng"), DENY (kết quả nghiệp vụ HTTP 200)
 *   và lỗi HTTP (retry cùng scanId vì kết quả có thể đã được lưu).
 * - Token chỉ nằm trong ref khi cần retry và bị xóa sau kết quả cuối; không log, không lưu trữ, không hiển thị.
 */
const edge = createHttpClient({ baseUrl: EDGE_BASE_URL, auth: false });
const RESULT_RESET_MS = 5000;
const SCAN_TIMEOUT_MS = 5000;

export default function KioskPage() {
  const [gateId, setGateId] = useState(import.meta.env?.VITE_KIOSK_GATE_ID || '');
  const [phase, setPhase] = useState('idle'); // idle | sending | result | error
  const [view, setView] = useState(null);
  const [elapsedMs, setElapsedMs] = useState(null);
  const pending = useRef(null); // { scanId, token, gateId } chỉ giữ khi lỗi tạm thời cho phép retry
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
      const result = await edge('/scans', { method: 'POST', timeoutMs: SCAN_TIMEOUT_MS,
        body: { scanId: attempt.scanId, gateId: attempt.gateId, token: attempt.token } });
      pending.current = null;
      setElapsedMs(Math.round(performance.now() - started));
      setView({ kind: 'scan', ...describeScanResult(result) });
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

  function dismiss() { pending.current = null; setView(null); setPhase('idle'); }

  const tone = view?.tone || 'idle';
  return <section aria-labelledby="kiosk-title">
    <h1 id="kiosk-title">Quét tại cổng</h1>
    {!gateValid && <div className="gs-panel">
      <div className="gs-field">
        <label htmlFor="gate-id">Mã cổng của kiosk này (UUID)</label>
        <input id="gate-id" value={gateId} onChange={event => setGateId(event.target.value.trim())}
          autoComplete="off" spellCheck="false" aria-invalid={gateId !== ''} />
        <p className="gs-hint">Cấu hình một lần theo cổng được cấp cho kiosk, hoặc đặt biến VITE_KIOSK_GATE_ID.</p>
      </div>
    </div>}
    <form onSubmit={onSubmit} className="gs-panel">
      <div className="gs-field">
        <label htmlFor="qr-input">Đưa mã QR vào đầu đọc</label>
        <input id="qr-input" ref={input} type="password" autoComplete="off" spellCheck="false"
          disabled={!gateValid || phase === 'sending'} aria-describedby="qr-hint" />
        <p id="qr-hint" className="gs-hint">Đầu đọc sẽ tự nhập mã và nhấn Enter.</p>
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
  </section>;
}
