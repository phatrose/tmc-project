// PHỤ TRÁCH: Task 5 / Task 8. Client API Quản trị hệ thống nối Central Backend.
export function createAdminApi(request) {
  return {
    listGates({ factoryId, signal } = {}) {
      const q = factoryId && factoryId !== 'ALL' ? `?factoryId=${encodeURIComponent(factoryId)}` : '';
      return request(`/admin/gates${q}`, { signal });
    },
    registerGate(body) {
      return request('/admin/gates', {
        method: 'POST',
        body,
        idempotencyKey: crypto.randomUUID(),
      });
    },
    updateGateStatus(gateId, status) {
      return request(`/admin/gates/${encodeURIComponent(gateId)}/status`, {
        method: 'PUT',
        body: { status },
        idempotencyKey: crypto.randomUUID(),
      });
    },
    listShifts({ factoryId, signal } = {}) {
      const q = factoryId && factoryId !== 'ALL' ? `?factoryId=${encodeURIComponent(factoryId)}` : '';
      return request(`/admin/shifts${q}`, { signal });
    },
    listHolidays({ factoryId, year, signal } = {}) {
      const params = new URLSearchParams();
      if (factoryId && factoryId !== 'ALL') params.set('factoryId', factoryId);
      if (year) params.set('year', String(year));
      const qs = params.toString();
      return request(`/admin/holidays${qs ? `?${qs}` : ''}`, { signal });
    },
    listConfigurations({ signal } = {}) {
      return request('/admin/configurations', { signal });
    },
    updateConfiguration(key, value) {
      return request(`/admin/configurations/${encodeURIComponent(key)}`, {
        method: 'PUT',
        body: { value },
        idempotencyKey: crypto.randomUUID(),
      });
    },
    listDevices({ factoryId, signal } = {}) {
      const q = factoryId && factoryId !== 'ALL' ? `?factoryId=${encodeURIComponent(factoryId)}` : '';
      return request(`/admin/devices${q}`, { signal });
    },
    listDsr({ signal } = {}) {
      return request('/admin/dsr', { signal });
    },
    submitDsr(body, idempotencyKey) {
      return request('/admin/dsr', {
        method: 'POST',
        body,
        idempotencyKey: idempotencyKey || crypto.randomUUID(),
      });
    },
    processDsr(requestId, status, notes = '') {
      return request(`/admin/dsr/${encodeURIComponent(requestId)}`, {
        method: 'PUT',
        body: { status, notes },
        idempotencyKey: crypto.randomUUID(),
      });
    },
    triggerOperationJob(jobType, idempotencyKey) {
      return request('/operations/jobs', {
        method: 'POST',
        body: { jobType },
        idempotencyKey: idempotencyKey || crypto.randomUUID(),
      });
    },
  };
}
