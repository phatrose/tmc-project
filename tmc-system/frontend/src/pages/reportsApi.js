export function createReportsApi(request, download) {
  const query = values => {
    const params = new URLSearchParams();
    for (const [key, value] of Object.entries(values)) {
      if (value !== undefined && value !== null && value !== '') params.set(key, String(value));
    }
    return params.toString();
  };
  const id = value => encodeURIComponent(value);
  const mutation = (path, body, key, signal) => request(path, { method: 'POST', body, idempotencyKey: key, signal });
  return {
    attendance: (filter, signal) => request(`/reports/attendance?${query(filter)}`, { signal }),
    dashboard: (factoryId, date, signal) => request(`/reports/dashboard?${query({ factoryId, date })}`, { signal }),
    audit: (filter, signal) => request(`/audit/events?${query(filter)}`, { signal }),
    exportAttendance: (body, key, signal) => mutation('/reports/exports', body, key, signal),
    exportAudit: (body, key, signal) => mutation('/audit/exports', body, key, signal),
    exportStatus: (kind, exportId, signal) => request(`/${kind === 'AUDIT' ? 'audit' : 'reports'}/exports/${id(exportId)}`, { signal }),
    exportFile: (kind, exportId, signal) => download(`/${kind === 'AUDIT' ? 'audit' : 'reports'}/exports/${id(exportId)}/file`, { signal }),
    close: (period, body, key, signal) => mutation(`/payroll/periods/${id(period)}/close`, body, key, signal),
    reopen: (period, body, key, signal) => mutation(`/payroll/periods/${id(period)}/reopen`, body, key, signal),
    batch: (batchId, signal) => request(`/payroll/batches/${id(batchId)}`, { signal }),
    history: (period, filter, signal) => request(`/payroll/periods/${id(period)}/history?${query(filter)}`, { signal }),
  };
}

export function createReportMutation(newKey = () => crypto.randomUUID()) {
  let pending = null;
  return async (kind, period, body, execute) => {
    const fingerprint = JSON.stringify({ kind, period: ['CLOSE', 'REOPEN'].includes(kind) ? period : undefined, body });
    if (pending?.fingerprint !== fingerprint) pending = { fingerprint, key: newKey() };
    const key = pending.key;
    try {
      const result = await execute(key);
      pending = null;
      return result;
    } catch (failure) {
      if (!failure.retryable) pending = null;
      throw failure;
    }
  };
}
