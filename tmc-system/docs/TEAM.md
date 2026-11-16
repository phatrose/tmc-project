# Phân công 8 task triển khai

Mỗi người nhận một Task, comment chỉ ghi `PHỤ TRÁCH: Task N`. Số Task triển khai khác số mục/Task báo cáo gốc. [DECISIONS.md](DECISIONS.md) là phương án ưu tiên.

| Task | Phạm vi |
|---|---|
| Task 1 | [Nhân sự, đăng nhập và quyền](#task-1) |
| Task 2 | [Quét và vận hành cổng tại Edge](#task-2) |
| Task 3 | [Chấm công và tiếp nhận sự kiện](#task-3) |
| Task 4 | [Báo cáo, audit và chốt Payroll](#task-4) |
| Task 5 | [Nhà máy, cổng, ca và cấu hình](#task-5) |
| Task 6 | [QR và bảo mật token](#task-6) |
| Task 7 | [Lưu trữ Edge và đồng bộ offline](#task-7) |
| Task 8 | [Nền tảng và tích hợp hệ thống](#task-8) |

## Cân bằng và phối hợp

- Cân bằng theo độ phức tạp dự kiến, không theo số file. Task 1 nhận thêm đăng nhập/quyền, Task 5 nhận quản lý cổng/nhà máy/ca, Task 2 tập trung runtime quét/thiết bị.
- Chủ mỗi phần tự làm model/repository/API/UI liên quan, migration và unit/integration test. Task 7 tự kiểm thử offline/recovery; Task 8 chỉ điều phối tích hợp và test toàn hệ thống.
- Mỗi file có một chủ trì; thay đổi contract phải thống nhất với bên dùng. Task 2/6/7 chốt verify–reserve–persist–unlock; Task 3/7 chốt ACK; Task 3/4 chốt kỳ; Task 1/5 chốt quyền và dữ liệu cấu hình.
- Mỗi người bàn giao code, test và hướng dẫn chạy. Sườn chưa có nghiệp vụ hoạt động; build thành công không chứng minh hoàn thành FR.

<a id="task-1"></a>

## Task 1 — Nhân sự, đăng nhập và quyền

Sở hữu user-mgmt: nhân viên, người dùng/đăng nhập, role/permission (gồm Auditor), quyền khu vực; EmployeePage và LoginPage. Tham chiếu nhà máy/ca do Task 5 quản lý.

Phối hợp: Task 2/5/6/8.

**File phụ trách:**

- [central-backend/src/main/java/vn/tmc/central/usermgmt/api/AuditorPermission.java](../central-backend/src/main/java/vn/tmc/central/usermgmt/api/AuditorPermission.java)
- [central-backend/src/main/java/vn/tmc/central/usermgmt/api/Authentication.java](../central-backend/src/main/java/vn/tmc/central/usermgmt/api/Authentication.java)
- [central-backend/src/main/java/vn/tmc/central/usermgmt/api/EmployeeManagement.java](../central-backend/src/main/java/vn/tmc/central/usermgmt/api/EmployeeManagement.java)
- [central-backend/src/main/java/vn/tmc/central/usermgmt/api/EmployeeView.java](../central-backend/src/main/java/vn/tmc/central/usermgmt/api/EmployeeView.java)
- [central-backend/src/main/java/vn/tmc/central/usermgmt/api/Role.java](../central-backend/src/main/java/vn/tmc/central/usermgmt/api/Role.java)
- [central-backend/src/main/java/vn/tmc/central/usermgmt/internal/controller/EmployeeController.java](../central-backend/src/main/java/vn/tmc/central/usermgmt/internal/controller/EmployeeController.java)
- [central-backend/src/main/java/vn/tmc/central/usermgmt/internal/model/Employee.java](../central-backend/src/main/java/vn/tmc/central/usermgmt/internal/model/Employee.java)
- [central-backend/src/main/java/vn/tmc/central/usermgmt/internal/repository/EmployeeRepository.java](../central-backend/src/main/java/vn/tmc/central/usermgmt/internal/repository/EmployeeRepository.java)
- [central-backend/src/main/java/vn/tmc/central/usermgmt/internal/service/AuthenticationService.java](../central-backend/src/main/java/vn/tmc/central/usermgmt/internal/service/AuthenticationService.java)
- [central-backend/src/main/java/vn/tmc/central/usermgmt/internal/service/EmployeeService.java](../central-backend/src/main/java/vn/tmc/central/usermgmt/internal/service/EmployeeService.java)
- [frontend/src/pages/EmployeePage.jsx](../frontend/src/pages/EmployeePage.jsx)
- [frontend/src/pages/LoginPage.jsx](../frontend/src/pages/LoginPage.jsx)

<a id="task-2"></a>

## Task 2 — Quét và vận hành cổng tại Edge

Sở hữu luồng gate-station: nhận scan, policy cache, điều phối verify → reserve/lưu → mở cổng, manual/emergency, Turnstile adapter, KioskPage và GuardConsolePage. Không quản lý cấu hình cổng ở Central; không tự viết verifier hoặc SQLite.

Phối hợp: Task 1/3/5/6/7.

**File phụ trách:**

- [edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/application/ScanQrUseCase.java](../edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/application/ScanQrUseCase.java)
- [edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/domain/AccessPolicyPort.java](../edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/domain/AccessPolicyPort.java)
- [edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/domain/TurnstileControlPort.java](../edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/domain/TurnstileControlPort.java)
- [edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/infrastructure/MockTurnstileAdapter.java](../edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/infrastructure/MockTurnstileAdapter.java)
- [edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/infrastructure/ScanController.java](../edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/infrastructure/ScanController.java)
- [frontend/src/pages/GuardConsolePage.jsx](../frontend/src/pages/GuardConsolePage.jsx)
- [frontend/src/pages/KioskPage.jsx](../frontend/src/pages/KioskPage.jsx)

<a id="task-3"></a>

## Task 3 — Chấm công và tiếp nhận sự kiện

Sở hữu attendance-processing: nhận/lưu event, idempotency và ACK sau commit, tính công, ca đêm thuộc tháng bắt đầu, điều chỉnh/phê duyệt, AttendancePage; EventEnvelope/AttendancePayload.

Phối hợp: Task 2/4/7.

**File phụ trách:**

- [central-backend/src/main/java/vn/tmc/central/attendanceprocessing/api/AttendanceQueries.java](../central-backend/src/main/java/vn/tmc/central/attendanceprocessing/api/AttendanceQueries.java)
- [central-backend/src/main/java/vn/tmc/central/attendanceprocessing/api/AttendanceSync.java](../central-backend/src/main/java/vn/tmc/central/attendanceprocessing/api/AttendanceSync.java)
- [central-backend/src/main/java/vn/tmc/central/attendanceprocessing/api/AttendanceView.java](../central-backend/src/main/java/vn/tmc/central/attendanceprocessing/api/AttendanceView.java)
- [central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/controller/AttendanceController.java](../central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/controller/AttendanceController.java)
- [central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/controller/AttendanceSyncController.java](../central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/controller/AttendanceSyncController.java)
- [central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/model/Attendance.java](../central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/model/Attendance.java)
- [central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/repository/AttendanceRepository.java](../central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/repository/AttendanceRepository.java)
- [central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/service/AttendanceAdjustmentService.java](../central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/service/AttendanceAdjustmentService.java)
- [central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/service/AttendanceProcessingService.java](../central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/service/AttendanceProcessingService.java)
- [frontend/src/pages/AttendancePage.jsx](../frontend/src/pages/AttendancePage.jsx)
- [shared-contracts/src/main/java/vn/tmc/contracts/AttendancePayload.java](../shared-contracts/src/main/java/vn/tmc/contracts/AttendancePayload.java)
- [shared-contracts/src/main/java/vn/tmc/contracts/EventEnvelope.java](../shared-contracts/src/main/java/vn/tmc/contracts/EventEnvelope.java)

<a id="task-4"></a>

## Task 4 — Báo cáo, audit và chốt Payroll

Sở hữu reporting-audit: tổng hợp/chốt chính thức 06:30 ngày 01, xuất/đối soát HR trước 12:00, outbox/audit, ReportsPage. Auditor được tra cứu và chỉ xuất nếu có quyền; không sửa dữ kiện công.

Phối hợp: Task 1/3/5/8.

**File phụ trách:**

- [central-backend/src/main/java/vn/tmc/central/reportingaudit/api/AttendanceReportView.java](../central-backend/src/main/java/vn/tmc/central/reportingaudit/api/AttendanceReportView.java)
- [central-backend/src/main/java/vn/tmc/central/reportingaudit/api/ReportingQueries.java](../central-backend/src/main/java/vn/tmc/central/reportingaudit/api/ReportingQueries.java)
- [central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/adapter/HrPayrollAdapter.java](../central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/adapter/HrPayrollAdapter.java)
- [central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/controller/ReportAuditController.java](../central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/controller/ReportAuditController.java)
- [central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/model/AttendanceReport.java](../central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/model/AttendanceReport.java)
- [central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/model/PeriodClosingSchedule.java](../central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/model/PeriodClosingSchedule.java)
- [central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/repository/AttendanceReportRepository.java](../central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/repository/AttendanceReportRepository.java)
- [central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/service/AttendanceReportService.java](../central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/service/AttendanceReportService.java)
- [central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/service/AuditService.java](../central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/service/AuditService.java)
- [central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/service/PayrollExportService.java](../central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/service/PayrollExportService.java)
- [frontend/src/pages/ReportsPage.jsx](../frontend/src/pages/ReportsPage.jsx)

<a id="task-5"></a>

## Task 5 — Nhà máy, cổng, ca và cấu hình

Sở hữu system-admin: nhà máy/timezone, cấu hình cổng và Edge, ca/ngày lễ, vận hành/backup/DSR, AdministrationPage. Không sở hữu đăng nhập, role hoặc permission; dùng API user-mgmt.

Phối hợp: Task 1/2/8.

**File phụ trách:**

- [central-backend/src/main/java/vn/tmc/central/systemadmin/api/Administration.java](../central-backend/src/main/java/vn/tmc/central/systemadmin/api/Administration.java)
- [central-backend/src/main/java/vn/tmc/central/systemadmin/api/GateManagement.java](../central-backend/src/main/java/vn/tmc/central/systemadmin/api/GateManagement.java)
- [central-backend/src/main/java/vn/tmc/central/systemadmin/api/GateView.java](../central-backend/src/main/java/vn/tmc/central/systemadmin/api/GateView.java)
- [central-backend/src/main/java/vn/tmc/central/systemadmin/api/SystemConfigurationView.java](../central-backend/src/main/java/vn/tmc/central/systemadmin/api/SystemConfigurationView.java)
- [central-backend/src/main/java/vn/tmc/central/systemadmin/internal/controller/AdministrationController.java](../central-backend/src/main/java/vn/tmc/central/systemadmin/internal/controller/AdministrationController.java)
- [central-backend/src/main/java/vn/tmc/central/systemadmin/internal/controller/GateController.java](../central-backend/src/main/java/vn/tmc/central/systemadmin/internal/controller/GateController.java)
- [central-backend/src/main/java/vn/tmc/central/systemadmin/internal/model/Factory.java](../central-backend/src/main/java/vn/tmc/central/systemadmin/internal/model/Factory.java)
- [central-backend/src/main/java/vn/tmc/central/systemadmin/internal/model/Gate.java](../central-backend/src/main/java/vn/tmc/central/systemadmin/internal/model/Gate.java)
- [central-backend/src/main/java/vn/tmc/central/systemadmin/internal/model/Shift.java](../central-backend/src/main/java/vn/tmc/central/systemadmin/internal/model/Shift.java)
- [central-backend/src/main/java/vn/tmc/central/systemadmin/internal/model/SystemConfiguration.java](../central-backend/src/main/java/vn/tmc/central/systemadmin/internal/model/SystemConfiguration.java)
- [central-backend/src/main/java/vn/tmc/central/systemadmin/internal/repository/GateRepository.java](../central-backend/src/main/java/vn/tmc/central/systemadmin/internal/repository/GateRepository.java)
- [central-backend/src/main/java/vn/tmc/central/systemadmin/internal/repository/SystemConfigurationRepository.java](../central-backend/src/main/java/vn/tmc/central/systemadmin/internal/repository/SystemConfigurationRepository.java)
- [central-backend/src/main/java/vn/tmc/central/systemadmin/internal/service/AdministrationService.java](../central-backend/src/main/java/vn/tmc/central/systemadmin/internal/service/AdministrationService.java)
- [central-backend/src/main/java/vn/tmc/central/systemadmin/internal/service/GateConfigurationService.java](../central-backend/src/main/java/vn/tmc/central/systemadmin/internal/service/GateConfigurationService.java)
- [frontend/src/pages/AdministrationPage.jsx](../frontend/src/pages/AdministrationPage.jsx)

<a id="task-6"></a>

## Task 6 — QR và bảo mật token

Sở hữu issuer/credential/khóa tại qr-security ở Central; JWS header/claims, verifier trong gate-station ở Edge, QrPage. Quyền nhà máy qua cache, QR không gửi email/SMS. Cùng người phụ trách nhưng issuer/verifier vẫn ở hai runtime khác nhau.

Phối hợp: Task 1/2/7.

**File phụ trách:**

- [central-backend/src/main/java/vn/tmc/central/qrsecurity/api/QrIssuer.java](../central-backend/src/main/java/vn/tmc/central/qrsecurity/api/QrIssuer.java)
- [central-backend/src/main/java/vn/tmc/central/qrsecurity/internal/controller/QrController.java](../central-backend/src/main/java/vn/tmc/central/qrsecurity/internal/controller/QrController.java)
- [central-backend/src/main/java/vn/tmc/central/qrsecurity/internal/model/QrCredential.java](../central-backend/src/main/java/vn/tmc/central/qrsecurity/internal/model/QrCredential.java)
- [central-backend/src/main/java/vn/tmc/central/qrsecurity/internal/repository/QrCredentialRepository.java](../central-backend/src/main/java/vn/tmc/central/qrsecurity/internal/repository/QrCredentialRepository.java)
- [central-backend/src/main/java/vn/tmc/central/qrsecurity/internal/service/QrCredentialService.java](../central-backend/src/main/java/vn/tmc/central/qrsecurity/internal/service/QrCredentialService.java)
- [edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/domain/QrVerificationPort.java](../edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/domain/QrVerificationPort.java)
- [edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/domain/VerifiedQr.java](../edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/domain/VerifiedQr.java)
- [edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/infrastructure/JwsVerifier.java](../edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/infrastructure/JwsVerifier.java)
- [frontend/src/pages/QrPage.jsx](../frontend/src/pages/QrPage.jsx)
- [shared-contracts/src/main/java/vn/tmc/contracts/JwsHeader.java](../shared-contracts/src/main/java/vn/tmc/contracts/JwsHeader.java)
- [shared-contracts/src/main/java/vn/tmc/contracts/QrClaims.java](../shared-contracts/src/main/java/vn/tmc/contracts/QrClaims.java)
- [shared-contracts/src/main/java/vn/tmc/contracts/QrTokenResponse.java](../shared-contracts/src/main/java/vn/tmc/contracts/QrTokenResponse.java)

<a id="task-7"></a>

## Task 7 — Lưu trữ Edge và đồng bộ offline

Sở hữu edge-sync: SQLite/WAL, delivery/cache/batch, reserve replay nguyên tử, bootstrap/revocation, retry/recovery và WAN monitor; schema/REST client/sync DTO. Mục tiêu offline ≥24 giờ; không xử lý quét UI hoặc mật mã.

Phối hợp: Task 2/3/6/8.

**File phụ trách:**

- [edge-gate-agent/pom.xml](../edge-gate-agent/pom.xml)
- [edge-gate-agent/src/main/java/vn/tmc/edge/EdgeApplication.java](../edge-gate-agent/src/main/java/vn/tmc/edge/EdgeApplication.java)
- [edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/api/LocalEventStorePort.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/api/LocalEventStorePort.java)
- [edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/application/BootstrapWorker.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/application/BootstrapWorker.java)
- [edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/application/ConnectivityMonitor.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/application/ConnectivityMonitor.java)
- [edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/application/RecoveryWorker.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/application/RecoveryWorker.java)
- [edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/application/SyncWorker.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/application/SyncWorker.java)
- [edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/domain/BootstrapPort.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/domain/BootstrapPort.java)
- [edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/domain/DeliveryState.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/domain/DeliveryState.java)
- [edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/domain/EventDelivery.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/domain/EventDelivery.java)
- [edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/domain/SyncPort.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/domain/SyncPort.java)
- [edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/infrastructure/RestSyncClient.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/infrastructure/RestSyncClient.java)
- [edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/infrastructure/SqliteStore.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/infrastructure/SqliteStore.java)
- [edge-gate-agent/src/main/resources/schema.sql](../edge-gate-agent/src/main/resources/schema.sql)
- [shared-contracts/src/main/java/vn/tmc/contracts/BootstrapResponse.java](../shared-contracts/src/main/java/vn/tmc/contracts/BootstrapResponse.java)
- [shared-contracts/src/main/java/vn/tmc/contracts/SyncBatchAck.java](../shared-contracts/src/main/java/vn/tmc/contracts/SyncBatchAck.java)
- [shared-contracts/src/main/java/vn/tmc/contracts/SyncBatchRequest.java](../shared-contracts/src/main/java/vn/tmc/contracts/SyncBatchRequest.java)

<a id="task-8"></a>

## Task 8 — Nền tảng và tích hợp hệ thống

Cấu hình/build/CI/Compose, entry Central, App/navigation/layout, HTTP client/ApiError, bean wiring và kiểm thử xuyên module/performance. Không làm thay toàn bộ migration hoặc unit test của các task khác.

Phối hợp: Task 1–7.

**File phụ trách:**

- [central-backend/pom.xml](../central-backend/pom.xml)
- [central-backend/src/main/java/vn/tmc/central/CentralApplication.java](../central-backend/src/main/java/vn/tmc/central/CentralApplication.java)
- [central-backend/src/main/resources/application.yml](../central-backend/src/main/resources/application.yml)
- [frontend/index.html](../frontend/index.html)
- [frontend/src/App.jsx](../frontend/src/App.jsx)
- [frontend/src/api.js](../frontend/src/api.js)
- [frontend/src/components/ModulePlaceholder.jsx](../frontend/src/components/ModulePlaceholder.jsx)
- [frontend/src/main.jsx](../frontend/src/main.jsx)
- [frontend/src/style.css](../frontend/src/style.css)
- [frontend/vite.config.js](../frontend/vite.config.js)
- [pom.xml](../pom.xml)
- [shared-contracts/pom.xml](../shared-contracts/pom.xml)
- [shared-contracts/src/main/java/vn/tmc/contracts/ApiError.java](../shared-contracts/src/main/java/vn/tmc/contracts/ApiError.java)

Task 8 quản lý thêm frontend/package.json, package-lock.json, .vscode/settings.json, .gitignore và .gitattributes; JSON không hỗ trợ comment nên ghi phân công tại đây.
