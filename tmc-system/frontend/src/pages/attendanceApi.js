// PHỤ TRÁCH: Task 3 / Task 8. Client API chấm công nối Central Backend (CONTRACT-V1 §9).
export function createAttendanceApi(request) {
  return {
    listEvents({ page = 0, size = 20, signal } = {}) {
      return request(`/attendance/events?page=${page}&size=${size}`, { signal });
    },
    listRecords({ from, to, page = 0, size = 20, signal } = {}) {
      const params = new URLSearchParams({ from, to, page: String(page), size: String(size) });
      return request(`/attendance/records?${params.toString()}`, { signal });
    },
    listAdjustments({ page = 0, size = 20, signal } = {}) {
      return request(`/attendance/adjustments?page=${page}&size=${size}`, { signal });
    },
    createAdjustment(body, idempotencyKey) {
      return request('/attendance/adjustments', {
        method: 'POST',
        body,
        idempotencyKey,
      });
    },
    decideAdjustment(id, decision) {
      return request(`/attendance/adjustments/${encodeURIComponent(id)}/decision`, {
        method: 'POST',
        body: decision,
        idempotencyKey: crypto.randomUUID(),
      });
    },
  };
}
