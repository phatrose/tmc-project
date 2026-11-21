import test from 'node:test';
import assert from 'node:assert/strict';
import { setSession, clearSession, getSession, getAccessToken, subscribeSession } from '../src/session.js';
import { canAccess, routes, routeFromHash } from '../src/navigation.js';
const sample = () => ({ accessToken: 'TEST_ONLY', tokenType: 'Bearer',
  expiresAt: new Date(Date.now() + 60000).toISOString(),
  actor: { userId: 'id', employeeId: 'employee', roles: ['EMPLOYEE'], permissions: [] } });
// PHỤ TRÁCH: Task 8. Không chứng nhận RBAC backend qua kiểm tra menu.
test('session snapshot notifies subscribers and clears token', () => {
  let changed = 0;
  const off = subscribeSession(() => changed++);
  const source = sample(); setSession(source); source.actor.roles.push('ADMIN');
  assert.equal(getAccessToken(), 'TEST_ONLY');
  assert.deepEqual(getSession().actor.roles, ['EMPLOYEE']);
  clearSession(); assert.equal(getAccessToken(), null); assert.equal(changed, 2); off();
});
test('expired or invalid session is rejected', () => {
  assert.throws(() => setSession({ ...sample(), expiresAt: '2000-01-01T00:00:00Z' }), TypeError);
  assert.throws(() => setSession({ ...sample(), tokenType: 'Basic' }), TypeError);
});
test('routes resolve back/forward hashes and fail closed for unknown paths', () => {
  assert.equal(routeFromHash('#/attendance').id, 'attendance');
  assert.equal(routeFromHash('').id, 'home');
  assert.equal(routeFromHash('#/missing'), null);
  assert.equal(canAccess(routes.find(r => r.id === 'employees'), null), false);
});
test('Auditor mutation restrictions override extra permissions', () => {
  const session = sample(); session.actor.roles = ['AUDITOR', 'ADMIN'];
  session.actor.permissions = ['system.manage', 'gate.override', 'audit.read'];
  assert.equal(canAccess(routes.find(r => r.id === 'administration'), session), false);
  assert.equal(canAccess(routes.find(r => r.id === 'guard'), session), false);
  assert.equal(canAccess(routes.find(r => r.id === 'reports'), session), true);
});
