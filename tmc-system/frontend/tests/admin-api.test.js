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
