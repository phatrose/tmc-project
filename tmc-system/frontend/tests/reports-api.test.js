import test from 'node:test';
import assert from 'node:assert/strict';
import { createReportsApi, createReportMutation } from '../src/pages/reportsApi.js';

const id = number => `00000000-0000-4000-8000-${String(number).padStart(12, '0')}`;

test('attendance query follows v1 and omits empty filters', async () => {
  let sent;
  const api = createReportsApi(async (path, options) => {
    sent = { path, options }; return { items: [], page: 2, size: 20, totalElements: 0 };
  });
  const controller = new AbortController();
  const result = await api.attendance({ factoryId: id(2), employeeId: '', from: '2026-10-01', to: '2026-11-01', page: 2, size: 20 }, controller.signal);
  assert.equal(result.page, 2); assert.match(sent.path, /from=2026-10-01&to=2026-11-01&page=2&size=20/);
  assert.equal(new URL(sent.path, 'http://fixture').searchParams.has('employeeId'), false);
  assert.equal(sent.options.signal, controller.signal);
});

test('export file uses the supplied binary transport', async () => {
  let sent;
  const file = new Blob([new Uint8Array([0x50, 0x4b, 1])]);
  const api = createReportsApi(null, async (path, options) => { sent = { path, options }; return file; });
  const controller = new AbortController();
  assert.equal(await api.exportFile('ATTENDANCE', id(3), controller.signal), file);
  assert.equal(sent.path, `/reports/exports/${id(3)}/file`);assert.equal(sent.options.signal, controller.signal);
});

test('export file preserves transport errors and correlation', async () => {
  const failure = Object.assign(new Error('File expired'), { code: 'NOT_FOUND', requestId: id(4), retryable: false });
  const api = createReportsApi(null, async () => { throw failure; });
  await assert.rejects(api.exportFile('AUDIT', id(3)), error => error === failure);
});

test('uncertain retry keeps the same input and key, a new export gets a new key', async () => {
  const sent = []; let attempt = 0; let keys = 10;
  const api = createReportsApi(async (path, options) => {
    sent.push({ path, body: JSON.stringify(options.body), key: options.idempotencyKey });
    if (++attempt === 1) throw Object.assign(new Error('Lost response'), { retryable: true });
    return { id: id(20), state: 'QUEUED' };
  });
  const mutate = createReportMutation(() => id(keys++));
  const body = { factoryId: id(2), period: '2026-10', format: 'CSV' };
  const run = () => mutate('ATTENDANCE', '2026-10', body, key => api.exportAttendance(body, key));
  await assert.rejects(run()); await run(); await run();
  assert.deepEqual(sent[0], sent[1]); assert.notEqual(sent[1].key, sent[2].key);
});

test('payroll mutations use v1 paths, version and explicit keys', async () => {
  const sent = [];
  const api = createReportsApi(async (path, options) => { sent.push({ path, ...options }); return {}; });
  await api.close('2026-10', { factoryId: id(2), reason: 'Close period' }, id(5));
  await api.reopen('2026-10', { factoryId: id(2), reason: 'Late event', version: 1 }, id(6));
  assert.equal(sent[0].path, '/payroll/periods/2026-10/close');assert.equal(sent[0].idempotencyKey, id(5));
  assert.equal(sent[1].path, '/payroll/periods/2026-10/reopen');assert.equal(sent[1].body.version, 1);
  assert.equal(sent[1].idempotencyKey, id(6));
});
