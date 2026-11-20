package vn.tmc.central.qrsecurity.internal.controller;

import vn.tmc.central.qrsecurity.api.QrCredentialView;
import vn.tmc.central.qrsecurity.api.QrIssuer;
import vn.tmc.central.qrsecurity.internal.service.QrCredentialService;
import vn.tmc.central.usermgmt.api.ActorContext;
import vn.tmc.contracts.QrKeySet;
import vn.tmc.contracts.QrTokenResponse;

/**
 * KHUNG TRIỂN KHAI — phần Java đã hiện thực, HTTP mapping chờ bean wiring của
 * Task 8 (Spring annotations/route sẽ thêm khi wiring chính thức).
 * PHỤ TRÁCH: Task 6.
 * VAI TRÒ: controller module qr-security.
 * NGUỒN: CONTRACT-V1.md §4 bảng API Task 6; docs/tasks/04-api-contracts.md.
 *
 * Mapping hợp đồng (base /api/v1, bearer bắt buộc trừ dòng ghi rõ mTLS):
 *
 * | Method | Path                                   | Request      | Response                      |
 * |--------|----------------------------------------|--------------|-------------------------------|
 * | GET    | /me/qr-token                           | —            | 200 QrTokenResponse           |
 * | POST   | /employees/{id}/qr-credential          | {reason}     | 201 QrCredentialView; 409 nếu đã ACTIVE |
 * | POST   | /employees/{id}/qr-credential/revoke   | {reason}     | 200 QrCredentialView REVOKED  |
 * | POST   | /employees/{id}/qr-credential/reissue  | {reason}     | 201 QrCredentialView mới      |
 * | GET    | /qr/keys/refresh                       | mTLS Edge    | 200 QrKeySet (§8)             |
 *
 * Yêu cầu bắt buộc ở tầng HTTP khi wiring (Task 8):
 * - /me/qr-token: Cache-Control: no-store; không log token; employeeId lấy từ
 *   ActorContext của phiên (đã kiểm tra trong service), không đọc từ query/body.
 * - Ba endpoint credential: bearer; permission credential.manage và scope đã
 *   kiểm ở service; id trong URL không được đổi qua body (§4).
 * - /qr/keys/refresh: xác thực mTLS certificate Edge + binding edgeId/factoryId
 *   rồi mới gọi refreshKeys; không nhận URL khóa từ token (§4).
 * - Lỗi QrSecurityException → ApiError {code, message, requestId, retryable}
 *   với HTTP status đúng trong exception (§2); không trả stack trace.
 * PHỐI HỢP: Task 1 (ActorContext), Task 7 (Edge gọi refresh), Task 8 (wiring).
 */
public final class QrController {

    /** Header bắt buộc trên GET /me/qr-token khi wiring HTTP (§4). */
    public static final String CACHE_CONTROL_NO_STORE = "no-store";

    private final QrIssuer qrIssuer;
    private final QrCredentialService operations;

    public QrController(QrIssuer qrIssuer, QrCredentialService operations) {
        this.qrIssuer = java.util.Objects.requireNonNull(qrIssuer, "qrIssuer");
        this.operations = java.util.Objects.requireNonNull(operations, "operations");
    }

    /** GET /me/qr-token: chỉ QR của chính người gọi, từ ActorContext tin cậy. */
    public QrTokenResponse getMyQrToken(ActorContext actor) {
        return qrIssuer.issue(actor);
    }

    /** POST /employees/{id}/qr-credential → 201 (409 khi đã ACTIVE). */
    public QrCredentialView grantCredential(ActorContext actor, String employeeId, String reason) {
        return operations.grantCredential(actor, employeeId, reason);
    }

    /** POST /employees/{id}/qr-credential/revoke → 200 REVOKED. */
    public QrCredentialView revokeCredential(ActorContext actor, String employeeId, String reason) {
        return operations.revokeCredential(actor, employeeId, reason);
    }

    /** POST /employees/{id}/qr-credential/reissue → 201 credential mới. */
    public QrCredentialView reissueCredential(ActorContext actor, String employeeId, String reason) {
        return operations.reissueCredential(actor, employeeId, reason);
    }

    /** GET /qr/keys/refresh: chỉ gọi sau khi mTLS binding edgeId/factoryId hợp lệ. */
    public QrKeySet refreshKeys(String edgeId, String factoryId) {
        return operations.refreshKeys(edgeId, factoryId);
    }
}
