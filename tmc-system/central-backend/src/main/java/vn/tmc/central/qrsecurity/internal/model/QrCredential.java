package vn.tmc.central.qrsecurity.internal.model;

import java.time.Instant;
import java.util.Objects;

import vn.tmc.central.qrsecurity.api.QrCredentialView;

/**
 * PHỤ TRÁCH: Task 6.
 * Model credential nội bộ theo CONTRACT-V1.md §4:
 * {id, employeeId, state:"ACTIVE"|"REVOKED", createdAt, revokedAt:Instant?}.
 * Bất biến: ACTIVE thì revokedAt null; REVOKED thì revokedAt bắt buộc.
 * ID là UUIDv4 chữ thường; thời điểm wire là UTC ISO-8601 milliseconds.
 * HƯỚNG DẪN TRIỂN KHAI:
 * - Đầu vào/vai trò: Đơn vị dữ liệu của issuer; một người tối đa một ACTIVE
 *   (FR-UQ-02) do service và unique constraint ở adapter bảo đảm.
 * - Cần làm: Adapter JPA mapping bảng qr_credential; không trả model này ra
 *   ngoài module (dùng QrCredentialView).
 * - Lưu ý bàn giao: Không hard-delete credential đã sinh dữ kiện công;
 *   lifecycle chỉ ACTIVE → REVOKED (mục 12 contract: dùng active=false khi cần).
 */
public record QrCredential(String id, String employeeId, State state, Instant createdAt, Instant revokedAt) {

    public enum State { ACTIVE, REVOKED }

    public QrCredential {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        if (state == State.ACTIVE && revokedAt != null) {
            throw new IllegalArgumentException("Credential ACTIVE không có revokedAt");
        }
        if (state == State.REVOKED && revokedAt == null) {
            throw new IllegalArgumentException("Credential REVOKED cần revokedAt");
        }
    }

    public QrCredentialView view() {
        return new QrCredentialView(id, employeeId, state.name(), createdAt, revokedAt);
    }
}
