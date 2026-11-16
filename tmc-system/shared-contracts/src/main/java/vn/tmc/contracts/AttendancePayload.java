package vn.tmc.contracts;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: DTO EVT-01.
 * NGUỒN: main.pdf, §5.2.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 * TODO[Task 3]: employeeId, direction IN/OUT, source ONLINE/OFFLINE/MANUAL, jti/kid và commandId.
 * TODO[Task 3]: MANUAL có operatorId/reason và không jti; command result là EVT-03 riêng.
 * PHỐI HỢP: Task 2, Task 4, Task 7.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Payload sự kiện công chứa nhân viên, hướng, source và tham chiếu token/command.
 * - Cần làm: MANUAL cần operatorId/reason; chốt nullability jti/kid theo nguồn. Kết quả lệnh cổng là EVT-03 riêng.
 * - Lưu ý bàn giao: Giữ nguyên dữ kiện sau commit; không trộn ACK đồng bộ hoặc trạng thái thiết bị vào payload này.
 */
public record AttendancePayload(String employeeId, Direction direction, Source source, String jti, String kid, String commandId, String operatorId, String reason) {
    public enum Direction { IN, OUT }
    public enum Source { ONLINE, OFFLINE, MANUAL }
}
