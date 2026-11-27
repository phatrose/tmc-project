import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { encodeQr } from '../lib/qrEncoder.js';

/**
 * PHỤ TRÁCH: Task 6 — QrPage: QR cá nhân (FR-UQ-02, FR-UQ-03, FR-QR-01/02).
 * GET /api/v1/me/qr-token: Bearer phiên hợp lệ; Cache-Control: no-store.
 * Token chỉ giữ trong bộ nhớ React state; không cache storage, không in/log token.
 */

const API_BASE = import.meta.env?.VITE_API_BASE_URL ?? '/api/v1';
const REFRESH_MARGIN_SECONDS = 10;
const TTL_SECONDS = 60;

async function fetchQrToken(accessToken) {
  const requestId = crypto.randomUUID();
  const response = await fetch(`${API_BASE}/me/qr-token`, {
    method: 'GET',
    cache: 'no-store',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: 'application/json',
      'X-Request-Id': requestId,
    },
  });
  if (!response.ok) {
    let apiError = { code: 'INTERNAL_ERROR', message: 'Lỗi không xác định', requestId, retryable: true };
    try {
      const body = await response.json();
      if (body && typeof body.code === 'string') apiError = body;
    } catch {
      // giữ ApiError tổng quát
    }
    const error = new Error(apiError.message || 'Không lấy được QR');
    error.apiError = apiError;
    error.httpStatus = response.status;
    throw error;
  }
  const body = await response.json();
  if (typeof body?.token !== 'string' || typeof body?.expiresAt !== 'string') {
    const error = new Error('Response /me/qr-token sai contract');
    error.apiError = { code: 'INTERNAL_ERROR', message: 'Response sai contract', requestId, retryable: false };
    throw error;
  }
  return { token: body.token, expiresAt: new Date(body.expiresAt) };
}

function QrCanvas({ token }) {
  const qr = useMemo(() => {
    try {
      return encodeQr(token, { ecLevel: 'M' });
    } catch {
      return null;
    }
  }, [token]);
  if (!qr) return <p role="alert">Không tạo được ảnh QR từ token.</p>;
  const quiet = 4;
  const paths = [];
  for (let r = 0; r < qr.size; r++) {
    for (let c = 0; c < qr.size; c++) {
      if (qr.modules[r * qr.size + c]) {
        paths.push(`M${c + quiet} ${r + quiet}h1v1h-1z`);
      }
    }
  }
  const dimension = qr.size + 2 * quiet;
  return (
    <svg
      role="img"
      aria-label="Mã QR cá nhân đang hoạt động, quét tại cổng để chấm công"
      width="240"
      height="240"
      viewBox={`0 0 ${dimension} ${dimension}`}
      style={{ background: '#ffffff', shapeRendering: 'crispEdges' }}
    >
      <rect width={dimension} height={dimension} fill="#ffffff" />
      <path d={paths.join('')} fill="#000000" />
    </svg>
  );
}

export default function QrPage({ session }) {
  const accessToken = session?.accessToken;
  const [state, setState] = useState({ status: 'idle', token: null, expiresAt: null, error: null });
  const [now, setNow] = useState(() => Date.now());
  const inFlight = useRef(false);

  const load = useCallback(async () => {
    if (!accessToken || inFlight.current) return;
    inFlight.current = true;
    setState((prev) => ({ ...prev, status: prev.token ? 'refreshing' : 'loading', error: null }));
    try {
      const { token, expiresAt } = await fetchQrToken(accessToken);
      setState({ status: 'ready', token, expiresAt, error: null });
    } catch (error) {
      setState({ status: 'error', token: null, expiresAt: null, error });
    } finally {
      inFlight.current = false;
    }
  }, [accessToken]);

  useEffect(() => {
    if (!accessToken) return undefined;
    load();
    const tick = setInterval(() => setNow(Date.now()), 1000);
    const onVisible = () => {
      if (document.visibilityState === 'visible') setNow(Date.now());
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      clearInterval(tick);
      document.removeEventListener('visibilitychange', onVisible);
      inFlight.current = false;
      setState({ status: 'idle', token: null, expiresAt: null, error: null });
    };
  }, [accessToken, load]);

  const secondsLeft = state.expiresAt ? Math.max(0, Math.round((state.expiresAt.getTime() - now) / 1000)) : 0;
  const progressPct = Math.min(100, Math.max(0, (secondsLeft / TTL_SECONDS) * 100));

  useEffect(() => {
    if (state.status !== 'ready' && state.status !== 'refreshing') return;
    if (secondsLeft <= REFRESH_MARGIN_SECONDS) load();
  }, [secondsLeft, state.status, load]);

  if (!accessToken) {
    return (
      <section className="personal-qr-card">
        <span className="module-label">Thẻ chấm công điện tử</span>
        <h1>QR cá nhân</h1>
        <p className="status">Cần đăng nhập để hiển thị QR động. QR không gửi qua email/SMS.</p>
      </section>
    );
  }

  const expiresAtText = state.expiresAt
    ? state.expiresAt.toLocaleTimeString('vi-VN', { hour12: false })
    : '';
  let notice = null;
  if (state.error) {
    const code = state.error.apiError?.code;
    if (state.error.httpStatus === 401 || code === 'SESSION_EXPIRED' || code === 'UNAUTHENTICATED') {
      notice = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
    } else if (state.error.httpStatus === 409 || code === 'INVALID_STATE') {
      notice = 'Tài khoản chưa có credential QR ACTIVE. Vui lòng liên hệ HR để được cấp credential.';
    } else if (code === 'RATE_LIMITED' || state.error.apiError?.retryable) {
      notice = 'Hệ thống bận, đang thử lại. (Mã lỗi: ' + (code ?? '—') + ')';
    } else {
      notice = 'Không lấy được QR. (Mã lỗi: ' + (code ?? '—') + ' · requestId: ' + (state.error.apiError?.requestId ?? '—') + ')';
    }
  }

  return (
    <section className="personal-qr-card">
      <div style={{ display: 'flex', justifyContent: 'center', gap: 8, marginBottom: 8 }}>
        <span className="tmc-badge tmc-badge--accent">ES256 Dynamic QR</span>
        <span className={`tmc-badge ${state.status === 'ready' ? 'tmc-badge--success' : 'tmc-badge--warning'}`}>
          {state.status === 'ready' ? 'Đang hoạt động' : state.status === 'refreshing' ? 'Đang làm mới…' : 'Đang tải…'}
        </span>
      </div>

      <h1>QR cá nhân</h1>
      <p>Mã QR động có hiệu lực 60 giây, chỉ dùng một lần tại cổng. Không chia sẻ ảnh chụp màn hình.</p>

      {state.token && (state.status === 'ready' || state.status === 'refreshing') && (
        <>
          <div className="qr-frame">
            <QrCanvas token={state.token} />
          </div>
          <div className="qr-progress-track" aria-hidden="true">
            <div className="qr-progress-bar" style={{ width: `${progressPct}%` }} />
          </div>
          <p className="qr-meta">
            Hết hạn lúc <strong>{expiresAtText}</strong>{' '}
            {state.status === 'refreshing' ? '— đang cấp lại…' : `— còn ${secondsLeft} giây`}
          </p>
        </>
      )}

      {state.status === 'loading' && <p className="status" style={{ margin: '28px 0' }}>Đang lấy mã QR…</p>}
      {notice && <div role="alert" className="notice error" style={{ marginTop: 18 }}>{notice}</div>}

      <button
        type="button"
        className="tmc-btn-primary"
        onClick={load}
        disabled={state.status === 'loading' || state.status === 'refreshing'}
      >
        Cấp lại QR ngay
      </button>
    </section>
  );
}
