# TMC — Sườn triển khai theo tài liệu M2

Repo này cung cấp **khung code Java/React, vị trí file và checklist**, để nhóm nhận việc theo [phương án thống nhất](docs/DECISIONS.md), đối chiếu [main.pdf](docs/reference/main.pdf). Có giao diện điều hướng cơ bản; chưa có nghiệp vụ, HTTP API hoặc test chức năng hoàn chỉnh. Không dùng phần trăm để mô tả tiến độ.

**Đã chia 8 Task, mỗi Task dành cho một thành viên.** Xem [phân công và danh sách file](docs/TEAM.md). Số Task trong code là phân công mới; số mục PDF trong tài liệu tham chiếu là hệ thống đánh số riêng.

## Bắt đầu tại đây

1. [Module để chia cho thành viên](docs/MODULES.md): phạm vi, file bắt đầu và đầu ra.
2. [Task và nơi nhận việc](docs/TEAM.md): chọn phần 1–8, đọc checklist và điểm bắt đầu.
3. [49 yêu cầu cần triển khai](docs/REQUIREMENTS.md): từng mã FR có mức ưu tiên, Task phối hợp và đường dẫn file.
4. [Điểm cần thống nhất trong PDF](docs/SOURCE-NOTES.md): không bỏ qua các chỗ khác nhau giữa các chương.
5. [Cấu trúc MVC/Ports & Adapters](docs/architecture.md): giữ đúng ranh giới trước khi viết code.

## Các khu vực

| Khu vực | Mục cần triển khai |
|---|---|
| [central-backend](central-backend) | 5 module: nhân sự/quyền, QR, chấm công, báo cáo/audit và quản trị nhà máy/cổng/ca |
| [edge-gate-agent](edge-gate-agent) | 2 module: gate-station (quét/xác minh/mở cổng), edge-sync (SQLite/cache/offline/sync) |
| [shared-contracts](shared-contracts) | Envelope/DTO cho API, sync, bootstrap và QR |
| [frontend](frontend) | View nhân viên/QR/kiosk/bảo vệ/chấm công/báo cáo/quản trị |
| [tests](docs/tasks/08-testing-validation.md) | Kế hoạch JUnit/Mockito/Testcontainers/Bruno/k6 và evidence |
| [infrastructure](docs/tasks/01-context-allocation.md) | Checklist Docker Compose/CI/cấu hình môi trường |

## Cách sử dụng file sườn

- Java đã có package, class, record, interface và constructor injection. React đã có component và điều hướng. Comment giữ vai trò, Task, nguồn yêu cầu và TODO; các hàm nghiệp vụ chưa làm sẽ báo UnsupportedOperationException.
- Mỗi module có model và repository interface tối thiểu, cùng checklist các bảng còn cần làm. Chốt trường/quan hệ/contract trước khi viết JPA adapter và migration.
- Nghiệp vụ, HTTP, dữ liệu và thiết bị có ranh giới riêng; chủ mỗi module chốt port/schema và tự kiểm thử; Task 8 điều phối tích hợp.
- Chỉ sửa src/docs/config cần thiết. target, dist, node_modules và cache là thư mục sinh tự động, không phải phần thành viên phải viết.

## Công nghệ giữ để nhóm triển khai

Java 21; Spring Boot/Security/JPA, Spring Modulith/ArchUnit, PostgreSQL/Flyway; Edge Java/SQLite WAL; JWS bất đối xứng và ZXing; React/Vite; JUnit 5/Mockito/Testcontainers/Bruno/k6. Dependency hiện có giữ trong pom/package-lock; Task 6 chốt thư viện JWS trước khi bổ sung. Compose/GitHub Actions mới có checklist.

## Kiểm tra sườn

`mvn compile` biên dịch khung Java. Trong `frontend`, chạy `npm ci` rồi `npm run dev` để xem các trang sườn, `npm run build` để đóng gói. Backend chưa chạy chức năng: cần database/migration, bean wiring, HTTP mapping và xác thực. Build thành công không chứng minh nghiệp vụ đã hoàn thành.

Các demo, test/report cũ và contract không còn khớp PDF đã được bỏ khỏi bản làm việc. Lịch sử Git được giữ; chỉ ghi kết quả mới khi đã có implementation và chạy thật.

## Làm việc bằng Git

Dùng nhánh `task-N/ten-cong-viec` và tạo Pull Request về `main`. Xem [quy trình Git và bảo vệ nhánh chính](CONTRIBUTING.md). Quy tắc bảo vệ chỉ có hiệu lực sau khi bật trên GitHub.
