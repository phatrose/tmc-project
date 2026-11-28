# Nguyên tắc làm việc: Module ownership + Contract-first

Nhóm thống nhất giữ cách chia 8 Task theo module/phạm vi nghiệp vụ hiện tại trong [TEAM.md](TEAM.md), kết hợp chốt contract trước khi triển khai. Không chia lại nhóm thành FE riêng và BE riêng, cũng không dùng contract để thay thế trách nhiệm nghiệp vụ.

Baseline giao tiếp: [CONTRACT-V1.md](CONTRACT-V1.md), kèm [fixture request/response](CONTRACT-EXAMPLES.md). Các chữ ký stub cần sửa theo bảng chuyển đổi v1; xác nhận review của thành viên được ghi riêng.

## 1. Trách nhiệm theo module

- Mỗi Task có một người chủ trì, chịu trách nhiệm luồng nghiệp vụ thuộc phần mình: dữ liệu, xử lý, API, giao diện liên quan và kiểm thử.
- Ví dụ Task 1 làm quản lý nhân sự từ API, nghiệp vụ đến giao diện quản lý nhân viên; có thể tự kiểm tra toàn bộ luồng.
- Edge Sync và các phần tích hợp không bắt buộc có cả FE lẫn BE; phạm vi được xác định theo chức năng thực tế.
- Có thể hỗ trợ chéo theo thế mạnh FE/BE. Người hỗ trợ thống nhất trước với chủ Task; trách nhiệm bàn giao vẫn thuộc chủ Task.
- Mỗi file có một Task chủ trì. Không tự sửa contract hoặc implementation của Task khác khi chưa thống nhất.

## 2. Chốt contract trước khi code phụ thuộc

Contract là thỏa thuận giao tiếp giữa các phần, không thay thế logic nghiệp vụ. Mỗi người chuẩn bị API, DTO/interface và danh sách phụ thuộc của Task mình. Những Task cung cấp và sử dụng cùng một contract phải cùng rà soát, thống nhất trước khi triển khai phần phụ thuộc vào contract đó.

Mỗi contract cần ghi đủ phần liên quan:

| Nội dung | Cần thống nhất |
|---|---|
| Phạm vi và chủ trì | Chức năng, Task cung cấp, Task sử dụng, Task quản lý file contract |
| Điểm giao tiếp | Method/path của API hoặc chữ ký interface; phiên bản nếu có |
| Đầu vào/đầu ra | Trường, kiểu dữ liệu, bắt buộc/null, định dạng ID/thời gian, ví dụ request/response |
| Quy tắc xử lý | Validation, tiền điều kiện, hậu điều kiện, trạng thái và ý nghĩa kết quả |
| Bảo mật | Xác thực, quyền, data scope và thông tin không được ghi log |
| Lỗi và retry | Mã lỗi, timeout, retry/idempotency, ACK và transaction nếu áp dụng |
| Kiểm chứng | Trường hợp đúng/sai, fixture chung và kiểm thử tích hợp/contract |

Không cần điền mục không áp dụng. Contract phải bám [DECISIONS.md](DECISIONS.md), không tự thay đổi các phương án nghiệp vụ đã chốt.

## 3. Quy trình phối hợp

1. **Chuẩn bị:** chủ Task liệt kê chức năng, API/DTO/interface và phụ thuộc; ghi các điểm chưa rõ.
2. **Cùng chốt:** các Task liên quan rà request/response, lỗi và semantics. Ghi kết quả thống nhất trong tài liệu contract hoặc PR có liên kết, không chỉ trao đổi miệng.
3. **Xác nhận:** ghi phiên bản/ngày, Task chủ trì và các bên đã xác nhận. Contract còn bất đồng giữ trạng thái Draft; chỉ phần đã thống nhất mới được dùng làm chuẩn tích hợp.
4. **Làm song song:** FE, BE và các module có thể dùng mock/stub theo cùng contract. Đánh dấu rõ dữ liệu mô phỏng; mock chạy được không chứng minh backend hoặc nghiệp vụ đã hoàn thành.
5. **Tích hợp:** đối chiếu kết quả thật với contract, chạy kiểm thử liên quan và xử lý sai lệch trước khi merge.

Contract dùng chung phải được bên cung cấp và bên sử dụng cùng chốt. API/interface chỉ dùng nội bộ một module do chủ module thiết kế theo quy chuẩn chung, không cần chờ cả nhóm họp.

## 4. Thay đổi contract sau khi chốt

- Người đề xuất nêu lý do, khác biệt trước/sau, Task và file bị ảnh hưởng, mức độ tương thích.
- Thống nhất với các bên sử dụng trước khi merge thay đổi phá vỡ tương thích; kèm kế hoạch cập nhật đồng bộ hoặc phiên bản/chuyển tiếp khi cần.
- Cập nhật cùng lúc contract, ví dụ, mock/fixture, implementation và test thuộc phạm vi thay đổi. Ghi rõ phần còn chờ Task khác.
- Chủ file contract tổng hợp thay đổi; các Task góp ý qua review, tránh cùng sửa một file không có điều phối.
- Task 8 điều phối tích hợp và quy chuẩn chung, không làm thay hoặc tự quyết contract nghiệp vụ của mọi Task.

## 5. Các nhóm cần phối hợp trước

| Chủ đề | Task liên quan |
|---|---|
| Đăng nhập, phiên, role/scope và HTTP client | 1, 8; các Task sử dụng quyền xác nhận phần liên quan |
| Nhà máy/cổng/ca và quyền khu vực | 1, 2, 5 |
| Xác minh QR, kiểm tra policy, reserve replay, lưu event trước mở cổng | 1, 2, 6, 7 |
| Event envelope, batch, ACK, retry và idempotency | 3, 7; Task 2 xác nhận dữ kiện phát sinh |
| Dữ liệu tính công, chốt kỳ, báo cáo và Payroll | 3, 4, 5 |
| Audit, truy vết và quyền Auditor | 1, 4; các Task phát sinh audit |

## 6. Điều kiện bàn giao và review

- Công việc đúng phạm vi Task, không chồng chéo người chủ trì.
- Có liên kết contract đã thống nhất hoặc giải thích vì sao thay đổi không ảnh hưởng contract.
- Có kiểm thử phù hợp và kết quả thực tế; ghi rõ mock, TODO, lỗi và phần chưa chạy.
- Thay đổi contract được các Task cung cấp/sử dụng liên quan xác nhận; review quyền merge trên GitHub là bước riêng.
- Mỗi đầu việc dùng nhánh riêng và Pull Request theo [CONTRIBUTING.md](../CONTRIBUTING.md). Mẫu PR yêu cầu nêu ảnh hưởng contract để việc review có đủ thông tin.
