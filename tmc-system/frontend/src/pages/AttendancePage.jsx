import React, { useState, useMemo } from 'react';

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: View.
 * NGUỒN: main.pdf, FR-AT-03..07; DECISIONS.md §1, §6, §8.
 * 
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Lịch sử công cá nhân, bất thường, yêu cầu/phê duyệt điều chỉnh theo scope.
 * - Tách người tạo/người duyệt, nhập lý do bắt buộc và xem lịch sử; không sửa event gốc.
 * - Auditor là vai trò chỉ đọc, cấm duyệt điều chỉnh.
 * - Ca đêm bắt đầu cuối tháng trước kết thúc 06:00 ngày 01 thuộc tháng trước.
 */

// Danh sách diễn vai người dùng để kiểm chứng RBAC và phân quyền
const USERS = [
  { id: 'EMP001', name: 'Nguyễn Văn An', role: 'EMPLOYEE', roleTitle: 'Nhân viên', dept: 'Phân xưởng A' },
  { id: 'SUP001', name: 'Trần Thị Bình', role: 'SUPERVISOR', roleTitle: 'Tổ trưởng / Quản lý', dept: 'Phân xưởng A' },
  { id: 'HR001', name: 'Lê Hoàng Cường', role: 'HR', roleTitle: 'Chuyên viên Nhân sự', dept: 'Phòng Nhân sự' },
  { id: 'AUD001', name: 'Phạm Minh Đức', role: 'AUDITOR', roleTitle: 'Kiểm toán viên (Auditor)', dept: 'Ban Kiểm toán' }
];

export default function AttendancePage() {
  // Trạng thái người dùng đang đăng nhập (để kiểm tra phân quyền & RBAC)
  const [currentUser, setCurrentUser] = useState(USERS[0]);
  const [activeTab, setActiveTab] = useState('daily'); // 'daily' | 'events' | 'adjustments' | 'simulator'

  // Dữ liệu sự kiện quẹt thẻ bất biến (attendance_events)
  const [events, setEvents] = useState([
    {
      eventId: '018f45a1-0001-7000-8000-000000000001',
      occurredAt: '2026-10-31T22:00:00Z',
      direction: 'IN',
      source: 'ONLINE',
      gateId: 'GATE-01',
      factoryId: 'F01',
      employeeId: 'EMP001',
      jti: 'jti-oct31-in',
      flags: [],
      payloadSha256: 'a1b2c3d4e5f6...31a'
    },
    {
      eventId: '018f45a1-0002-7000-8000-000000000002',
      occurredAt: '2026-11-01T06:00:00Z',
      direction: 'OUT',
      source: 'ONLINE',
      gateId: 'GATE-01',
      factoryId: 'F01',
      employeeId: 'EMP001',
      jti: 'jti-nov01-out',
      flags: [],
      payloadSha256: 'f6e5d4c3b2a1...01b'
    },
    {
      eventId: '018f45a1-0003-7000-8000-000000000003',
      occurredAt: '2026-11-10T06:30:00Z',
      direction: 'IN',
      source: 'ONLINE',
      gateId: 'GATE-02',
      factoryId: 'F01',
      employeeId: 'EMP001',
      jti: 'jti-nov10-in',
      flags: ['LATE_ARRIVAL'],
      payloadSha256: '778899aabbcc...10a'
    },
    {
      eventId: '018f45a1-0004-7000-8000-000000000004',
      occurredAt: '2026-11-10T14:00:00Z',
      direction: 'OUT',
      source: 'ONLINE',
      gateId: 'GATE-02',
      factoryId: 'F01',
      employeeId: 'EMP001',
      jti: 'jti-nov10-out',
      flags: [],
      payloadSha256: '112233445566...10b'
    },
    {
      eventId: '018f45a1-0005-7000-8000-000000000005',
      occurredAt: '2026-11-12T06:00:00Z',
      direction: 'IN',
      source: 'ONLINE',
      gateId: 'GATE-01',
      factoryId: 'F01',
      employeeId: 'EMP001',
      jti: 'jti-nov12-in1',
      flags: [],
      payloadSha256: '445566778899...12a'
    },
    {
      eventId: '018f45a1-0006-7000-8000-000000000006',
      occurredAt: '2026-11-12T08:00:00Z',
      direction: 'IN',
      source: 'ONLINE',
      gateId: 'GATE-01',
      factoryId: 'F01',
      employeeId: 'EMP001',
      jti: 'jti-nov12-in2',
      flags: ['CONSECUTIVE_ENTRY'],
      payloadSha256: '998877665544...12b'
    }
  ]);

  // Dữ liệu bảng công tổng hợp ngày (daily_attendance_records)
  const [dailyRecords, setDailyRecords] = useState([
    {
      id: 'DAY-EMP001-2026-10-31',
      employeeId: 'EMP001',
      workDate: '2026-10-31',
      shiftCode: 'SHIFT_NIGHT',
      shiftName: 'Ca Đêm (22:00 - 06:00)',
      firstIn: '2026-10-31 22:00:00',
      lastOut: '2026-11-01 06:00:00',
      hoursWorked: 8.0,
      isLate: false,
      lateMinutes: 0,
      isEarlyLeave: false,
      earlyMinutes: 0,
      isMissingSwipe: false,
      isAdjusted: false,
      anomalyFlags: [],
      note: 'Ca đêm kết thúc 06:00 ngày 01/11, vẫn tính vào tháng 10 (DECISIONS.md §1)'
    },
    {
      id: 'DAY-EMP001-2026-11-10',
      employeeId: 'EMP001',
      workDate: '2026-11-10',
      shiftCode: 'SHIFT_1',
      shiftName: 'Ca Sáng (06:00 - 14:00)',
      firstIn: '2026-11-10 06:30:00',
      lastOut: '2026-11-10 14:00:00',
      hoursWorked: 7.5,
      isLate: true,
      lateMinutes: 30,
      isEarlyLeave: false,
      earlyMinutes: 0,
      isMissingSwipe: false,
      isAdjusted: false,
      anomalyFlags: ['LATE_ARRIVAL'],
      note: 'Đi trễ 30 phút'
    },
    {
      id: 'DAY-EMP001-2026-11-12',
      employeeId: 'EMP001',
      workDate: '2026-11-12',
      shiftCode: 'SHIFT_1',
      shiftName: 'Ca Sáng (06:00 - 14:00)',
      firstIn: '2026-11-12 06:00:00',
      lastOut: null,
      hoursWorked: 0.0,
      isLate: false,
      lateMinutes: 0,
      isEarlyLeave: false,
      earlyMinutes: 0,
      isMissingSwipe: true,
      isAdjusted: false,
      anomalyFlags: ['CONSECUTIVE_ENTRY', 'MISSING_OUT'],
      note: 'Vào 2 lần liên tiếp, thiếu quẹt ra'
    }
  ]);

  // Dữ liệu yêu cầu điều chỉnh công (attendance_adjustments)
  const [adjustments, setAdjustments] = useState([
    {
      id: 'ADJ-202611-001',
      employeeId: 'EMP001',
      workDate: '2026-11-10',
      adjustmentType: 'EXCUSE_LATE',
      adjustmentTypeName: 'Miễn trừ đi trễ',
      requestedTime: null,
      adjustedHours: 8.0,
      reason: 'Xe đưa đón công nhân gặp sự cố trên quốc lộ, có biên bản xác nhận của đội xe',
      status: 'APPROVED',
      requestedBy: 'EMP001',
      requestedAt: '2026-11-10 15:30:00',
      reviewedBy: 'HR001',
      reviewedAt: '2026-11-11 09:15:00',
      reviewNote: 'Đã xác thực danh sách xe đưa đón hợp lệ'
    },
    {
      id: 'ADJ-202611-002',
      employeeId: 'EMP001',
      workDate: '2026-11-12',
      adjustmentType: 'ADD_OUT',
      adjustmentTypeName: 'Bổ sung giờ ra',
      requestedTime: '2026-11-12 14:00:00',
      adjustedHours: 8.0,
      reason: 'Cổng 1 bảo trì đột xuất lúc tan ca, đã báo bảo vệ trực cổng',
      status: 'PENDING',
      requestedBy: 'EMP001',
      requestedAt: '2026-11-12 14:30:00',
      reviewedBy: null,
      reviewedAt: null,
      reviewNote: null
    }
  ]);

  // Form tạo điều chỉnh mới
  const [formWorkDate, setFormWorkDate] = useState('2026-11-12');
  const [formType, setFormType] = useState('ADD_OUT');
  const [formTime, setFormTime] = useState('14:00');
  const [formReason, setFormReason] = useState('');
  const [formError, setFormError] = useState('');
  const [formSuccess, setFormSuccess] = useState('');

  // Bộ lọc tháng
  const [selectedMonth, setSelectedMonth] = useState('2026-11');
  const [simulatorStatus, setSimulatorStatus] = useState('');

  // Tính toán KPI theo tháng đang chọn
  const kpiData = useMemo(() => {
    const filtered = dailyRecords.filter(r => r.workDate.startsWith(selectedMonth));
    const totalHours = filtered.reduce((acc, r) => acc + (r.hoursWorked || 0), 0);
    const completedDays = filtered.filter(r => !r.isMissingSwipe).length;
    const lateDays = filtered.filter(r => r.isLate).length;
    const earlyDays = filtered.filter(r => r.isEarlyLeave).length;
    const anomalyCount = filtered.reduce((acc, r) => acc + (r.anomalyFlags ? r.anomalyFlags.length : 0), 0);

    return { totalHours, completedDays, lateDays, earlyDays, anomalyCount };
  }, [dailyRecords, selectedMonth]);

  // Xử lý gửi yêu cầu điều chỉnh công
  const handleCreateAdjustment = (e) => {
    e.preventDefault();
    setFormError('');
    setFormSuccess('');

    // RÀNG BUỘC: Lý do không được để trống (FR-AT-05)
    if (!formReason.trim()) {
      setFormError('Lý do điều chỉnh (reason) là bắt buộc và không được để trống!');
      return;
    }

    const typeNames = {
      ADD_IN: 'Bổ sung giờ vào',
      ADD_OUT: 'Bổ sung giờ ra',
      MODIFY_HOURS: 'Chỉnh sửa giờ công',
      EXCUSE_LATE: 'Miễn trừ đi trễ',
      EXCUSE_EARLY: 'Miễn trừ về sớm'
    };

    const newAdj = {
      id: `ADJ-${Date.now().toString().slice(-6)}`,
      employeeId: currentUser.id,
      workDate: formWorkDate,
      adjustmentType: formType,
      adjustmentTypeName: typeNames[formType],
      requestedTime: `${formWorkDate} ${formTime}:00`,
      adjustedHours: 8.0,
      reason: formReason.trim(),
      status: 'PENDING',
      requestedBy: currentUser.id,
      requestedAt: new Date().toISOString().replace('T', ' ').slice(0, 19),
      reviewedBy: null,
      reviewedAt: null,
      reviewNote: null
    };

    setAdjustments([newAdj, ...adjustments]);
    setFormReason('');
    setFormSuccess('Gửi yêu cầu điều chỉnh thành công! Trạng thái hiện tại: Chờ phê duyệt (PENDING).');
  };

  // Xử lý phê duyệt / từ chối điều chỉnh (Kiểm tra RBAC độc lập)
  const handleReviewAdjustment = (adjId, approved) => {
    const adj = adjustments.find(a => a.id === adjId);
    if (!adj) return;

    // RÀNG BUỘC 1: Auditor là vai trò chỉ đọc, cấm duyệt hoặc sửa dữ liệu công (DECISIONS.md §8)
    if (currentUser.role === 'AUDITOR') {
      alert('TỪ CHỐI TRUY CẬP: Auditor là vai trò chỉ đọc; cấm phê duyệt hoặc từ chối điều chỉnh công (DECISIONS.md §8).');
      return;
    }

    // RÀNG BUỘC 2: Người tạo không được tự duyệt yêu cầu của chính mình (Tách quyền độc lập)
    if (adj.requestedBy.toLowerCase() === currentUser.id.toLowerCase()) {
      alert('TỪ CHỐI: Người tạo không được tự phê duyệt yêu cầu điều chỉnh của chính mình!');
      return;
    }

    // RÀNG BUỘC 3: Chỉ Supervisor hoặc HR mới có thẩm quyền duyệt
    if (currentUser.role !== 'SUPERVISOR' && currentUser.role !== 'HR') {
      alert('TỪ CHỐI: Chỉ Tổ trưởng (Supervisor) hoặc Nhân sự (HR) mới có quyền duyệt yêu cầu này.');
      return;
    }

    const reviewNote = window.prompt(approved ? 'Nhập ghi chú phê duyệt (tùy chọn):' : 'Nhập lý do từ chối:') || '';

    // Cập nhật trạng thái adjustment
    const updatedAdjustments = adjustments.map(a => {
      if (a.id === adjId) {
        return {
          ...a,
          status: approved ? 'APPROVED' : 'REJECTED',
          reviewedBy: currentUser.id,
          reviewedAt: new Date().toISOString().replace('T', ' ').slice(0, 19),
          reviewNote
        };
      }
      return a;
    });
    setAdjustments(updatedAdjustments);

    // Nếu phê duyệt, cập nhật bản ghi dẫn xuất daily_attendance_records (KHÔNG SỬA EVENT GỐC)
    if (approved) {
      setDailyRecords(dailyRecords.map(d => {
        if (d.workDate === adj.workDate && d.employeeId === adj.employeeId) {
          const updated = { ...d, isAdjusted: true };
          if (adj.adjustmentType === 'ADD_OUT') {
            updated.lastOut = adj.requestedTime;
            updated.isMissingSwipe = false;
            updated.hoursWorked = 8.0;
            updated.anomalyFlags = d.anomalyFlags.filter(f => f !== 'MISSING_OUT');
          } else if (adj.adjustmentType === 'EXCUSE_LATE') {
            updated.isLate = false;
            updated.lateMinutes = 0;
            updated.anomalyFlags = d.anomalyFlags.filter(f => f !== 'LATE_ARRIVAL');
          }
          return updated;
        }
        return d;
      }));
    }
  };

  // Mô phỏng các kịch bản Edge Sync
  const runSimulatorScenario = (scenario) => {
    const nowIso = new Date().toISOString();
    switch (scenario) {
      case 'NIGHT_SHIFT_MONTH_BOUNDARY': {
        // DECISIONS.md §1: Ca đêm bắt đầu 31/10 22:00 kết thúc 01/11 06:00
        const inId = '018f-sim-oct31-in';
        const outId = '018f-sim-nov01-out';
        const inEvt = {
          eventId: inId,
          occurredAt: '2026-10-31T22:00:00Z',
          direction: 'IN',
          source: 'ONLINE',
          gateId: 'GATE-01',
          factoryId: 'F01',
          employeeId: 'EMP001',
          jti: 'jti-sim-oct31',
          flags: [],
          payloadSha256: 'sha256-sim-oct31'
        };
        const outEvt = {
          eventId: outId,
          occurredAt: '2026-11-01T06:00:00Z',
          direction: 'OUT',
          source: 'ONLINE',
          gateId: 'GATE-01',
          factoryId: 'F01',
          employeeId: 'EMP001',
          jti: 'jti-sim-nov01',
          flags: [],
          payloadSha256: 'sha256-sim-nov01'
        };
        setEvents(prev => [outEvt, inEvt, ...prev]);
        setSimulatorStatus('ĐÃ XỬ LÝ: Ca đêm bắt đầu 31/10 22:00 kết thúc 01/11 06:00 -> Thuộc kỳ tháng 10/2026 (DECISIONS.md §1). Trả về: ACCEPTED.');
        break;
      }
      case 'DUPLICATE_RETRY': {
        // FR-OS-06: Trùng ID cùng hash trả DUPLICATE
        setSimulatorStatus('ĐÃ XỬ LÝ IDEMPOTENCY: Edge retry gửi lại eventId đã tồn tại và cùng SHA-256 hash. Central trả về DUPLICATE, không thêm dòng.');
        break;
      }
      case 'RAPID_REPEAT': {
        // BR-03: Quét 2 lần trong 30s
        const rapidEvt = {
          eventId: `018f-sim-${Date.now()}`,
          occurredAt: nowIso,
          direction: 'IN',
          source: 'ONLINE',
          gateId: 'GATE-01',
          factoryId: 'F01',
          employeeId: 'EMP001',
          jti: `jti-rapid-${Date.now()}`,
          flags: ['RAPID_REPEAT_SCAN'],
          payloadSha256: 'sha256-rapid'
        };
        setEvents(prev => [rapidEvt, ...prev]);
        setSimulatorStatus('ĐÃ XỬ LÝ: Quét lặp lại trong <30s (BR-03) -> Ghi nhận bất biến và gắn cờ cảnh báo RAPID_REPEAT_SCAN.');
        break;
      }
      case 'CONSECUTIVE_IN': {
        // FR-AT-04: Vào liên tiếp
        const inEvt = {
          eventId: `018f-sim-${Date.now()}`,
          occurredAt: nowIso,
          direction: 'IN',
          source: 'ONLINE',
          gateId: 'GATE-01',
          factoryId: 'F01',
          employeeId: 'EMP001',
          jti: `jti-in-${Date.now()}`,
          flags: ['CONSECUTIVE_ENTRY'],
          payloadSha256: 'sha256-consec'
        };
        setEvents(prev => [inEvt, ...prev]);
        setSimulatorStatus('ĐÃ XỬ LÝ: Quét Vào liên tiếp khi chưa có sự kiện Ra -> Ghi nhận bất biến và gắn cờ CONSECUTIVE_ENTRY.');
        break;
      }
      default:
        break;
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {/* Header & Phân vai người dùng */}
      <div style={{
        background: '#fff',
        padding: '20px',
        borderRadius: '8px',
        border: '1px solid #dce2ea',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: '12px'
      }}>
        <div>
          <h2 style={{ margin: '0 0 4px 0', color: '#182b42' }}>Chấm công & Tiếp nhận Sự kiện (Task 3)</h2>
          <p style={{ margin: 0, color: '#556987', fontSize: '14px' }}>
            Module <code>attendance-processing</code>: Tiếp nhận sự kiện, chống trùng lặp, tính công ca đêm, bất thường và duyệt điều chỉnh.
          </p>
        </div>

        {/* User Role Switcher */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <label style={{ fontSize: '13px', fontWeight: 'bold', color: '#334e68' }}>Người dùng giả lập:</label>
          <select
            value={currentUser.id}
            onChange={(e) => setCurrentUser(USERS.find(u => u.id === e.target.value))}
            style={{
              padding: '8px 12px',
              borderRadius: '6px',
              border: '1px solid #376bc5',
              background: '#e6efff',
              color: '#182b42',
              fontWeight: 600
            }}
          >
            {USERS.map(u => (
              <option key={u.id} value={u.id}>
                {u.name} — {u.roleTitle} ({u.dept})
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* KPI Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '16px' }}>
        <div style={{ background: '#fff', padding: '16px', borderRadius: '8px', border: '1px solid #dce2ea' }}>
          <div style={{ fontSize: '12px', color: '#627d98', textTransform: 'uppercase', fontWeight: 'bold' }}>Tổng giờ làm việc ({selectedMonth})</div>
          <div style={{ fontSize: '26px', fontWeight: 'bold', color: '#0b69a3', marginTop: '6px' }}>{kpiData.totalHours.toFixed(1)} h</div>
        </div>
        <div style={{ background: '#fff', padding: '16px', borderRadius: '8px', border: '1px solid #dce2ea' }}>
          <div style={{ fontSize: '12px', color: '#627d98', textTransform: 'uppercase', fontWeight: 'bold' }}>Ca hoàn thành hợp lệ</div>
          <div style={{ fontSize: '26px', fontWeight: 'bold', color: '#107e3e', marginTop: '6px' }}>{kpiData.completedDays} ca</div>
        </div>
        <div style={{ background: '#fff', padding: '16px', borderRadius: '8px', border: '1px solid #dce2ea' }}>
          <div style={{ fontSize: '12px', color: '#627d98', textTransform: 'uppercase', fontWeight: 'bold' }}>Lượt đi trễ</div>
          <div style={{ fontSize: '26px', fontWeight: 'bold', color: '#ba360a', marginTop: '6px' }}>{kpiData.lateDays}</div>
        </div>
        <div style={{ background: '#fff', padding: '16px', borderRadius: '8px', border: '1px solid #dce2ea' }}>
          <div style={{ fontSize: '12px', color: '#627d98', textTransform: 'uppercase', fontWeight: 'bold' }}>Lượt về sớm</div>
          <div style={{ fontSize: '26px', fontWeight: 'bold', color: '#ba360a', marginTop: '6px' }}>{kpiData.earlyDays}</div>
        </div>
        <div style={{ background: '#fff', padding: '16px', borderRadius: '8px', border: '1px solid #dce2ea' }}>
          <div style={{ fontSize: '12px', color: '#627d98', textTransform: 'uppercase', fontWeight: 'bold' }}>Cảnh báo bất thường</div>
          <div style={{ fontSize: '26px', fontWeight: 'bold', color: '#775100', marginTop: '6px' }}>{kpiData.anomalyCount}</div>
        </div>
      </div>

      {/* Tabs Navigation */}
      <div style={{ display: 'flex', gap: '8px', borderBottom: '2px solid #dce2ea', paddingBottom: '4px' }}>
        <button
          onClick={() => setActiveTab('daily')}
          style={{
            background: activeTab === 'daily' ? '#376bc5' : '#fff',
            color: activeTab === 'daily' ? '#fff' : '#182b42',
            border: '1px solid #dce2ea',
            borderRadius: '6px 6px 0 0',
            fontWeight: 600,
            cursor: 'pointer'
          }}
        >
          📅 Bảng công hàng ngày
        </button>
        <button
          onClick={() => setActiveTab('events')}
          style={{
            background: activeTab === 'events' ? '#376bc5' : '#fff',
            color: activeTab === 'events' ? '#fff' : '#182b42',
            border: '1px solid #dce2ea',
            borderRadius: '6px 6px 0 0',
            fontWeight: 600,
            cursor: 'pointer'
          }}
        >
          📜 Nhật ký sự kiện bất biến (Audit Log)
        </button>
        <button
          onClick={() => setActiveTab('adjustments')}
          style={{
            background: activeTab === 'adjustments' ? '#376bc5' : '#fff',
            color: activeTab === 'adjustments' ? '#fff' : '#182b42',
            border: '1px solid #dce2ea',
            borderRadius: '6px 6px 0 0',
            fontWeight: 600,
            cursor: 'pointer'
          }}
        >
          ✏️ Điều chỉnh & Phê duyệt công
        </button>
        <button
          onClick={() => setActiveTab('simulator')}
          style={{
            background: activeTab === 'simulator' ? '#376bc5' : '#fff',
            color: activeTab === 'simulator' ? '#fff' : '#182b42',
            border: '1px solid #dce2ea',
            borderRadius: '6px 6px 0 0',
            fontWeight: 600,
            cursor: 'pointer'
          }}
        >
          ⚡ Giả lập Edge Sync & Kịch bản kiểm thử
        </button>
      </div>

      {/* TAB 1: BẢNG CÔNG HÀNG NGÀY */}
      {activeTab === 'daily' && (
        <div style={{ background: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #dce2ea' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
            <h3 style={{ margin: 0, color: '#182b42' }}>Bảng chấm công chi tiết theo ngày làm việc</h3>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <label style={{ fontSize: '13px', fontWeight: 'bold' }}>Kỳ / Tháng:</label>
              <select
                value={selectedMonth}
                onChange={(e) => setSelectedMonth(e.target.value)}
                style={{ padding: '6px 10px', borderRadius: '4px', border: '1px solid #cdd5df' }}
              >
                <option value="2026-11">Tháng 11/2026</option>
                <option value="2026-10">Tháng 10/2026</option>
              </select>
            </div>
          </div>

          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '14px' }}>
            <thead>
              <tr style={{ background: '#f5f7fa', borderBottom: '2px solid #dce2ea' }}>
                <th style={{ padding: '10px' }}>Ngày công</th>
                <th style={{ padding: '10px' }}>Ca làm việc</th>
                <th style={{ padding: '10px' }}>Quẹt vào</th>
                <th style={{ padding: '10px' }}>Quẹt ra</th>
                <th style={{ padding: '10px' }}>Giờ công</th>
                <th style={{ padding: '10px' }}>Trạng thái</th>
                <th style={{ padding: '10px' }}>Bất thường & Ghi chú</th>
              </tr>
            </thead>
            <tbody>
              {dailyRecords.filter(r => r.workDate.startsWith(selectedMonth)).map(r => (
                <tr key={r.id} style={{ borderBottom: '1px solid #edf0f5' }}>
                  <td style={{ padding: '10px', fontWeight: 'bold' }}>{r.workDate}</td>
                  <td style={{ padding: '10px' }}>{r.shiftName}</td>
                  <td style={{ padding: '10px', color: r.isLate ? '#ba360a' : '#182b42' }}>
                    {r.firstIn || <span style={{ color: '#ba360a' }}>Thiếu quẹt</span>}
                    {r.isLate && <span style={{ fontSize: '12px', color: '#ba360a', display: 'block' }}>+{r.lateMinutes}p trễ</span>}
                  </td>
                  <td style={{ padding: '10px', color: r.isEarlyLeave ? '#ba360a' : '#182b42' }}>
                    {r.lastOut || <span style={{ color: '#ba360a' }}>Thiếu quẹt</span>}
                    {r.isEarlyLeave && <span style={{ fontSize: '12px', color: '#ba360a', display: 'block' }}>-{r.earlyMinutes}p sớm</span>}
                  </td>
                  <td style={{ padding: '10px', fontWeight: 'bold', color: '#0b69a3' }}>{r.hoursWorked.toFixed(1)} h</td>
                  <td style={{ padding: '10px' }}>
                    {r.isAdjusted && (
                      <span style={{ background: '#e3f9e5', color: '#107e3e', padding: '3px 8px', borderRadius: '4px', fontSize: '12px', fontWeight: 'bold', display: 'inline-block', marginRight: '6px' }}>
                        Đã duyệt điều chỉnh
                      </span>
                    )}
                    {r.isMissingSwipe ? (
                      <span style={{ background: '#ffe3e3', color: '#ba360a', padding: '3px 8px', borderRadius: '4px', fontSize: '12px', fontWeight: 'bold' }}>
                        Thiếu quẹt
                      </span>
                    ) : r.isLate ? (
                      <span style={{ background: '#fff3c4', color: '#775100', padding: '3px 8px', borderRadius: '4px', fontSize: '12px', fontWeight: 'bold' }}>
                        Đi trễ
                      </span>
                    ) : (
                      <span style={{ background: '#e3f9e5', color: '#107e3e', padding: '3px 8px', borderRadius: '4px', fontSize: '12px', fontWeight: 'bold' }}>
                        Đúng giờ
                      </span>
                    )}
                  </td>
                  <td style={{ padding: '10px', fontSize: '13px' }}>
                    {r.anomalyFlags && r.anomalyFlags.map(f => (
                      <span key={f} style={{ background: '#fff0d9', color: '#9c4800', padding: '2px 6px', borderRadius: '3px', marginRight: '4px', fontSize: '11px', fontWeight: 'bold' }}>
                        {f}
                      </span>
                    ))}
                    <div style={{ color: '#556987', marginTop: '2px' }}>{r.note}</div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* TAB 2: NHẬT KÝ SỰ KIỆN QUẸT THẺ (RAW AUDIT EVENTS) */}
      {activeTab === 'events' && (
        <div style={{ background: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #dce2ea' }}>
          <h3 style={{ margin: '0 0 12px 0', color: '#182b42' }}>Nhật ký sự kiện quẹt thẻ bất biến (attendance_events)</h3>
          <p style={{ color: '#556987', fontSize: '13px', margin: '0 0 16px 0' }}>
            Mỗi lượt quẹt thẻ tại cổng Edge được đồng bộ lên Central, ghi nhận bất biến (append-only), kiểm tra mã băm SHA-256 và giữ nguyên sau khi commit.
          </p>

          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
            <thead>
              <tr style={{ background: '#f5f7fa', borderBottom: '2px solid #dce2ea' }}>
                <th style={{ padding: '10px' }}>Thời gian (UTC)</th>
                <th style={{ padding: '10px' }}>Hướng</th>
                <th style={{ padding: '10px' }}>Cổng / Nhà máy</th>
                <th style={{ padding: '10px' }}>Nhân viên</th>
                <th style={{ padding: '10px' }}>Nguồn</th>
                <th style={{ padding: '10px' }}>Token JTI</th>
                <th style={{ padding: '10px' }}>Cờ bất thường</th>
                <th style={{ padding: '10px' }}>Payload SHA-256</th>
              </tr>
            </thead>
            <tbody>
              {events.map(e => (
                <tr key={e.eventId} style={{ borderBottom: '1px solid #edf0f5' }}>
                  <td style={{ padding: '10px', fontFamily: 'monospace' }}>{e.occurredAt}</td>
                  <td style={{ padding: '10px' }}>
                    <span style={{
                      padding: '3px 8px',
                      borderRadius: '4px',
                      fontSize: '12px',
                      fontWeight: 'bold',
                      background: e.direction === 'IN' ? '#e6efff' : '#fff0d9',
                      color: e.direction === 'IN' ? '#376bc5' : '#9c4800'
                    }}>
                      {e.direction === 'IN' ? 'VÀO (IN)' : 'RA (OUT)'}
                    </span>
                  </td>
                  <td style={{ padding: '10px' }}>{e.gateId} ({e.factoryId})</td>
                  <td style={{ padding: '10px', fontWeight: 'bold' }}>{e.employeeId}</td>
                  <td style={{ padding: '10px' }}>{e.source}</td>
                  <td style={{ padding: '10px', fontFamily: 'monospace', color: '#556987' }}>{e.jti}</td>
                  <td style={{ padding: '10px' }}>
                    {e.flags && e.flags.length > 0 ? (
                      e.flags.map(f => (
                        <span key={f} style={{ background: '#ffe3e3', color: '#ba360a', padding: '2px 6px', borderRadius: '3px', fontSize: '11px', fontWeight: 'bold' }}>
                          {f}
                        </span>
                      ))
                    ) : (
                      <span style={{ color: '#107e3e', fontSize: '12px' }}>✓ Bình thường</span>
                    )}
                  </td>
                  <td style={{ padding: '10px', fontFamily: 'monospace', fontSize: '11px', color: '#627d98' }}>{e.payloadSha256}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* TAB 3: ĐIỀU CHỈNH & PHÊ DUYỆT CÔNG */}
      {activeTab === 'adjustments' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* Form tạo yêu cầu */}
          <div style={{ background: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #dce2ea' }}>
            <h3 style={{ margin: '0 0 12px 0', color: '#182b42' }}>Tạo yêu cầu điều chỉnh công (FR-AT-05)</h3>
            <p style={{ color: '#556987', fontSize: '13px', margin: '0 0 16px 0' }}>
              Người gửi: <strong>{currentUser.name}</strong> ({currentUser.roleTitle}). Lưu ý: Người tạo không thể tự duyệt yêu cầu của mình.
            </p>

            {formError && (
              <div style={{ background: '#ffe3e3', color: '#ba360a', padding: '10px 14px', borderRadius: '6px', marginBottom: '14px', fontSize: '14px', fontWeight: 600 }}>
                ⚠️ {formError}
              </div>
            )}
            {formSuccess && (
              <div style={{ background: '#e3f9e5', color: '#107e3e', padding: '10px 14px', borderRadius: '6px', marginBottom: '14px', fontSize: '14px', fontWeight: 600 }}>
                ✓ {formSuccess}
              </div>
            )}

            <form onSubmit={handleCreateAdjustment} style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '14px' }}>
              <div>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: 'bold', marginBottom: '4px' }}>Ngày làm việc:</label>
                <input
                  type="date"
                  value={formWorkDate}
                  onChange={(e) => setFormWorkDate(e.target.value)}
                  style={{ width: '100%', padding: '8px', borderRadius: '4px', border: '1px solid #cdd5df' }}
                  required
                />
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: 'bold', marginBottom: '4px' }}>Loại điều chỉnh:</label>
                <select
                  value={formType}
                  onChange={(e) => setFormType(e.target.value)}
                  style={{ width: '100%', padding: '8px', borderRadius: '4px', border: '1px solid #cdd5df' }}
                >
                  <option value="ADD_OUT">Bổ sung giờ ra (ADD_OUT)</option>
                  <option value="ADD_IN">Bổ sung giờ vào (ADD_IN)</option>
                  <option value="EXCUSE_LATE">Miễn trừ đi trễ (EXCUSE_LATE)</option>
                  <option value="EXCUSE_EARLY">Miễn trừ về sớm (EXCUSE_EARLY)</option>
                  <option value="MODIFY_HOURS">Chỉnh sửa giờ công (MODIFY_HOURS)</option>
                </select>
              </div>

              <div>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: 'bold', marginBottom: '4px' }}>Giờ đề xuất:</label>
                <input
                  type="time"
                  value={formTime}
                  onChange={(e) => setFormTime(e.target.value)}
                  style={{ width: '100%', padding: '8px', borderRadius: '4px', border: '1px solid #cdd5df' }}
                />
              </div>

              <div style={{ gridColumn: '1 / -1' }}>
                <label style={{ display: 'block', fontSize: '13px', fontWeight: 'bold', marginBottom: '4px' }}>
                  Lý do điều chỉnh <span style={{ color: '#ba360a' }}>* (bắt buộc, không được để trống)</span>:
                </label>
                <textarea
                  rows={2}
                  value={formReason}
                  onChange={(e) => setFormReason(e.target.value)}
                  placeholder="Ví dụ: Thiết bị cổng bảo trì, công tác đột xuất, sự cố xe đưa đón..."
                  style={{ width: '100%', padding: '8px', borderRadius: '4px', border: '1px solid #cdd5df' }}
                />
              </div>

              <div style={{ gridColumn: '1 / -1' }}>
                <button
                  type="submit"
                  style={{
                    background: '#376bc5',
                    color: '#fff',
                    padding: '10px 20px',
                    borderRadius: '6px',
                    border: 'none',
                    fontWeight: 'bold',
                    cursor: 'pointer'
                  }}
                >
                  Gửi yêu cầu điều chỉnh
                </button>
              </div>
            </form>
          </div>

          {/* Danh sách yêu cầu điều chỉnh */}
          <div style={{ background: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #dce2ea' }}>
            <h3 style={{ margin: '0 0 16px 0', color: '#182b42' }}>Danh sách yêu cầu điều chỉnh & Phê duyệt</h3>

            {currentUser.role === 'AUDITOR' && (
              <div style={{ background: '#fff3c4', color: '#775100', padding: '10px 14px', borderRadius: '6px', marginBottom: '14px', fontSize: '13px' }}>
                🔒 <strong>Chế độ Auditor:</strong> Bạn đang đăng nhập với vai trò Kiểm toán viên (Auditor). Theo <code>DECISIONS.md §8</code>, Auditor chỉ có quyền tra cứu và xem lịch sử, cấm phê duyệt hoặc từ chối điều chỉnh.
              </div>
            )}

            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
              <thead>
                <tr style={{ background: '#f5f7fa', borderBottom: '2px solid #dce2ea' }}>
                  <th style={{ padding: '10px' }}>Mã yêu cầu</th>
                  <th style={{ padding: '10px' }}>Ngày công</th>
                  <th style={{ padding: '10px' }}>Loại điều chỉnh</th>
                  <th style={{ padding: '10px' }}>Lý do</th>
                  <th style={{ padding: '10px' }}>Người gửi</th>
                  <th style={{ padding: '10px' }}>Trạng thái</th>
                  <th style={{ padding: '10px' }}>Người duyệt / Ghi chú</th>
                  <th style={{ padding: '10px' }}>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {adjustments.map(adj => {
                  const isCreator = adj.requestedBy.toLowerCase() === currentUser.id.toLowerCase();
                  const isAuditor = currentUser.role === 'AUDITOR';
                  const canApprove = !isCreator && !isAuditor && adj.status === 'PENDING';

                  return (
                    <tr key={adj.id} style={{ borderBottom: '1px solid #edf0f5' }}>
                      <td style={{ padding: '10px', fontWeight: 'bold' }}>{adj.id}</td>
                      <td style={{ padding: '10px' }}>{adj.workDate}</td>
                      <td style={{ padding: '10px' }}>{adj.adjustmentTypeName}</td>
                      <td style={{ padding: '10px', maxWidth: '240px' }}>{adj.reason}</td>
                      <td style={{ padding: '10px' }}>
                        {adj.requestedBy}
                        {isCreator && <span style={{ fontSize: '11px', color: '#376bc5', display: 'block' }}>(Bạn)</span>}
                      </td>
                      <td style={{ padding: '10px' }}>
                        <span style={{
                          padding: '3px 8px',
                          borderRadius: '4px',
                          fontSize: '12px',
                          fontWeight: 'bold',
                          background: adj.status === 'APPROVED' ? '#e3f9e5' : adj.status === 'REJECTED' ? '#ffe3e3' : '#fff3c4',
                          color: adj.status === 'APPROVED' ? '#107e3e' : adj.status === 'REJECTED' ? '#ba360a' : '#775100'
                        }}>
                          {adj.status}
                        </span>
                      </td>
                      <td style={{ padding: '10px', fontSize: '12px', color: '#556987' }}>
                        {adj.reviewedBy ? (
                          <div>
                            <strong>{adj.reviewedBy}</strong> ({adj.reviewedAt})
                            <div style={{ fontStyle: 'italic' }}>{adj.reviewNote}</div>
                          </div>
                        ) : '—'}
                      </td>
                      <td style={{ padding: '10px' }}>
                        {adj.status === 'PENDING' ? (
                          <div style={{ display: 'flex', gap: '6px' }}>
                            <button
                              disabled={!canApprove}
                              onClick={() => handleReviewAdjustment(adj.id, true)}
                              title={isCreator ? 'Người tạo không được tự duyệt' : isAuditor ? 'Auditor cấm duyệt' : 'Phê duyệt yêu cầu'}
                              style={{
                                background: canApprove ? '#107e3e' : '#e0e0e0',
                                color: canApprove ? '#fff' : '#888',
                                border: 'none',
                                padding: '6px 10px',
                                borderRadius: '4px',
                                fontSize: '12px',
                                cursor: canApprove ? 'pointer' : 'not-allowed',
                                fontWeight: 'bold'
                              }}
                            >
                              Duyệt
                            </button>
                            <button
                              disabled={!canApprove}
                              onClick={() => handleReviewAdjustment(adj.id, false)}
                              title={isCreator ? 'Người tạo không được tự duyệt' : isAuditor ? 'Auditor cấm duyệt' : 'Từ chối yêu cầu'}
                              style={{
                                background: canApprove ? '#ba360a' : '#e0e0e0',
                                color: canApprove ? '#fff' : '#888',
                                border: 'none',
                                padding: '6px 10px',
                                borderRadius: '4px',
                                fontSize: '12px',
                                cursor: canApprove ? 'pointer' : 'not-allowed',
                                fontWeight: 'bold'
                              }}
                            >
                              Từ chối
                            </button>
                          </div>
                        ) : (
                          <span style={{ color: '#556987', fontSize: '12px' }}>Đã xử lý</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 4: MÔ PHỎNG EDGE SYNC & KỊCH BẢN KIỂM THỬ */}
      {activeTab === 'simulator' && (
        <div style={{ background: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #dce2ea' }}>
          <h3 style={{ margin: '0 0 12px 0', color: '#182b42' }}>Trình giả lập tiếp nhận sự kiện từ Edge (Sync Simulator)</h3>
          <p style={{ color: '#556987', fontSize: '13px', margin: '0 0 16px 0' }}>
            Kiểm thử tương tác giữa Edge và Central: tiếp nhận sự kiện, bảo vệ bất biến, ca đêm qua tháng và phát hiện bất thường.
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '14px', marginBottom: '20px' }}>
            <div style={{ border: '1px solid #dce2ea', borderRadius: '6px', padding: '14px' }}>
              <h4 style={{ margin: '0 0 6px 0', color: '#376bc5' }}>🌙 Ca đêm qua ranh giới tháng</h4>
              <p style={{ fontSize: '13px', color: '#556987', margin: '0 0 10px 0' }}>
                Ca đêm bắt đầu 31/10 22:00, kết thúc 01/11 06:00. Kiểm chứng tính công tự động quy về tháng 10 (DECISIONS.md §1).
              </p>
              <button
                onClick={() => runSimulatorScenario('NIGHT_SHIFT_MONTH_BOUNDARY')}
                style={{ background: '#e6efff', color: '#376bc5', border: '1px solid #376bc5', padding: '6px 12px', borderRadius: '4px', cursor: 'pointer', fontWeight: 600 }}
              >
                Chạy mô phỏng ca đêm 31/10
              </button>
            </div>

            <div style={{ border: '1px solid #dce2ea', borderRadius: '6px', padding: '14px' }}>
              <h4 style={{ margin: '0 0 6px 0', color: '#376bc5' }}>🔁 Idempotency & Gửi lại (Retry)</h4>
              <p style={{ fontSize: '13px', color: '#556987', margin: '0 0 10px 0' }}>
                Edge retry gửi lại eventId đã tồn tại và cùng SHA-256 hash. Kiểm chứng trả về DUPLICATE, không chèn bản ghi mới (FR-OS-06).
              </p>
              <button
                onClick={() => runSimulatorScenario('DUPLICATE_RETRY')}
                style={{ background: '#e6efff', color: '#376bc5', border: '1px solid #376bc5', padding: '6px 12px', borderRadius: '4px', cursor: 'pointer', fontWeight: 600 }}
              >
                Chạy mô phỏng gửi trùng
              </button>
            </div>

            <div style={{ border: '1px solid #dce2ea', borderRadius: '6px', padding: '14px' }}>
              <h4 style={{ margin: '0 0 6px 0', color: '#ba360a' }}>⏱️ Quét lặp nhanh &lt; 30s</h4>
              <p style={{ fontSize: '13px', color: '#556987', margin: '0 0 10px 0' }}>
                Cùng 1 nhân viên quẹt thẻ 2 lần trong vòng 30 giây (BR-03). Kiểm chứng gắn cờ RAPID_REPEAT_SCAN.
              </p>
              <button
                onClick={() => runSimulatorScenario('RAPID_REPEAT')}
                style={{ background: '#ffe3e3', color: '#ba360a', border: '1px solid #ba360a', padding: '6px 12px', borderRadius: '4px', cursor: 'pointer', fontWeight: 600 }}
              >
                Chạy mô phỏng quét nhanh
              </button>
            </div>

            <div style={{ border: '1px solid #dce2ea', borderRadius: '6px', padding: '14px' }}>
              <h4 style={{ margin: '0 0 6px 0', color: '#ba360a' }}>🚪 Vào 2 lần liên tiếp</h4>
              <p style={{ fontSize: '13px', color: '#556987', margin: '0 0 10px 0' }}>
                Nhân viên quẹt Vào (IN) khi chưa quẹt Ra (OUT) (FR-AT-04). Kiểm chứng gắn cờ CONSECUTIVE_ENTRY.
              </p>
              <button
                onClick={() => runSimulatorScenario('CONSECUTIVE_IN')}
                style={{ background: '#ffe3e3', color: '#ba360a', border: '1px solid #ba360a', padding: '6px 12px', borderRadius: '4px', cursor: 'pointer', fontWeight: 600 }}
              >
                Chạy mô phỏng Vào liên tiếp
              </button>
            </div>
          </div>

          {simulatorStatus && (
            <div style={{ background: '#e6efff', borderLeft: '4px solid #376bc5', padding: '12px 16px', borderRadius: '0 6px 6px 0', fontSize: '14px', color: '#182b42', fontWeight: 600 }}>
              {simulatorStatus}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
