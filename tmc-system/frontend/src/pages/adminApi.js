// PHỤ TRÁCH: Task 5 / Task 8. Client API Quản trị hệ thống nối Central Backend.
export function createAdminApi(request) {
  return {
    listGates({ factoryId, signal } = {}) {
      const q = factoryId && factoryId !== 'ALL' ? `?factoryId=${encodeURIComponent(factoryId)}` : '';
      return request(`/admin/gates${q}`, { signal });
    },
    updateGateStatus(gateId, status) {
      return request(`/admin/gates/${encodeURIComponent(gateId)}/status`, {
        method: 'PUT',
        body: { status },
        idempotencyKey: crypto.randomUUID(),
      });
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
        idempotencyKey,
      });
    },
  };
}
