# Cấu trúc Central và Edge

PHỤ TRÁCH: Task 8. Áp dụng [phương án thống nhất](DECISIONS.md) thay các ranh giới module cũ trong PDF.

| Module | Runtime | Vị trí |
|---|---|---|
| user-mgmt | Central | [usermgmt](../central-backend/src/main/java/vn/tmc/central/usermgmt) |
| qr-security | Central | [qrsecurity](../central-backend/src/main/java/vn/tmc/central/qrsecurity) |
| attendance-processing | Central | [attendanceprocessing](../central-backend/src/main/java/vn/tmc/central/attendanceprocessing) |
| reporting-audit | Central | [reportingaudit](../central-backend/src/main/java/vn/tmc/central/reportingaudit) |
| system-admin | Central | [systemadmin](../central-backend/src/main/java/vn/tmc/central/systemadmin) |
| gate-station | Edge | [gatestation](../edge-gate-agent/src/main/java/vn/tmc/edge/gatestation) |
| edge-sync | Edge | [edgesync](../edge-gate-agent/src/main/java/vn/tmc/edge/edgesync) |

Central là modular monolith gồm 5 module. Edge có 2 module cục bộ. shared-contracts là thư viện DTO, không phải module nghiệp vụ thứ tám. Frontend là lớp View.

Central: View → Controller → Service → Repository port → JPA adapter/PostgreSQL. Domain không import JPA; API công khai không trả internal model. Named interfaces và dependency tests Modulith vẫn cần triển khai.

Edge: gate-station dùng policy/verifier/turnstile qua port và gọi edgesync.api.LocalEventStorePort để reserve replay + lưu trước mở. edge-sync thực hiện SQLite và REST tới Central. Hai runtime không truy cập database của nhau; lượt scan không chờ Central.

Quản lý cấu hình cổng nằm ở system-admin; xác minh QR và thực thi lệnh cổng nằm ở gate-station. Người dùng/vai trò/quyền nằm ở user-mgmt; nhà máy/cổng/ca nằm ở system-admin. Phân công người làm được ghi riêng tại [TEAM.md](TEAM.md).
