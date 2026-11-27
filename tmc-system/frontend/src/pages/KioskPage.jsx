import React, { useEffect, useRef, useState } from 'react';
import { request } from '../api.js';
import { describeError, describeScanResult, newScanId } from '../lib/gateStation.js';
import '../styles/gate-station.css';

/**
 * PHỤ TRÁCH: Task 2. Màn hình Kiosk quét tự động tại cổng (FR-GS-03; UC-02/03; CONTRACT-V1.md §5).
 * - Tự động bật camera ngay khi mở trang, không cần thao tác thủ công.
 * - Nhân viên chỉ cần đưa mã QR vào khung ngắm: tự nhận diện, xác minh và ghi nhận phiên chấm công.
 */
const DEFAULT_GATE_ID = import.meta.env?.VITE_KIOSK_GATE_ID || '00000000-0000-4000-8000-000000000011';
const RESULT_RESET_MS = 4000;
const SCAN_TIMEOUT_MS = 5000;

export default function KioskPage() {
  const [phase, setPhase] = useState('idle'); // idle | sending | result | error
  const [view, setView] = useState(null);
  const [elapsedMs, setElapsedMs] = useState(null);
  const [history, setHistory] = useState([]);
  const [cameraOn, setCameraOn] = useState(false);
  const [cameraError, setCameraError] = useState('');
  const videoRef = useRef(null);
  const streamRef = useRef(null);
  const lastScannedRef = useRef({ token: '', at: 0 });
  const phaseRef = useRef('idle');

  useEffect(() => { phaseRef.current = phase; }, [phase]);

  useEffect(() => {
    if (phase !== 'result' && phase !== 'error') return undefined;
    const timer = setTimeout(() => { setPhase('idle'); setView(null); }, RESULT_RESET_MS);
    return () => clearTimeout(timer);
  }, [phase]);

  function stopCamera() {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
    if (videoRef.current) {
      videoRef.current.srcObject = null;
    }
    setCameraOn(false);
  }

  async function startCamera() {
    setCameraError('');
    if (!navigator.mediaDevices?.getUserMedia) {
      setCameraError('Trình duyệt không hỗ trợ camera (cần HTTPS hoặc localhost).');
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' },
        audio: false,
      });
      streamRef.current = stream;
      setCameraOn(true);
    } catch (err) {
      setCameraError(`Không thể tự động mở camera: ${err?.message || 'chưa cấp quyền webcam'}.`);
    }
  }

  // Tự động bật camera ngay khi mở trang Kiosk
  useEffect(() => {
    startCamera();
    return () => stopCamera();
  }, []);

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
      setElapsedMs(null);
      setView({ kind: 'error', tone: 'danger', ...described });
      setPhase('error');
    }
  }

  // Tự động quét QR liên tục từ camera
  useEffect(() => {
    if (!cameraOn || !streamRef.current || !videoRef.current) return undefined;
    const video = videoRef.current;
    video.srcObject = streamRef.current;
    video.play().catch(() => {});

    if (typeof window.BarcodeDetector !== 'function') {
      setCameraError('Trình duyệt này chưa hỗ trợ tự động đọc QR (BarcodeDetector). Hãy mở bằng Chrome hoặc Edge.');
      return undefined;
    }

    const detector = new window.BarcodeDetector({ formats: ['qr_code'] });
    const interval = setInterval(async () => {
      if (!videoRef.current || videoRef.current.readyState < 2 || phaseRef.current === 'sending') return;
      try {
        const codes = await detector.detect(videoRef.current);
        const raw = codes?.[0]?.rawValue?.trim();
        if (!raw) return;
        const now = Date.now();
        if (lastScannedRef.current.token === raw && now - lastScannedRef.current.at < 3000) return;
        lastScannedRef.current = { token: raw, at: now };
        send({ scanId: newScanId(), token: raw, gateId: DEFAULT_GATE_ID });
      } catch {
        // Bỏ qua lỗi frame đơn lẻ
      }
    }, 250);

    return () => clearInterval(interval);
  }, [cameraOn]);

  const tone = view?.tone || 'idle';
  return <section aria-labelledby="kiosk-title">
    <div className="module-heading">
      <div>
        <h1 id="kiosk-title">Quét tại cổng</h1>
        <p>Đưa mã QR vào trước camera — cổng sẽ tự động xác minh và mở khóa.</p>
      </div>
    </div>

    <div className="gs-panel" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12 }}>
      {cameraOn ? (
        <div className="gs-camera-stage">
          <video ref={videoRef} playsInline muted />
          <div className="gs-camera-reticle" />
        </div>
      ) : (
        <div style={{ textAlign: 'center', padding: '16px 0' }}>
          <p className="gs-hint" role="alert">{cameraError || 'Đang khởi động camera...'}</p>
          {cameraError && (
            <button type="button" onClick={startCamera}>Thử bật lại camera</button>
          )}
        </div>
      )}
    </div>

    <div className={`gs-result gs-result--${phase === 'sending' ? 'idle' : tone}`} role="status" aria-live="assertive">
      {phase === 'sending' && <h2>Đang xác minh mã QR…</h2>}
      {phase === 'idle' && <h2>Sẵn sàng quét — vui lòng đưa mã QR vào khung ngắm</h2>}
      {(phase === 'result' || phase === 'error') && view && <>
        <h2>{view.title}{view.offline && <span className="gs-badge gs-badge--offline">Offline</span>}</h2>
        <p>{view.detail}</p>
        {phase === 'result' && elapsedMs !== null && <p className="gs-meta">Xử lý trong {elapsedMs} ms</p>}
      </>}
    </div>

    {history.length > 0 && (
      <div className="gs-panel">
        <h3>Phiên quét vừa ghi nhận ({history.length})</h3>
        <table className="gs-table">
          <thead>
            <tr>
              <th>Thời gian</th>
              <th>Chiều</th>
              <th>Kết quả</th>
              <th>Chi tiết</th>
              <th>Độ trễ</th>
            </tr>
          </thead>
          <tbody>
            {history.map((item) => (
              <tr key={item.id}>
                <td>{item.time}</td>
                <td><code>{item.direction}</code></td>
                <td>{item.decision}</td>
                <td>{item.detail}</td>
                <td>{item.ms} ms</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    )}
  </section>;
}
