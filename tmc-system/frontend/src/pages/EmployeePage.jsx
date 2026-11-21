// PHỤ TRÁCH: Task 1.
// VAI TRÒ: View — Danh sách nhân viên, tạo mới, cập nhật phiên bản theo CONTRACT-V1.md §4.

import React, { useState, useEffect } from 'react';
import { getSession } from './LoginPage.jsx';

const BASE = '/api/v1';

function authHeaders() {
  const session = getSession();
  const token = session?.accessToken;
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

async function handleResponse(res) {
  if (res.status === 401) {
    window.location.href = '/login';
    throw new Error('Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.');
  }
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(body.message || body.error || `Lỗi máy chủ (${res.status})`);
  }
  return body;
}

async function listEmployeesApi(factoryId, page = 0, size = 20) {
  const params = new URLSearchParams();
  if (factoryId) params.append('factoryId', factoryId);
  params.append('page', page);
  params.append('size', size);

  const res = await fetch(`${BASE}/employees?${params.toString()}`, {
    headers: authHeaders(),
  });
  return handleResponse(res);
}

async function getEmployeeApi(id) {
  const res = await fetch(`${BASE}/employees/${encodeURIComponent(id)}`, {
    headers: authHeaders(),
  });
  return handleResponse(res);
}

async function createEmployeeApi(data) {
  const res = await fetch(`${BASE}/employees`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data),
  });
  return handleResponse(res);
}

async function updateEmployeeApi(id, data) {
  const res = await fetch(`${BASE}/employees/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(data),
  });
  return handleResponse(res);
}

export default function EmployeePage() {
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
  const canManage = actor?.permissions?.includes('employee.manage') || actor?.roles?.includes('ADMIN') || actor?.roles?.includes('HR');

  async function fetchEmployees(factoryId, p = 0) {
    setLoading(true);
    setError('');
    setSelectedEmployee(null);
    try {
      const pageResult = await listEmployeesApi(factoryId || '', p, size);
      setEmployees(pageResult.items || []);
      setTotalElements(pageResult.totalElements || 0);
      setPage(pageResult.page || 0);
    } catch (err) {
      setError(err.message || 'Không thể tải danh sách nhân viên.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    fetchEmployees('');
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
      await createEmployeeApi({ ...form });
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
    <div style={styles.page}>
      <div style={styles.header}>
        <div>
          <h2 style={styles.title}>Quản lý Nhân sự</h2>
          <p style={styles.sub}>Theo hợp đồng CONTRACT-V1 — Tổng số: {totalElements} nhân viên</p>
        </div>
        {canManage && (
          <button style={styles.btnPrimary} onClick={() => setShowForm(true)}>
            + Thêm nhân viên
          </button>
        )}
      </div>

      <div style={styles.filterBar}>
        <input
          type="text"
          placeholder="Lọc theo mã nhà máy (factoryId)..."
          value={filterFactoryId}
          onChange={(e) => setFilterFactoryId(e.target.value)}
          style={styles.inputSearch}
        />
        <button style={styles.btnSecondary} onClick={() => fetchEmployees(filterFactoryId, 0)}>
          Tìm kiếm
        </button>
      </div>

      {error && <div style={styles.errorAlert}>{error}</div>}

      {showForm && (
        <div style={styles.modalOverlay}>
          <div style={styles.modal}>
            <h3>Thêm nhân viên mới</h3>
            {formError && <div style={styles.errorAlert}>{formError}</div>}
            <form onSubmit={handleCreateSubmit} style={styles.modalForm}>
              <label>Mã nhân viên (*):</label>
              <input
                type="text"
                required
                value={form.employeeCode}
                onChange={(e) => setForm({ ...form, employeeCode: e.target.value })}
                style={styles.modalInput}
              />

              <label>Họ và tên (*):</label>
              <input
                type="text"
                required
                value={form.displayName}
                onChange={(e) => setForm({ ...form, displayName: e.target.value })}
                style={styles.modalInput}
              />

              <label>Mã nhà máy (UUID) (*):</label>
              <input
                type="text"
                required
                value={form.factoryId}
                onChange={(e) => setForm({ ...form, factoryId: e.target.value })}
                style={styles.modalInput}
              />

              <label>Mã phòng ban (UUID):</label>
              <input
                type="text"
                value={form.departmentId}
                onChange={(e) => setForm({ ...form, departmentId: e.target.value })}
                style={styles.modalInput}
              />

              <div style={styles.modalActions}>
                <button type="button" onClick={() => setShowForm(false)} style={styles.btnSecondary}>
                  Hủy
                </button>
                <button type="submit" disabled={formLoading} style={styles.btnPrimary}>
                  {formLoading ? 'Đang lưu...' : 'Lưu'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {loading ? (
        <div style={styles.loading}>Đang tải dữ liệu...</div>
      ) : (
        <table style={styles.table}>
          <thead>
            <tr style={styles.thRow}>
              <th style={styles.th}>Mã NV</th>
              <th style={styles.th}>Họ và tên</th>
              <th style={styles.th}>Nhà máy</th>
              <th style={styles.th}>Phòng ban</th>
              <th style={styles.th}>Trạng thái</th>
              <th style={styles.th}>Version</th>
              <th style={styles.th}>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {employees.length === 0 ? (
              <tr>
                <td colSpan={7} style={styles.tdEmpty}>
                  Không tìm thấy nhân viên nào.
                </td>
              </tr>
            ) : (
              employees.map((emp) => (
                <tr key={emp.id} style={styles.tr}>
                  <td style={styles.tdCode}>{emp.employeeCode}</td>
                  <td style={styles.td}>{emp.displayName}</td>
                  <td style={styles.td}>{emp.factoryId}</td>
                  <td style={styles.td}>{emp.departmentId || '—'}</td>
                  <td style={styles.td}>
                    <span style={emp.active ? styles.badgeActive : styles.badgeInactive}>
                      {emp.active ? 'Đang hoạt động' : 'Đã nghỉ'}
                    </span>
                  </td>
                  <td style={styles.td}>v{emp.version}</td>
                  <td style={styles.tdActions}>
                    <button onClick={() => handleViewDetail(emp.id)} style={styles.btnLink}>
                      Chi tiết
                    </button>
                    {canManage && (
                      <button onClick={() => handleToggleActive(emp)} style={styles.btnLinkDanger}>
                        {emp.active ? 'Hủy kích hoạt' : 'Kích hoạt'}
                      </button>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      )}

      {selectedEmployee && (
        <div style={styles.detailBox}>
          <h4>Chi tiết hồ sơ nhân viên</h4>
          <p><strong>ID:</strong> {selectedEmployee.id}</p>
          <p><strong>Mã NV:</strong> {selectedEmployee.employeeCode}</p>
          <p><strong>Họ tên:</strong> {selectedEmployee.displayName}</p>
          <p><strong>Nhà máy:</strong> {selectedEmployee.factoryId}</p>
          <p><strong>Phòng ban:</strong> {selectedEmployee.departmentId || 'Chưa gán'}</p>
          <p><strong>Trạng thái:</strong> {selectedEmployee.active ? 'Hoạt động' : 'Ngừng hoạt động'}</p>
          <p><strong>Phiên bản:</strong> v{selectedEmployee.version}</p>
          <p><strong>Cập nhật lúc:</strong> {selectedEmployee.updatedAt}</p>
          <button onClick={() => setSelectedEmployee(null)} style={styles.btnSecondary}>
            Đóng
          </button>
        </div>
      )}
    </div>
  );
}

const styles = {
  page: { padding: '24px', fontFamily: 'system-ui, sans-serif' },
  header: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' },
  title: { margin: 0, fontSize: '24px', color: '#1f2937' },
  sub: { margin: '4px 0 0 0', color: '#6b7280', fontSize: '14px' },
  filterBar: { display: 'flex', gap: '12px', marginBottom: '16px' },
  inputSearch: { flex: 1, padding: '8px 12px', borderRadius: '4px', border: '1px solid #d1d5db', fontSize: '14px' },
  btnPrimary: { background: '#2563eb', color: '#fff', border: 'none', padding: '8px 16px', borderRadius: '4px', cursor: 'pointer', fontWeight: 600 },
  btnSecondary: { background: '#f3f4f6', color: '#374151', border: '1px solid #d1d5db', padding: '8px 16px', borderRadius: '4px', cursor: 'pointer' },
  btnLink: { background: 'none', border: 'none', color: '#2563eb', cursor: 'pointer', marginRight: '8px' },
  btnLinkDanger: { background: 'none', border: 'none', color: '#dc2626', cursor: 'pointer' },
  table: { width: '100%', borderCollapse: 'collapse', background: '#fff', borderRadius: '8px', overflow: 'hidden', boxShadow: '0 1px 3px rgba(0,0,0,0.1)' },
  thRow: { background: '#f9fafb', borderBottom: '1px solid #e5e7eb' },
  th: { padding: '12px', textAlign: 'left', fontSize: '13px', fontWeight: 600, color: '#4b5563' },
  tr: { borderBottom: '1px solid #f3f4f6' },
  td: { padding: '12px', fontSize: '14px', color: '#374151' },
  tdCode: { padding: '12px', fontSize: '14px', fontWeight: 600, color: '#111827' },
  tdActions: { padding: '12px' },
  tdEmpty: { padding: '24px', textAlign: 'center', color: '#9ca3af' },
  badgeActive: { background: '#dcfce7', color: '#15803d', padding: '4px 8px', borderRadius: '12px', fontSize: '12px', fontWeight: 500 },
  badgeInactive: { background: '#fee2e2', color: '#b91c1c', padding: '4px 8px', borderRadius: '12px', fontSize: '12px', fontWeight: 500 },
  errorAlert: { background: '#fef2f2', border: '1px solid #fecaca', color: '#991b1b', padding: '12px', borderRadius: '4px', marginBottom: '16px' },
  loading: { padding: '24px', textAlign: 'center', color: '#6b7280' },
  modalOverlay: { position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 },
  modal: { background: '#fff', padding: '24px', borderRadius: '8px', width: '400px', maxWidth: '90%' },
  modalForm: { display: 'flex', flexDirection: 'column', gap: '10px' },
  modalInput: { padding: '8px', borderRadius: '4px', border: '1px solid #d1d5db' },
  modalActions: { display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' },
  detailBox: { marginTop: '24px', padding: '16px', background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '8px' },
};
