// PHỤ TRÁCH: Task 2. Logic thuần của KioskPage/GuardConsolePage (không React, không mạng) để kiểm thử bằng node:test.
// Nguyên tắc từ CONTRACT-V1.md §5: ALLOW KHÔNG có nghĩa là người đã qua cổng, DENY là kết quả nghiệp vụ (HTTP 200)
// chứ không phải lỗi, và lỗi HTTP phải tách khỏi hai loại trên.

export const EDGE_BASE_URL = import.meta.env?.VITE_EDGE_BASE_URL || '/edge/v1';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
export const isUuid = value => typeof value === 'string' && UUID.test(value);

/** Mỗi lượt quét/override có scanId UUIDv4 riêng; retry sau lỗi mạng PHẢI dùng lại đúng scanId cũ. */
export const newScanId = () => crypto.randomUUID();

export const DENY_REASONS = {
  INVALID_TOKEN: 'Mã QR không hợp lệ.',
  TOKEN_EXPIRED: 'Mã QR đã hết hạn. Hãy làm mới mã trên điện thoại.',
  UNKNOWN_KID: 'Mã QR chưa được nhận diện. Hãy làm mới mã trên điện thoại.',
  REVOKED: 'Mã QR đã bị thu hồi.',
  REPLAY: 'Mã QR này đã được sử dụng.',
  OUTSIDE_POLICY: 'Không có quyền qua cổng này vào lúc này.',
  DUPLICATE_SCAN: 'Vừa quét xong. Vui lòng chờ vài giây.',
  GATE_DISABLED: 'Cổng đang tạm đóng.',
  CACHE_UNAVAILABLE: 'Thiết bị chưa có dữ liệu quyền hợp lệ. Hãy báo bảo vệ.',
  CLOCK_UNTRUSTED: 'Đồng hồ thiết bị chưa đáng tin. Hãy báo bảo vệ.',
};

const COMMAND_OUTCOMES = {
  UNLOCK_ACCEPTED: { tone: 'success', title: 'Đã gửi lệnh mở cổng', detail: 'Thiết bị đã nhận lệnh. Vui lòng đi qua cổng.' },
  FAILED: { tone: 'warning', title: 'Hợp lệ nhưng cổng không nhận lệnh',
    detail: 'Lượt quét đã được ghi nhận. Hãy báo bảo vệ để mở cổng.' },
  TIMEOUT: { tone: 'warning', title: 'Hợp lệ nhưng chưa rõ cổng đã mở chưa',
    detail: 'Lượt quét đã được ghi nhận. Đừng quét lại; nhờ bảo vệ xác nhận.' },
  NOT_SENT: { tone: 'warning', title: 'Đã ghi nhận, chưa gửi lệnh mở cổng', detail: 'Hãy báo bảo vệ.' },
};

/** Diễn giải ScanResult (HTTP 200) cho người dùng cuối. Trả { tone, title, detail, offline }. */
export function describeScanResult(result) {
  const offline = result?.mode === 'OFFLINE';
  if (!result || typeof result.decision !== 'string') {
    return { tone: 'danger', title: 'Phản hồi không hợp lệ', detail: 'Hãy thử lại hoặc báo bảo vệ.', offline: false };
  }
  if (result.decision === 'DENY') {
    return { tone: 'danger', title: 'Không cho qua',
      detail: DENY_REASONS[result.reasonCode] || `Bị từ chối (mã lý do: ${result.reasonCode}).`, offline };
  }
  const outcome = COMMAND_OUTCOMES[result.commandStatus]
    || { tone: 'warning', title: 'Trạng thái lệnh không xác định', detail: 'Hãy báo bảo vệ.' };
  const title = result.decision === 'EMERGENCY' ? `Mở khẩn cấp: ${outcome.title.toLowerCase()}` : outcome.title;
  return { ...outcome, title, offline };
}

/** Diễn giải lỗi HTTP (HttpError của Task 8). Lỗi mạng/timeout nghĩa là KẾT QUẢ CHƯA RÕ nên chỉ retry cùng scanId. */
export function describeError(error) {
  const requestId = error?.requestId ? ` (mã yêu cầu: ${error.requestId})` : '';
  switch (error?.code) {
    case 'VALIDATION_ERROR':
    case 'MALFORMED_JSON':
      return { title: 'Yêu cầu không hợp lệ', detail: `Kiểm tra lại dữ liệu nhập.${requestId}`, retryable: false };
    case 'DEVICE_BINDING_MISMATCH':
    case 'FORBIDDEN':
    case 'SCOPE_MISMATCH':
      return { title: 'Thiết bị chưa được cấp quyền cho cổng này', detail: `Liên hệ quản trị hệ thống.${requestId}`, retryable: false };
    case 'IDEMPOTENCY_CONFLICT':
      return { title: 'Lượt quét này đã được xử lý với nội dung khác', detail: `Hãy quét lại từ đầu.${requestId}`, retryable: false };
    case 'STORAGE_UNAVAILABLE':
    case 'DEPENDENCY_UNAVAILABLE':
      return { title: 'Hệ thống tại cổng tạm thời không sẵn sàng', detail: `Chưa mở cổng. Thử lại hoặc báo bảo vệ.${requestId}`, retryable: true };
    case 'TIMEOUT':
    case 'NETWORK_ERROR':
      return { title: 'Không kết nối được thiết bị tại cổng', detail: 'Kết quả chưa rõ. Thử lại cùng lượt quét, đừng tạo lượt mới.', retryable: true };
    case 'UNAUTHENTICATED':
    case 'SESSION_EXPIRED':
      return { title: 'Phiên đăng nhập đã hết hạn', detail: 'Hãy đăng nhập lại.', retryable: false };
    default:
      return { title: 'Có lỗi xảy ra', detail: `Hãy thử lại sau.${requestId}`, retryable: Boolean(error?.retryable) };
  }
}

const codePoints = text => Array.from(text ?? '').length;

/** Kiểm tra form override ở client (backend vẫn kiểm lại). Trả object lỗi theo field; rỗng nghĩa là hợp lệ. */
export function validateOverrideForm({ mode, gateId, edgeId, employeeId, reason, password, confirmed }) {
  const errors = {};
  if (mode !== 'MANUAL' && mode !== 'EMERGENCY') errors.mode = 'Chọn mở thủ công hoặc khẩn cấp.';
  if (!isUuid(gateId)) errors.gateId = 'Mã cổng phải là UUID.';
  if (!isUuid(edgeId)) errors.edgeId = 'Mã Edge phải là UUID.';
  if (mode === 'MANUAL' && !isUuid(employeeId)) errors.employeeId = 'Mã nhân viên phải là UUID.';
  const length = codePoints(reason);
  if (length < 10 || length > 500) errors.reason = 'Lý do phải từ 10 đến 500 ký tự.';
  if (!password) errors.password = 'Nhập lại mật khẩu để xác nhận.';
  else if (password.length > 128) errors.password = 'Mật khẩu quá dài.';
  if (mode === 'EMERGENCY' && !confirmed) errors.confirmed = 'Cần xác nhận mở khẩn cấp.';
  return errors;
}

/** Thân yêu cầu xin action grant của Central (CONTRACT-V1.md §5). EMERGENCY có employeeId = null. */
export function grantRequestBody({ mode, edgeId, gateId, employeeId, reason, password }) {
  return { password, action: mode, edgeId, gateId, employeeId: mode === 'MANUAL' ? employeeId : null, reason };
}

/** Thân yêu cầu POST /edge/v1/overrides. */
export function overrideRequestBody({ scanId, mode, gateId, employeeId, reason, grant }) {
  return { scanId, gateId, employeeId: mode === 'MANUAL' ? employeeId : null, reason, mode, grant };
}

/** Tóm tắt hiện diện: chỉ người có lượt gần nhất là IN mới tính là đang ở trong. */
export function summarizePresence(presence) {
  const items = Array.isArray(presence?.items) ? presence.items : [];
  const inside = items.filter(item => item.lastDirection === 'IN');
  return { total: items.length, inside: inside.length, items: inside };
}
