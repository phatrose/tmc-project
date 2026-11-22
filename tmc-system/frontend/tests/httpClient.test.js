import test from 'node:test';
import assert from 'node:assert/strict';
import { createHttpClient, HttpError } from '../src/httpClient.js';
const id = '00000000-0000-4000-8000-000000000041';
const json = (data, status = 200, headers = {}) => new Response(JSON.stringify(data), {
  status, headers: { 'Content-Type': 'application/json', ...headers },
});
// PHỤ TRÁCH: Task 8. Transport mock chỉ được inject trong test.
test('sends bearer, JSON, correlation and explicit stable idempotency key', async () => {
  const request = createHttpClient({ getToken: () => 'opaque', fetchImpl: async (url, options) => {
    assert.equal(url, '/api/v1/employees');
    assert.equal(options.headers.get('Authorization'), 'Bearer opaque');
    assert.equal(options.headers.get('X-Request-Id'), id);
    assert.equal(options.headers.get('Idempotency-Key'), id);
    assert.equal(options.body, '{"name":"A"}');
    assert.equal(options.credentials, 'omit');
    assert.equal(options.redirect, 'error');
    return json({ id });
  }});
  assert.deepEqual(await request('/employees', { method: 'POST', body: { name: 'A' }, requestId: id, idempotencyKey: id }), { id });
});
test('HTTP 200 DENY and mixed ACK remain business results', async () => {
  for (const result of [{ decision: 'DENY' }, { acks: [{ status: 'REJECTED' }] }]) {
    assert.deepEqual(await createHttpClient({ fetchImpl: async () => json(result) })('/example'), result);
  }
});
test('429 preserves retry metadata without automatically repeating mutation', async () => {
  let calls = 0;
  const request = createHttpClient({ fetchImpl: async () => {
    calls++;
    return json({ code: 'RATE_LIMITED', message: 'Wait', requestId: id, retryable: true }, 429, { 'Retry-After': '3' });
  }});
  await assert.rejects(request('/employees', { method: 'POST', body: {}, idempotencyKey: id }), error =>
    error instanceof HttpError && error.status === 429 && error.retryable && error.retryAfter === 3);
  assert.equal(calls, 1);
});
test('mutations require an explicit stable key; login is exempt', async () => {
  const request = createHttpClient({ fetchImpl: async () => json({}) });
  await assert.rejects(request('/employees', { method: 'POST', body: {} }), TypeError);
  assert.deepEqual(await request('/auth/login', { method: 'POST', body: {}, auth: false }), {});
});
test('401 clears only the session used for that request', async () => {
  let token = 'old', clears = 0;
  const make = change => createHttpClient({ getToken: () => token, onUnauthorized: () => clears++, fetchImpl: async () => {
    if (change) token = 'new';
    return json({ code: 'SESSION_EXPIRED', message: 'Login', requestId: id, retryable: false }, 401);
  }});
  await assert.rejects(make(false)('/auth/me'));
  assert.equal(clears, 1);
  await assert.rejects(make(true)('/auth/me'));
  assert.equal(clears, 1);
});
test('logout 204 works and public calls never attach supplied bearer', async () => {
  const request = createHttpClient({ getToken: () => 'private', fetchImpl: async (_, options) => {
    assert.equal(options.headers.has('Authorization'), false);
    return new Response(null, { status: 204 });
  }});
  assert.equal(await request('/auth/logout', { auth: false, headers: { Authorization: 'bad' } }), null);
});
test('invalid HTML response does not expose server text', async () => {
  await assert.rejects(createHttpClient({ fetchImpl: async () => new Response('SECRET <html>', { status: 500 }) })('/bad'),
    error => error.code === 'INVALID_RESPONSE' && !error.message.includes('SECRET'));
});
test('timeout and user cancellation are distinguished, no retries', async () => {
  const fetchImpl = async (_, { signal }) => new Promise((resolve, reject) => {
    if (signal.aborted) reject(signal.reason);
    else signal.addEventListener('abort', () => reject(signal.reason), { once: true });
  });
  const request = createHttpClient({ fetchImpl });
  await assert.rejects(request('/slow', { timeoutMs: 5 }), e => e.code === 'TIMEOUT' && e.retryable);
  const controller = new AbortController(); controller.abort();
  await assert.rejects(request('/slow', { signal: controller.signal }), e => e.code === 'CANCELLED' && !e.retryable);
});
test('rejects path escapes before sending token', async () => {
  let sent = 0;
  const request = createHttpClient({ fetchImpl: async () => { sent++; return json({}); } });
  for (const path of ['https://evil.test', '//evil.test', '/../secret', '/%2e%2e/secret', '/a\\b']) {
    await assert.rejects(request(path), TypeError);
  }
  assert.equal(sent, 0);
});
