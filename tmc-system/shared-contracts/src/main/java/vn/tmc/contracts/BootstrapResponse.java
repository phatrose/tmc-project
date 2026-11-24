package vn.tmc.contracts;

import vn.tmc.contracts.AttendancePayload.Direction;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contract v1 bootstrap wire data. Edge validates scope and trust before persisting it. */
public record BootstrapResponse(Mode mode, long baseRevision, long revision, Instant generatedAt, Instant trustedUntil,
                                UUID factoryId, UUID edgeId, List<RevocationChange> revocations, KeySet keySet,
                                PolicySnapshot policy, List<Gate> gates) {
    public BootstrapResponse {
        revocations = List.copyOf(revocations);
        gates = List.copyOf(gates);
    }

    public enum Mode { FULL, DELTA }
    public enum Operation { UPSERT, DELETE }
    public enum RevocationKind { SUB, JTI, KID }
    public enum KeyState { ACTIVE, VERIFY_ONLY, REVOKED }

    public record RevocationChange(UUID id, Operation op, RevocationKind kind, String value, Instant revokedBefore,
                                   Instant effectiveAt) {}

    public record KeySet(long version, Instant generatedAt, Instant trustedUntil, List<SigningKey> keys) {
        public KeySet { keys = List.copyOf(keys); }
    }

    public record SigningKey(String kid, String kty, String crv, String alg, String use, String x, String y,
                             KeyState state, Instant notBefore, Instant notAfter) {}

    public record PolicySnapshot(long version, List<EmployeePolicy> employees) {
        public PolicySnapshot { employees = List.copyOf(employees); }
    }

    public record EmployeePolicy(UUID employeeId, boolean active, List<PolicyWindow> windows) {
        public EmployeePolicy { windows = List.copyOf(windows); }
    }

    public record PolicyWindow(UUID gateId, Direction direction, Instant startsAt, Instant endsAt) {}

    public record Gate(UUID id, long version, String name, UUID factoryId, UUID edgeId, Direction direction,
                       String turnstileApiId, boolean enabled) {}
}
