package vn.tmc.central.qrsecurity.api;

import vn.tmc.central.usermgmt.api.ActorContext;
import vn.tmc.contracts.QrTokenResponse;

/**
 * PHỤ TRÁCH: Task 6.
 * Port phát hành Dynamic QR theo CONTRACT-V1.md §10:
 * "QrIssuer.issue | ActorContext → QrTokenResponse | 6; employeeId từ context".
 * employeeId lấy từ ActorContext tin cậy do phiên đăng nhập (Task 1) tạo;
 * KHÔNG nhận employeeId tùy ý từ client (GET /me/qr-token không nhận
 * employeeId, §4). Nhân viên phải có credential ACTIVE (FR-UQ-02).
 *
 * Lỗi ném ra là QrSecurityException với code hợp đồng ApiError (§2):
 * UNAUTHENTICATED, VALIDATION_ERROR, NOT_FOUND, INVALID_STATE, INTERNAL_ERROR.
 * PHỐI HỢP: Task 1 (ActorContext/EmployeeManagement), Task 8 (HTTP mapping).
 */
public interface QrIssuer {
    QrTokenResponse issue(ActorContext actor);
}
