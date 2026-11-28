import test from 'node:test';
import assert from 'node:assert/strict';
import { describeScanResult, describeError, validateOverrideForm, grantRequestBody, overrideRequestBody,
  summarizePresence, isUuid, DENY_REASONS } from '../src/lib/gateStation.js';

// PHỤ TRÁCH: Task 2. Các quy tắc hiển thị từ CONTRACT-V1.md §5.
const base = { scanId: 's', reasonCode: 'OK', occurredAt: '2026-11-17T02:00:00.000Z', mode: 'ONLINE' };

test('ALLOW is not presented as a confirmed passage and follows commandStatus', () => {
  const accepted = describeScanResult({ ...base, decision: 'ALLOW', commandStatus: 'UNLOCK_ACCEPTED' });
  assert.equal(accepted.tone, 'success');
  assert.match(accepted.title, /gửi lệnh/);
  assert.doesNotMatch(accepted.detail, /đã qua/);

  const failed = describeScanResult({ ...base, decision: 'ALLOW', commandStatus: 'FAILED' });
  assert.equal(failed.tone, 'warning');
  const timeout = describeScanResult({ ...base, decision: 'ALLOW', commandStatus: 'TIMEOUT' });
  assert.equal(timeout.tone, 'warning');
  assert.match(timeout.detail, /đừng quét lại/i);
  assert.equal(describeScanResult({ ...base, decision: 'ALLOW', commandStatus: 'NOT_SENT' }).tone, 'warning');
});

test('DENY is a business outcome with a human reason, never a success tone', () => {
  for (const [code, text] of Object.entries(DENY_REASONS)) {
    const view = describeScanResult({ ...base, decision: 'DENY', reasonCode: code, commandStatus: 'NOT_SENT' });
    assert.equal(view.tone, 'danger');
    assert.equal(view.detail, text);
  }
  const unknown = describeScanResult({ ...base, decision: 'DENY', reasonCode: 'NEW_CODE', commandStatus: 'NOT_SENT' });
  assert.match(unknown.detail, /NEW_CODE/);
});

test('offline mode is surfaced and emergency is labelled', () => {
  assert.equal(describeScanResult({ ...base, decision: 'ALLOW', commandStatus: 'UNLOCK_ACCEPTED', mode: 'OFFLINE' }).offline, true);
  const emergency = describeScanResult({ ...base, decision: 'EMERGENCY', reasonCode: 'EMERGENCY', commandStatus: 'UNLOCK_ACCEPTED' });
  assert.match(emergency.title, /khẩn cấp/i);
});

test('malformed responses fail closed', () => {
  assert.equal(describeScanResult(null).tone, 'danger');
  assert.equal(describeScanResult({}).tone, 'danger');
});

test('network and 503 errors are retryable with the same scan, validation errors are not', () => {
  assert.equal(describeError({ code: 'NETWORK_ERROR' }).retryable, true);
  assert.equal(describeError({ code: 'TIMEOUT' }).retryable, true);
  assert.equal(describeError({ code: 'STORAGE_UNAVAILABLE', requestId: 'r1' }).retryable, true);
  assert.match(describeError({ code: 'STORAGE_UNAVAILABLE', requestId: 'r1' }).detail, /r1/);
  assert.equal(describeError({ code: 'VALIDATION_ERROR' }).retryable, false);
  assert.equal(describeError({ code: 'DEVICE_BINDING_MISMATCH' }).retryable, false);
  assert.equal(describeError({ code: 'IDEMPOTENCY_CONFLICT' }).retryable, false);
  assert.equal(describeError({ code: 'SOMETHING', retryable: true }).retryable, true);
  assert.equal(describeError(undefined).retryable, false);
});

const ids = { gateId: '00000000-0000-4000-8000-000000000022', edgeId: '00000000-0000-4000-8000-000000000021',
  employeeId: '00000000-0000-4000-8000-000000000011' };
const reason = 'Điện thoại hết pin, đã đối chiếu thẻ';

test('override form validation matches the contract', () => {
  assert.deepEqual(validateOverrideForm({ mode: 'MANUAL', ...ids, reason, password: 'pw' }), {});
  assert.deepEqual(validateOverrideForm({ mode: 'EMERGENCY', ...ids, employeeId: '', reason, password: 'pw', confirmed: true }), {});

  const empty = validateOverrideForm({ mode: 'MANUAL', gateId: 'x', edgeId: '', employeeId: 'y', reason: 'ngắn', password: '' });
  assert.deepEqual(Object.keys(empty).sort(), ['edgeId', 'employeeId', 'gateId', 'password', 'reason']);

  assert.ok(validateOverrideForm({ mode: 'EMERGENCY', ...ids, reason, password: 'pw', confirmed: false }).confirmed);
  assert.ok(validateOverrideForm({ mode: 'MANUAL', ...ids, reason: 'x'.repeat(501), password: 'pw' }).reason);
  assert.ok(validateOverrideForm({ mode: 'NOPE', ...ids, reason, password: 'pw' }).mode);
  assert.ok(validateOverrideForm({ mode: 'MANUAL', ...ids, reason, password: 'p'.repeat(129) }).password);
});

test('reason length counts characters, not UTF-16 units', () => {
  assert.deepEqual(validateOverrideForm({ mode: 'MANUAL', ...ids, reason: '😀'.repeat(10), password: 'pw' }), {});
});

test('request bodies carry employeeId only for MANUAL', () => {
  const manual = grantRequestBody({ mode: 'MANUAL', ...ids, reason, password: 'pw' });
  assert.equal(manual.employeeId, ids.employeeId);
  const emergency = grantRequestBody({ mode: 'EMERGENCY', ...ids, reason, password: 'pw' });
  assert.equal(emergency.employeeId, null);
  assert.equal(overrideRequestBody({ scanId: 's', mode: 'EMERGENCY', ...ids, reason, grant: 'g' }).employeeId, null);
  assert.equal(overrideRequestBody({ scanId: 's', mode: 'MANUAL', ...ids, reason, grant: 'g' }).employeeId, ids.employeeId);
});

test('presence only counts people whose last direction is IN', () => {
  const summary = summarizePresence({ items: [
    { employeeId: 'a', lastDirection: 'IN' }, { employeeId: 'b', lastDirection: 'OUT' }, { employeeId: 'c', lastDirection: 'IN' }] });
  assert.equal(summary.total, 3);
  assert.equal(summary.inside, 2);
  assert.deepEqual(summary.items.map(item => item.employeeId), ['a', 'c']);
  assert.deepEqual(summarizePresence(null), { total: 0, inside: 0, items: [] });
});

test('uuid helper accepts lowercase RFC 4122 only', () => {
  assert.equal(isUuid(ids.gateId), true);
  const withLetters = '0000000a-0000-4000-8000-00000000000b';
  assert.equal(isUuid(withLetters), true);
  assert.equal(isUuid(withLetters.toUpperCase()), false);
  assert.equal(isUuid('nope'), false);
});
