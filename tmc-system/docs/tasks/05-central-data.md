# Mục PDF 5 — Central PostgreSQL và Event Contract

Tài liệu tham chiếu theo mục của PDF. Khi khác nhau, áp dụng [DECISIONS.md](../DECISIONS.md). Phân công triển khai xem [TEAM.md](../TEAM.md).

Nguồn: [main.pdf](../reference/main.pdf) phần 5; PDF tr. 73–81.

## Việc cần làm

- [ ] Hoàn thiện ERD, PK/FK/CHECK/index, entity mapping/repository và Flyway cho Central.
- [ ] Event bất biến, UUIDv7, 2 trục thời gian UTC, sequence theo Edge, payload/hash cố định.
- [ ] Mỗi event một transaction; chèn hoặc so hash; commit trước ACK. Ghi outbox/audit theo ranh giới đã chốt.
- [ ] Dùng PostgreSQL/Testcontainers kiểm chứng schema, constraints và chống trùng; không viết kết quả trước khi chạy.

## Model và bảng cần triển khai

| Module sở hữu | Thực thể/bảng cốt lõi |
|---|---|
| user-mgmt | employees |
| system-admin / gate-station | edge_nodes/gates do system-admin quản lý; manual_override_logs phát sinh tại Edge, đồng bộ để lưu/truy vết |
| attendance-processing | attendance_events, denied_attempts, attendance_adjustments, daily_attendance_records, sync_batches |
| reporting-audit | export_batches, outbox_messages, audit_logs |
| qr-security | qr_credentials, signing_keys, revocation_entries, replay_jtis |

16 thực thể cốt lõi theo §5.1. Bảng phụ cần chốt: factories, departments, shifts, access_zone_assignments, user_accounts, roles, permissions, system_configs, gate_command_results, adjustment_histories, sync_conflicts, monthly_attendance_summaries. Danh sách này là việc cần làm, chưa có DDL.

## Event contract

- Envelope: `eventId` UUIDv7, `eventType`, `schemaVersion` MAJOR.MINOR, `edgeId/gateId/factoryId`, `sequenceNo`, `correlationId`, `occurredAt`, `payloadSha256`, `payload`.
- Payload EVT-01: `employeeId`, direction IN/OUT, source ONLINE/OFFLINE/MANUAL, `jti`, `kid`, `commandId`; MANUAL thêm operatorId/reason và không jti.
- EVT-01 `attendance.event.recorded`; EVT-02 `access.attempt.denied`; EVT-03 `gate.command.result`; EVT-04 `replay.cache.reported`.
- FEED-01 `revocation.delta`; FEED-02 `signing-key.set` (JWKS); FEED-03 `access-policy.snapshot`. HR outbox topic `export.batch.created`.
- Hash trên canonical JSON UTF-8 theo §4–5; chốt quy tắc số/escaping/null với Mục PDF 4/6. Không tái dựng payload khi retry.
- `occurredAt` wire ánh xạ `observed_at` database; `received_at` do Central ghi. Không từ chối event đến muộn chỉ vì QR hết hạn tại lúc nhận.
- Unique event_id và (edge_id, sequence_no); jti trên attendance_events không unique. Dữ kiện nghi replay vẫn được giữ, thêm REPLAY_SUSPECTED/POST_REVOKE_VIOLATION.
- Command result độc lập EVT-03, không sửa payload event ban đầu sau khi biết cổng nhận lệnh.

## Ràng buộc và kiểm chứng

- Partial unique credential ACTIVE/nhân viên, một khóa ký ACTIVE; private key chỉ tham chiếu secret store.
- Append-only event/denied/audit; tạo khác duyệt; MANUAL có operator/reason.
- Index nhân viên/nhà máy + observed_at, revision, jti khi có, outbox đến hạn; EXPLAIN sau khi có dữ liệu.
- Không partition attendance theo ngày làm mất unique eventId; audit partition theo tháng.
- Retention dự kiến §5: online ≥2 năm, cold 5 năm; chốt ngoại lệ archive role và ẩn danh hồ sơ trước khi viết job.
- Testcontainers: schema từ DB trống, gửi cùng event 3 lần chỉ một dòng, CHECK/append-only và mixed ACK.

## Vị trí bắt đầu

Các thư mục `internal/model/` và `internal/repository/` trong năm module Central; Flyway.

## Bàn giao

- [ ] Ghi contract đầu vào/đầu ra và Task phối hợp trước khi nối module.
- [ ] Cập nhật [yêu cầu liên quan](../REQUIREMENTS.md) và test Mục PDF 8 khi có implementation.
- [ ] Nộp bằng chứng thực tế; không đổi trạng thái chỉ vì có file sườn.

## Checklist bổ sung tại vị trí triển khai

### `central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/model`

- [ ] attendance_events: bất biến, event_id UUIDv7, observed_at và received_at
- [ ] denied_attempts: từ chối, không tính vào giờ công
- [ ] attendance_adjustments, adjustment_histories: lý do, người tạo khác người duyệt
- [ ] daily_attendance_records: dữ liệu dẫn xuất; work_date theo ngày bắt đầu ca
- [ ] sync_batches, sync_conflicts: batch ID ổn định, xung đột hash và truy vết

### `central-backend/src/main/java/vn/tmc/central/attendanceprocessing/internal/repository`

- [ ] Transaction riêng từng event; commit rồi mới ACK ACCEPTED/DUPLICATE.
- [ ] Unique event_id và (edge_id, sequence_no); trùng ID khác hash giữ bản gốc và ghi conflict.
- [ ] jti trên attendance_events không unique: vẫn giữ event nghi replay/thu hồi muộn và cảnh báo.
- [ ] Schema/hash/version theo shared-contracts; chống UPDATE/DELETE với role ứng dụng.
- [ ] Chốt PK/FK/CHECK/index với Flyway và viết test PostgreSQL thật cùng Mục PDF 8.
- [ ] Không đưa luật UI, HTTP hoặc mật mã vào repository.

### `central-backend/src/main/java/vn/tmc/central/systemadmin/internal/model`

- [ ] gates, edge_nodes: factory, hướng IN/OUT, turnstile_api_id, identity thiết bị
- [ ] manual_override_logs: MANUAL có employee/event; EMERGENCY không tạo công giả
- [ ] gate_status_logs: heartbeat/trạng thái; gate_command_results: kết quả command riêng

### `central-backend/src/main/java/vn/tmc/central/systemadmin/internal/repository`

- [ ] Ràng buộc gate thuộc factory/edge; đăng ký danh tính chứng thư thiết bị.
- [ ] Lưu heartbeat, override và command result, không sửa attendance event gốc.
- [ ] Xác định module sở hữu gate_command_results với Mục PDF 3/5 trước khi viết migration.

### `central-backend/src/main/java/vn/tmc/central/qrsecurity/internal/model`

- [ ] qr_credentials: tối đa một credential ACTIVE/nhân viên; không lưu mỗi token 60 giây
- [ ] signing_keys: public key và private_key_ref, ACTIVE/GRACE/EXPIRED/REVOKED tại Central
- [ ] revocation_entries: revision tăng dần, thu hồi theo sub/jti/kid
- [ ] replay_jtis: hợp nhất token đã dùng từ các Edge; phát hiện và cảnh báo
- [ ] DynamicQrToken: value object; kid thuộc protected header, không phải payload claim

### `central-backend/src/main/java/vn/tmc/central/qrsecurity/internal/repository`

- [ ] Partial unique active credential và active signing key; private key không lưu trong DB.
- [ ] Revocation delta theo revision; replay_jtis được ghi khi token sử dụng, không theo từng lần phát hành.
- [ ] Mục PDF 7 sở hữu nghiệp vụ mật mã, Mục PDF 5 sở hữu schema/persistence; Edge chỉ có public key.

### `central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/model`

- [ ] monthly_attendance_summaries: dữ liệu báo cáo dẫn xuất
- [ ] export_batches: kỳ/nhà máy/version, checksum, EXPORT_SUCCESS/PARTIAL
- [ ] outbox_messages: ghi cùng transaction nghiệp vụ, gửi HR độc lập
- [ ] audit_logs: append-only, actor tham chiếu mềm, correlation ID; partition theo tháng

### `central-backend/src/main/java/vn/tmc/central/reportingaudit/internal/repository`

- [ ] Không truy cập repository attendance từ service báo cáo; dùng port công khai.
- [ ] Outbox lấy việc đến hạn và retry; batch_id ổn định để HR chống trùng.
- [ ] Truy vấn audit/report theo role và data scope; không cung cấp sửa/xóa audit tùy ý.
- [ ] Retention/archive theo Mục PDF 5 và quyết định nhóm; không phân vùng attendance phá unique event_id.

### `central-backend/src/main/java/vn/tmc/central/systemadmin/internal/model`

- [ ] factories, shifts: nhà máy, timezone và cấu hình ca; user-mgmt chỉ tham chiếu ID.
- [ ] system_configs: ca, ngày lễ, dung sai, chống trùng, timeout/retry
- [ ] data_subject_requests: quy trình truy cập/chỉnh sửa/ẩn danh dữ liệu

### `central-backend/src/main/java/vn/tmc/central/systemadmin/internal/repository`

- [ ] Quản lý cấu hình nhà máy/cổng/ca, không sở hữu account/role.
- [ ] Cấu hình tích hợp lưu tham chiếu secret, không commit khóa/mật khẩu.
- [ ] Backup/restore/archive và DSR có audit, không tự xóa event bất biến.

### `central-backend/src/main/java/vn/tmc/central/usermgmt/internal/model`

- [ ] employees: mã NV, PII tối thiểu, trạng thái, factory_id, anonymized_at
- [ ] user_accounts, roles, permissions thuộc user-mgmt; tách account khỏi hồ sơ nhân viên, có Auditor chính thức.
- [ ] user-mgmt: departments/access_zone_assignments; factories/shifts do system-admin sở hữu, chỉ tham chiếu ID

### `central-backend/src/main/java/vn/tmc/central/usermgmt/internal/repository`

- [ ] Đồng bộ hồ sơ theo phiên bản/thời điểm mới nhất; không nhân đôi employee_code.
- [ ] Truy vấn có data scope; module khác gọi query port, không import repository.
- [ ] Đổi trạng thái nhân viên phát sự kiện cho phần thu hồi credential, không ký QR tại đây.

### `central-backend/src/main/resources/db/migration`

- [ ] Thiết kế đầy đủ schema theo 16 thực thể cốt lõi và các bảng phụ trong tài liệu Mục PDF 5.
- [ ] PK/FK, event UUIDv7, unique (edge_id, sequence_no), CHECK manual/approval, append-only, outbox và indexes.
- [ ] Viết migration đánh phiên bản khi chốt ERD; không sửa migration đã áp dụng vào môi trường dùng chung.
- [ ] Test khởi tạo database trống bằng Testcontainers cùng Mục PDF 8.
