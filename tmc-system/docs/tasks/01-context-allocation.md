# Mục PDF 1 — Context, Scope và Allocation

Tài liệu tham chiếu theo mục của PDF. Khi khác nhau, áp dụng [DECISIONS.md](../DECISIONS.md). Phân công triển khai xem [TEAM.md](../TEAM.md).

Nguồn: [main.pdf](../reference/main.pdf) phần 1; PDF tr. 9–20.

## Việc cần làm

- [ ] Chốt phạm vi, Context/Allocation cho BD ~800 và LA ~600 nhân viên; thiết bị, Edge, Central, HR mock và secret store.
- [ ] Giữ stack đã chọn; hoàn thiện bootstrap/build sau khi có code, không bổ sung tính năng ngoài PDF.
- [ ] Chuẩn bị Docker Compose/GitHub Actions theo §1.2 và §1.4, nhưng sườn hiện chỉ có checklist, chưa có deployment/workflow chạy.
- [ ] Tổng hợp Delta/ADR, README chạy dự án và bằng chứng phần 9–10 sau khi nhóm thực sự hiện thực.

## Giới hạn phạm vi

- Không làm tính lương/thuế, sinh trắc học, RFID, visitor/nhà thầu, MDM, thiết bị cổng hoặc HR thật.
- Mô phỏng Turnstile và HR/Payroll qua adapter. Chưa có dịch vụ triển khai hoặc số đo NFR.
- Mục tiêu thiết kế: 800 lượt/30 phút/nhà máy; p95 ≤2 giây; công sẵn sàng đối soát trước 12:00 ngày 01.

## Vị trí bắt đầu

| File | Vai trò |
|---|---|
| [CentralApplication.java](../../central-backend/src/main/java/vn/tmc/central/CentralApplication.java) | Bootstrap |
| [App.jsx](../../frontend/src/App.jsx) | App shell |
| [main.jsx](../../frontend/src/main.jsx) | Frontend entry |

## Bàn giao

- [ ] Ghi contract đầu vào/đầu ra và Task phối hợp trước khi nối module.
- [ ] Cập nhật [yêu cầu liên quan](../REQUIREMENTS.md) và test Mục PDF 8 khi có implementation.
- [ ] Nộp bằng chứng thực tế; không đổi trạng thái chỉ vì có file sườn.

## Checklist bổ sung tại vị trí triển khai

### `.github/workflows`

- [ ] Khi đã có implementation, thêm workflow build Java/frontend và kiểm thử theo môi trường nhóm.
- [ ] Thu test/evidence, kiểm tra kiến trúc và secrets qua cấu hình GitHub.
- [ ] Chưa có YAML workflow hoạt động; sườn không trigger chạy hoặc deployment.

### `infrastructure`

- [ ] Dockerfiles/Compose cho Central, PostgreSQL, Edge và mock Turnstile/HR; mô tả hai nhà máy.
- [ ] Persistent volumes PostgreSQL/SQLite, network mô phỏng WAN và secret/certificate riêng.
- [ ] HTTPS/mTLS, identity thiết bị, bearer và secret store cùng Mục PDF 4/7.
- [ ] Startup/health/migration, backup/restore và cấu hình test cùng Mục PDF 5/6/8.
- [ ] GitHub Actions build/test/architecture/integration khi đã có code; chưa tự động publish/deploy.
