# Danh sách yêu cầu cần triển khai

PHỤ TRÁCH: Task 8 tổng hợp; chủ từng yêu cầu theo bảng dưới. Tất cả chưa triển khai / chưa kiểm chứng.

Ưu tiên các điều chỉnh trong [DECISIONS.md](DECISIONS.md). Nguồn gốc: [main.pdf](reference/main.pdf), §2.4, Bảng 2.3–2.9 (trang PDF 26–32). Các chi tiết đồng bộ/mật mã dùng §4–7 và ghi rõ điểm lệch tại [SOURCE-NOTES.md](SOURCE-NOTES.md).

Đếm theo từng mã trong bảng có **49 yêu cầu**: UQ 6 + GS 6 + AT 7 + RP 6 + SA 6 + QR 8 + OS 10. PDF ghi tổng 46 ở §2.4.8; giữ đủ 49 mã để không bỏ sót. M/S/C là mức ưu tiên Must/Should/Could của tài liệu, không phải trạng thái đã làm.

Một hàng chỉ trỏ điểm bắt đầu; phải phối hợp các Task ở cột kế bên. Mỗi Task tương ứng một thành viên, chưa gắn tên cá nhân.

| Mã | Việc cần triển khai | Ưu tiên | Task chính / phối hợp | Điểm bắt đầu | Trang PDF |
|---|---|---|---|---|---|
| FR-UQ-01 | Đồng bộ/nhập hồ sơ nhân viên, lấy bản cập nhật mới nhất | M | Task 1 / Task 2/5/6/8 | [EmployeeService.java](../central-backend/src/main/java/vn/tmc/central/usermgmt/internal/service/EmployeeService.java) | 26 |
| FR-UQ-02 | Một credential QR ACTIVE cho nhân viên đang làm việc | M | Task 6 / Task 1/2/7 | [QrCredentialService.java](../central-backend/src/main/java/vn/tmc/central/qrsecurity/internal/service/QrCredentialService.java) | 26 |
| FR-UQ-03 | Hiển thị QR động trên app/web sau đăng nhập; email/SMS không chứa QR/JWS | M | Task 6 / Task 1/2/7 | [QrPage.jsx](../frontend/src/pages/QrPage.jsx) | 26 |
| FR-UQ-04 | Thu hồi credential khi nghỉ việc/mất thiết bị/nghi lộ | M | Task 6 / Task 1/2/7 | [QrCredentialService.java](../central-backend/src/main/java/vn/tmc/central/qrsecurity/internal/service/QrCredentialService.java) | 26 |
| FR-UQ-05 | Cấp lại sau xác minh, thu hồi credential cũ | M | Task 6 / Task 1/2/7 | [QrCredentialService.java](../central-backend/src/main/java/vn/tmc/central/qrsecurity/internal/service/QrCredentialService.java) | 26 |
| FR-UQ-06 | Quyền theo nhà máy/khu vực/ca, kiểm tra tại Edge | M | Task 1 / Task 2/5/6/8 | [EmployeeService.java](../central-backend/src/main/java/vn/tmc/central/usermgmt/internal/service/EmployeeService.java) | 26 |
| FR-GS-01 | Đăng ký cổng/kiosk/scanner, hướng và turnstile_api_id | M | Task 5 / Task 1/2/8 | [GateConfigurationService.java](../central-backend/src/main/java/vn/tmc/central/systemadmin/internal/service/GateConfigurationService.java) | 26–27 |
| FR-GS-02 | Lệnh Turnstile, phản hồi/lỗi/timeout qua adapter | M | Task 2 / Task 1/3/5/6/7 | [MockTurnstileAdapter.java](../edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/infrastructure/MockTurnstileAdapter.java) | 26–27 |
| FR-GS-03 | Kiosk hiển thị cho phép/từ chối và lý do trong mục tiêu 2 giây | M | Task 2 / Task 1/3/5/6/7 | [KioskPage.jsx](../frontend/src/pages/KioskPage.jsx) | 26–27 |
| FR-GS-04 | Heartbeat, trạng thái thiết bị và cảnh báo | M | Task 7 / Task 2/3/6/8 | [ConnectivityMonitor.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/application/ConnectivityMonitor.java) | 26–27 |
| FR-GS-05 | Bật/tắt và emergency có re-auth/audit; không tạo công giả | S | Task 2 / Task 1/3/5/6/7 | [GuardConsolePage.jsx](../frontend/src/pages/GuardConsolePage.jsx) | 26–27 |
| FR-GS-06 | Manual override có operator/reason và event MANUAL | M | Task 2 / Task 1/3/5/6/7 | [ScanQrUseCase.java](../edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/application/ScanQrUseCase.java) | 26–27 |
| FR-AT-01 | Ghi AttendanceEvent bất biến cho lượt hợp lệ | M | Task 3 / Task 2/4/7 | [AttendanceProcessingService.java](../central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/service/AttendanceProcessingService.java) | 27–28 |
| FR-AT-02 | Idempotency eventId; phân biệt retry với quét trùng nghiệp vụ | M | Task 3 / Task 2/4/7 | [AttendanceSyncController.java](../central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/controller/AttendanceSyncController.java) | 27–28 |
| FR-AT-03 | Tính công/đi trễ/về sớm/thiếu quẹt theo ca | M | Task 3 / Task 2/4/7 | [AttendanceProcessingService.java](../central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/service/AttendanceProcessingService.java) | 27–28 |
| FR-AT-04 | Phát hiện vào liên tiếp, thiếu ra, replay giữa cổng | S | Task 3 / Task 2/4/7 | [AttendanceProcessingService.java](../central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/service/AttendanceProcessingService.java) | 27–28 |
| FR-AT-05 | Điều chỉnh có lý do, duyệt độc lập, lịch sử; không sửa event | M | Task 3 / Task 2/4/7 | [AttendanceAdjustmentService.java](../central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/service/AttendanceAdjustmentService.java) | 27–28 |
| FR-AT-06 | Nhân viên xem lịch sử của chính mình | S | Task 3 / Task 2/4/7 | [AttendancePage.jsx](../frontend/src/pages/AttendancePage.jsx) | 27–28 |
| FR-AT-07 | Danh sách hiện diện phục vụ sơ tán | C | Task 2 / Task 1/3/5/6/7 | [GuardConsolePage.jsx](../frontend/src/pages/GuardConsolePage.jsx) | 27–28 |
| FR-RP-01 | Báo cáo ngày/tuần/tháng theo nhân viên/phòng/nhà máy | M | Task 4 / Task 1/3/5/8 | [AttendanceReportService.java](../central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/service/AttendanceReportService.java) | 28 |
| FR-RP-02 | Dashboard chuyên cần, hiện diện và bất thường | S | Task 4 / Task 1/3/5/8 | [ReportsPage.jsx](../frontend/src/pages/ReportsPage.jsx) | 28 |
| FR-RP-03 | Chốt chính thức 06:30 ngày 01, ca đêm thuộc tháng trước; outbox/xuất HR trước 12:00 và retry | M | Task 4 / Task 1/3/5/8 | [PayrollExportService.java](../central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/service/PayrollExportService.java) | 28 |
| FR-RP-04 | Đối soát số bản ghi gửi với HR xác nhận | S | Task 4 / Task 1/3/5/8 | [PayrollExportService.java](../central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/service/PayrollExportService.java) | 28 |
| FR-RP-05 | Tra cứu AuditLog append-only theo actor/action/time/resource | M | Task 4 / Task 1/3/5/8 | [AuditService.java](../central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/service/AuditService.java) | 28 |
| FR-RP-06 | Xuất CSV/Excel/PDF theo quyền | S | Task 4 / Task 1/3/5/8 | [AttendanceReportService.java](../central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/service/AttendanceReportService.java) | 28 |
| FR-SA-01 | Tài khoản, role/permission, khóa/mở; 7 vai trò | M | Task 1 / Task 2/5/6/8 | [AuthenticationService.java](../central-backend/src/main/java/vn/tmc/central/usermgmt/internal/service/AuthenticationService.java) | 29 |
| FR-SA-02 | Ca, ngày lễ, dung sai và cửa sổ chống trùng | M | Task 5 / Task 1/2/8 | [AdministrationService.java](../central-backend/src/main/java/vn/tmc/central/systemadmin/internal/service/AdministrationService.java) | 29 |
| FR-SA-03 | Cấu hình endpoint, secret reference, timeout/retry tích hợp | M | Task 5 / Task 1/2/8 | [AdministrationService.java](../central-backend/src/main/java/vn/tmc/central/systemadmin/internal/service/AdministrationService.java) | 29 |
| FR-SA-04 | Giám sát lỗi tích hợp, thiết bị và queue dài | S | Task 5 / Task 1/2/8 | [AdministrationPage.jsx](../frontend/src/pages/AdministrationPage.jsx) | 29 |
| FR-SA-05 | Backup/restore/archive/ẩn danh theo chính sách | M | Task 5 / Task 1/2/8 | [AdministrationService.java](../central-backend/src/main/java/vn/tmc/central/systemadmin/internal/service/AdministrationService.java) | 29 |
| FR-SA-06 | Quy trình yêu cầu của chủ thể dữ liệu | S | Task 5 / Task 1/2/8 | [AdministrationService.java](../central-backend/src/main/java/vn/tmc/central/systemadmin/internal/service/AdministrationService.java) | 29 |
| FR-QR-01 | Phát hành JWS bất đối xứng ES256, allowlist; kid ở header | M | Task 6 / Task 1/2/7 | [QrCredentialService.java](../central-backend/src/main/java/vn/tmc/central/qrsecurity/internal/service/QrCredentialService.java) | 29–30 |
| FR-QR-02 | TTL mặc định 60 giây; kiểm tra iat/nbf/exp/skew | M | Task 6 / Task 1/2/7 | [JwsVerifier.java](../edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/infrastructure/JwsVerifier.java) | 29–30 |
| FR-QR-03 | Atomic replay jti tại một Edge trong TTL | M | Task 7 / Task 2/3/6/8 | [SqliteStore.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/infrastructure/SqliteStore.java) | 29–30 |
| FR-QR-04 | Revocation theo sub/jti/kid và revision | M | Task 7 / Task 2/3/6/8 | [BootstrapWorker.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/application/BootstrapWorker.java) | 29–30 |
| FR-QR-05 | Rotation mặc định 90 ngày, overlap và thu hồi khẩn cấp | S | Task 6 / Task 1/2/7 | [QrCredentialService.java](../central-backend/src/main/java/vn/tmc/central/qrsecurity/internal/service/QrCredentialService.java) | 29–30 |
| FR-QR-06 | Xác minh chữ ký tại Edge bằng public key tin cậy | M | Task 6 / Task 1/2/7 | [JwsVerifier.java](../edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/infrastructure/JwsVerifier.java) | 29–30 |
| FR-QR-07 | Unknown kid: refresh qua mTLS, thất bại thì từ chối/cảnh báo | M | Task 6 / Task 1/2/7 | [JwsVerifier.java](../edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/infrastructure/JwsVerifier.java) | 29–30 |
| FR-QR-08 | Từ chối có mã lý do/audit; không JWS thô hoặc PII dư thừa | M | Task 6 / Task 1/2/7 | [JwsVerifier.java](../edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/infrastructure/JwsVerifier.java) | 29–30 |
| FR-OS-01 | Vận hành offline bằng cache tin cậy; nêu rõ rủi ro thu hồi chậm | M | Task 2 / Task 1/3/5/6/7 | [ScanQrUseCase.java](../edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/application/ScanQrUseCase.java) | 31–32 |
| FR-OS-02 | SQLite commit event/replay trước command; lỗi ghi thì không mở | M | Task 7 / Task 2/3/6/8 | [SqliteStore.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/infrastructure/SqliteStore.java) | 31–32 |
| FR-OS-03 | eventId UUIDv7, sequence/hash/payload giữ nguyên khi retry | M | Task 3 / Task 2/4/7 | [EventEnvelope.java](../shared-contracts/src/main/java/vn/tmc/contracts/EventEnvelope.java) | 31–32 |
| FR-OS-04 | Batch tối đa 50, ACK riêng từng event sau commit | M | Task 7 / Task 2/3/6/8 | [SyncWorker.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/application/SyncWorker.java) | 31–32 |
| FR-OS-05 | Backoff+jitter, không xóa trước ACK | M | Task 7 / Task 2/3/6/8 | [SyncWorker.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/application/SyncWorker.java) | 31–32 |
| FR-OS-06 | Central trùng ID/cùng hash trả DUPLICATE, không thêm dòng | M | Task 3 / Task 2/4/7 | [AttendanceSyncController.java](../central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/controller/AttendanceSyncController.java) | 31–32 |
| FR-OS-07 | Replay cache cục bộ, đồng bộ Central để đối chiếu | S | Task 7 / Task 2/3/6/8 | [SqliteStore.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/infrastructure/SqliteStore.java) | 31–32 |
| FR-OS-08 | Giám sát dung lượng 30 ngày/50K là mốc thiết kế, không tự xóa pending | S | Task 7 / Task 2/3/6/8 | [RecoveryWorker.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/application/RecoveryWorker.java) | 31–32 |
| FR-OS-09 | Delivery state PENDING/ACKNOWLEDGED/FAILED theo §6; tách khỏi event | M | Task 7 / Task 2/3/6/8 | [SyncWorker.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/application/SyncWorker.java) | 31–32 |
| FR-OS-10 | FIFO sequenceNo; tính công theo thời điểm sự kiện | M | Task 7 / Task 2/3/6/8 | [SyncWorker.java](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/application/SyncWorker.java) | 31–32 |
