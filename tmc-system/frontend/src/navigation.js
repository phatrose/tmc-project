// PHỤ TRÁCH: Task 8. UI hints only; backend của Task 1 vẫn phải kiểm tra permission/scope.
export const routes = [
  { id: 'home', title: 'Tổng quan', public: true },
  { id: 'employees', title: 'Nhân sự', task: 1, permission: 'employee.manage' },
  { id: 'qr', title: 'QR cá nhân', task: 6, employee: true },
  { id: 'kiosk', title: 'Quét tại cổng', task: 2, device: true, public: true },
  { id: 'guard', title: 'Vận hành cổng', task: 2, permissions: ['gate.override', 'gate.emergency'], mutation: true },
  { id: 'attendance', title: 'Chấm công', task: 3, roles: ['EMPLOYEE', 'SUPERVISOR', 'HR'] },
  { id: 'reports', title: 'Báo cáo và kiểm toán', task: 4, permissions: ['report.read', 'audit.read'] },
  { id: 'administration', title: 'Quản trị', task: 5, permission: 'system.manage', mutation: true },
  { id: 'login', title: 'Đăng nhập', task: 1, public: true },
];
export function canAccess(route, session) {
  if (route.public) return true;
  if (!session || Date.parse(session.expiresAt) <= Date.now()) return false;
  const actor = session.actor;
  if (route.mutation && actor.roles.includes('AUDITOR')) return false;
  if (route.employee) return Boolean(actor.employeeId) && !actor.roles.includes('AUDITOR');
  if (route.permission) return actor.permissions.includes(route.permission);
  if (route.permissions) return route.permissions.some(p => actor.permissions.includes(p));
  if (route.roles) return route.roles.some(r => actor.roles.includes(r));
  return false;
}
export function routeFromHash(hash) {
  const id = hash.replace(/^#\/?/, '') || 'home';
  return routes.find(route => route.id === id) ?? null;
}
