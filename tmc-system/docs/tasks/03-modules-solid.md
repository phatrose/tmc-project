# Mục PDF 3 — Module, Class/Port, SOLID và Reflection

Tài liệu tham chiếu theo mục của PDF. Khi khác nhau, áp dụng [DECISIONS.md](../DECISIONS.md). Phân công triển khai xem [TEAM.md](../TEAM.md).

Nguồn: [main.pdf](../reference/main.pdf) phần 3; PDF tr. 45–64.

## Việc cần làm

- [ ] Chốt module view, dependency DAG và public port; chọn tên interface thống nhất trước khi các phần import nhau.
- [ ] Các package api cần được export có chủ đích; internal không được truy cập từ module khác.
- [ ] Domain/model/use case chỉ phụ thuộc abstraction phù hợp; JPA/HTTP/SQLite/JWS nằm ở adapter.
- [ ] Viết architecture rules cùng Mục PDF 8; Reflection chỉ ghi trải nghiệm/bằng chứng thực sự có sau hiện thực.

## Cấu trúc mục tiêu

Năm module Central: `usermgmt`, `qrsecurity`, `attendanceprocessing`, `reportingaudit`, `systemadmin`. Hai module Edge: `gatestation`, `edgesync`. Ranh giới mới theo [DECISIONS.md](../DECISIONS.md).

- Central: `api` (port công khai) và `internal/controller`, `internal/service`, `internal/model`, `internal/repository`; HR adapter riêng khi cần.
- View: React ở frontend. Controller → service/use case → port → repository/adapter; model/DTO không phải bước gọi riêng.
- Edge: `domain`, `application`, `infrastructure`; không phụ thuộc implementation của Central.
- Shared-contracts: chỉ wire DTO/envelope, không JPA, Spring hoặc business rules.

SOLID là tiêu chí khi triển khai: SRP tách vai trò; OCP mở rộng adapter; LSP giữ semantics lỗi/trạng thái; ISP port nhỏ theo bên gọi; DIP nghiệp vụ phụ thuộc abstraction. Cấu trúc thư mục và file comment chưa chứng minh đạt SOLID.

## Vị trí bắt đầu

Các interface trong `api/` của năm module Central và [Edge domain/ports](06-edge-offline.md).

## Bàn giao

- [ ] Ghi contract đầu vào/đầu ra và Task phối hợp trước khi nối module.
- [ ] Cập nhật [yêu cầu liên quan](../REQUIREMENTS.md) và test Mục PDF 8 khi có implementation.
- [ ] Nộp bằng chứng thực tế; không đổi trạng thái chỉ vì có file sườn.

## Checklist bổ sung tại vị trí triển khai

### `central-backend/src/main/java/vn/tmc/central/attendanceprocessing/api`

- [ ] Chốt các interface: AttendanceProcessingPort, AdjustmentPort, AttendanceQueryPort; inbound SyncAttendanceBatchUseCase.
- [ ] Chốt DTO/event công khai và semantics lỗi; tránh interface trùng tên/chức năng.
- [ ] Không expose JPA entity hoặc repository; module khác chỉ qua API này.
- [ ] Khi hiện thực Spring Modulith: chọn public API ở package gốc hoặc khai báo named interface cho subpackage api; không giả định api tự được export.
- [ ] Thêm kiểm thử dependency vòng, truy cập internal và domain độc lập framework cùng Mục PDF 8.

### `central-backend/src/main/java/vn/tmc/central/systemadmin/api`

- [ ] Dùng GateManagement cho cấu hình cổng/thiết bị; API điều khiển Turnstile và tiếp nhận scan thuộc gate-station tại Edge.

### `central-backend/src/main/java/vn/tmc/central/qrsecurity/api`

- [ ] Chốt các interface: IssueDynamicQrUseCase, QrSigningKeyPort, RevocationQueryPort.

### `central-backend/src/main/java/vn/tmc/central/reportingaudit/api`

- [ ] Chốt các interface: ReportingQueryPort, PayrollExportPort, AuditPort, HrPayrollIntegrationPort.

### `central-backend/src/main/java/vn/tmc/central/systemadmin/api`

- [ ] Chốt ConfigurationPort cho nhà máy/ca/cấu hình; SecurityContextPort thuộc user-mgmt.

### `central-backend/src/main/java/vn/tmc/central/usermgmt/api`

- [ ] Chốt các interface: Authentication, SecurityContextPort, EmployeeManagement/EmployeeSyncPort; tránh khai báo trùng chức năng.
