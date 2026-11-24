package vn.tmc.edge.gatestation.domain;

import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 2.
 * Gửi lệnh mở cổng (CONTRACT-V1.md §5 "Turnstile mock"): unlock(commandId, gateId) → CommandResult.
 * - Chỉ được gọi SAU khi transaction lưu event/command intent đã commit.
 * - commandId ổn định cho một lệnh; cùng commandId không được mở lại (dedup ít nhất 48 giờ).
 * - Timeout 1 giây; kết quả chưa rõ trả TIMEOUT, không retry mù. UNLOCK_ACCEPTED không chứng minh người đã qua cổng.
 * Hợp đồng LSP: mọi adapter (thật, mock, decorator) giữ nguyên các ngữ nghĩa trên và không ném exception ra ngoài
 * cho lỗi thiết bị; lỗi phải được biểu diễn bằng status FAILED hoặc TIMEOUT.
 */
public interface TurnstileControlPort {
    CommandResult unlock(UUID commandId, UUID gateId);
}
