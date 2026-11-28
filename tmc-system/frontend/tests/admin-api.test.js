import test from 'node:test';
import assert from 'node:assert/strict';
import { createAdminApi } from '../src/pages/adminApi.js';

test('adminApi calls v1 admin endpoints with query params and idempotency keys', async () => {
  const calls = [];
  const api = createAdminApi(async (path, options) => {
    calls.push({ path, options });
    return [];
  });

  await api.listGates({ factoryId: 'FACTORY-BD' });
  await api.listConfigurations();
  await api.listDsr();
  await api.submitDsr({ employeeId: 'EMP001', requestType: 'ACCESS', reason: 'Trích xuất dữ liệu' }, '00000000-0000-4000-8000-000000000077');

  assert.equal(calls[0].path, '/admin/gates?factoryId=FACTORY-BD');
  assert.equal(calls[1].path, '/admin/configurations');
  assert.equal(calls[2].path, '/admin/dsr');
  assert.equal(calls[3].path, '/admin/dsr');
  assert.equal(calls[3].options.method, 'POST');
  assert.equal(calls[3].options.idempotencyKey, '00000000-0000-4000-8000-000000000077');
});

test('adminApi forwards shifts, holidays, devices, registerGate and processDsr contracts', async () => {
  const calls = [];
  const api = createAdminApi(async (path, options) => {
    calls.push({ path, options });
    return {};
  });

  await api.listShifts({ factoryId: 'F-01' });
  await api.listHolidays({ factoryId: 'F-01', year: 2026 });
  await api.listDevices({ factoryId: 'ALL' });
  await api.registerGate({ gateCode: 'G-09', factoryId: 'F-01' });
  await api.processDsr('dsr-1', 'COMPLETED', 'Đã xuất hồ sơ');
  await api.triggerOperationJob('RETENTION_AUDIT', '00000000-0000-4000-8000-000000000088');

  assert.equal(calls[0].path, '/admin/shifts?factoryId=F-01');
  assert.equal(calls[1].path, '/admin/holidays?factoryId=F-01&year=2026');
  assert.equal(calls[2].path, '/admin/devices');
  assert.equal(calls[3].path, '/admin/gates');
  assert.equal(calls[3].options.method, 'POST');
  assert.match(calls[3].options.idempotencyKey, /^[0-9a-f-]{36}$/);
  assert.equal(calls[4].path, '/admin/dsr/dsr-1');
  assert.equal(calls[4].options.method, 'PUT');
  assert.deepEqual(calls[4].options.body, { status: 'COMPLETED', notes: 'Đã xuất hồ sơ' });
  assert.equal(calls[5].path, '/operations/jobs');
  assert.equal(calls[5].options.method, 'POST');
  assert.deepEqual(calls[5].options.body, { jobType: 'RETENTION_AUDIT' });
  assert.equal(calls[5].options.idempotencyKey, '00000000-0000-4000-8000-000000000088');
});
