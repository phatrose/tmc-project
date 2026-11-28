import React, { useState, useEffect } from 'react';
import { request } from '../api.js';
import { createAdminApi } from './adminApi.js';

const adminApi = createAdminApi(request);

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: View Quản trị hệ thống (Nhà máy, Cổng, Ca làm việc, Cấu hình, Giám sát, DSR và Lưu trữ).
 */
export default function AdministrationPage({ session } = {}) {
  const [activeTab, setActiveTab] = useState('gates');
  const [selectedFactory, setSelectedFactory] = useState('ALL');
  const [notification, setNotification] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [loading, setLoading] = useState(false);

  const roles = session?.actor?.roles || [];
  const canMutate = roles.includes('ADMIN') && !roles.includes('AUDITOR');

  const [gates, setGates] = useState([]);
  const [shifts, setShifts] = useState([]);
  const [holidays, setHolidays] = useState([]);
  const [configs, setConfigs] = useState([]);
  const [devices, setDevices] = useState([]);
  const [dsrList, setDsrList] = useState([]);

  useEffect(() => {
    if (!session) return undefined;
    const controller = new AbortController();
    setLoading(true);
    setLoadError('');
    Promise.all([
      adminApi.listGates({ signal: controller.signal }),
      adminApi.listShifts({ signal: controller.signal }),
      adminApi.listHolidays({ signal: controller.signal }),
      adminApi.listConfigurations({ signal: controller.signal }),
      adminApi.listDevices({ signal: controller.signal }),
      adminApi.listDsr({ signal: controller.signal }),
    ])
      .then(([liveGates, liveShifts, liveHolidays, liveConfigs, liveDevices, liveDsr]) => {
        setGates(Array.isArray(liveGates) ? liveGates : []);
        setShifts(Array.isArray(liveShifts) ? liveShifts : []);
        setHolidays(Array.isArray(liveHolidays) ? liveHolidays : []);
        setConfigs(
          Array.isArray(liveConfigs)
            ? liveConfigs.map((c) => ({
                key: c.key,
                value: c.value,
                category: c.category || 'SYSTEM',
                desc: c.description || c.desc || '',
              }))
            : []
        );
        setDevices(Array.isArray(liveDevices) ? liveDevices : []);
        setDsrList(Array.isArray(liveDsr) ? liveDsr : []);
      })
      .catch((err) => {
        if (err?.name === 'AbortError') return;
        setLoadError(err?.message || 'Không thể tải dữ liệu quản trị từ Central Backend.');
      })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [session]);

  const [newGate, setNewGate] = useState({
    id: '',
    factoryId: 'FACTORY-BD',
    edgeId: 'EDGE-BD-01',
    name: '',
    direction: 'IN',
    turnstileApiId: ''
  });
  const [showAddGateModal, setShowAddGateModal] = useState(false);
  const [newDsr, setNewDsr] = useState({ employeeId: '', requestType: 'ACCESS', reason: '' });

  const showToast = (msg) => {
    setNotification(msg);
    setTimeout(() => setNotification(null), 4000);
  };

  const handleToggleGateStatus = async (gateId) => {
    const current = gates.find((g) => g.id === gateId);
    if (!current) return;
    const nextStatus = current.status === 'ACTIVE' ? 'MAINTENANCE' : current.status === 'MAINTENANCE' ? 'INACTIVE' : 'ACTIVE';
    try {
      const updated = await adminApi.updateGateStatus(gateId, nextStatus);
      setGates((prev) => prev.map((g) => (g.id === gateId ? updated : g)));
      showToast(`Đã cập nhật trạng thái cổng ${gateId} sang ${updated.status}`);
    } catch (err) {
      showToast(`Lỗi cập nhật cổng ${gateId}: ${err?.message || 'không thể lưu'}`);
    }
  };

  const handleAddGate = async (e) => {
    e.preventDefault();
    if (!newGate.id || !newGate.turnstileApiId) return;
    try {
      const created = await adminApi.registerGate(newGate);
      setGates((prev) => [...prev, created]);
      setShowAddGateModal(false);
      setNewGate({ id: '', factoryId: 'FACTORY-BD', edgeId: 'EDGE-BD-01', name: '', direction: 'IN', turnstileApiId: '' });
      showToast(`Đăng ký cổng mới ${created.id} thành công!`);
    } catch (err) {
      showToast(`Không thể đăng ký cổng: ${err?.message || 'lỗi dữ liệu'}`);
    }
  };

  const handleSaveConfigs = async () => {
    try {
      const updated = await Promise.all(configs.map((c) => adminApi.updateConfiguration(c.key, c.value)));
      setConfigs(
        updated.map((c) => ({
          key: c.key,
          value: c.value,
          category: c.category || 'SYSTEM',
          desc: c.description || c.desc || '',
        }))
      );
      showToast('Đã lưu toàn bộ cấu hình lên Central Backend.');
    } catch (err) {
      showToast(`Không thể lưu cấu hình: ${err?.message || 'lỗi hệ thống'}`);
    }
  };

  const handleAddDsr = async (e) => {
    e.preventDefault();
    if (!newDsr.employeeId) return;
    try {
      const created = await adminApi.submitDsr(
        {
          employeeId: newDsr.employeeId.trim(),
          requestType: newDsr.requestType,
          reason: newDsr.reason.trim() || 'Yêu cầu của người lao động',
        },
        crypto.randomUUID()
      );
      setDsrList((prev) => [created, ...prev]);
      setNewDsr({ employeeId: '', requestType: 'ACCESS', reason: '' });
      showToast(`Đã tiếp nhận yêu cầu DSR ${created.id}`);
    } catch (err) {
      showToast(`Không thể tạo yêu cầu DSR: ${err?.message || 'lỗi hệ thống'}`);
    }
  };

  const handleProcessDsr = async (id, newStatus) => {
    try {
      const updated = await adminApi.processDsr(id, newStatus);
      setDsrList((prev) => prev.map((item) => (item.id === id ? updated : item)));
      showToast(`Đã cập nhật yêu cầu DSR ${id} sang ${updated.status}`);
    } catch (err) {
      showToast(`Không thể cập nhật DSR ${id}: ${err?.message || 'lỗi hệ thống'}`);
    }
  };

  const filteredGates = selectedFactory === 'ALL' ? gates : gates.filter((g) => g.factoryId === selectedFactory);
  const edgeNodes = devices.filter((d) => d.deviceType === 'EDGE_NODE');
  const onlineEdgeNodes = edgeNodes.filter((d) => d.status === 'ONLINE').length;
  const activeGatesCount = gates.filter((g) => g.status === 'ACTIVE').length;

  return (
    <section>
      <div className="module-heading">
        <div>
          <span className="module-label">Quản trị hệ thống</span>
          <h1>Nhà máy, Cổng, Ca & Vận hành</h1>
          <p>Quản lý hạ tầng cổng kiểm soát, ca làm việc, cấu hình tích hợp, giám sát thiết bị và chính sách dữ liệu.</p>
        </div>
        <span className={`tmc-badge ${loadError ? 'tmc-badge--danger' : 'tmc-badge--success'}`}>
          {loading ? 'Đang tải…' : loadError ? 'Lỗi kết nối' : canMutate ? 'Đã kết nối API' : 'Chỉ xem (Auditor)'}
        </span>
      </div>

      {loadError && (
        <div role="alert" className="notice" style={{ borderColor: 'var(--danger-border)', color: 'var(--danger-ink)' }}>
          {loadError}
        </div>
      )}

      {notification && (
        <div className="notice" style={{ background: 'var(--accent-soft)', borderColor: '#b3e3db', color: 'var(--accent-ink)' }}>
          {notification}
        </div>
      )}

      <div className="tmc-tabs" role="tablist" aria-label="Phân mục quản trị">
        {[
          { id: 'gates', label: 'Nhà máy & cổng' },
          { id: 'shifts', label: 'Ca làm & ngày lễ' },
          { id: 'configs', label: 'Cấu hình' },
          { id: 'monitoring', label: 'Thiết bị' },
          { id: 'dsr_retention', label: 'Dữ liệu & lưu trữ' }
        ].map((tab) => (
          <button
            key={tab.id}
            role="tab"
            aria-selected={activeTab === tab.id}
            aria-pressed={activeTab === tab.id}
            onClick={() => setActiveTab(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'gates' && (
        <div className="tmc-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12, marginBottom: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <label htmlFor="admin-factory" style={{ fontSize: 13.5, fontWeight: 600 }}>Nhà máy:</label>
              <select id="admin-factory" value={selectedFactory} onChange={(e) => setSelectedFactory(e.target.value)}>
                <option value="ALL">Tất cả nhà máy</option>
                <option value="FACTORY-BD">Nhà máy Bình Dương (FACTORY-BD)</option>
                <option value="FACTORY-LA">Nhà máy Long An (FACTORY-LA)</option>
              </select>
            </div>
            {canMutate && (
              <button className="tmc-btn-primary" onClick={() => setShowAddGateModal(true)}>
                + Đăng ký cổng mới
              </button>
            )}
          </div>

          <div className="data-table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Mã cổng</th>
                  <th>Tên cổng</th>
                  <th>Nhà máy</th>
                  <th>Trạm Edge</th>
                  <th>Hướng</th>
                  <th>Turnstile API ID</th>
                  <th>Trạng thái</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {filteredGates.map((gate) => (
                  <tr key={gate.id}>
                    <td className="mono"><strong>{gate.id}</strong></td>
                    <td>{gate.name}</td>
                    <td><span className="tmc-badge tmc-badge--neutral">{gate.factoryId === 'FACTORY-BD' ? 'Bình Dương' : 'Long An'}</span></td>
                    <td className="mono">{gate.edgeId}</td>
                    <td>
                      <span className={`tmc-badge ${gate.direction === 'IN' ? 'tmc-badge--accent' : 'tmc-badge--warning'}`}>
                        {gate.direction === 'IN' ? 'VÀO (IN)' : 'RA (OUT)'}
                      </span>
                    </td>
                    <td className="mono">{gate.turnstileApiId}</td>
                    <td>
                      <span className={`tmc-badge ${gate.status === 'ACTIVE' ? 'tmc-badge--success' : gate.status === 'MAINTENANCE' ? 'tmc-badge--warning' : 'tmc-badge--neutral'}`}>
                        {gate.status === 'ACTIVE' ? 'Hoạt động' : gate.status === 'MAINTENANCE' ? 'Bảo trì' : 'Tạm dừng'}
                      </span>
                    </td>
                    <td>
                      <button className="tmc-btn-ghost" onClick={() => handleToggleGateStatus(gate.id)}>
                        Đổi trạng thái
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {showAddGateModal && (
            <div className="tmc-modal-backdrop" role="dialog" aria-modal="true">
              <div className="tmc-modal">
                <span className="module-label">Đăng ký thiết bị</span>
                <h3>Thêm cổng / Turnstile mới</h3>
                <form onSubmit={handleAddGate} className="tmc-form-grid">
                  <div className="tmc-field">
                    <label>Mã cổng (Gate ID)</label>
                    <input placeholder="VD: GATE-BD-03" value={newGate.id} onChange={(e) => setNewGate({ ...newGate, id: e.target.value })} required />
                  </div>
                  <div className="tmc-field">
                    <label>Tên cổng</label>
                    <input placeholder="VD: Cổng kho vật tư 1" value={newGate.name} onChange={(e) => setNewGate({ ...newGate, name: e.target.value })} required />
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                    <div className="tmc-field">
                      <label>Nhà máy</label>
                      <select
                        value={newGate.factoryId}
                        onChange={(e) => setNewGate({ ...newGate, factoryId: e.target.value, edgeId: e.target.value === 'FACTORY-BD' ? 'EDGE-BD-01' : 'EDGE-LA-01' })}
                      >
                        <option value="FACTORY-BD">Bình Dương</option>
                        <option value="FACTORY-LA">Long An</option>
                      </select>
                    </div>
                    <div className="tmc-field">
                      <label>Hướng</label>
                      <select value={newGate.direction} onChange={(e) => setNewGate({ ...newGate, direction: e.target.value })}>
                        <option value="IN">VÀO (IN)</option>
                        <option value="OUT">RA (OUT)</option>
                      </select>
                    </div>
                  </div>
                  <div className="tmc-field">
                    <label>Turnstile API ID (duy nhất)</label>
                    <input placeholder="VD: TURNSTILE-BD-03-IN" value={newGate.turnstileApiId} onChange={(e) => setNewGate({ ...newGate, turnstileApiId: e.target.value })} required />
                  </div>
                  <div className="tmc-modal-actions">
                    <button type="button" className="tmc-btn-secondary" onClick={() => setShowAddGateModal(false)}>Hủy</button>
                    <button type="submit" className="tmc-btn-primary">Lưu cổng</button>
                  </div>
                </form>
              </div>
            </div>
          )}
        </div>
      )}

      {activeTab === 'shifts' && (
        <>
          <div className="notice">
            <strong>Quy tắc chốt kỳ công:</strong> Ca đêm (22:00 – 06:00) kết thúc ngày 01 vẫn thuộc kỳ công tháng trước. Khóa kỳ chính thức từ 06:30 ngày 01 theo giờ nhà máy; xuất HR trước 12:00.
          </div>
          <div className="tmc-card">
            <h3>Danh sách ca làm việc</h3>
            <div className="data-table-scroll" style={{ marginTop: 12 }}>
              <table>
                <thead>
                  <tr>
                    <th>Mã ca</th>
                    <th>Tên ca</th>
                    <th>Nhà máy</th>
                    <th>Bắt đầu</th>
                    <th>Kết thúc</th>
                    <th>Dung sai</th>
                    <th>Phân loại</th>
                  </tr>
                </thead>
                <tbody>
                  {shifts.map((shift) => (
                    <tr key={shift.id}>
                      <td className="mono"><strong>{shift.id}</strong></td>
                      <td>{shift.name}</td>
                      <td>{shift.factoryId === 'FACTORY-BD' ? 'Bình Dương' : 'Long An'}</td>
                      <td className="mono">{shift.startsAt}</td>
                      <td className="mono">{shift.endsAt}</td>
                      <td>±{shift.graceLate} phút</td>
                      <td>
                        <span className={`tmc-badge ${shift.isOvernight ? 'tmc-badge--accent' : 'tmc-badge--neutral'}`}>
                          {shift.isOvernight ? 'Ca đêm (qua ngày)' : 'Ca ngày'}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div className="tmc-card">
            <h3>Ngày nghỉ lễ hưởng lương (2026)</h3>
            <div className="data-table-scroll" style={{ marginTop: 12 }}>
              <table>
                <thead>
                  <tr>
                    <th>Mã lễ</th>
                    <th>Tên ngày lễ</th>
                    <th>Ngày dương lịch</th>
                    <th>Chế độ</th>
                  </tr>
                </thead>
                <tbody>
                  {holidays.map((h) => (
                    <tr key={h.id}>
                      <td className="mono"><strong>{h.id}</strong></td>
                      <td>{h.name}</td>
                      <td className="mono">{h.date}</td>
                      <td><span className="tmc-badge tmc-badge--success">Hưởng 100% lương</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}

      {activeTab === 'configs' && (
        <div className="tmc-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12, marginBottom: 16 }}>
            <div>
              <h3>Tham số vận hành & tích hợp</h3>
              <p style={{ margin: 0, fontSize: 13.5, color: 'var(--muted)' }}>
                Cấu hình múi giờ nhà máy, cửa sổ chống quét lặp và chính sách lưu trữ.
              </p>
            </div>
            {canMutate && (
              <button className="tmc-btn-primary" onClick={handleSaveConfigs}>
                Lưu cấu hình
              </button>
            )}
          </div>
          <div style={{ display: 'grid', gap: 10 }}>
            {configs.map((item) => (
              <div
                key={item.key}
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'minmax(220px, 1.1fr) minmax(180px, 1fr) auto',
                  gap: 12,
                  alignItems: 'center',
                  padding: '12px 14px',
                  background: 'var(--surface-soft)',
                  border: '1px solid var(--border)',
                  borderRadius: 10
                }}
              >
                <div>
                  <strong className="mono" style={{ fontSize: 13 }}>{item.key}</strong>
                  <div style={{ fontSize: 12, color: 'var(--muted)' }}>{item.desc}</div>
                </div>
                <input
                  className="mono"
                  value={item.value}
                  onChange={(e) => setConfigs((prev) => prev.map((c) => (c.key === item.key ? { ...c, value: e.target.value } : c)))}
                />
                <span className="tmc-badge tmc-badge--neutral">{item.category}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {activeTab === 'monitoring' && (
        <>
          <div className="tmc-kpi-grid">
            <div className="tmc-kpi">
              <div className="tmc-kpi-label">Toàn hệ thống</div>
              <div className={`tmc-kpi-value ${loadError ? 'tmc-kpi-value--danger' : onlineEdgeNodes > 0 ? 'tmc-kpi-value--success' : 'tmc-kpi-value--warning'}`}>
                {loadError ? 'MẤT KẾT NỐI' : onlineEdgeNodes > 0 ? 'TRỰC TUYẾN' : 'CHƯA CÓ HEARTBEAT'}
              </div>
            </div>
            <div className="tmc-kpi">
              <div className="tmc-kpi-label">Edge Gate Agents</div>
              <div className="tmc-kpi-value">{onlineEdgeNodes} / {edgeNodes.length} Trạm</div>
            </div>
            <div className="tmc-kpi">
              <div className="tmc-kpi-label">Cổng Turnstile</div>
              <div className="tmc-kpi-value">{activeGatesCount} / {gates.length} Cổng</div>
            </div>
            <div className="tmc-kpi">
              <div className="tmc-kpi-label">Hàng đợi đồng bộ</div>
              <div className="tmc-kpi-value tmc-kpi-value--accent">Chưa nối Edge</div>
            </div>
          </div>

          <div className="tmc-card">
            <h3>Trạng thái thiết bị & Heartbeat</h3>
            <div className="data-table-scroll" style={{ marginTop: 12 }}>
              <table>
                <thead>
                  <tr>
                    <th>Mã thiết bị</th>
                    <th>Loại</th>
                    <th>Tên thiết bị</th>
                    <th>IP</th>
                    <th>Độ trễ</th>
                    <th>Heartbeat</th>
                    <th>Trạng thái</th>
                  </tr>
                </thead>
                <tbody>
                  {devices.map((dev) => (
                    <tr key={dev.deviceId}>
                      <td className="mono"><strong>{dev.deviceId}</strong></td>
                      <td><span className="tmc-badge tmc-badge--neutral">{dev.deviceType}</span></td>
                      <td>{dev.name}</td>
                      <td className="mono">{dev.ip}</td>
                      <td>{dev.ping}</td>
                      <td>{dev.lastHeartbeat}</td>
                      <td><span className="tmc-badge tmc-badge--success">ONLINE</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}

      {activeTab === 'dsr_retention' && (
        <>
          <div className="tmc-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16 }}>
            <div>
              <h3>Chính sách lưu trữ & bảo vệ dữ liệu</h3>
              <p style={{ margin: 0, fontSize: 13.5, color: 'var(--muted)' }}>
                Online tối thiểu 2 năm · Cold Archive 5 năm · Ẩn danh PII sau 36 tháng nghỉ việc · Không xóa sự kiện công gốc.
              </p>
            </div>
            <button
              type="button"
              className="tmc-btn-secondary"
              disabled
              title="Endpoint /api/v1/operations/jobs cần cấu hình job bất đồng bộ & re-auth"
            >
              Lưu trữ tự động (chưa bật)
            </button>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1.6fr) minmax(280px, 1fr)', gap: 18 }}>
            <div className="tmc-card" style={{ marginBottom: 0 }}>
              <h3>Danh sách yêu cầu chủ thể dữ liệu (DSR)</h3>
              <div className="data-table-scroll" style={{ marginTop: 12 }}>
                <table>
                  <thead>
                    <tr>
                      <th>Mã</th>
                      <th>Mã NV</th>
                      <th>Loại</th>
                      <th>Lý do</th>
                      <th>Trạng thái</th>
                      <th>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dsrList.map((item) => (
                      <tr key={item.id}>
                        <td className="mono"><strong>{item.id}</strong></td>
                        <td className="mono">{item.employeeId}</td>
                        <td><span className="tmc-badge tmc-badge--neutral">{item.requestType}</span></td>
                        <td>{item.reason}</td>
                        <td>
                          <span className={`tmc-badge ${item.status === 'COMPLETED' ? 'tmc-badge--success' : 'tmc-badge--warning'}`}>
                            {item.status}
                          </span>
                        </td>
                        <td>
                          {item.status === 'PENDING' && (
                            <button className="tmc-btn-ghost" onClick={() => handleProcessDsr(item.id, 'COMPLETED')}>
                              Hoàn thành
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="tmc-card" style={{ marginBottom: 0, height: 'fit-content' }}>
              <h3>Tiếp nhận yêu cầu DSR mới</h3>
              <form onSubmit={handleAddDsr} className="tmc-form-grid">
                <div className="tmc-field">
                  <label>Mã nhân viên</label>
                  <input placeholder="VD: EMP-0205" value={newDsr.employeeId} onChange={(e) => setNewDsr({ ...newDsr, employeeId: e.target.value })} required />
                </div>
                <div className="tmc-field">
                  <label>Loại yêu cầu</label>
                  <select value={newDsr.requestType} onChange={(e) => setNewDsr({ ...newDsr, requestType: e.target.value })}>
                    <option value="ACCESS">ACCESS (Truy xuất)</option>
                    <option value="RECTIFY">RECTIFY (Đính chính)</option>
                    <option value="ANONYMIZE">ANONYMIZE (Ẩn danh PII)</option>
                  </select>
                </div>
                <div className="tmc-field">
                  <label>Lý do</label>
                  <textarea rows={3} placeholder="Mô tả nội dung yêu cầu…" value={newDsr.reason} onChange={(e) => setNewDsr({ ...newDsr, reason: e.target.value })} />
                </div>
                <button type="submit" className="tmc-btn-primary">Tạo yêu cầu DSR</button>
              </form>
            </div>
          </div>
        </>
      )}
    </section>
  );
}
