// PHỤ TRÁCH: Task 8. Transport §2/3; không tự retry hoặc diễn giải DENY/ACK như HTTP error.
const uuid4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
export class HttpError extends Error {
  constructor({ code, message, requestId, retryable = false, status = 0, retryAfter = null }) {
    super(message);
    this.name = 'HttpError';
    Object.assign(this, { code, requestId, retryable, status, retryAfter });
  }
}
export function createHttpClient({ baseUrl = '/api/v1', fetchImpl = globalThis.fetch,
  getToken = () => null, onUnauthorized = () => {}, newId = () => crypto.randomUUID(),
  credentials = 'omit' } = {}) {
  const base = baseUrl.replace(/\/+$/, '');
  if (!base || (!base.startsWith('/') && !/^https?:\/\//.test(base))) throw new TypeError('Invalid API base URL');
  return async function request(path, { method = 'GET', body, headers: suppliedHeaders, signal,
    timeoutMs = 15000, auth = true, idempotencyKey, requestId = newId() } = {}) {
    // Chỉ endpoint tương đối: ngăn gửi bearer sang URL do caller chèn vào.
    if (typeof path !== 'string' || !path.startsWith('/') || path.startsWith('//') ||
        /[\\#]/.test(path) || /(?:^|\/)\.\.(?:\/|$)/.test(path.split('?')[0]) || /%2e|%2f|%5c/i.test(path.split('?')[0])) {
      throw new TypeError('Expected an API-relative path');
    }
    if (!uuid4.test(requestId)) throw new TypeError('Invalid requestId');
    if (!Number.isFinite(timeoutMs) || timeoutMs <= 0) throw new TypeError('Invalid timeout');
    const headers = new Headers(suppliedHeaders);
    headers.delete('Authorization');
    headers.set('Accept', 'application/json');
    headers.set('X-Request-Id', requestId);
    if (idempotencyKey !== undefined) {
      if (!uuid4.test(idempotencyKey)) throw new TypeError('Invalid idempotencyKey');
      headers.set('Idempotency-Key', idempotencyKey);
    }
    const postWithoutKey = ['/auth/login', '/auth/action-grants', '/scans', '/attendance/sync', '/edge/heartbeat'];
    if (method.toUpperCase() === 'POST' && !postWithoutKey.includes(path.split('?')[0]) &&
        !uuid4.test(headers.get('Idempotency-Key') || '')) {
      throw new TypeError('Mutation requires a stable UUIDv4 idempotencyKey');
    }
    const payload = body === undefined ? undefined : JSON.stringify(body);
    const token = auth ? getToken() : null;
    if (token) headers.set('Authorization', `Bearer ${token}`);
    if (body !== undefined) headers.set('Content-Type', 'application/json');
    const controller = new AbortController();
    const cancel = () => controller.abort(signal?.reason);
    if (signal?.aborted) cancel();
    else signal?.addEventListener('abort', cancel, { once: true });
    let timedOut = false;
    const timer = setTimeout(() => { timedOut = true; controller.abort(); }, timeoutMs);
    try {
      const response = await fetchImpl(base + path, {
        method: method.toUpperCase(), headers, signal: controller.signal,
        body: payload,
        credentials, redirect: 'error', cache: 'no-store',
      });
      // A late 401 from an old session must not erase a newly logged-in session.
      if (response.status === 401 && ((token && getToken() === token) || (auth && credentials === 'same-origin' && !token))) {
        onUnauthorized();
      }
      const responseId = response.headers.get('X-Request-Id') || requestId;
      const text = await response.text();
      let data;
      try { data = text ? JSON.parse(text) : null; } catch {
        throw new HttpError({ code: 'INVALID_RESPONSE', message: 'Máy chủ trả dữ liệu không hợp lệ.',
          requestId: responseId, status: response.status });
      }
      if (!response.ok) {
        const valid = data && typeof data.code === 'string' && typeof data.message === 'string' &&
          typeof data.requestId === 'string' && typeof data.retryable === 'boolean';
        const retrySeconds = response.headers.get('Retry-After');
        throw new HttpError({
          code: valid ? data.code : 'INVALID_RESPONSE',
          message: valid ? data.message : 'Không thể xử lý phản hồi máy chủ.',
          requestId: valid ? data.requestId : responseId,
          retryable: valid && data.retryable, status: response.status,
          retryAfter: retrySeconds && /^\d+$/.test(retrySeconds) ? Number(retrySeconds) : null,
        });
      }
      return response.status === 204 ? null : data;
    } catch (error) {
      if (error instanceof HttpError) throw error;
      if (signal?.aborted) throw new HttpError({ code: 'CANCELLED', message: 'Đã hủy yêu cầu.', requestId });
      if (timedOut) throw new HttpError({ code: 'TIMEOUT', message: 'Yêu cầu quá thời gian chờ.', requestId, retryable: true });
      throw new HttpError({ code: 'NETWORK_ERROR', message: 'Không kết nối được máy chủ.', requestId, retryable: true });
    } finally {
      clearTimeout(timer);
      signal?.removeEventListener('abort', cancel);
    }
  };
}
