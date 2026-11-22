// PHỤ TRÁCH: Task 8. Kiểm tra HTTP thật qua nginx bằng chính client frontend.
import assert from 'node:assert/strict';
import { createHttpClient, HttpError } from '../frontend/src/httpClient.js';

const central = process.env.CENTRAL_URL || 'http://127.0.0.1:8080';
const frontend = process.env.FRONTEND_URL || 'http://127.0.0.1:5173';
const id = '00000000-0000-4000-8000-000000000041';
const health = await fetch(`${central}/actuator/health`, {
  headers: { 'X-Request-Id': id }, signal: AbortSignal.timeout(10000),
});
assert.equal(health.status, 200);
assert.equal(health.headers.get('X-Request-Id'), id);
assert.equal((await health.json()).status, 'UP');
const page = await fetch(frontend, { signal: AbortSignal.timeout(10000) });
assert.equal(page.status, 200);
const html = await page.text();
assert.match(html, /id="root"/);
const asset = html.match(/src="([^"]+\.js)"/);
assert.ok(asset, 'Built JS entry must be served');
assert.equal((await fetch(new URL(asset[1], frontend), { signal: AbortSignal.timeout(10000) })).status, 200);
const request = createHttpClient({ baseUrl: `${frontend}/api/v1` });
await assert.rejects(request('/auth/me', { requestId: id }), error =>
  error instanceof HttpError && error.status === 401 && error.code === 'UNAUTHENTICATED' &&
  error.requestId === id && error.retryable === false);
console.log('PASS: health + requestId, frontend HTML/JS, nginx proxy + real client ApiError 401.');
console.log('Scope: platform only; no authenticated business or scan performance claim.');
