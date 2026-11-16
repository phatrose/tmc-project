package vn.tmc.central.attendanceprocessing.internal.controller;

import vn.tmc.central.attendanceprocessing.api.AttendanceSync;
import vn.tmc.contracts.SyncBatchRequest;
import vn.tmc.contracts.SyncBatchAck;

/**
 * KHUNG TRIỂN KHAI — có khai báo, chưa hiện thực nghiệp vụ.
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: controller.
 * NGUỒN: main.pdf, §4.2–4.3; §5.2.
 * TÀI LIỆU PDF: docs/tasks/04-api-contracts.md; docs/REQUIREMENTS.md.
 * TODO[Task 3]: POST /api/v1/attendance/sync: mTLS, binding edge/factory, tối đa 50 event.
 * TODO[Task 3]: ACK từng event sau commit: ACCEPTED/DUPLICATE/CONFLICT/REJECTED.
 * PHỐI HỢP: Task 2, Task 4, Task 7.
 * Kiểu dữ liệu là khung đề xuất; chốt contract trước khi tích hợp runtime.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận batch JSON từ thiết bị đã xác thực mTLS; kiểm tra edge/factory binding và tối đa 50 event.
 * - Cần làm: Gọi AttendanceSync, trả đúng ACK sau xử lý; controller không tự mở transaction cho cả batch.
 * - Lưu ý bàn giao: Phân biệt lỗi request HTTP với lỗi từng event; chưa có HTTP mapping/bean wiring trong bản sườn.
 */
public final class AttendanceSyncController {
    private final AttendanceSync service;
    public AttendanceSyncController(AttendanceSync service) { this.service = java.util.Objects.requireNonNull(service); }
    // TODO[Task 3]: POST /api/v1/attendance/sync; mTLS and edge/factory binding first.
    public SyncBatchAck synchronize(SyncBatchRequest batch) { return service.synchronize(batch); }
}
