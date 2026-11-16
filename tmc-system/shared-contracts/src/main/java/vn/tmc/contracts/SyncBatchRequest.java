package vn.tmc.contracts;

import java.util.UUID;
import java.util.List;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 7.
 * VAI TRÒ: DTO request sync.
 * NGUỒN: main.pdf, §4.2–4.3.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 * TODO[Task 7]: syncBatchId và events tối đa 50; retry giữ ID/membership.
 * TODO[Task 7]: Chốt validation envelope với mục PDF 5, không dùng schema demo cũ.
 * PHỐI HỢP: Task 2, Task 3, Task 6, Task 8.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: syncBatchId định danh lô; events chứa các envelope nguyên bản, tối đa MAX_EVENTS=50.
 * - Cần làm: Bổ sung kiểm tra null/rỗng/trùng ID và scope; batch ID/membership phải được lưu trước lần gửi đầu.
 * - Lưu ý bàn giao: Không tái tạo ID hoặc hash khi retry; record hiện chưa tự validation hoặc sao chép bất biến danh sách.
 */
public record SyncBatchRequest(UUID syncBatchId, List<EventEnvelope> events) {
    public static final int MAX_EVENTS = 50;
    // TODO[Task 7]: validate size, required fields and stable retry membership.
}
