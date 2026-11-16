package vn.tmc.central.reportingaudit.internal.adapter;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 4.
 * VAI TRÒ: adapter.
 * NGUỒN: main.pdf, §3.3.2, 4.1; UC-05.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 * TODO[Task 4]: Tách HR mock sau port, idempotency theo export batch ID.
 * TODO[Task 4]: HTTP timeout/retry độc lập với đường mở cổng; fallback CSV theo contract.
 * PHỐI HỢP: Task 3, Task 5, Task 8.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận exportBatchId đã tồn tại để gửi dữ liệu công tới HR/Payroll mock.
 * - Cần làm: Bổ sung request/response, checksum và số lượng bản ghi xác nhận; dùng batchId để chống gửi trùng.
 * - Lưu ý bàn giao: Phân biệt timeout, partial và đã nhận; không báo EXPORT_SUCCESS trước khi đối soát đủ điều kiện.
 */
public final class HrPayrollAdapter {
    public void send(String exportBatchId) {
        throw new UnsupportedOperationException("TODO: send");
    }
}
