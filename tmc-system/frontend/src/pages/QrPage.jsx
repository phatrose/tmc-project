import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { encodeQr } from '../lib/qrEncoder.js';

/**
 * PHỤ TRÁCH: Task 6 — QrPage: QR cá nhân (FR-UQ-02, FR-UQ-03, FR-QR-01/02).
 * HỢP ĐỒNG (CONTRACT-V1.md §3, §4):
 * - GET /api/v1/me/qr-token: Bearer phiên hợp lệ; KHÔNG nhận employeeId từ client;
 *   response {token, expiresAt}; HTTP layer phải gắn Cache-Control: no-store.
 * - QR chỉ hiển thị trên app/web sau đăng nhập; không gửi QR/JWS qua email/SMS
 *   (DECISIONS.md §5). Token phiên khác token QR; không dùng token phiên làm QR.
 * - Mỗi lần cấp có jti mới; TTL 60 giây; expiresAt để FE đếm và cấp lại đúng lúc.
 * - FE không tự ký, không tự quyết quyền mở cổng, không cache token ở storage,
 *   không in/log token; token chỉ giữ trong bộ nhớ React state.
 * PHỐI HỢP: Task 1 (phiên đăng nhập), Task 8 (api.js/session wiring, base URL).
 * Ghi chú: api.js của Task 8 chưa sẵn sàng nên trang tự gọi fetch với shape
 * ApiError {code,message,requestId,retryable}; khi Task 8 hoàn thiện HTTP client,
 * thay hàm fetchQrToken bằng request() mà không đổi UI.
 */

const API_BASE = import.meta.env?.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';
const REFRESH_MARGIN_SECONDS = 10; // cấp lại sớm trước khi hết hạn

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
      // body không phải JSON: giữ ApiError tổng quát
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
      style={{ background: '#ffffff', border: '1px solid #dce2ea', borderRadius: 8, shapeRendering: 'crispEdges' }}
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

  // Bỏ token khỏi bộ nhớ khi rời trang; dừng mọi timer/abort.
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

  // Hết hạn hoặc sắp hết hạn (còn <= REFRESH_MARGIN_SECONDS) thì tự cấp lại.
  useEffect(() => {
    if (state.status !== 'ready' && state.status !== 'refreshing') return;
    if (secondsLeft <= REFRESH_MARGIN_SECONDS) load();
  }, [secondsLeft, state.status, load]);

  if (!accessToken) {
    return (
      <section>
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
    <section>
      <h1>QR cá nhân</h1>
      <p>QR động 60 giây để quét tại cổng. Mỗi lần cấp chỉ dùng được một lần, không chia sẻ ảnh chụp.</p>
      {state.token && (state.status === 'ready' || state.status === 'refreshing') && (
        <>
          <QrCanvas token={state.token} />
          <p>
            Hết hạn lúc <strong>{expiresAtText}</strong>{' '}
            {state.status === 'refreshing' ? '— đang cấp lại…' : `— còn ${secondsLeft} giây`}
          </p>
        </>
      )}
      {state.status === 'loading' && <p className="status">Đang lấy QR…</p>}
      {notice && <p role="alert" className="status">{notice}</p>}
      <button type="button" onClick={load} disabled={state.status === 'loading' || state.status === 'refreshing'}>
        Cấp lại QR ngay
      </button>
    </section>
  );
}
