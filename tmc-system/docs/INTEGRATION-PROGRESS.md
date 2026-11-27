# Bản thử tích hợp — 25/11/2026

Workspace: `D:/1/TEST/tmc-integration-test`, nhánh `integration/test-all`.
Cả 8 nhánh task đã được gộp cục bộ theo các remote ref đã tải trước đó. Không push. Không sửa dự án gốc.

## Đã kiểm chứng
- Central: `mvn -B -ntp -f tmc-system/pom.xml -pl central-backend -am verify -Pintegration` thành công; 106 unit test và 45 integration test, không bỏ qua test.
- Integration test dùng PostgreSQL/Testcontainers thật: migration, đăng nhập, đọc nhân sự, cấp QR, từ chối request thiếu xác thực và chặn endpoint thiết bị chưa được cấu hình.
- Edge: lần kiểm tra trước thành công 227 test (xem `edge-test.log` ở gốc clone).
- Frontend: `npm test` và `npm run build` thành công sau khi sửa session dùng chung và nối client báo cáo; 28 test.
- Đã sửa xung đột SecurityFilterChain: reporting chạy trước preview; fallback không bật trong profile integration-preview.

## Giới hạn cần giữ rõ
- Profile `integration-preview` là cấu hình thử riêng, không phải production mặc định.
- Tài khoản nhân sự lưu PostgreSQL; attendance, QR credentials/revocation dùng bộ nhớ trong profile thử. Restart làm mất các dữ liệu bộ nhớ.
- Khóa ký QR là fixture riêng cho preview.
- Trang quản trị Task 5 dùng dữ liệu mẫu trong trình duyệt, chưa nối API; đã gắn thông báo trên trang.
- Edge–Central mTLS chưa nối end-to-end. Các endpoint thiết bị bị chặn trong preview; không dùng header giả để vượt xác thực.
- Action-grant signer chưa tích hợp: trả 503, không phát token giả.
- Một số nguồn báo cáo/payroll chưa có adapter. Test từng module thành công không chứng minh mọi luồng nghiệp vụ đã hoàn chỉnh.
- Graphify tại dự án gốc phản ánh code cũ, chưa cập nhật cho clone tích hợp này.

## Chạy bản thử
Tại thư mục `tmc-system`, Docker Desktop phải đang chạy:

```powershell
docker compose --env-file .env.preview -f compose.yml -f compose.preview.yml -p tmc-integration-preview up -d --build
```

Giao diện: http://localhost:5173/#/login
Tài khoản: `preview.admin`. Mật khẩu ở biến `TMC_PREVIEW_PASSWORD` trong `.env.preview` (file local bị gitignore; không gửi lên Git).

```powershell
docker compose --env-file .env.preview -f compose.yml -f compose.preview.yml -p tmc-integration-preview down
```

Lệnh dừng không xóa volume dữ liệu.

## Kết quả chạy thực tế
Compose đã khởi động thành công ngày 25/11/2026. Central và PostgreSQL healthy; frontend phục vụ trên loopback cổng 5173.
`python tests/smoke-preview.py` PASS cả 8 kiểm tra: HTML 200, thiếu phiên 401, login 200, employees 200 và có dữ liệu, QR 200 với JWS 3 phần, endpoint thiết bị 403, logout 204, token đã logout 401.
Chưa kiểm tra trực quan tương tác trình duyệt. Docker build có bước package bỏ test để đóng gói; kết quả test ở trên đến từ lệnh Maven verify riêng, không từ Docker build.

## Bước tiếp theo
Nối API quản trị Task 5 và các nguồn báo cáo còn thiếu; thiết lập danh tính thiết bị/mTLS, signer action grant và luồng scan → SQLite → Central. Cần thay các repository bộ nhớ bằng adapter nghiệp vụ đã thống nhất trước khi coi là bản chạy hoàn chỉnh. Không bật các endpoint thiết bị bằng header giả để demo.

## Nhóm file thay đổi chính
- `PlatformConfiguration.java`, `Preview*Configuration.java`: tách bảo mật fallback, wiring các module trong profile thử, adapter báo cáo.
- `usermgmt/internal/PreviewSeed.java`, `db/migration/V001__usermgmt.sql`: seed tài khoản thử và schema tương ứng entity Task 1.
- `qrsecurity/internal/PreviewQr*`: endpoint QR dùng phiên Task 1, fixture key chỉ bật trong preview.
- `attendanceprocessing/internal/controller/AttendanceController.java`: lấy actor từ SecurityContext, bỏ actor HR giả.
- `ActorContext.java`, `AttendancePayload.java`, các lớp SQLite: tương thích UUID giữa các task mà giữ validation.
- `frontend/src/App.jsx`, `LoginPage.jsx`, `EmployeePage.jsx`, `QrPage.jsx`: dùng chung session, điều hướng và nối API; AdministrationPage ghi rõ dữ liệu mẫu.
- `PostgresPlatformIT.java`, test SQLite/controller liên quan: xác minh wiring, xác thực và contract sau tích hợp.
- `tests/smoke-preview.py`: kiểm tra HTTP qua frontend proxy, không in secret/token. Chạy `python tests/smoke-preview.py` tại `tmc-system` khi Compose sẵn sàng.

## Cập nhật giao diện và sửa lỗi — 25/11/2026
- Shell xanh đậm, trang tổng quan, thẻ chức năng và form đăng nhập mới; menu ngang trên màn hình nhỏ, focus bàn phím rõ.
- Login có label liên kết input, trạng thái lỗi đọc được, trim username; thông báo đúng thời hạn phiên cố định (không phải idle timeout).
- Navigation từ chối session sai expiry/actor thay vì crash hoặc cho qua; thêm regression test.
- Error boundary riêng từng trang, chuyển trang có thể phục hồi mà không phải reload mất phiên.
- EmployeePage dùng shared HTTP client (timeout, request ID, xử lý 401), hủy list request cũ khi lọc/unmount, giữ idempotency key khi retry payload tạo nhân viên không đổi, department trống gửi null.
- Quyền nút quản lý nhân sự dựa trên permission và chặn Auditor ở giao diện; backend vẫn là nơi quyết định quyền.
- AttendancePage cũng là mô phỏng local (đính chính mô tả trước); đã thêm cảnh báo ngay trên trang và tổng quan. Không giả vờ đã nối backend.
- `npm test`: 29 pass, 0 fail; `npm run build`: thành công.
- Công cụ browser báo host unavailable, chưa thể xác minh trực quan desktop/mobile. Không coi build là bằng chứng UI không còn lỗi.
- Các phần nghiệp vụ/device/action grant chưa hoàn thiện vẫn giữ nguyên giới hạn ở trên; chưa thể tuyên bố đã sửa mọi lỗi toàn hệ thống.

## Đồng bộ giao diện tất cả trang — 25/11/2026
- Thêm `frontend/src/styles/module-theme.css`, scope qua `.workspace-page` và class từng route để đồng bộ các module.
- Nhân sự: bảng cuộn ngang, bộ lọc và header responsive, màu nút/badge đồng nhất.
- QR: thẻ cá nhân và khung QR nền trắng, giữ quiet zone và tương phản để quét.
- Kiosk/bảo vệ: tiêu đề, form, trạng thái kết quả; giữ màu đỏ riêng cho thao tác khẩn cấp.
- Báo cáo: tab, bộ lọc, bảng, phân trang và tệp xuất theo cùng theme.
- Chấm công/quản trị: palette, bảng và tab đồng bộ, vẫn hiển thị nhãn mô phỏng. Loại mã FR khỏi nhãn tab quản trị.
- Login/tổng quan giữ thiết kế đã cập nhật; không thay logic nghiệp vụ trong lần chỉnh giao diện này.
- 29 frontend test pass; Vite build thành công; image frontend đã build và container đã thay mới. Kiểm tra HTTP smoke chạy lại đạt 8/8.
- Chưa kiểm tra trực quan bằng browser do host của công cụ không khả dụng ở phiên này.
