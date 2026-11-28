# Task 8 — Chạy và tích hợp nền tảng

PHỤ TRÁCH: Task 8. Áp dụng Contract V1 §2/3/10–12. Đây là nền tảng chạy được, không phải hoàn thành nghiệp vụ Task 1–7. Chưa có bằng chứng toàn nhóm đã duyệt mọi chi tiết baseline V1.

## Build và test

Từ `tmc-project/tmc-system`, dùng JDK 21+, Maven 3.9+, Node 24 và npm theo lockfile:

```powershell
mvn -B -ntp verify
Set-Location frontend
npm ci --no-audit --no-fund
npm test
npm run build
```

Maven mặc định chạy unit/wiring/architecture tests, chưa chạy PostgreSQL container. Với Docker engine đang chạy:

```powershell
mvn -B -ntp verify -Pintegration
```

`PostgresPlatformIT` kiểm tra PostgreSQL/Flyway thật với schema fixture chỉ trong test. Khi thiếu Docker, integration test phải fail, không skip để báo xanh giả. Migration nghiệp vụ vẫn do từng Task sở hữu.

## Chạy local

Kiểm tra nền tảng không DB:

```powershell
java -jar central-backend/target/central-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=platform
# Terminal khác, tại frontend:
npm run dev
```

- Frontend: http://127.0.0.1:5173; Vite chuyển `/api` đến Central 8080. Có thể đổi `TMC_CENTRAL_URL` trong môi trường chạy Vite.
- `GET /actuator/health` trả trạng thái nền tảng. Không dùng health để tuyên bố nghiệp vụ đã sẵn sàng.
- `/api/**` hiện trả lỗi xác thực; LoginPage và trang nghiệp vụ vẫn do chủ Task triển khai. Không có tài khoản mặc định.
- Profile `platform` bỏ DB auto-configuration; không dùng để chứng nhận readiness database hoặc chạy production.

Chạy Central + PostgreSQL + frontend bằng Compose:

```powershell
Copy-Item .env.example .env
# Điền TMC_DB_PASSWORD bằng mật khẩu riêng cho môi trường local.
docker compose config --quiet
docker compose up --build --wait --wait-timeout 240
docker compose down
```

`down` giữ volume PostgreSQL. Chỉ dùng `down --volumes` khi chủ động muốn xóa database local. PostgreSQL không publish cổng ra host; Central/frontend chỉ bind loopback. Compose này dùng HTTP local; production phải có HTTPS ingress và đường mTLS/device binding riêng theo Task 1/2/7. Không đưa chứng chỉ/private key vào image hoặc VITE_*.

Sau khi stack lên, chạy `node tests/platform-smoke.mjs`: kiểm tra health/requestId, HTML/JS frontend và chính HTTP client gọi qua nginx nhận ApiError 401. Có thể đổi origin bằng `CENTRAL_URL`/`FRONTEND_URL`. Bài này không đo nghiệp vụ hoặc hiệu năng scan.

Mặc định không bật profile `platform`: datasource/Flyway/JPA dùng PostgreSQL, Hibernate `validate`, không tự tạo schema nghiệp vụ. Cấp `TMC_DB_URL`, `TMC_DB_USER`, `TMC_DB_PASSWORD` qua môi trường. Chưa thêm migration giả vào production.

## Điểm bàn giao cho Task 1–7

- **Task 1:** LoginPage gọi `login(username,password)` từ `src/api.js`; helper nhận Session đúng §3 và lưu memory. Reload yêu cầu login lại. Cung cấp SecurityFilterChain xác thực opaque bearer và authorization theo scope để thay fallback của Task 8. Đăng ký RequestIdFilter trước security; reuse PlatformErrors cho lỗi security. Không tin ActorContext từ body. Phải có test chứng minh chain thật thay fallback và Auditor vẫn bị cấm mutation.
- **Task 2/7:** Kiosk cần mTLS thiết bị, không gửi session bearer như danh tính kiosk. Task 8 không nối browser chung tới Edge qua proxy giả danh thiết bị. Các port scan/store hiện chưa khớp V1 nên chưa wiring nghiệp vụ.
- **Task 3/4/5/6:** Chủ module bổ sung controller/bean thật, repository/migration của mình; không đăng ký stub ném TODO. Giữ public API và internal boundary; `ModuleBoundariesTest` kiểm tra Modulith.
- **Tất cả trang:** dùng `request('/path', options)`. Response trả trực tiếp; DENY/mixed ACK 200 do chủ trang diễn giải. Dùng RequestState cho loading/error/empty; chỉ cung cấp onRetry khi luồng cho phép retry.
- **Mutation:** POST ngoài các ngoại lệ của §2 bắt buộc truyền `idempotencyKey: crypto.randomUUID()` từ chủ thao tác. Giữ key cùng body khi retry kết quả chưa rõ. Client không tự retry hoặc tự đổi key. Logout helper cấp key cho một lần logout.
- **Lỗi:** ApiException chỉ dùng tại adapter HTTP và chỉ chứa message công khai. Không đưa Spring/HTTP exception vào domain. Mã ngoài baseline phải cùng chốt trước. `retryable` không đồng nghĩa được phép lặp mọi mutation.
- **UI:** route guards là tiện ích hiển thị, không thay backend authorization. Không có mock tự bật trong production; controller fixture và transport mock chỉ tồn tại trong tests.

## CI và kiểm thử tích hợp

Workflow nằm tại **`tmc-project/.github/workflows/ci.yml`** (Git root), không nằm trong `.github` của thư mục con. Ba job: Java/JDK21 + PostgreSQL IT; frontend test/build; Compose smoke qua nginx. Chỉ viết file local, chưa push/chạy GitHub Actions hoặc đặt branch protection.

Bruno: mở `tests/bruno`, chọn environment Local, chạy Health và Unauthenticated. Đây là smoke nền tảng, chưa phải collection nghiệm thu 49 FR.

| Luồng cần tích hợp | Chủ bàn giao | Điều kiện / trạng thái |
|---|---|---|
| Session → client → backend scope | 1 + 8 | Chờ Session/auth implementation; test client mock đã tách riêng |
| QR → scan → SQLite → command | 2/6/7 + 8 | Chờ typed port V1, cache/key và thiết bị test |
| Event → sync → ACK → retry | 3/7 + 8 | Chờ per-event transaction/typed bootstrap; dùng fixtures §11 khi sẵn sàng |
| Công → khóa kỳ → Payroll | 3/4/5 + 8 | Chờ dữ liệu ca/kỳ và HR adapter; kiểm tra ca đêm/06:30/partial ACK |
| Platform → PostgreSQL/Flyway | 8 | Có IT chạy thật, xem kết quả trong TASK8-PROGRESS |

## Hiệu năng scan thật

`tests/k6/scan.js` là harness cho NFR-PERF-01: 800 lượt/30 phút tại **một nhà máy**, p95 scan ≤2 giây, không dropped iterations, ALLOW + eventId/commandId + ONLINE/UNLOCK_ACCEPTED. Không suy ra người đã đi qua cổng khi không có passage sensor. Login/cấp QR đo riêng, không tính vào scan_duration.

Điều kiện: Task 1/2/6/7 chạy thật; cache tin cậy, policy/ca hợp lệ; 800 nhân viên test khác nhau để không đụng cửa sổ chống trùng; credential ACTIVE; kiosk cert/CA hợp lệ; Turnstile simulator hoặc thiết bị test được ghi rõ trong evidence. Không chạy lên cổng sản xuất.

File ngoài Git có dạng `[{"username":"...","password":"..."}, ...]`. Không commit tài khoản/chứng chỉ. Cấp biến môi trường `CENTRAL_URL`, `EDGE_URL` (HTTPS origin, không kèm /api/v1), `GATE_ID`, `USERS_FILE`, `KIOSK_CERT`, `KIOSK_KEY`, `KIOSK_TLS_DOMAIN` (hostname Edge), và CA tin cậy cho k6. Sau đó:

```powershell
k6 run tests/k6/scan.js
```

Summary lưu `tests/evidence/scan-summary.json` (gitignored). Ghi máy, commit, môi trường, dataset, số lượt, p95, error rate và backlog vào evidence. Không có kết quả đo khi chưa chạy. WAN cut/24h offline, replay/crash và backlog drain là các bài riêng còn chờ module thật; không tuyên bố đạt từ bài tải online này.

## Graphify và tiếp tục công việc

Đồ thị ở workspace cha `D:/Documents/ChatGPT/TMC/graphify-out`. Tra từ thư mục đó. Sau sửa code chạy `graphify update .` từ `D:/Documents/ChatGPT/TMC`; cập nhật AST không đồng nghĩa đã cập nhật ý nghĩa tài liệu. Đọc TASK8-PROGRESS để tiếp tục, đối chiếu git diff và kết quả test hiện tại trước khi sửa.
