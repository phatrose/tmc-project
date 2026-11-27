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
    credentials: 'omit',
    redirect: 'error',
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
    const error = new Error('Phản hồi cấp mã QR không đúng định dạng.');
    error.apiError = { code: 'INTERNAL_ERROR', message: 'Phản hồi sai định dạng', requestId, retryable: false };
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
  if (!qr) return <p role="alert">Không tạo được hình mã QR từ dữ liệu nhận về.</p>;
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
  const isExpired = state.status === 'ready' && Boolean(state.expiresAt) && secondsLeft === 0;
  const progressPct = Math.min(100, Math.max(0, (secondsLeft / TTL_SECONDS) * 100));

  useEffect(() => {
    if (state.status !== 'ready' && state.status !== 'refreshing') return;
    if (secondsLeft <= REFRESH_MARGIN_SECONDS) load();
  }, [secondsLeft, state.status, load]);

  if (!accessToken) {
    return (
      <section className="personal-qr-card">
        <h1>QR cá nhân</h1>
        <p className="status">Bạn cần đăng nhập để nhận mã QR động. Hệ thống không gửi QR qua email hoặc SMS.</p>
        <a className="primary-link" href="#/login" style={{ marginTop: 14 }}>Đến trang đăng nhập</a>
      </section>
    );
  }

  const expiresAtText = state.expiresAt
    ? state.expiresAt.toLocaleTimeString('vi-VN', { hour12: false })
    : '';

  let errorDetail = null;
  let needsRelogin = false;
  if (state.error) {
    const code = state.error.apiError?.code;
    if (state.error.httpStatus === 401 || code === 'SESSION_EXPIRED' || code === 'UNAUTHENTICATED') {
      needsRelogin = true;
      errorDetail = {
        title: 'Phiên đăng nhập đã hết hạn',
        guide: 'Vui lòng đăng nhập lại để hệ thống cấp mã QR động mới.',
      };
    } else if (state.error.httpStatus === 409 || code === 'INVALID_STATE') {
      errorDetail = {
        title: 'Tài khoản chưa được cấp quyền QR đang hoạt động',
        guide: 'Liên hệ phòng Nhân sự (HR) để kích hoạt thông tin QR cho tài khoản của bạn, sau đó bấm Thử lại.',
      };
    } else if (code === 'RATE_LIMITED' || state.error.apiError?.retryable) {
      errorDetail = {
        title: 'Hệ thống đang bận hoặc kết nối tạm gián đoạn',
        guide: `Chờ vài giây rồi bấm "Thử lại" để lấy mã QR mới. (Mã lỗi: ${code ?? 'NETWORK'})`,
      };
    } else {
      errorDetail = {
        title: 'Không thể lấy mã QR lúc này',
        guide: `Vui lòng bấm "Thử lại" hoặc báo quản trị viên kèm mã tra cứu: ${state.error.apiError?.requestId ?? '—'}.`,
      };
    }
  }

  const badge = (() => {
    if (state.status === 'error') return { cls: 'tmc-badge--danger', text: 'Lấy mã thất bại' };
    if (isExpired) return { cls: 'tmc-badge--warning', text: 'Mã đã hết hạn' };
    if (state.status === 'ready') return { cls: 'tmc-badge--success', text: 'Đang hoạt động' };
    if (state.status === 'refreshing') return { cls: 'tmc-badge--warning', text: 'Đang cấp mã mới…' };
    return { cls: 'tmc-badge--neutral', text: 'Đang tải mã…' };
  })();

  return (
    <section className="personal-qr-card">
      <div style={{ display: 'flex', justifyContent: 'center', gap: 8, marginBottom: 10 }}>
        <span className="tmc-badge tmc-badge--accent">QR động ES256</span>
        <span className={`tmc-badge ${badge.cls}`}>{badge.text}</span>
      </div>

      <h1>QR cá nhân</h1>
      <p>Mã QR có hiệu lực 60 giây, chỉ dùng một lần tại cổng và tự làm mới trước khi hết hạn. Không chia sẻ ảnh chụp màn hình.</p>

      {state.status === 'loading' && (
        <div className="ops-empty" aria-busy="true">
          <p>Đang kết nối máy chủ để cấp mã QR động…</p>
        </div>
      )}

      {state.token && (state.status === 'ready' || state.status === 'refreshing') && !isExpired && (
        <>
          <div className="qr-frame">
            <QrCanvas token={state.token} />
          </div>
          <div className="qr-progress-track" aria-hidden="true">
            <div className="qr-progress-bar" style={{ width: `${progressPct}%` }} />
          </div>
          <p className="qr-meta">
            Hết hạn lúc <strong>{expiresAtText}</strong>{' '}
            {state.status === 'refreshing' ? '— đang tự động cấp mã mới…' : `— còn ${secondsLeft} giây`}
          </p>
        </>
      )}

      {isExpired && (
        <div role="status" className="notice" style={{ marginTop: 18, textAlign: 'left' }}>
          <strong>Mã QR đã hết hiệu lực</strong>
          <p>Bấm “Cấp lại QR ngay” bên dưới để tạo mã mới trước khi đưa vào đầu đọc tại cổng.</p>
        </div>
      )}

      {errorDetail && (
        <div role="alert" className="notice error" style={{ marginTop: 18, textAlign: 'left' }}>
          <strong>{errorDetail.title}</strong>
          <p>{errorDetail.guide}</p>
        </div>
      )}

      <div style={{ display: 'flex', justifyContent: 'center', gap: 10, marginTop: 16, flexWrap: 'wrap' }}>
        {needsRelogin ? (
          <a className="primary-link" href="#/login">Đăng nhập lại</a>
        ) : (
          <button
            type="button"
            className="tmc-btn-primary"
            onClick={load}
            disabled={state.status === 'loading' || state.status === 'refreshing'}
          >
            {state.status === 'error' ? 'Thử lại' : 'Cấp lại QR ngay'}
          </button>
        )}
      </div>
    </section>
  );
}
