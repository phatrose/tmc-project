package vn.tmc.central.qrsecurity.api;

import java.time.Instant;
import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 6 (mới, theo CONTRACT-V1.md §4).
 * Credential = {id, employeeId, state:"ACTIVE"|"REVOKED", createdAt,
 * revokedAt:Instant?}. Đây là shape trả ra ngoài API (201/200 của
 * /employees/{id}/qr-credential...); model nội bộ nằm ở internal.model.
 * Một người tối đa một credential ACTIVE; revokedAt null khi ACTIVE.
 */
public record QrCredentialView(String id, String employeeId, String state, Instant createdAt, Instant revokedAt) {

    public static final String STATE_ACTIVE = "ACTIVE";
    public static final String STATE_REVOKED = "REVOKED";

    public QrCredentialView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
