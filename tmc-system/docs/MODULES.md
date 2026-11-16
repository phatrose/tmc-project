# Module và task phụ trách

Áp dụng [DECISIONS.md](DECISIONS.md); danh sách file trong [TEAM.md](TEAM.md). Có 7 module nghiệp vụ, chia 8 người theo phạm vi liên quan.

| Task | Phạm vi |
|---|---|
| [Task 1](TEAM.md#task-1) — Nhân sự, đăng nhập và quyền | Sở hữu user-mgmt: nhân viên, người dùng/đăng nhập, role/permission (gồm Auditor), quyền khu vực; EmployeePage và LoginPage. Tham chiếu nhà máy/ca do Task 5 quản lý. |
| [Task 2](TEAM.md#task-2) — Quét và vận hành cổng tại Edge | Sở hữu luồng gate-station: nhận scan, policy cache, điều phối verify → reserve/lưu → mở cổng, manual/emergency, Turnstile adapter, KioskPage và GuardConsolePage. Không quản lý cấu hình cổng ở Central; không tự viết verifier hoặc SQLite. |
| [Task 3](TEAM.md#task-3) — Chấm công và tiếp nhận sự kiện | Sở hữu attendance-processing: nhận/lưu event, idempotency và ACK sau commit, tính công, ca đêm thuộc tháng bắt đầu, điều chỉnh/phê duyệt, AttendancePage; EventEnvelope/AttendancePayload. |
| [Task 4](TEAM.md#task-4) — Báo cáo, audit và chốt Payroll | Sở hữu reporting-audit: tổng hợp/chốt chính thức 06:30 ngày 01, xuất/đối soát HR trước 12:00, outbox/audit, ReportsPage. Auditor được tra cứu và chỉ xuất nếu có quyền; không sửa dữ kiện công. |
| [Task 5](TEAM.md#task-5) — Nhà máy, cổng, ca và cấu hình | Sở hữu system-admin: nhà máy/timezone, cấu hình cổng và Edge, ca/ngày lễ, vận hành/backup/DSR, AdministrationPage. Không sở hữu đăng nhập, role hoặc permission; dùng API user-mgmt. |
| [Task 6](TEAM.md#task-6) — QR và bảo mật token | Sở hữu issuer/credential/khóa tại qr-security ở Central; JWS header/claims, verifier trong gate-station ở Edge, QrPage. Quyền nhà máy qua cache, QR không gửi email/SMS. Cùng người phụ trách nhưng issuer/verifier vẫn ở hai runtime khác nhau. |
| [Task 7](TEAM.md#task-7) — Lưu trữ Edge và đồng bộ offline | Sở hữu edge-sync: SQLite/WAL, delivery/cache/batch, reserve replay nguyên tử, bootstrap/revocation, retry/recovery và WAN monitor; schema/REST client/sync DTO. Mục tiêu offline ≥24 giờ; không xử lý quét UI hoặc mật mã. |
| [Task 8](TEAM.md#task-8) — Nền tảng và tích hợp hệ thống | Cấu hình/build/CI/Compose, entry Central, App/navigation/layout, HTTP client/ApiError, bean wiring và kiểm thử xuyên module/performance. Không làm thay toàn bộ migration hoặc unit test của các task khác. |
