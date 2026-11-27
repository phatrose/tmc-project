import React, { useState } from 'react';

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: View Quản trị hệ thống (Nhà máy, Cổng, Ca làm việc, Cấu hình, Giám sát, DSR và Lưu trữ).
 */
export default function AdministrationPage() {
  const [activeTab, setActiveTab] = useState('gates');
  const [selectedFactory, setSelectedFactory] = useState('ALL');
  const [notification, setNotification] = useState(null);

  const [gates, setGates] = useState([
    { id: 'GATE-BD-01', factoryId: 'FACTORY-BD', edgeId: 'EDGE-BD-01', name: 'Cổng chính 1 - Vào', direction: 'IN', turnstileApiId: 'TURNSTILE-BD-01-IN', status: 'ACTIVE' },
    { id: 'GATE-BD-02', factoryId: 'FACTORY-BD', edgeId: 'EDGE-BD-01', name: 'Cổng chính 2 - Ra', direction: 'OUT', turnstileApiId: 'TURNSTILE-BD-02-OUT', status: 'ACTIVE' },
    { id: 'GATE-LA-01', factoryId: 'FACTORY-LA', edgeId: 'EDGE-LA-01', name: 'Cổng xưởng A - Vào', direction: 'IN', turnstileApiId: 'TURNSTILE-LA-01-IN', status: 'ACTIVE' },
    { id: 'GATE-LA-02', factoryId: 'FACTORY-LA', edgeId: 'EDGE-LA-01', name: 'Cổng xưởng A - Ra', direction: 'OUT', turnstileApiId: 'TURNSTILE-LA-02-OUT', status: 'ACTIVE' }
  ]);

  const [newGate, setNewGate] = useState({
    id: '',
    factoryId: 'FACTORY-BD',
    edgeId: 'EDGE-BD-01',
    name: '',
    direction: 'IN',
    turnstileApiId: ''
  });
  const [showAddGateModal, setShowAddGateModal] = useState(false);

  const [shifts] = useState([
    { id: 'SHIFT-BD-01', factoryId: 'FACTORY-BD', name: 'Ca 1 - Hành chính', startsAt: '08:00', endsAt: '17:00', isOvernight: false, graceLate: 15 },
    { id: 'SHIFT-BD-02', factoryId: 'FACTORY-BD', name: 'Ca 2 - Chiều', startsAt: '14:00', endsAt: '22:00', isOvernight: false, graceLate: 15 },
    { id: 'SHIFT-BD-03', factoryId: 'FACTORY-BD', name: 'Ca 3 - Ca đêm', startsAt: '22:00', endsAt: '06:00', isOvernight: true, graceLate: 15 },
    { id: 'SHIFT-LA-01', factoryId: 'FACTORY-LA', name: 'Ca 1 - Hành chính', startsAt: '07:30', endsAt: '16:30', isOvernight: false, graceLate: 15 },
    { id: 'SHIFT-LA-02', factoryId: 'FACTORY-LA', name: 'Ca 2 - Chiều', startsAt: '15:00', endsAt: '23:00', isOvernight: false, graceLate: 15 },
    { id: 'SHIFT-LA-03', factoryId: 'FACTORY-LA', name: 'Ca 3 - Ca đêm', startsAt: '23:00', endsAt: '07:00', isOvernight: true, graceLate: 15 }
  ]);

  const [holidays] = useState([
    { id: 'HOL-01', name: 'Tết Dương lịch', date: '2026-01-01', paid: true },
    { id: 'HOL-02', name: 'Giỗ Tổ Hùng Vương', date: '2026-04-18', paid: true },
    { id: 'HOL-03', name: 'Ngày Chiến thắng 30/4', date: '2026-04-30', paid: true },
    { id: 'HOL-04', name: 'Quốc tế Lao động 1/5', date: '2026-05-01', paid: true },
    { id: 'HOL-05', name: 'Quốc khánh 2/9', date: '2026-09-02', paid: true }
  ]);

  const [configs, setConfigs] = useState([
    { key: 'system.timezone.default', value: 'Asia/Ho_Chi_Minh', category: 'SYSTEM', desc: 'Múi giờ vận hành chốt công nhà máy' },
    { key: 'attendance.duplicate-scan-window-seconds', value: '30', category: 'ATTENDANCE', desc: 'Cửa sổ chống quét lặp nghiệp vụ 30s (BR-03)' },
    { key: 'attendance.period-closing-time', value: '06:30', category: 'ATTENDANCE', desc: 'Giờ chốt kỳ công ngày 01 hàng tháng' },
    { key: 'attendance.hr-export-deadline', value: '12:00', category: 'ATTENDANCE', desc: 'Hạn chót xuất bảng công sang HR ngày 01' },
    { key: 'hr.payroll.endpoint', value: 'https://hr-mock.tmc.internal/api/v1/payroll', category: 'INTEGRATION', desc: 'Endpoint hệ thống HR/Payroll' },
    { key: 'edge.sync.batch-size-max', value: '50', category: 'INTEGRATION', desc: 'Số sự kiện tối đa trong 1 lô sync' },
    { key: 'retention.online-years', value: '2', category: 'RETENTION', desc: 'Số năm lưu trữ trực tuyến tối thiểu' },
    { key: 'retention.archive-years', value: '5', category: 'RETENTION', desc: 'Số năm lưu trữ kho lạnh (Cold Archive)' },
    { key: 'retention.anonymize-inactive-months', value: '36', category: 'RETENTION', desc: 'Thời hạn ẩn danh hồ sơ sau nghỉ việc (BR-11)' }
  ]);

  const [devices] = useState([
    { deviceId: 'EDGE-BD-01', deviceType: 'EDGE_NODE', name: 'Edge Node Bình Dương 01', factoryId: 'FACTORY-BD', status: 'ONLINE', ip: '192.168.10.10', ping: '12ms', lastHeartbeat: 'Vừa xong' },
    { deviceId: 'GATE-BD-01', deviceType: 'GATE', name: 'Turnstile Cổng Chính BD 01 (Vào)', factoryId: 'FACTORY-BD', status: 'ONLINE', ip: '192.168.10.21', ping: '15ms', lastHeartbeat: 'Vừa xong' },
    { deviceId: 'GATE-BD-02', deviceType: 'GATE', name: 'Turnstile Cổng Chính BD 02 (Ra)', factoryId: 'FACTORY-BD', status: 'ONLINE', ip: '192.168.10.22', ping: '14ms', lastHeartbeat: 'Vừa xong' },
    { deviceId: 'EDGE-LA-01', deviceType: 'EDGE_NODE', name: 'Edge Node Long An 01', factoryId: 'FACTORY-LA', status: 'ONLINE', ip: '192.168.20.10', ping: '18ms', lastHeartbeat: 'Vừa xong' },
    { deviceId: 'GATE-LA-01', deviceType: 'GATE', name: 'Turnstile Cổng Xưởng LA 01 (Vào)', factoryId: 'FACTORY-LA', status: 'ONLINE', ip: '192.168.20.21', ping: '20ms', lastHeartbeat: 'Vừa xong' },
    { deviceId: 'GATE-LA-02', deviceType: 'GATE', name: 'Turnstile Cổng Xưởng LA 02 (Ra)', factoryId: 'FACTORY-LA', status: 'ONLINE', ip: '192.168.20.22', ping: '19ms', lastHeartbeat: 'Vừa xong' }
  ]);

  const [dsrList, setDsrList] = useState([
    { id: 'DSR-2026-001', employeeId: 'EMP-0099', requestType: 'ACCESS', status: 'COMPLETED', reason: 'Yêu cầu trích xuất dữ liệu quẹt thẻ cá nhân', processedBy: 'admin_tmc' },
    { id: 'DSR-2026-002', employeeId: 'EMP-0145', requestType: 'ERASURE', status: 'PENDING', reason: 'Nhân viên nghỉ việc yêu cầu ẩn danh thông tin cá nhân', processedBy: '—' }
  ]);
  const [newDsr, setNewDsr] = useState({ employeeId: '', requestType: 'ACCESS', reason: '' });

  const showToast = (msg) => {
    setNotification(msg);
    setTimeout(() => setNotification(null), 4000);
  };

  const handleToggleGateStatus = (gateId) => {
    setGates((prev) =>
      prev.map((g) => {
        if (g.id !== gateId) return g;
        const nextStatus = g.status === 'ACTIVE' ? 'MAINTENANCE' : g.status === 'MAINTENANCE' ? 'INACTIVE' : 'ACTIVE';
        showToast(`Đã chuyển trạng thái cổng ${gateId} sang ${nextStatus}`);
        return { ...g, status: nextStatus };
      })
    );
  };

  const handleAddGate = (e) => {
    e.preventDefault();
    if (!newGate.id || !newGate.turnstileApiId) return;
    if (gates.some((g) => g.turnstileApiId.toLowerCase() === newGate.turnstileApiId.toLowerCase())) {
      window.alert(`Turnstile API ID '${newGate.turnstileApiId}' đã tồn tại!`);
      return;
    }
    setGates((prev) => [...prev, { ...newGate, status: 'ACTIVE' }]);
    setShowAddGateModal(false);
    setNewGate({ id: '', factoryId: 'FACTORY-BD', edgeId: 'EDGE-BD-01', name: '', direction: 'IN', turnstileApiId: '' });
    showToast(`Đăng ký cổng mới ${newGate.id} thành công!`);
  };

  const handleAddDsr = (e) => {
    e.preventDefault();
    if (!newDsr.employeeId) return;
    const item = {
      id: `DSR-2026-${String(dsrList.length + 1).padStart(3, '0')}`,
      employeeId: newDsr.employeeId,
      requestType: newDsr.requestType,
      status: 'PENDING',
      reason: newDsr.reason || 'Yêu cầu của người lao động',
      processedBy: '—'
    };
    setDsrList((prev) => [item, ...prev]);
    setNewDsr({ employeeId: '', requestType: 'ACCESS', reason: '' });
    showToast(`Đã tiếp nhận yêu cầu DSR ${item.id}`);
  };

  const handleProcessDsr = (id, newStatus) => {
    setDsrList((prev) =>
      prev.map((item) => (item.id === id ? { ...item, status: newStatus, processedBy: 'admin_system' } : item))
    );
    showToast(`Đã cập nhật yêu cầu DSR ${id} sang ${newStatus}`);
  };

  const filteredGates = selectedFactory === 'ALL' ? gates : gates.filter((g) => g.factoryId === selectedFactory);

  return (
    <section>
      <p role="status" className="notice">
        <strong>Dữ liệu mô phỏng:</strong> Các thay đổi trên trang quản trị hiện chỉ lưu trong phiên trình duyệt, chưa nối API backend.
      </p>

      <div className="module-heading">
        <div>
          <span className="module-label">Quản trị hệ thống</span>
          <h1>Nhà máy, Cổng, Ca & Vận hành</h1>
          <p>Quản lý hạ tầng cổng kiểm soát, ca làm việc, cấu hình tích hợp, giám sát thiết bị và chính sách dữ liệu.</p>
        </div>
        <span className="tmc-badge tmc-badge--warning">Chế độ mô phỏng</span>
      </div>

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
            <button className="tmc-btn-primary" onClick={() => setShowAddGateModal(true)}>
              + Đăng ký cổng mới
            </button>
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
            <button className="tmc-btn-primary" onClick={() => showToast('Đã lưu cấu hình trong phiên mô phỏng.')}>
              Lưu cấu hình
            </button>
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
              <div className="tmc-kpi-value tmc-kpi-value--success">HOẠT ĐỘNG TỐT</div>
            </div>
            <div className="tmc-kpi">
              <div className="tmc-kpi-label">Edge Gate Agents</div>
              <div className="tmc-kpi-value">2 / 2 Trạm</div>
            </div>
            <div className="tmc-kpi">
              <div className="tmc-kpi-label">Cổng Turnstile</div>
              <div className="tmc-kpi-value">4 / 4 Cổng</div>
            </div>
            <div className="tmc-kpi">
              <div className="tmc-kpi-label">Hàng đợi đồng bộ</div>
              <div className="tmc-kpi-value tmc-kpi-value--accent">0 Lô tồn</div>
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
              className="tmc-btn-primary"
              onClick={() => showToast('Đã kích hoạt tác vụ lưu trữ mô phỏng theo chính sách BR-11.')}
            >
              Kích hoạt lưu trữ
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
