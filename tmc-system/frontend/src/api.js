

/**
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: HTTP client chung nhận path/options; response/error contract cần chốt với backend.
 * - Cần làm: Bổ sung base URL, bearer/session, requestId, AbortSignal và ánh xạ ApiError; không log token/mật khẩu.
 * - Lưu ý bàn giao: Không tự retry request thay đổi dữ liệu nếu chưa có idempotency; hàm hiện chủ động báo chưa triển khai.
 */

// PHỤ TRÁCH: Task 8.
// TODO: base URL, bearer/session, requestId, AbortSignal và ánh xạ ApiError.
// Không gọi API hoặc lưu token cho tới khi chốt contract bảo mật.
export async function request(path, options = {}) {
  throw new Error('TODO: triển khai HTTP client theo docs/tasks/04-api-contracts.md');
}
