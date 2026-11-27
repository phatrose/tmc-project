import test from 'node:test';
import assert from 'node:assert/strict';
import { createAttendanceApi } from '../src/pages/attendanceApi.js';

test('attendanceApi calls v1 endpoints with query params and idempotency key', async () => {
  const calls = [];
  const api = createAttendanceApi(async (path, options) => {
    calls.push({ path, options });
    return { items: [], total: 0 };
  });

  await api.listEvents({ page: 1, size: 10 });
  await api.listRecords({ from: '2026-11-01', to: '2026-12-01', page: 0, size: 20 });
  await api.listAdjustments({ page: 0, size: 20 });
  await api.createAdjustment({ reason: 'Quên quẹt thẻ giờ vào' }, '00000000-0000-4000-8000-000000000099');

  assert.equal(calls[0].path, '/attendance/events?page=1&size=10');
  assert.equal(calls[1].path, '/attendance/records?from=2026-11-01&to=2026-12-01&page=0&size=20');
  assert.equal(calls[2].path, '/attendance/adjustments?page=0&size=20');
  assert.equal(calls[3].path, '/attendance/adjustments');
  assert.equal(calls[3].options.method, 'POST');
  assert.equal(calls[3].options.idempotencyKey, '00000000-0000-4000-8000-000000000099');
});
