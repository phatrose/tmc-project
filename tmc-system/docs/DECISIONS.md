# Phương án triển khai thống nhất

Nguồn ưu tiên khi khác PDF: [Final](https://docs.google.com/document/d/1m7kE9Fa-oxiN0MfalyYsmn8aXWNKC2DB9pknqK-blbY/edit), đối chiếu ngày 16/11/2026. Người dùng yêu cầu chỉnh sườn theo phương án này. PDF gốc giữ nguyên để truy xuất; các mô tả cũ không ghi đè các quyết định dưới đây. Đây là yêu cầu cho implementation, chưa phải kết quả kiểm thử.

## 1. Chốt kỳ công — Task 3/4

- Ca đêm bắt đầu cuối tháng trước, kết thúc 06:00 ngày 01, vẫn thuộc tháng trước.
- Tổng hợp và chốt chính thức bắt đầu 06:30 ngày 01; đối soát/xuất HR hoàn tất trước 12:00.
- 23:00 cuối tháng chỉ được dùng cho tổng hợp tạm, không phải thời điểm khóa kỳ.
- Scheduler dùng timezone nhà máy cấu hình bởi Task 5; event vẫn truyền/lưu UTC. Không suy ra tháng công chỉ từ ngày kết thúc ca.
- Điểm bắt đầu: AttendanceProcessingService, PayrollExportService, PeriodClosingSchedule. Task 4 cần phối hợp Task 3 về dữ liệu đến muộn/mở lại kỳ.

## 2. Bảy module và ranh giới

| Module | Nơi chạy | Package | Trách nhiệm |
|---|---|---|---|
| user-mgmt | Central | `vn.tmc.central.usermgmt` | Nhân viên, người dùng, đăng nhập, vai trò và quyền |
| qr-security | Central | `vn.tmc.central.qrsecurity` | Phát hành/ký QR, credential, thu hồi và khóa |
| attendance-processing | Central | `vn.tmc.central.attendanceprocessing` | Tiếp nhận, lưu và tổng hợp sự kiện công |
| reporting-audit | Central | `vn.tmc.central.reportingaudit` | Báo cáo, audit, chốt/xuất Payroll |
| system-admin | Central | `vn.tmc.central.systemadmin` | Nhà máy, quản lý cổng/thiết bị, ca làm việc và cấu hình |
| gate-station | Edge | `vn.tmc.edge.gatestation` | Đọc/xác minh QR, quyền/replay và điều khiển turnstile |
| edge-sync | Edge | `vn.tmc.edge.edgesync` | SQLite, cache, lưu offline, bootstrap và đồng bộ |

## 3. Giao tiếp Central–Edge — Task 2/3/7/8

- Central không đọc SQLite; Edge không kết nối PostgreSQL. Hai tiến trình trao đổi qua REST API với xác thực/binding.
- Scan chỉ phụ thuộc cache/khóa và store cục bộ; không chờ phản hồi Central để mở cổng.
- Quy ước triển khai của sườn để đáp ứng nguyên tắc trên: unknown kid từ chối lượt hiện tại; refresh khóa chạy nền qua mTLS. Không tải khóa từ URL trong token.
- Gate-station gọi LocalEventStorePort do edge-sync cung cấp: reserve jti, sequence, event và delivery trong cùng transaction trước mở. Không thay transaction này bằng hai thao tác ghi riêng.
- Task 6 có thể phụ trách issuer ở Central và verifier trong gate-station tại Edge; cùng người làm không đồng nghĩa cùng runtime/module.

## 4. Header/claims JWS — Task 6

- Protected header: alg=ES256, kid. Payload: sub, iss, aud, iat, nbf, exp, jti; NumericDate theo giây UTC.
- aud xác định dịch vụ xác minh, không chứa mã nhà máy. Sườn chọn phương án đơn giản được tài liệu đề xuất: không thêm factoryId, kiểm tra nhà máy/khu vực/ca qua access-policy cache tại Edge.
- TTL mặc định 60 giây; mỗi token một jti mới; một jti chỉ dùng một lần trong replay cache có thẩm quyền tại Edge.
- Không log toàn bộ JWS; Central giữ private key, Edge chỉ giữ public key.
- Replay giữa hai Edge đang mất kết nối vẫn là giới hạn kỹ thuật: không tuyên bố chống replay toàn cục tuyệt đối khi offline. Cần đối soát và cảnh báo khi nối lại.
- QrClaims/JwsHeader/VerifiedQr là khung kiểu dữ liệu; chưa kiểm tra chữ ký hoặc tự chứng minh token hợp lệ.

## 5. Kênh phân phối QR — Task 1/6

- Nhân viên đăng nhập app/web; Central cấp QR động sau xác thực, hiển thị trực tiếp và cấp lại khi cần/hết hạn.
- Email/SMS tuyệt đối không chứa QR hoặc JWS credential. Chỉ dùng thông báo kích hoạt/thu hồi, liên kết đăng nhập, cảnh báo hoặc hướng dẫn khôi phục.
- FR-UQ-03 được hiểu theo quyết định này thay cho mô tả email chứa QR trong PDF.

## 6. Delivery và ACK — Task 3/7

EventEnvelope/AttendancePayload là dữ liệu bất biến; không chứa trạng thái giao vận. EventDelivery được lưu ở event_delivery.

| Trạng thái hiện tại | Kết quả | Trạng thái tiếp theo |
|---|---|---|
| PENDING | ACCEPTED hoặc DUPLICATE sau commit | ACKNOWLEDGED |
| PENDING | CONFLICT hoặc REJECTED | FAILED, giữ dữ liệu để xử lý thủ công |
| PENDING | Timeout hoặc mất ACK | PENDING, retry cùng ID/payload/batch |

Chỉ có ba delivery state trên. Không dùng SYNCED/PENDING_SYNC. Trạng thái batch READY/IN_FLIGHT/RETRY_WAIT/COMPLETED là vòng đời của lô, không phải delivery state của event. HTTP 200 không có nghĩa toàn lô được chấp nhận.

## 7. Revocation online/offline — Task 6/7, đối soát Task 3

- Online: mục tiêu cập nhật revocation ≤60 giây; xác thực delta/snapshot rồi lưu bền vững trước áp dụng revision.
- Offline: thiết kế để hoạt động tối thiểu 24 giờ bằng cache gần nhất; có thể cho qua người mới bị thu hồi tại Central, đây là cửa sổ rủi ro được chấp nhận.
- Chuẩn bị trước public key/trust window, policy, dung lượng và đồng hồ để đáp ứng 24 giờ; cần kiểm thử mất WAN ≥24 giờ. Không tự kéo dài trust khi khóa/cache đã mất tính hợp lệ. Trường hợp key hết hạn trong cửa sổ này phải được giải quyết bằng provisioning/config trước bàn giao.
- Khi phục hồi: gửi các event đã lưu, cập nhật revocation, đối soát theo thời điểm; phát POST_REVOKE_VIOLATION cho HR/an ninh, không xóa/sửa event gốc.

## 8. Auditor — Task 1, dữ liệu tra cứu Task 3/4

Auditor là actor chính thức. Được xem/tìm audit theo actor/thời gian/hành động, xem manual override, lịch sử điều chỉnh và chốt/mở lại kỳ. Xuất báo cáo audit chỉ khi được cấp quyền.

Cấm sửa AttendanceEvent, phê duyệt điều chỉnh công, mở cổng, cấp/thu hồi QR, đổi tài khoản/quyền/cấu hình và xóa Audit Log. Phải kiểm tra tại backend và theo data scope; ẩn nút frontend không đủ. Role/AuditorPermission mới là contract, chưa phải bộ kiểm soát quyền hoạt động.
