package vn.tmc.edge.gatestation.infrastructure;

import vn.tmc.edge.gatestation.domain.TurnstileControlPort;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 2.
 * VAI TRÒ: Adapter / Controller.
 * NGUỒN: main.pdf, §1.2.2, 3.3.2; UC-04.
 * TÀI LIỆU PDF: docs/tasks/06-edge-offline.md; docs/REQUIREMENTS.md.
 * TODO[Task 2]: Mock đúng TurnstileControlPort: command_id, accepted/error/timeout.
 * TODO[Task 2]: Không diễn giải nhận lệnh là người đã đi qua; không gọi thiết bị thật.
 * PHỐI HỢP: Task 1, Task 3, Task 6, Task 7.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận commandId qua TurnstileControlPort; dùng để mô phỏng thiết bị khi triển khai test.
 * - Cần làm: Bổ sung kịch bản nhận lệnh, timeout, lỗi và trùng command; không luôn trả thành công.
 * - Lưu ý bàn giao: Kết quả thiết bị phải được ghi thành EVT-03 riêng; mock hiện chưa thực hiện việc mở cổng.
 */
public final class MockTurnstileAdapter implements TurnstileControlPort {
    @Override
    public void unlock(String commandId) {
        throw new UnsupportedOperationException("TODO: unlock");
    }
}
