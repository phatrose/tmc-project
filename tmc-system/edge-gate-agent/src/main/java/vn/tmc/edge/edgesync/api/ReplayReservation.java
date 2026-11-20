package vn.tmc.edge.edgesync.api;

import vn.tmc.contracts.AttendancePayload.Direction;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Verified claims and local scan context. expiresAt is the signed QR or ACTION expiry. */
public record ReplayReservation(Kind kind, UUID jti, Instant expiresAt, UUID employeeId, Direction direction,
                                UUID gateId, ScanInputFingerprint inputFingerprint) {
    public enum Kind { QR, ACTION }

    public ReplayReservation {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(jti, "jti");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(gateId, "gateId");
        Objects.requireNonNull(inputFingerprint, "inputFingerprint");
    }
}
