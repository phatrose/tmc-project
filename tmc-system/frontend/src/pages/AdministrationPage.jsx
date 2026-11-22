import React, { useState } from 'react';

/**
 * PHỤ TRÁCH: Task 5.
 * VAI TRÒ: View Quản trị hệ thống (Nhà máy, Cổng, Ca làm việc, Cấu hình, Giám sát, DSR và Lưu trữ).
 * NGUỒN: main.pdf, FR-GS-01..06; FR-SA-01..06; BR-03, BR-08, BR-10, BR-11; DECISIONS.md §1, §6, §7.
 * TÀI LIỆU PDF: docs/tasks/02-requirements-rbac.md; docs/REQUIREMENTS.md.
 */
export default function AdministrationPage() {
  const [activeTab, setActiveTab] = useState('gates');
  const [selectedFactory, setSelectedFactory] = useState('ALL');
  const [notification, setNotification] = useState(null);

  // =========================================================================
  // Dữ liệu mẫu khởi tạo (State)
  // =========================================================================

  // 1. Quản lý cổng (FR-GS-01)
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

  // 2. Ca làm việc & Ngày lễ (FR-SA-02, DECISIONS.md §1)
  const [shifts, setShifts] = useState([
    { id: 'SHIFT-BD-01', factoryId: 'FACTORY-BD', name: 'Ca 1 - Hành chính', startsAt: '08:00', endsAt: '17:00', isOvernight: false, graceLate: 15, graceEarly: 15, active: true },
    { id: 'SHIFT-BD-02', factoryId: 'FACTORY-BD', name: 'Ca 2 - Chiều', startsAt: '14:00', endsAt: '22:00', isOvernight: false, graceLate: 15, graceEarly: 15, active: true },
    { id: 'SHIFT-BD-03', factoryId: 'FACTORY-BD', name: 'Ca 3 - Ca đêm', startsAt: '22:00', endsAt: '06:00', isOvernight: true, graceLate: 15, graceEarly: 15, active: true },
    { id: 'SHIFT-LA-01', factoryId: 'FACTORY-LA', name: 'Ca 1 - Hành chính', startsAt: '07:30', endsAt: '16:30', isOvernight: false, graceLate: 15, graceEarly: 15, active: true },
    { id: 'SHIFT-LA-02', factoryId: 'FACTORY-LA', name: 'Ca 2 - Chiều', startsAt: '15:00', endsAt: '23:00', isOvernight: false, graceLate: 15, graceEarly: 15, active: true },
    { id: 'SHIFT-LA-03', factoryId: 'FACTORY-LA', name: 'Ca 3 - Ca đêm', startsAt: '23:00', endsAt: '07:00', isOvernight: true, graceLate: 15, graceEarly: 15, active: true }
  ]);

  const [holidays, setHolidays] = useState([
    { id: 'HOL-01', name: 'Tết Dương lịch', date: '2026-01-01', paid: true },
    { id: 'HOL-02', name: 'Giỗ Tổ Hùng Vương', date: '2026-04-18', paid: true },
    { id: 'HOL-03', name: 'Ngày Chiến thắng 30/4', date: '2026-04-30', paid: true },
    { id: 'HOL-04', name: 'Quốc tế Lao động 1/5', date: '2026-05-01', paid: true },
    { id: 'HOL-05', name: 'Quốc khánh 2/9', date: '2026-09-02', paid: true }
  ]);

  // 3. Tham số cấu hình hệ thống (FR-SA-02, FR-SA-03)
  const [configs, setConfigs] = useState([
    { key: 'system.timezone.default', value: 'Asia/Ho_Chi_Minh', category: 'SYSTEM', desc: 'Múi giờ vận hành chốt công nhà máy' },
    { key: 'attendance.duplicate-scan-window-seconds', value: '30', category: 'ATTENDANCE', desc: 'Cửa sổ chống quét lặp nghiệp vụ 30s (BR-03)' },
    { key: 'attendance.period-closing-time', value: '06:30', category: 'ATTENDANCE', desc: 'Giờ chốt kỳ công ngày 01 hàng tháng' },
    { key: 'attendance.hr-export-deadline', value: '12:00', category: 'ATTENDANCE', desc: 'Hạn chót xuất bảng công sang HR ngày 01' },
    { key: 'hr.payroll.endpoint', value: 'https://hr-mock.tmc.internal/api/v1/payroll', category: 'INTEGRATION', desc: 'Endpoint hệ thống HR/Payroll' },
    { key: 'hr.payroll.timeout-ms', value: '5000', category: 'INTEGRATION', desc: 'Timeout gọi hệ thống HR (ms)' },
    { key: 'hr.payroll.retry-max-attempts', value: '3', category: 'INTEGRATION', desc: 'Số lần thử lại khi gọi HR' },
    { key: 'edge.sync.batch-size-max', value: '50', category: 'INTEGRATION', desc: 'Số sự kiện tối đa trong 1 lô sync (FR-OS-04)' },
    { key: 'retention.online-years', value: '2', category: 'RETENTION', desc: 'Số năm lưu trữ trực tuyến tối thiểu (OQ-05)' },
    { key: 'retention.archive-years', value: '5', category: 'RETENTION', desc: 'Số năm lưu trữ kho lạnh (Cold Archive)' },
    { key: 'retention.anonymize-inactive-months', value: '36', category: 'RETENTION', desc: 'Thời hạn ẩn danh hồ sơ sau nghỉ việc (BR-11)' }
  ]);

  // 4. Giám sát thiết bị & Sức khỏe (FR-SA-04, FR-GS-04)
  const [devices, setDevices] = useState([
    { deviceId: 'EDGE-BD-01', deviceType: 'EDGE_NODE', name: 'Edge Node Bình Dương 01', factoryId: 'FACTORY-BD', status: 'ONLINE', ip: '192.168.10.10', ping: '12ms', lastHeartbeat: 'Vừa xong' },
    { deviceId: 'GATE-BD-01', deviceType: 'GATE', name: 'Turnstile Cổng Chính BD 01 (Vào)', factoryId: 'FACTORY-BD', status: 'ONLINE', ip: '192.168.10.21', ping: '15ms', lastHeartbeat: 'Vừa xong' },
    { deviceId: 'GATE-BD-02', deviceType: 'GATE', name: 'Turnstile Cổng Chính BD 02 (Ra)', factoryId: 'FACTORY-BD', status: 'ONLINE', ip: '192.168.10.22', ping: '14ms', lastHeartbeat: 'Vừa xong' },
    { deviceId: 'EDGE-LA-01', deviceType: 'EDGE_NODE', name: 'Edge Node Long An 01', factoryId: 'FACTORY-LA', status: 'ONLINE', ip: '192.168.20.10', ping: '18ms', lastHeartbeat: 'Vừa xong' },
    { deviceId: 'GATE-LA-01', deviceType: 'GATE', name: 'Turnstile Cổng Xưởng LA 01 (Vào)', factoryId: 'FACTORY-LA', status: 'ONLINE', ip: '192.168.20.21', ping: '20ms', lastHeartbeat: 'Vừa xong' },
    { deviceId: 'GATE-LA-02', deviceType: 'GATE', name: 'Turnstile Cổng Xưởng LA 02 (Ra)', factoryId: 'FACTORY-LA', status: 'ONLINE', ip: '192.168.20.22', ping: '19ms', lastHeartbeat: 'Vừa xong' }
  ]);

  // 5. Yêu cầu chủ thể dữ liệu DSR (FR-SA-06, BR-11)
  const [dsrList, setDsrList] = useState([
    { id: 'DSR-2026-001', employeeId: 'EMP-0099', requestType: 'ACCESS', status: 'COMPLETED', reason: 'Yêu cầu trích xuất dữ liệu quẹt thẻ cá nhân', requestedAt: '2026-11-20 09:15', processedBy: 'admin_tmc', notes: 'Đã xuất dữ liệu theo đúng phạm vi nhân viên.' },
    { id: 'DSR-2026-002', employeeId: 'EMP-0145', requestType: 'ERASURE', status: 'PENDING', reason: 'Nhân viên nghỉ việc yêu cầu ẩn danh thông tin cá nhân', requestedAt: '2026-11-21 14:30', processedBy: '-', notes: 'Chờ đối soát theo BR-11.' }
  ]);

  const [newDsr, setNewDsr] = useState({ employeeId: '', requestType: 'ACCESS', reason: '' });

  // =========================================================================
  // Xử lý sự kiện (Handlers)
  // =========================================================================

  const showToast = (msg) => {
    setNotification(msg);
    setTimeout(() => setNotification(null), 4000);
  };

  const handleToggleGateStatus = (gateId) => {
    setGates(prev => prev.map(g => {
      if (g.id === gateId) {
        const nextStatus = g.status === 'ACTIVE' ? 'MAINTENANCE' : (g.status === 'MAINTENANCE' ? 'INACTIVE' : 'ACTIVE');
        showToast(`Đã chuyển trạng thái cổng ${gateId} thành: ${nextStatus}`);
        return { ...g, status: nextStatus };
      }
      return g;
    }));
  };

  const handleAddGate = (e) => {
    e.preventDefault();
    if (!newGate.id || !newGate.turnstileApiId) {
      alert('Vui lòng nhập đầy đủ Mã Cổng và Turnstile API ID!');
      return;
    }
    // Kiểm tra trùng turnstile_api_id (§5.1 UK constraint)
    if (gates.some(g => g.turnstileApiId.toLowerCase() === newGate.turnstileApiId.toLowerCase())) {
      alert(`Turnstile API ID '${newGate.turnstileApiId}' đã tồn tại trong hệ thống! (Ràng buộc duy nhất §5.1)`);
      return;
    }
    const createdGate = {
      ...newGate,
      status: 'ACTIVE'
    };
    setGates(prev => [...prev, createdGate]);
    setShowAddGateModal(false);
    setNewGate({ id: '', factoryId: 'FACTORY-BD', edgeId: 'EDGE-BD-01', name: '', direction: 'IN', turnstileApiId: '' });
    showToast(`Đăng ký cổng mới ${createdGate.id} thành công!`);
  };

  const handleConfigChange = (key, val) => {
    setConfigs(prev => prev.map(c => c.key === key ? { ...c, value: val } : c));
  };

  const handleSaveConfigs = () => {
    showToast('Đã lưu toàn bộ cấu hình tham số hệ thống thành công!');
  };

  const handleTriggerArchival = () => {
    showToast('Đã kích hoạt tác vụ sao lưu và ẩn danh dữ liệu theo chính sách BR-11 (Lưu kho lạnh 1.250 bản ghi, ẩn danh 12 hồ sơ quá hạn).');
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
      requestedAt: 'Vừa xong',
      processedBy: '-',
      notes: 'Đang chờ xử lý'
    };
    setDsrList(prev => [item, ...prev]);
    setNewDsr({ employeeId: '', requestType: 'ACCESS', reason: '' });
    showToast(`Đã tiếp nhận yêu cầu DSR mã ${item.id} cho nhân viên ${item.employeeId}`);
  };

  const handleProcessDsr = (id, newStatus) => {
    setDsrList(prev => prev.map(item => {
      if (item.id === id) {
        const note = item.requestType === 'ERASURE' && newStatus === 'COMPLETED'
          ? 'Đã ẩn danh hồ sơ nhân sự theo BR-11. Dữ liệu sự kiện công bất biến theo BR-04.'
          : `Đã cập nhật trạng thái: ${newStatus}`;
        return { ...item, status: newStatus, processedBy: 'admin_system', notes: note };
      }
      return item;
    }));
    showToast(`Đã cập nhật trạng thái yêu cầu DSR ${id} sang ${newStatus}`);
  };

  const filteredGates = selectedFactory === 'ALL'
    ? gates
    : gates.filter(g => g.factoryId === selectedFactory);

  // =========================================================================
  // Giao diện (Render)
  // =========================================================================

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', fontFamily: 'system-ui, -apple-system, sans-serif' }}>
      {/* Header trang */}
      <header style={{ marginBottom: '24px', borderBottom: '1px solid #e2e8f0', paddingBottom: '16px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <h1 style={{ margin: '0 0 6px 0', fontSize: '26px', color: '#1e293b' }}>Quản Trị Hệ Thống</h1>
            <p style={{ margin: 0, color: '#64748b', fontSize: '14px' }}>
              Quản lý nhà máy, trạm cổng, ca làm việc, cấu hình tích hợp, giám sát thiết bị và chính sách dữ liệu (Task 5).
            </p>
          </div>
          <div style={{ display: 'flex', gap: '10px' }}>
            <span style={{ display: 'inline-flex', alignItems: 'center', padding: '6px 12px', background: '#dcfce7', color: '#166534', borderRadius: '16px', fontSize: '13px', fontWeight: '500' }}>
              ● Trạng thái: Bình thường (HEALTHY)
            </span>
          </div>
        </div>

        {/* Thông báo dạng Toast */}
        {notification && (
          <div style={{ marginTop: '14px', padding: '10px 16px', background: '#e0f2fe', color: '#0369a1', borderRadius: '6px', fontSize: '14px', border: '1px solid #bae6fd' }}>
            ℹ️ {notification}
          </div>
        )}

        {/* Thanh chuyển Tabs */}
        <nav style={{ display: 'flex', gap: '8px', marginTop: '20px' }}>
          {[
            { id: 'gates', label: '🚪 Nhà Máy & Cổng (FR-GS-01)' },
            { id: 'shifts', label: '⏰ Ca & Ngày Lễ (FR-SA-02)' },
            { id: 'configs', label: '⚙️ Tham Số & Tích Hợp (FR-SA-03)' },
            { id: 'monitoring', label: '📡 Giám Sát Thiết Bị (FR-SA-04)' },
            { id: 'dsr_retention', label: '🛡️ Quyền DSR & Lưu Trữ (FR-SA-05,06)' }
          ].map(tab => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              style={{
                padding: '10px 16px',
                borderRadius: '6px',
                border: activeTab === tab.id ? '2px solid #2563eb' : '1px solid #cbd5e1',
                background: activeTab === tab.id ? '#eff6ff' : '#ffffff',
                color: activeTab === tab.id ? '#1d4ed8' : '#475569',
                fontWeight: activeTab === tab.id ? '600' : '400',
                cursor: 'pointer',
                fontSize: '14px'
              }}
            >
              {tab.label}
            </button>
          ))}
        </nav>
      </header>

      {/* =====================================================================
          TAB 1: NHÀ MÁY & CỔNG (FR-GS-01)
          ===================================================================== */}
      {activeTab === 'gates' && (
        <section>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
            <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
              <label style={{ fontSize: '14px', fontWeight: '500', color: '#334155' }}>Chọn Nhà máy:</label>
              <select
                value={selectedFactory}
                onChange={e => setSelectedFactory(e.target.value)}
                style={{ padding: '6px 12px', borderRadius: '6px', border: '1px solid #cbd5e1', fontSize: '14px' }}
              >
                <option value="ALL">Tất cả nhà máy (Bình Dương & Long An)</option>
                <option value="FACTORY-BD">Nhà máy Bình Dương (FACTORY-BD, ~800 NV)</option>
                <option value="FACTORY-LA">Nhà máy Long An (FACTORY-LA, ~600 NV)</option>
              </select>
            </div>
            <button
              onClick={() => setShowAddGateModal(true)}
              style={{ background: '#2563eb', color: '#fff', border: 'none', padding: '8px 16px', borderRadius: '6px', cursor: 'pointer', fontSize: '14px', fontWeight: '500' }}
            >
              + Đăng ký Cổng mới
            </button>
          </div>

          {/* Bảng danh sách cổng */}
          <div style={{ overflowX: 'auto', background: '#fff', borderRadius: '8px', border: '1px solid #e2e8f0', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '14px' }}>
              <thead>
                <tr style={{ background: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#475569' }}>
                  <th style={{ padding: '12px 16px' }}>Mã Cổng (Gate ID)</th>
                  <th style={{ padding: '12px 16px' }}>Tên Cổng</th>
                  <th style={{ padding: '12px 16px' }}>Nhà Máy</th>
                  <th style={{ padding: '12px 16px' }}>Trạm Edge</th>
                  <th style={{ padding: '12px 16px' }}>Hướng</th>
                  <th style={{ padding: '12px 16px' }}>Turnstile API ID (UK)</th>
                  <th style={{ padding: '12px 16px' }}>Trạng Thái</th>
                  <th style={{ padding: '12px 16px' }}>Thao Tác</th>
                </tr>
              </thead>
              <tbody>
                {filteredGates.map(gate => (
                  <tr key={gate.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <td style={{ padding: '12px 16px', fontWeight: '600', color: '#0f172a' }}>{gate.id}</td>
                    <td style={{ padding: '12px 16px' }}>{gate.name}</td>
                    <td style={{ padding: '12px 16px' }}>
                      <span style={{ padding: '3px 8px', background: '#f1f5f9', borderRadius: '4px', fontSize: '12px' }}>
                        {gate.factoryId === 'FACTORY-BD' ? 'Bình Dương' : 'Long An'}
                      </span>
                    </td>
                    <td style={{ padding: '12px 16px', color: '#64748b' }}>{gate.edgeId}</td>
                    <td style={{ padding: '12px 16px' }}>
                      <span style={{
                        padding: '4px 8px',
                        borderRadius: '4px',
                        fontSize: '12px',
                        fontWeight: '600',
                        background: gate.direction === 'IN' ? '#ecfdf5' : '#eff6ff',
                        color: gate.direction === 'IN' ? '#047857' : '#1d4ed8'
                      }}>
                        {gate.direction === 'IN' ? '↓ VÀO (IN)' : '↑ RA (OUT)'}
                      </span>
                    </td>
                    <td style={{ padding: '12px 16px', fontFamily: 'monospace', color: '#475569' }}>{gate.turnstileApiId}</td>
                    <td style={{ padding: '12px 16px' }}>
                      <span style={{
                        padding: '4px 10px',
                        borderRadius: '12px',
                        fontSize: '12px',
                        fontWeight: '500',
                        background: gate.status === 'ACTIVE' ? '#dcfce7' : (gate.status === 'MAINTENANCE' ? '#fef3c7' : '#f1f5f9'),
                        color: gate.status === 'ACTIVE' ? '#15803d' : (gate.status === 'MAINTENANCE' ? '#b45309' : '#64748b')
                      }}>
                        {gate.status === 'ACTIVE' ? 'Hoạt động' : (gate.status === 'MAINTENANCE' ? 'Bảo trì' : 'Tạm dừng')}
                      </span>
                    </td>
                    <td style={{ padding: '12px 16px' }}>
                      <button
                        onClick={() => handleToggleGateStatus(gate.id)}
                        style={{
                          padding: '4px 10px',
                          fontSize: '12px',
                          border: '1px solid #cbd5e1',
                          background: '#fff',
                          borderRadius: '4px',
                          cursor: 'pointer'
                        }}
                      >
                        Đổi trạng thái
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Modal Đăng ký Cổng mới */}
          {showAddGateModal && (
            <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 100 }}>
              <div style={{ background: '#fff', padding: '24px', borderRadius: '8px', width: '480px', maxWidth: '90%' }}>
                <h3 style={{ marginTop: 0, marginBottom: '16px', color: '#1e293b' }}>Đăng Ký Cổng / Turnstile Mới</h3>
                <form onSubmit={handleAddGate} style={{ display: 'grid', gap: '14px' }}>
                  <div>
                    <label style={{ display: 'block', fontSize: '13px', marginBottom: '4px', fontWeight: '500' }}>Mã Cổng (Gate ID):</label>
                    <input
                      placeholder="VD: GATE-BD-03"
                      value={newGate.id}
                      onChange={e => setNewGate({ ...newGate, id: e.target.value })}
                      style={{ width: '100%', padding: '8px', borderRadius: '4px', border: '1px solid #cbd5e1' }}
                      required
                    />
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: '13px', marginBottom: '4px', fontWeight: '500' }}>Tên Cổng:</label>
                    <input
                      placeholder="VD: Cổng Kho Vật Tư 1"
                      value={newGate.name}
                      onChange={e => setNewGate({ ...newGate, name: e.target.value })}
                      style={{ width: '100%', padding: '8px', borderRadius: '4px', border: '1px solid #cbd5e1' }}
                      required
                    />
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                    <div>
                      <label style={{ display: 'block', fontSize: '13px', marginBottom: '4px', fontWeight: '500' }}>Nhà máy:</label>
                      <select
                        value={newGate.factoryId}
                        onChange={e => setNewGate({ ...newGate, factoryId: e.target.value, edgeId: e.target.value === 'FACTORY-BD' ? 'EDGE-BD-01' : 'EDGE-LA-01' })}
                        style={{ width: '100%', padding: '8px', borderRadius: '4px', border: '1px solid #cbd5e1' }}
                      >
                        <option value="FACTORY-BD">Bình Dương</option>
                        <option value="FACTORY-LA">Long An</option>
                      </select>
                    </div>
                    <div>
                      <label style={{ display: 'block', fontSize: '13px', marginBottom: '4px', fontWeight: '500' }}>Hướng:</label>
                      <select
                        value={newGate.direction}
                        onChange={e => setNewGate({ ...newGate, direction: e.target.value })}
                        style={{ width: '100%', padding: '8px', borderRadius: '4px', border: '1px solid #cbd5e1' }}
                      >
                        <option value="IN">VÀO (IN)</option>
                        <option value="OUT">RA (OUT)</option>
                      </select>
                    </div>
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: '13px', marginBottom: '4px', fontWeight: '500' }}>Turnstile API ID (Duy nhất §5.1):</label>
                    <input
                      placeholder="VD: TURNSTILE-BD-03-IN"
                      value={newGate.turnstileApiId}
                      onChange={e => setNewGate({ ...newGate, turnstileApiId: e.target.value })}
                      style={{ width: '100%', padding: '8px', borderRadius: '4px', border: '1px solid #cbd5e1' }}
                      required
                    />
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '12px' }}>
                    <button
                      type="button"
                      onClick={() => setShowAddGateModal(false)}
                      style={{ padding: '8px 14px', borderRadius: '4px', border: '1px solid #cbd5e1', background: '#fff', cursor: 'pointer' }}
                    >
                      Hủy
                    </button>
                    <button
                      type="submit"
                      style={{ padding: '8px 16px', borderRadius: '4px', border: 'none', background: '#2563eb', color: '#fff', fontWeight: '500', cursor: 'pointer' }}
                    >
                      Xác Nhận Lưu
                    </button>
                  </div>
                </form>
              </div>
            </div>
          )}
        </section>
      )}

      {/* =====================================================================
          TAB 2: CA LÀM VIỆC & NGÀY NGHỈ LỄ (FR-SA-02)
          ===================================================================== */}
      {activeTab === 'shifts' && (
        <section>
          {/* Banner quy tắc chốt kỳ công */}
          <div style={{ background: '#fffbeb', border: '1px solid #fef3c7', borderRadius: '8px', padding: '14px 18px', marginBottom: '20px' }}>
            <h4 style={{ margin: '0 0 6px 0', color: '#92400e', fontSize: '15px' }}>
              📌 Quy tắc Nghiệp vụ Chốt Kỳ Công (DECISIONS.md §1)
            </h4>
            <p style={{ margin: 0, fontSize: '13px', color: '#78350f', lineHeight: '1.5' }}>
              • <strong>Ca đêm (22:00 – 06:00)</strong> bắt đầu cuối tháng trước và kết thúc lúc 06:00 ngày 01 vẫn tính vào <strong>kỳ công của tháng trước</strong>.<br/>
              • <strong>Tổng hợp & Khóa kỳ chính thức</strong>: Bắt đầu <strong>06:30 ngày 01</strong> theo múi giờ nhà máy (không khóa lúc 23:00 đêm).<br/>
              • <strong>Thời hạn xuất dữ liệu sang HR/Payroll</strong>: Hoàn tất đối soát trước <strong>12:00 ngày 01</strong>.
            </p>
          </div>

          <h3 style={{ fontSize: '17px', color: '#1e293b', marginBottom: '12px' }}>Danh Sách Ca Làm Việc Theo Nhà Máy</h3>
          <div style={{ overflowX: 'auto', background: '#fff', borderRadius: '8px', border: '1px solid #e2e8f0', marginBottom: '28px' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '14px' }}>
              <thead>
                <tr style={{ background: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#475569' }}>
                  <th style={{ padding: '12px 16px' }}>Mã Ca</th>
                  <th style={{ padding: '12px 16px' }}>Tên Ca Làm Việc</th>
                  <th style={{ padding: '12px 16px' }}>Nhà Máy</th>
                  <th style={{ padding: '12px 16px' }}>Giờ Bắt Đầu</th>
                  <th style={{ padding: '12px 16px' }}>Giờ Kết Thúc</th>
                  <th style={{ padding: '12px 16px' }}>Dung Sai Đi Trễ / Về Sớm</th>
                  <th style={{ padding: '12px 16px' }}>Phân Loại</th>
                </tr>
              </thead>
              <tbody>
                {shifts.map(shift => (
                  <tr key={shift.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <td style={{ padding: '12px 16px', fontWeight: '600' }}>{shift.id}</td>
                    <td style={{ padding: '12px 16px' }}>{shift.name}</td>
                    <td style={{ padding: '12px 16px' }}>
                      {shift.factoryId === 'FACTORY-BD' ? 'Bình Dương' : 'Long An'}
                    </td>
                    <td style={{ padding: '12px 16px', fontWeight: '500', color: '#0f172a' }}>{shift.startsAt}</td>
                    <td style={{ padding: '12px 16px', fontWeight: '500', color: '#0f172a' }}>{shift.endsAt}</td>
                    <td style={{ padding: '12px 16px' }}>±{shift.graceLate} phút</td>
                    <td style={{ padding: '12px 16px' }}>
                      {shift.isOvernight ? (
                        <span style={{ padding: '3px 8px', background: '#ede9fe', color: '#6d28d9', borderRadius: '4px', fontSize: '12px', fontWeight: '500' }}>
                          🌙 Ca Đêm (Qua ngày)
                        </span>
                      ) : (
                        <span style={{ padding: '3px 8px', background: '#f1f5f9', color: '#475569', borderRadius: '4px', fontSize: '12px' }}>
                          ☀️ Ca Ngày
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <h3 style={{ fontSize: '17px', color: '#1e293b', marginBottom: '12px' }}>Danh Mục Ngày Nghỉ Lễ Hưởng Lương (Năm 2026)</h3>
          <div style={{ overflowX: 'auto', background: '#fff', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '14px' }}>
              <thead>
                <tr style={{ background: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#475569' }}>
                  <th style={{ padding: '12px 16px' }}>Mã Lễ</th>
                  <th style={{ padding: '12px 16px' }}>Tên Ngày Lễ</th>
                  <th style={{ padding: '12px 16px' }}>Ngày Dương Lịch</th>
                  <th style={{ padding: '12px 16px' }}>Chế Độ</th>
                </tr>
              </thead>
              <tbody>
                {holidays.map(h => (
                  <tr key={h.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <td style={{ padding: '12px 16px', fontWeight: '600' }}>{h.id}</td>
                    <td style={{ padding: '12px 16px' }}>{h.name}</td>
                    <td style={{ padding: '12px 16px', fontFamily: 'monospace' }}>{h.date}</td>
                    <td style={{ padding: '12px 16px' }}>
                      <span style={{ padding: '3px 8px', background: '#dcfce7', color: '#166534', borderRadius: '4px', fontSize: '12px' }}>
                        Hưởng 100% lương
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {/* =====================================================================
          TAB 3: THAM SỐ CẤU HÌNH & TÍCH HỢP (FR-SA-03)
          ===================================================================== */}
      {activeTab === 'configs' && (
        <section>
          <div style={{ background: '#fff', borderRadius: '8px', border: '1px solid #e2e8f0', padding: '20px', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
              <div>
                <h3 style={{ margin: '0 0 4px 0', fontSize: '17px', color: '#1e293b' }}>Tham Số Vận Hành & Tích Hợp Hệ Thống</h3>
                <p style={{ margin: 0, color: '#64748b', fontSize: '13px' }}>Cấu hình các tham số kỹ thuật, giới hạn thời gian, quy tắc chống quét lặp và endpoint bên ngoài.</p>
              </div>
              <button
                onClick={handleSaveConfigs}
                style={{ background: '#16a34a', color: '#fff', border: 'none', padding: '8px 18px', borderRadius: '6px', cursor: 'pointer', fontSize: '14px', fontWeight: '500' }}
              >
                💾 Lưu Cấu Hình
              </button>
            </div>

            <div style={{ display: 'grid', gap: '16px' }}>
              {configs.map(item => (
                <div key={item.key} style={{ display: 'grid', gridTemplateColumns: '280px 1fr 140px', alignItems: 'center', padding: '10px 14px', background: '#f8fafc', borderRadius: '6px', border: '1px solid #e2e8f0' }}>
                  <div>
                    <span style={{ fontFamily: 'monospace', fontWeight: '600', color: '#1e293b', fontSize: '13px' }}>{item.key}</span>
                    <p style={{ margin: '2px 0 0 0', fontSize: '12px', color: '#64748b' }}>{item.desc}</p>
                  </div>
                  <div style={{ padding: '0 16px' }}>
                    <input
                      value={item.value}
                      onChange={e => handleConfigChange(item.key, e.target.value)}
                      style={{ width: '100%', padding: '6px 10px', borderRadius: '4px', border: '1px solid #cbd5e1', fontSize: '13px', fontFamily: 'monospace' }}
                    />
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <span style={{ fontSize: '11px', padding: '3px 8px', borderRadius: '4px', background: '#e2e8f0', color: '#475569', fontWeight: '500' }}>
                      {item.category}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>
      )}

      {/* =====================================================================
          TAB 4: GIÁM SÁT SỨC KHỎE & THIẾT BỊ (FR-SA-04)
          ===================================================================== */}
      {activeTab === 'monitoring' && (
        <section>
          {/* Dashboard chỉ số */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '16px', marginBottom: '24px' }}>
            <div style={{ background: '#fff', padding: '18px', borderRadius: '8px', border: '1px solid #e2e8f0', boxShadow: '0 1px 2px rgba(0,0,0,0.05)' }}>
              <div style={{ fontSize: '13px', color: '#64748b', marginBottom: '6px' }}>Trạng Thái Toàn Hệ Thống</div>
              <div style={{ fontSize: '22px', fontWeight: '700', color: '#16a34a' }}>HOẠT ĐỘNG TỐT</div>
              <div style={{ fontSize: '12px', color: '#059669', marginTop: '4px' }}>Tất cả Edge Node & Cổng Online</div>
            </div>
            <div style={{ background: '#fff', padding: '18px', borderRadius: '8px', border: '1px solid #e2e8f0', boxShadow: '0 1px 2px rgba(0,0,0,0.05)' }}>
              <div style={{ fontSize: '13px', color: '#64748b', marginBottom: '6px' }}>Edge Gate Agents</div>
              <div style={{ fontSize: '22px', fontWeight: '700', color: '#0f172a' }}>2 / 2 Trạm</div>
              <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>BD: 192.168.10.10, LA: 192.168.20.10</div>
            </div>
            <div style={{ background: '#fff', padding: '18px', borderRadius: '8px', border: '1px solid #e2e8f0', boxShadow: '0 1px 2px rgba(0,0,0,0.05)' }}>
              <div style={{ fontSize: '13px', color: '#64748b', marginBottom: '6px' }}>Cổng Kiểm Soát (Turnstiles)</div>
              <div style={{ fontSize: '22px', fontWeight: '700', color: '#0f172a' }}>4 / 4 Cổng</div>
              <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px' }}>100% sẵn sàng quét QR</div>
            </div>
            <div style={{ background: '#fff', padding: '18px', borderRadius: '8px', border: '1px solid #e2e8f0', boxShadow: '0 1px 2px rgba(0,0,0,0.05)' }}>
              <div style={{ fontSize: '13px', color: '#64748b', marginBottom: '6px' }}>Hàng Đợi Đồng Bộ (Sync Queue)</div>
              <div style={{ fontSize: '22px', fontWeight: '700', color: '#0f172a' }}>0 Lô Tồn Đọng</div>
              <div style={{ fontSize: '12px', color: '#16a34a', marginTop: '4px' }}>Đồng bộ liên tục ≤60 giây</div>
            </div>
          </div>

          <h3 style={{ fontSize: '17px', color: '#1e293b', marginBottom: '12px' }}>Chi Tiết Trạng Thái Thiết Bị & Nhịp Tim (Heartbeat)</h3>
          <div style={{ overflowX: 'auto', background: '#fff', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '14px' }}>
              <thead>
                <tr style={{ background: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#475569' }}>
                  <th style={{ padding: '12px 16px' }}>Mã Thiết Bị</th>
                  <th style={{ padding: '12px 16px' }}>Loại</th>
                  <th style={{ padding: '12px 16px' }}>Tên Thiết Bị</th>
                  <th style={{ padding: '12px 16px' }}>Địa chỉ IP</th>
                  <th style={{ padding: '12px 16px' }}>Độ Trễ</th>
                  <th style={{ padding: '12px 16px' }}>Heartbeat Gần Nhất</th>
                  <th style={{ padding: '12px 16px' }}>Trạng Thái</th>
                </tr>
              </thead>
              <tbody>
                {devices.map(dev => (
                  <tr key={dev.deviceId} style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <td style={{ padding: '12px 16px', fontWeight: '600', fontFamily: 'monospace' }}>{dev.deviceId}</td>
                    <td style={{ padding: '12px 16px' }}>
                      <span style={{ padding: '3px 8px', background: '#f1f5f9', borderRadius: '4px', fontSize: '12px' }}>
                        {dev.deviceType}
                      </span>
                    </td>
                    <td style={{ padding: '12px 16px' }}>{dev.name}</td>
                    <td style={{ padding: '12px 16px', fontFamily: 'monospace', color: '#475569' }}>{dev.ip}</td>
                    <td style={{ padding: '12px 16px', color: '#16a34a' }}>{dev.ping}</td>
                    <td style={{ padding: '12px 16px', color: '#64748b' }}>{dev.lastHeartbeat}</td>
                    <td style={{ padding: '12px 16px' }}>
                      <span style={{ padding: '3px 10px', borderRadius: '12px', fontSize: '12px', fontWeight: '500', background: '#dcfce7', color: '#166534' }}>
                        ● ONLINE
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {/* =====================================================================
          TAB 5: QUYỀN CHỦ THỂ DỮ LIỆU (DSR) & CHÍNH SÁCH LƯU TRỮ (FR-SA-05,06)
          ===================================================================== */}
      {activeTab === 'dsr_retention' && (
        <section>
          {/* Khung chính sách lưu trữ */}
          <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '8px', padding: '18px', marginBottom: '24px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <h3 style={{ margin: '0 0 6px 0', fontSize: '16px', color: '#0f172a' }}>
                  Chính Sách Lưu Trữ Dữ Liệu & Tuân Thủ (Retention & Privacy Policy)
                </h3>
                <p style={{ margin: 0, fontSize: '13px', color: '#475569', lineHeight: '1.5' }}>
                  • <strong>Lưu trữ trực tuyến (Online Storage)</strong>: Tối thiểu <strong>2 năm</strong> trên PostgreSQL phục vụ đối soát và báo cáo (OQ-05).<br/>
                  • <strong>Lưu trữ kho lạnh (Cold Archival)</strong>: <strong>5 năm</strong> theo quy định kiểm toán.<br/>
                  • <strong>Quy tắc bất biến & Ẩn danh (BR-04, BR-11)</strong>: Hồ sơ nhân viên nghỉ việc ẩn danh thông tin PII sau 36 tháng; sự kiện chấm công (AttendanceEvent) <strong>tuyệt đối không bị xóa hoặc sửa đổi</strong>.
                </p>
              </div>
              <button
                onClick={handleTriggerArchival}
                style={{ padding: '8px 16px', background: '#0284c7', color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontSize: '13px', fontWeight: '500' }}
              >
                📦 Kích Hoạt Lưu Trữ Ngay
              </button>
            </div>
          </div>

          {/* Quản lý yêu cầu chủ thể dữ liệu (DSR) */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: '24px' }}>
            <div>
              <h3 style={{ fontSize: '17px', color: '#1e293b', marginBottom: '12px' }}>
                Danh Sách Yêu Cầu Của Chủ Thể Dữ Liệu (DSR)
              </h3>
              <div style={{ overflowX: 'auto', background: '#fff', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
                  <thead>
                    <tr style={{ background: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#475569' }}>
                      <th style={{ padding: '10px 12px' }}>Mã Yêu Cầu</th>
                      <th style={{ padding: '10px 12px' }}>Mã NV</th>
                      <th style={{ padding: '10px 12px' }}>Loại Quyền</th>
                      <th style={{ padding: '10px 12px' }}>Lý Do</th>
                      <th style={{ padding: '10px 12px' }}>Trạng Thái</th>
                      <th style={{ padding: '10px 12px' }}>Thao Tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dsrList.map(item => (
                      <tr key={item.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                        <td style={{ padding: '10px 12px', fontWeight: '600' }}>{item.id}</td>
                        <td style={{ padding: '10px 12px', fontFamily: 'monospace' }}>{item.employeeId}</td>
                        <td style={{ padding: '10px 12px' }}>
                          <span style={{ padding: '3px 6px', background: '#f1f5f9', borderRadius: '4px', fontSize: '11px', fontWeight: '500' }}>
                            {item.requestType}
                          </span>
                        </td>
                        <td style={{ padding: '10px 12px', color: '#475569' }}>{item.reason}</td>
                        <td style={{ padding: '10px 12px' }}>
                          <span style={{
                            padding: '3px 8px',
                            borderRadius: '10px',
                            fontSize: '11px',
                            fontWeight: '500',
                            background: item.status === 'COMPLETED' ? '#dcfce7' : (item.status === 'PENDING' ? '#fef3c7' : '#f1f5f9'),
                            color: item.status === 'COMPLETED' ? '#15803d' : (item.status === 'PENDING' ? '#b45309' : '#64748b')
                          }}>
                            {item.status}
                          </span>
                        </td>
                        <td style={{ padding: '10px 12px' }}>
                          {item.status === 'PENDING' && (
                            <button
                              onClick={() => handleProcessDsr(item.id, 'COMPLETED')}
                              style={{ padding: '4px 8px', fontSize: '11px', background: '#16a34a', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
                            >
                              Hoàn Thành
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Form tiếp nhận yêu cầu DSR mới */}
            <div style={{ background: '#fff', border: '1px solid #e2e8f0', borderRadius: '8px', padding: '18px', height: 'fit-content' }}>
              <h4 style={{ margin: '0 0 14px 0', fontSize: '15px', color: '#1e293b' }}>+ Tiếp Nhận Yêu Cầu DSR Mới</h4>
              <form onSubmit={handleAddDsr} style={{ display: 'grid', gap: '12px' }}>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', marginBottom: '4px', fontWeight: '500' }}>Mã Nhân Viên:</label>
                  <input
                    placeholder="VD: EMP-0205"
                    value={newDsr.employeeId}
                    onChange={e => setNewDsr({ ...newDsr, employeeId: e.target.value })}
                    style={{ width: '100%', padding: '7px', borderRadius: '4px', border: '1px solid #cbd5e1', fontSize: '13px' }}
                    required
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', marginBottom: '4px', fontWeight: '500' }}>Loại Quyền Cần Thực Hiện:</label>
                  <select
                    value={newDsr.requestType}
                    onChange={e => setNewDsr({ ...newDsr, requestType: e.target.value })}
                    style={{ width: '100%', padding: '7px', borderRadius: '4px', border: '1px solid #cbd5e1', fontSize: '13px' }}
                  >
                    <option value="ACCESS">ACCESS (Truy xuất bản ghi)</option>
                    <option value="RECTIFICATION">RECTIFICATION (Đính chính thông tin)</option>
                    <option value="ERASURE">ERASURE (Ẩn danh PII theo BR-11)</option>
                    <option value="RESTRICTION">RESTRICTION (Hạn chế xử lý)</option>
                  </select>
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '12px', marginBottom: '4px', fontWeight: '500' }}>Lý Do / Chi Tiết:</label>
                  <textarea
                    rows={3}
                    placeholder="Mô tả nội dung yêu cầu của nhân viên..."
                    value={newDsr.reason}
                    onChange={e => setNewDsr({ ...newDsr, reason: e.target.value })}
                    style={{ width: '100%', padding: '7px', borderRadius: '4px', border: '1px solid #cbd5e1', fontSize: '13px' }}
                  />
                </div>
                <button
                  type="submit"
                  style={{ background: '#2563eb', color: '#fff', border: 'none', padding: '8px', borderRadius: '4px', cursor: 'pointer', fontWeight: '500', fontSize: '13px' }}
                >
                  Tạo Yêu Cầu DSR
                </button>
              </form>
            </div>
          </div>
        </section>
      )}
    </div>
  );
}
