# Mục PDF 8 — NFR, Test Plan và Architecture Validation

Tài liệu tham chiếu theo mục của PDF. Khi khác nhau, áp dụng [DECISIONS.md](../DECISIONS.md). Phân công triển khai xem [TEAM.md](../TEAM.md).

Nguồn: [main.pdf](../reference/main.pdf) phần 8; PDF tr. 98–104.

## Việc cần làm

- [ ] Lập test plan, 5 quality scenarios, traceability FR/NFR–architecture–test và validation checklist.
- [ ] Chuẩn bị JUnit 5, Mockito, Testcontainers, Bruno, k6; chưa viết test pass giả hoặc mặc định chức năng đã có.
- [ ] Kiểm tra RBAC/data scope, sync mixed ACK, durability/restart, QR negative cases và module boundaries.
- [ ] Sau hiện thực, ghi lệnh/môi trường/commit/time, số test, p95/error/backlog và giới hạn thực tế vào evidence; phần 9–10 hiện chưa có kết quả.

## Mục tiêu cần đo, chưa phải kết quả

| NFR | Tiêu chí theo §8 |
|---|---|
| NFR-PERF-01 | Ít nhất 95% lượt online ≤2s tại tải mục tiêu 800 lượt/30 phút/nhà máy |
| NFR-AVL-01 | Edge tiếp tục bằng cache tin cậy khi WAN/Central lỗi |
| NFR-REL-01 | Không mất event đã commit, không trùng Central sau retry |
| NFR-SEC-01 | Token âm bị từ chối theo trạng thái tin cậy đã biết; giới hạn offline phải nêu rõ |
| NFR-PRI-01 | Payload/log tối thiểu, dữ liệu API theo scope |
| NFR-AUD-01 | Quyết định/override/lỗi có thời gian, actor/device, kết quả, correlation ID |
| NFR-OFF-01 | Queue bền vững; batch đủ điều kiện gửi trong ≤60s sau phục hồi |
| NFR-INT-01 | Turnstile/HR sau port/adapter và contract có version |
| NFR-MNT-01 | Không vòng dependency/rò internal; đổi adapter không phá contract |

5 quality scenarios: giờ cao điểm; WAN đứt; token giả/replay; Central timeout/5xx/mất ACK; thay adapter.

## Test case và bằng chứng cần tạo

- UT-GS-01 / API-GS-01 / PERF-GS-01: scan, không mở trước commit, latency.
- UT-AT-01 / IT-AT-01: event bất biến, tính công, idempotency; các ngày/ca biên.
- IT-OS-01 / REC-OS-01: WAN lỗi, restart, crash sau commit trước command.
- IT-OS-02 / REC-OS-02: mất ACK, mixed ACK, retry cùng batch/hash, revision crash và replay race.
- UT-QR-01 / SEC-QR-01 / SEC-QR-02 / API-QR-01: token âm, TTL, replay, revocation/rotation.
- API-RP-01 / IT-RP-01: scope báo cáo, outbox/HR idempotency và đối soát đúng hạn.
- UT-SA-01 / IT-ARCH-01: RBAC, module boundary, domain độc lập framework.

Entry: contract/schema/BR đã chốt, code build được, fixtures/migration test có sẵn. Exit: chạy các test bắt buộc, có evidence, ghi rõ lỗi/chưa chạy. Mọi mục hiện **Chưa triển khai / Chưa xác minh**; không kế thừa kết quả test demo cũ.

## Vị trí bắt đầu

Kế hoạch công cụ và evidence; các thư mục `src/test` trong Java và `frontend/tests`.

## Bàn giao

- [ ] Ghi contract đầu vào/đầu ra và Task phối hợp trước khi nối module.
- [ ] Cập nhật [yêu cầu liên quan](../REQUIREMENTS.md) và test Mục PDF 8 khi có implementation.
- [ ] Nộp bằng chứng thực tế; không đổi trạng thái chỉ vì có file sườn.

## Checklist bổ sung tại vị trí triển khai

### `central-backend/src/test`

- [ ] Mục PDF 3/8: module DAG/internal, domain không Spring/JPA; Mục PDF 5/8: PostgreSQL/Flyway, per-event transaction, mixed ACK, constraints; Mục PDF 2/4/8: RBAC/data scope/adjustment.

### `edge-gate-agent/src/test`

- [ ] Mục PDF 6/8: disk failure, crash/restart, batch ID/membership, ACK mất/mixed, revision atomic, queue capacity; Mục PDF 7/8: replay race, cache/key TTL; Mục PDF 2/8: không mở trước commit.

### `frontend/tests`

- [ ] Role/scope của từng view; token/API errors/timeout/cancel; không hiển thị thành công khi backend từ chối; không lưu secret. Chọn công cụ cùng Mục PDF 8 khi có component.

### `tests/bruno`

- [ ] Tạo collection theo bảy endpoint Mục PDF 4; bearer/mTLS, scope, validation và ACK. Môi trường dùng biến cho secret, không commit credential. Chưa có request chạy.

### `tests/evidence`

- [ ] Sau khi chạy, ghi ngày, commit, máy/môi trường, lệnh, dataset, expected/actual, report/log, latency/error/backlog và giới hạn. Chưa có bằng chứng đạt cho bản sườn.

### `tests/k6`

- [ ] Tạo workload quét QR end-to-end 800 lượt/30 phút/nhà máy, p95 ≤2s; fixtures ký hợp lệ riêng, WAN/Central failure và backlog drain. Không thay bằng health smoke.

## Kiểm thử cần bổ sung theo phương án Final

- [ ] Ca đêm cuối tháng kết thúc 06:00 thuộc kỳ trước; chốt 06:30 và hoàn tất xuất trước 12:00 theo timezone nhà máy; không khóa kỳ ở 23:00.
- [ ] Kiểm tra 5 module Central/2 Edge; không truy cập database chéo; ngắt Central nhưng scan dùng cache hợp lệ vẫn hoạt động.
- [ ] Header ES256/kid và đủ 7 claims; aud không là factory; cache policy từ chối sai khu vực. Unknown kid không tạo HTTP chờ trong scan.
- [ ] QR chỉ cấp sau đăng nhập; mọi email/SMS không chứa QR/JWS.
- [ ] ACCEPTED/DUPLICATE → ACKNOWLEDGED, CONFLICT/REJECTED → FAILED; mất ACK giữ PENDING, event gốc không bị sửa.
- [ ] Revocation online ≤60s; ngắt WAN ≥24h với cấu hình khóa/cache chuẩn bị hợp lệ; reconnect phát POST_REVOKE_VIOLATION khi cần.
- [ ] Auditor xem/tìm audit và lịch sử đúng scope; chỉ xuất khi được cấp quyền; tất cả thao tác sửa/duyệt/mở cổng/cấp QR/đổi quyền/xóa audit đều bị backend từ chối.

Các mục trên là tiêu chí phải kiểm thử khi có implementation; hiện mới kiểm tra biên dịch và cấu trúc sườn.
