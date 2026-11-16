# Mục PDF 2 — Actor, RBAC, FR, Business Rules và Use Case

Tài liệu tham chiếu theo mục của PDF. Khi khác nhau, áp dụng [DECISIONS.md](../DECISIONS.md). Phân công triển khai xem [TEAM.md](../TEAM.md).

Nguồn: [main.pdf](../reference/main.pdf) phần 2; PDF tr. 21–44.

## Việc cần làm

- [ ] Đối chiếu đủ 49 mã FR trong REQUIREMENTS.md; giữ ưu tiên M/S/C, BR-01..18 và UC-01..05.
- [ ] 7 role: Employee, Supervisor, HR, Guard, Admin, Executive, Auditor; scope bản thân/tổ/nhà máy/toàn hệ thống, auditor chỉ đọc.
- [ ] Tách tạo/duyệt điều chỉnh, manual/emergency có re-auth và audit; ca đêm kết thúc 06:00 ngày 01 thuộc tháng trước; chốt chính thức 06:30, xuất HR trước 12:00.
- [ ] Triển khai business service và các view nghiệp vụ theo module, không đặt SQL/mật mã/HTTP trong luật nghiệp vụ.

## Luồng cần đặc tả trước khi code

1. UC-01: lấy QR động sau xác thực và kiểm tra credential.
2. UC-02: quét online, verify/policy, ghi local, command và sync nền.
3. UC-03: offline, giữ queue/cache và retry khi reconnect.
4. UC-04: manual override hoặc emergency có re-auth/audit; phân biệt có/không event công.
5. UC-05: điều chỉnh/chốt công/xuất HR, không sửa event gốc.

BR-03 (chống quét lặp nghiệp vụ 30 giây), BR-13 (jti) và BR-17 (eventId retry) là ba quy tắc khác nhau. Lưu đầy đủ event đã xảy ra khi Central phát hiện replay giữa Edge hoặc vi phạm thu hồi sau đồng bộ; gắn cảnh báo, không bỏ chứng cứ.

## Vị trí bắt đầu

| File | Vai trò |
|---|---|
| [EmployeeService.java](../../central-backend/src/main/java/vn/tmc/central/usermgmt/internal/service/EmployeeService.java) | service |
| [GateConfigurationService.java](../../central-backend/src/main/java/vn/tmc/central/systemadmin/internal/service/GateConfigurationService.java) | service |
| [AttendanceProcessingService.java](../../central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/service/AttendanceProcessingService.java) | service |
| [AttendanceAdjustmentService.java](../../central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/service/AttendanceAdjustmentService.java) | service |
| [AttendanceReportService.java](../../central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/service/AttendanceReportService.java) | service |
| [PayrollExportService.java](../../central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/service/PayrollExportService.java) | service |
| [AuditService.java](../../central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/service/AuditService.java) | service |
| [AdministrationService.java](../../central-backend/src/main/java/vn/tmc/central/systemadmin/internal/service/AdministrationService.java) | service |
| [ScanQrUseCase.java](../../edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/application/ScanQrUseCase.java) | Use case / worker |
| [EmployeePage.jsx](../../frontend/src/pages/EmployeePage.jsx) | View |
| [KioskPage.jsx](../../frontend/src/pages/KioskPage.jsx) | View |
| [GuardConsolePage.jsx](../../frontend/src/pages/GuardConsolePage.jsx) | View |
| [AttendancePage.jsx](../../frontend/src/pages/AttendancePage.jsx) | View |
| [ReportsPage.jsx](../../frontend/src/pages/ReportsPage.jsx) | View |
| [AdministrationPage.jsx](../../frontend/src/pages/AdministrationPage.jsx) | View |

## Bàn giao

- [ ] Ghi contract đầu vào/đầu ra và Task phối hợp trước khi nối module.
- [ ] Cập nhật [yêu cầu liên quan](../REQUIREMENTS.md) và test Mục PDF 8 khi có implementation.
- [ ] Nộp bằng chứng thực tế; không đổi trạng thái chỉ vì có file sườn.
