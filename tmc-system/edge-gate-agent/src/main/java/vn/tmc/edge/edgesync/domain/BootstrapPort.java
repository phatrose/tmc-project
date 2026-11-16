package vn.tmc.edge.edgesync.domain;

import vn.tmc.contracts.BootstrapResponse;

/** PHỤ TRÁCH: Task 7. TODO: Hoàn thiện contract và nghiệp vụ theo PDF.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Nhận afterRevision của cache đã commit; trả BootstrapResponse trong scope Edge/nhà máy.
 * - Cần làm: Chốt delta hay full snapshot, revision, chứng thực và cách phát hiện thiếu revision với bên Central.
 * - Lưu ý bàn giao: Không chứa private key; không áp dụng revision mới nếu phần dữ liệu đi kèm chưa được lưu bền vững.
 */
public interface BootstrapPort {
    BootstrapResponse fetch(long afterRevision);
}
