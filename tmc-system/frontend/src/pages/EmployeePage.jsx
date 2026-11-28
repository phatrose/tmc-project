// PHỤ TRÁCH: Task 1.
// VAI TRÒ: View — Danh sách nhân viên, tạo mới, cập nhật phiên bản theo CONTRACT-V1.md §4.

import React, { useState, useEffect, useRef } from 'react';
import { getSession } from '../session.js';
import { request } from '../api.js';

function listEmployeesApi(factoryId, page = 0, size = 20, signal) {
  const params = new URLSearchParams({ page, size });
  if (factoryId) params.set('factoryId', factoryId);
  return request(`/employees?${params}`, { signal });
}
const getEmployeeApi = (id) => request(`/employees/${encodeURIComponent(id)}`);
const createEmployeeApi = (data, idempotencyKey) =>
  request('/employees', { method: 'POST', body: data, idempotencyKey });
const updateEmployeeApi = (id, data) =>
  request(`/employees/${encodeURIComponent(id)}`, { method: 'PUT', body: data });

function initials(name) {
  if (!name) return 'NV';
  const parts = name.trim().split(/\s+/);
  return (parts[0][0] + (parts[parts.length - 1]?.[0] || '')).toUpperCase();
}

export default function EmployeePage() {
  const listRequest = useRef(null);
  const createAttempt = useRef(null);
  const [employees, setEmployees] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [size] = useState(20);
  const [selectedEmployee, setSelectedEmployee] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [filterFactoryId, setFilterFactoryId] = useState('');

  const [form, setForm] = useState({
    employeeCode: '',
    displayName: '',
    factoryId: '',
    departmentId: '',
    active: true,
  });
  const [formLoading, setFormLoading] = useState(false);
  const [formError, setFormError] = useState('');

  const session = getSession();
  const actor = session?.actor;
  const canManage = !actor?.roles?.includes('AUDITOR') && actor?.permissions?.includes('employee.manage');

  async function fetchEmployees(factoryId, p = 0) {
    listRequest.current?.abort();
    const controller = new AbortController();
    listRequest.current = controller;
    setLoading(true);
    setError('');
    setSelectedEmployee(null);
    try {
      const pageResult = await listEmployeesApi(factoryId || '', p, size, controller.signal);
      if (controller.signal.aborted) return;
      setEmployees(pageResult.items || []);
      setTotalElements(pageResult.totalElements || 0);
      setPage(pageResult.page || 0);
    } catch (err) {
      if (!controller.signal.aborted) setError(err.message || 'Không thể tải danh sách nhân viên.');
    } finally {
      if (listRequest.current === controller) setLoading(false);
    }
  }

  useEffect(() => {
    fetchEmployees('');
    return () => listRequest.current?.abort();
  }, []);

  async function handleViewDetail(id) {
    try {
      const data = await getEmployeeApi(id);
      setSelectedEmployee(data);
    } catch (err) {
      setError(err.message || 'Không thể tải thông tin nhân viên.');
    }
  }

  async function handleToggleActive(emp) {
    if (!window.confirm(`Xác nhận đổi trạng thái của nhân viên "${emp.displayName}"?`)) return;
    try {
      await updateEmployeeApi(emp.id, {
        employeeCode: emp.employeeCode,
        displayName: emp.displayName,
        factoryId: emp.factoryId,
        departmentId: emp.departmentId,
        active: !emp.active,
        version: emp.version,
      });
      await fetchEmployees(filterFactoryId, page);
      if (selectedEmployee?.id === emp.id) {
        setSelectedEmployee({ ...selectedEmployee, active: !emp.active, version: emp.version + 1 });
      }
    } catch (err) {
      setError(err.message || 'Không thể cập nhật trạng thái nhân viên.');
    }
  }

  async function handleCreateSubmit(e) {
    e.preventDefault();
    setFormLoading(true);
    setFormError('');
    try {
      const payload = { ...form, departmentId: form.departmentId.trim() || null };
      const fingerprint = JSON.stringify(payload);
      if (createAttempt.current?.fingerprint !== fingerprint) {
        createAttempt.current = { fingerprint, key: crypto.randomUUID() };
      }
      await createEmployeeApi(payload, createAttempt.current.key);
      createAttempt.current = null;
      setShowForm(false);
      setForm({ employeeCode: '', displayName: '', factoryId: '', departmentId: '', active: true });
      await fetchEmployees(filterFactoryId, 0);
    } catch (err) {
      setFormError(err.message || 'Không thể tạo nhân viên.');
    } finally {
      setFormLoading(false);
    }
  }

  return (
    <section>
      <div className="module-heading">
        <div>
          <span className="module-label">Quản lý nhân sự</span>
          <h1>Hồ sơ nhân viên</h1>
          <p>Tổng số trong phạm vi: <strong>{totalElements}</strong> nhân viên · Trang {page + 1}</p>
        </div>
        {canManage && (
          <button className="tmc-btn-primary" onClick={() => setShowForm(true)}>
            + Thêm nhân viên
          </button>
        )}
      </div>

      <div className="tmc-toolbar">
        <input
          type="search"
          placeholder="Lọc theo mã nhà máy (factoryId UUID)…"
          value={filterFactoryId}
          onChange={(e) => setFilterFactoryId(e.target.value)}
        />
        <button className="tmc-btn-secondary" onClick={() => fetchEmployees(filterFactoryId.trim(), 0)}>
          Tìm kiếm
        </button>
        {filterFactoryId && (
          <button
            className="tmc-btn-ghost"
            onClick={() => { setFilterFactoryId(''); fetchEmployees('', 0); }}
          >
            Xóa lọc
          </button>
        )}
      </div>

      {error && <div className="notice error" role="alert">{error}</div>}

      {showForm && (
        <div className="tmc-modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="create-emp-title">
          <div className="tmc-modal">
            <span className="module-label">Tạo mới</span>
            <h3 id="create-emp-title">Thêm nhân viên mới</h3>
            {formError && <div className="notice error" role="alert">{formError}</div>}
            <form onSubmit={handleCreateSubmit} className="tmc-form-grid">
              <div className="tmc-field">
                <label htmlFor="emp-code">Mã nhân viên (*)</label>
                <input
                  id="emp-code"
                  type="text"
                  required
                  placeholder="VD: EMP-2026-001"
                  value={form.employeeCode}
                  onChange={(e) => setForm({ ...form, employeeCode: e.target.value })}
                />
              </div>
              <div className="tmc-field">
                <label htmlFor="emp-name">Họ và tên (*)</label>
                <input
                  id="emp-name"
                  type="text"
                  required
                  placeholder="Nguyễn Văn A"
                  value={form.displayName}
                  onChange={(e) => setForm({ ...form, displayName: e.target.value })}
                />
              </div>
              <div className="tmc-field">
                <label htmlFor="emp-factory">Mã nhà máy (UUID) (*)</label>
                <input
                  id="emp-factory"
                  type="text"
                  required
                  placeholder="00000000-0000-4000-8000-000000000023"
                  value={form.factoryId}
                  onChange={(e) => setForm({ ...form, factoryId: e.target.value })}
                />
              </div>
              <div className="tmc-field">
                <label htmlFor="emp-dept">Mã phòng ban (UUID, tùy chọn)</label>
                <input
                  id="emp-dept"
                  type="text"
                  value={form.departmentId}
                  onChange={(e) => setForm({ ...form, departmentId: e.target.value })}
                />
              </div>
              <div className="tmc-modal-actions">
                <button type="button" className="tmc-btn-secondary" onClick={() => setShowForm(false)}>
                  Hủy
                </button>
                <button type="submit" className="tmc-btn-primary" disabled={formLoading}>
                  {formLoading ? 'Đang lưu…' : 'Lưu nhân viên'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {loading ? (
        <div className="tmc-card" role="status">Đang tải danh sách nhân viên…</div>
      ) : (
        <div className="data-table-scroll">
          <table>
            <thead>
              <tr>
                <th>Nhân viên</th>
                <th>Mã NV</th>
                <th>Nhà máy</th>
                <th>Phòng ban</th>
                <th>Trạng thái</th>
                <th>Version</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {employees.length === 0 ? (
                <tr>
                  <td colSpan={7} className="td-empty">Không tìm thấy nhân viên nào trong phạm vi.</td>
                </tr>
              ) : (
                employees.map((emp) => (
                  <tr key={emp.id}>
                    <td>
                      <div className="emp-cell">
                        <span className="emp-avatar">{initials(emp.displayName)}</span>
                        <strong>{emp.displayName}</strong>
                      </div>
                    </td>
                    <td className="mono">{emp.employeeCode}</td>
                    <td className="mono">{emp.factoryId}</td>
                    <td className="mono">{emp.departmentId || '—'}</td>
                    <td>
                      <span className={`tmc-badge ${emp.active ? 'tmc-badge--success' : 'tmc-badge--danger'}`}>
                        {emp.active ? 'Đang hoạt động' : 'Đã nghỉ'}
                      </span>
                    </td>
                    <td><span className="tmc-badge tmc-badge--neutral">v{emp.version}</span></td>
                    <td>
                      <button className="tmc-btn-ghost" onClick={() => handleViewDetail(emp.id)}>
                        Chi tiết
                      </button>
                      {canManage && (
                        <button className="tmc-btn-danger-ghost" onClick={() => handleToggleActive(emp)}>
                          {emp.active ? 'Hủy kích hoạt' : 'Kích hoạt'}
                        </button>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {selectedEmployee && (
        <div className="tmc-card" style={{ marginTop: 18 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
            <div>
              <span className="module-label">Chi tiết hồ sơ</span>
              <h3>{selectedEmployee.displayName} ({selectedEmployee.employeeCode})</h3>
            </div>
            <button className="tmc-btn-secondary" onClick={() => setSelectedEmployee(null)}>Đóng</button>
          </div>
          <div className="tmc-detail-grid">
            <div className="tmc-detail-item"><span>Employee ID</span><strong className="mono">{selectedEmployee.id}</strong></div>
            <div className="tmc-detail-item"><span>Nhà máy</span><strong className="mono">{selectedEmployee.factoryId}</strong></div>
            <div className="tmc-detail-item"><span>Phòng ban</span><strong className="mono">{selectedEmployee.departmentId || 'Chưa gán'}</strong></div>
            <div className="tmc-detail-item"><span>Trạng thái</span><strong>{selectedEmployee.active ? 'Đang hoạt động' : 'Ngừng hoạt động'}</strong></div>
            <div className="tmc-detail-item"><span>Phiên bản</span><strong>v{selectedEmployee.version}</strong></div>
            <div className="tmc-detail-item"><span>Cập nhật lúc</span><strong className="mono">{selectedEmployee.updatedAt}</strong></div>
          </div>
        </div>
      )}
    </section>
  );
}
