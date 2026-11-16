# Mục PDF 4 — Component-and-Connector và API Contract

Tài liệu tham chiếu theo mục của PDF. Khi khác nhau, áp dụng [DECISIONS.md](../DECISIONS.md). Phân công triển khai xem [TEAM.md](../TEAM.md).

Nguồn: [main.pdf](../reference/main.pdf) phần 4; PDF tr. 65–72.

## Việc cần làm

- [ ] Chốt DTO, JSON schema/version, requestId, error envelope và quyền theo từng API.
- [ ] Frontend dùng bearer, Edge dùng mTLS và edge/factory binding; chưa có login/token implementation.
- [ ] Nhận batch/parse ACK theo từng event; HTTP 200 không đồng nghĩa mọi event thành công.
- [ ] Chốt LAN scanner/turnstile và HR adapter sau port; test contract với Mục PDF 8.

## Các endpoint đã nêu trong PDF

Tất cả là contract **cần triển khai**; đã có method Java nhưng chưa có HTTP mapping hoặc route chạy. Base `/api/v1`, JSON UTF-8, HTTPS. API frontend có page/size và requestId; thời gian ISO 8601 UTC.

| Method/path | Caller/quyền | Cần thực hiện |
|---|---|---|
| GET /me/qr-token | Bearer, chính nhân viên | JWS và thời điểm hết hạn |
| GET /attendance/events | Bearer + RBAC/scope | Lịch sử được phép xem |
| POST /attendance/adjustments | Bearer + RBAC | Tạo/duyệt/từ chối theo quyền |
| POST /attendance/sync | mTLS + edge/factory binding | Tối đa 50 envelope, ACK từng event |
| GET /edge/bootstrap?afterRevision={r} | mTLS + scope | Delta thu hồi, public JWKS, policy |
| GET /reports/attendance | Bearer + RBAC | Tổng hợp theo kỳ/scope |
| GET /qr/keys/refresh | mTLS | Refresh khóa công khai cho kid chưa biết |

Login, CRUD nhân viên/cổng/admin, override, audit và HR có yêu cầu nghiệp vụ nhưng URL chi tiết chưa được bảng 4.2 chốt. Đặt vào controller tương ứng rồi thống nhất contract, không coi URL tự đặt là trích từ PDF.

## Đồng bộ và lỗi

- Request: `syncBatchId`, `events`; envelope ở Mục PDF 5. Cùng lô retry giữ nguyên ID/membership/payload.
- Response: `syncBatchId`, `acks[{eventId,status,acceptedAt,code?}]`, `lastContiguousSequenceNo`.
- ACCEPTED/DUPLICATE: đã commit, Edge được đánh dấu ACKNOWLEDGED. CONFLICT/REJECTED: giữ dữ liệu, FAILED và điều tra.
- Transaction riêng từng event, không rollback cả lô do một event lỗi. ACK chỉ sau commit.
- Request lỗi: 400 validation, 401 xác thực, 403 quyền/binding, 429 Retry-After, 500/503 retry. Error envelope `code/message/requestId/retryable`.
- Event lỗi trong response 200: unsupported schema → REJECTED; cùng ID khác hash → CONFLICT. Không dùng HTTP 409 toàn batch như demo cũ.

## Vị trí bắt đầu

| File | Vai trò |
|---|---|
| [EmployeeController.java](../../central-backend/src/main/java/vn/tmc/central/usermgmt/internal/controller/EmployeeController.java) | controller |
| [GateController.java](../../central-backend/src/main/java/vn/tmc/central/systemadmin/internal/controller/GateController.java) | controller |
| [AttendanceController.java](../../central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/controller/AttendanceController.java) | controller |
| [AttendanceSyncController.java](../../central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/controller/AttendanceSyncController.java) | controller |
| [ReportAuditController.java](../../central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/controller/ReportAuditController.java) | controller |
| [HrPayrollAdapter.java](../../central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/adapter/HrPayrollAdapter.java) | adapter |
| [AdministrationController.java](../../central-backend/src/main/java/vn/tmc/central/systemadmin/internal/controller/AdministrationController.java) | controller |
| [QrController.java](../../central-backend/src/main/java/vn/tmc/central/qrsecurity/internal/controller/QrController.java) | controller |
| [ScanController.java](../../edge-gate-agent/src/main/java/vn/tmc/edge/gatestation/infrastructure/ScanController.java) | Adapter / Controller |
| [RestSyncClient.java](../../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync/infrastructure/RestSyncClient.java) | Adapter / Controller |
| [EventEnvelope.java](../../shared-contracts/src/main/java/vn/tmc/contracts/EventEnvelope.java) | DTO envelope |
| [AttendancePayload.java](../../shared-contracts/src/main/java/vn/tmc/contracts/AttendancePayload.java) | DTO EVT-01 |
| [SyncBatchRequest.java](../../shared-contracts/src/main/java/vn/tmc/contracts/SyncBatchRequest.java) | DTO request sync |
| [SyncBatchAck.java](../../shared-contracts/src/main/java/vn/tmc/contracts/SyncBatchAck.java) | DTO ACK |
| [BootstrapResponse.java](../../shared-contracts/src/main/java/vn/tmc/contracts/BootstrapResponse.java) | DTO bootstrap |
| [QrTokenResponse.java](../../shared-contracts/src/main/java/vn/tmc/contracts/QrTokenResponse.java) | DTO QR |
| [ApiError.java](../../shared-contracts/src/main/java/vn/tmc/contracts/ApiError.java) | DTO lỗi |
| [LoginPage.jsx](../../frontend/src/pages/LoginPage.jsx) | View |
| [api.js](../../frontend/src/api.js) | HTTP client |

## Bàn giao

- [ ] Ghi contract đầu vào/đầu ra và Task phối hợp trước khi nối module.
- [ ] Cập nhật [yêu cầu liên quan](../REQUIREMENTS.md) và test Mục PDF 8 khi có implementation.
- [ ] Nộp bằng chứng thực tế; không đổi trạng thái chỉ vì có file sườn.
