import React, { useEffect, useRef, useState } from 'react';
import { request } from '../api.js';
import { describeError, describeScanResult, newScanId } from '../lib/gateStation.js';
import '../styles/gate-station.css';

/**
 * PHỤ TRÁCH: Task 2. Màn hình Kiosk quét tự động tại cổng (FR-GS-03; UC-02/03; CONTRACT-V1.md §5).
 * - Tự động khởi động camera ngay khi mở trang.
 * - Phân biệt rõ: đang khởi động, bị từ chối quyền camera, không có/lỗi thiết bị, sẵn sàng quét, thành công và thất bại.
 */
const DEFAULT_GATE_ID = import.meta.env?.VITE_KIOSK_GATE_ID || '00000000-0000-4000-8000-000000000011';
const RESULT_RESET_MS = 4000;
const SCAN_TIMEOUT_MS = 5000;

function describeCameraError(err) {
  const name = err?.name || '';
  if (name === 'NotAllowedError' || name === 'PermissionDeniedError' || name === 'SecurityError') {
    return {
      kind: 'denied',
      title: 'Bị từ chối quyền truy cập camera',
      detail: 'Trình duyệt đang chặn quyền dùng camera. Hãy bấm biểu tượng ổ khóa trên thanh địa chỉ, cho phép quyền Camera rồi bấm "Thử bật lại camera".',
    };
  }
  if (name === 'NotFoundError' || name === 'DevicesNotFoundError' || name === 'OverconstrainedError') {
    return {
      kind: 'device',
      title: 'Không tìm thấy thiết bị camera',
      detail: 'Không phát hiện webcam hoặc đầu đọc hình ảnh trên máy này. Kiểm tra lại kết nối thiết bị rồi bấm "Thử bật lại camera".',
    };
  }
  if (name === 'NotReadableError' || name === 'TrackStartError' || name === 'AbortError') {
    return {
      kind: 'device',
      title: 'Camera đang bận hoặc gặp lỗi thiết bị',
      detail: 'Camera có thể đang được ứng dụng khác sử dụng. Hãy đóng ứng dụng đang dùng camera rồi thử lại.',
    };
  }
  return {
    kind: 'device',
    title: 'Không thể khởi động camera',
    detail: 'Thiết bị chưa sẵn sàng hoặc trình duyệt không hỗ trợ truy cập camera trên địa chỉ hiện tại.',
  };
}

export default function KioskPage() {
  const [phase, setPhase] = useState('idle'); // idle | sending | result | error
  const [view, setView] = useState(null);
  const [elapsedMs, setElapsedMs] = useState(null);
  const [history, setHistory] = useState([]);
  const [cameraState, setCameraState] = useState('starting'); // starting | ready | denied | device | unsupported
  const [cameraNotice, setCameraNotice] = useState(null);
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
  }

  async function startCamera() {
    stopCamera();
    setCameraState('starting');
    setCameraNotice(null);
    if (!navigator.mediaDevices?.getUserMedia) {
      setCameraState('unsupported');
      setCameraNotice({
        title: 'Trình duyệt chưa hỗ trợ truy cập camera',
        detail: 'Cần mở trang qua HTTPS hoặc localhost trên trình duyệt hiện đại (Chrome, Edge) để dùng camera quét QR.',
      });
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' },
        audio: false,
      });
      streamRef.current = stream;
      setCameraState('ready');
    } catch (err) {
      const info = describeCameraError(err);
      setCameraState(info.kind);
      setCameraNotice({ title: info.title, detail: info.detail });
    }
  }

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
          time: new Date().toLocaleTimeString('vi-VN', { hour12: false }),
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

  useEffect(() => {
    if (cameraState !== 'ready' || !streamRef.current || !videoRef.current) return undefined;
    const video = videoRef.current;
    video.srcObject = streamRef.current;
    video.play().catch(() => {});

    if (typeof window.BarcodeDetector !== 'function') {
      setCameraNotice({
        title: 'Trình duyệt chưa hỗ trợ tự động đọc mã QR',
        detail: 'Camera đã bật nhưng trình duyệt hiện tại không có bộ giải mã BarcodeDetector. Hãy mở bằng Chrome hoặc Edge phiên bản mới.',
      });
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
        // Bỏ qua lỗi đọc khung hình đơn lẻ
      }
    }, 250);

    return () => clearInterval(interval);
  }, [cameraState]);

  const tone = view?.tone || 'idle';
  const cameraBadge = (() => {
    if (cameraState === 'ready') return { cls: 'tmc-badge--success', text: 'Camera sẵn sàng' };
    if (cameraState === 'starting') return { cls: 'tmc-badge--warning', text: 'Đang khởi động camera…' };
    if (cameraState === 'denied') return { cls: 'tmc-badge--danger', text: 'Chưa cấp quyền camera' };
    return { cls: 'tmc-badge--danger', text: 'Lỗi thiết bị camera' };
  })();

  return (
    <section aria-labelledby="kiosk-title">
      <div className="module-heading">
        <div>
          <h1 id="kiosk-title">Quét tại cổng</h1>
          <p>Đưa mã QR động vào khung ngắm camera để xác minh và ghi nhận lượt vào/ra.</p>
        </div>
        <span className={`tmc-badge ${cameraBadge.cls}`}>{cameraBadge.text}</span>
      </div>

      <div className="gs-panel" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 14 }}>
        {cameraState === 'ready' ? (
          <div className="gs-camera-stage">
            <video ref={videoRef} playsInline muted />
            <div className="gs-camera-reticle" />
          </div>
        ) : cameraState === 'starting' ? (
          <div className="ops-empty" aria-busy="true">
            <p>Đang khởi động camera của thiết bị…</p>
          </div>
        ) : (
          <div className="notice error" role="alert" style={{ width: '100%', margin: 0 }}>
            <strong>{cameraNotice?.title || 'Camera chưa sẵn sàng'}</strong>
            <p>{cameraNotice?.detail}</p>
            <button type="button" className="tmc-btn-primary" onClick={startCamera} style={{ marginTop: 12 }}>
              Thử bật lại camera
            </button>
          </div>
        )}
        {cameraState === 'ready' && cameraNotice && (
          <div className="notice" role="status" style={{ width: '100%', margin: 0 }}>
            <strong>{cameraNotice.title}</strong>
            <p>{cameraNotice.detail}</p>
          </div>
        )}
      </div>

      <div className={`gs-result gs-result--${phase === 'sending' ? 'idle' : tone}`} role="status" aria-live="assertive">
        {phase === 'sending' && <h2>Đang xác minh mã QR…</h2>}
        {phase === 'idle' && (
          <>
            <h2>{cameraState === 'ready' ? 'Sẵn sàng quét' : 'Đang chờ camera sẵn sàng'}</h2>
            <p>{cameraState === 'ready' ? 'Đưa mã QR trên màn hình điện thoại vào giữa khung ngắm.' : 'Cấp quyền hoặc kiểm tra thiết bị camera phía trên để bắt đầu quét.'}</p>
          </>
        )}
        {(phase === 'result' || phase === 'error') && view && (
          <>
            <h2>{view.title}{view.offline && <span className="gs-badge gs-badge--offline">Offline</span>}</h2>
            <p>{view.detail}</p>
            {phase === 'result' && elapsedMs !== null && <p className="gs-meta">Thời gian xử lý: {elapsedMs} ms</p>}
          </>
        )}
      </div>

      {history.length > 0 && (
        <div className="gs-panel">
          <h3>Phiên quét vừa ghi nhận ({history.length})</h3>
          <div className="hc-table-wrap">
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
                    <td className="mono">{item.time}</td>
                    <td><code>{item.direction}</code></td>
                    <td>{item.decision === 'ALLOW' ? 'Cho phép (ALLOW)' : item.decision === 'DENY' ? 'Từ chối (DENY)' : item.decision}</td>
                    <td>{item.detail}</td>
                    <td className="mono">{item.ms} ms</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}
