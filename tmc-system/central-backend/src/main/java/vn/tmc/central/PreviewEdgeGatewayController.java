package vn.tmc.central;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.tmc.central.qrsecurity.api.QrKeySetQuery;
import vn.tmc.central.qrsecurity.api.QrRevocationQuery;
import vn.tmc.central.systemadmin.api.Administration;
import vn.tmc.central.systemadmin.api.GateManagement;
import vn.tmc.central.systemadmin.api.GateView;
import vn.tmc.central.usermgmt.api.*;
import vn.tmc.contracts.AttendancePayload;
import vn.tmc.contracts.BootstrapResponse;
import vn.tmc.contracts.QrKeySet;
import vn.tmc.contracts.QrKeySetEntry;
import vn.tmc.contracts.RevocationChange;

/**
 * PHỤ TRÁCH: Task 7 / Task 8.
 * Central Gateway cho Edge Gate Agent:
 * - GET /api/v1/edge/bootstrap?afterRevision=N
 * - POST /api/v1/edge/heartbeat
 * - GET /api/v1/qr/keys/refresh
 */
@RestController
@Profile("integration-preview")
@RequestMapping("/api/v1")
public final class PreviewEdgeGatewayController {

    private static final ActorContext SYSTEM_EDGE_ACTOR = new ActorContext(
            "00000000-0000-4000-8000-000000000001",
            null,
            List.of(Role.ADMIN),
            List.of("employee.read"),
            List.of(),
            List.of(),
            ActorContext.SCOPE_GLOBAL
    );

    private final QrKeySetQuery keySetQuery;
    private final QrRevocationQuery revocationQuery;
    private final GateManagement gateManagement;
    private final Administration administration;
    private final EmployeeManagement employeeManagement;
    private final Clock clock;

    public PreviewEdgeGatewayController(
            QrKeySetQuery keySetQuery,
            QrRevocationQuery revocationQuery,
            GateManagement gateManagement,
            Administration administration,
            EmployeeManagement employeeManagement,
            Clock clock
    ) {
        this.keySetQuery = Objects.requireNonNull(keySetQuery, "keySetQuery required");
        this.revocationQuery = Objects.requireNonNull(revocationQuery, "revocationQuery required");
        this.gateManagement = Objects.requireNonNull(gateManagement, "gateManagement required");
        this.administration = Objects.requireNonNull(administration, "administration required");
        this.employeeManagement = Objects.requireNonNull(employeeManagement, "employeeManagement required");
        this.clock = Objects.requireNonNull(clock, "clock required");
    }

    public record HeartbeatRequest(
            Long cacheRevision,
            Integer pendingCount,
            Integer failedCount,
            Long diskFreeBytes,
            Instant oldestPendingAt,
            Instant observedAt
    ) {}

    @GetMapping("/edge/bootstrap")
    public ResponseEntity<BootstrapResponse> bootstrap(
            @RequestParam(value = "afterRevision", defaultValue = "0") long afterRevision,
            @RequestHeader(value = "X-Edge-Id", required = false) String edgeIdHeader,
            @RequestHeader(value = "X-Factory-Id", required = false) String factoryIdHeader
    ) {
        QrKeySet ks = keySetQuery.keySet();
        long revision = Math.max(1L, ks.version() + revocationQuery.revision());
        if (afterRevision >= revision) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header("X-Revision", String.valueOf(revision))
                    .build();
        }

        UUID factoryId = toUuid(factoryIdHeader != null ? factoryIdHeader : "00000000-0000-4000-8000-000000000023");
        UUID edgeId = toUuid(edgeIdHeader != null ? edgeIdHeader : "00000000-0000-4000-8000-0000000000e1");
        Instant now = clock.instant();

        List<BootstrapResponse.SigningKey> signingKeys = ks.keys().stream()
                .map(k -> new BootstrapResponse.SigningKey(
                        k.kid(), k.kty(), k.crv(), k.alg(), k.use(), k.x(), k.y(),
                        mapKeyState(k.state()), k.notBefore(), k.notAfter()))
                .toList();
        BootstrapResponse.KeySet keySet = new BootstrapResponse.KeySet(
                ks.version(), ks.generatedAt(), ks.trustedUntil(), signingKeys);

        List<BootstrapResponse.RevocationChange> revocations = revocationQuery.activeRevocations().stream()
                .map(r -> new BootstrapResponse.RevocationChange(
                        toUuid(r.id()),
                        RevocationChange.OP_DELETE.equals(r.op())
                                ? BootstrapResponse.Operation.DELETE
                                : BootstrapResponse.Operation.UPSERT,
                        BootstrapResponse.RevocationKind.valueOf(r.kind()),
                        r.value(),
                        r.revokedBefore(),
                        r.effectiveAt()))
                .toList();

        List<BootstrapResponse.Gate> gates = gateManagement.findAllGates().stream()
                .map(g -> new BootstrapResponse.Gate(
                        toUuid(g.id()),
                        1L,
                        g.name(),
                        toUuid(g.factoryId()),
                        toUuid(g.edgeId()),
                        "OUT".equalsIgnoreCase(g.direction())
                                ? AttendancePayload.Direction.OUT
                                : AttendancePayload.Direction.IN,
                        g.turnstileApiId(),
                        "ACTIVE".equalsIgnoreCase(g.status())))
                .toList();

        List<BootstrapResponse.EmployeePolicy> policies = new ArrayList<>();
        var page = employeeManagement.listEmployees(SYSTEM_EDGE_ACTOR, null, null, null, 0, 100);
        for (EmployeeView emp : page.items()) {
            policies.add(new BootstrapResponse.EmployeePolicy(toUuid(emp.id()), emp.active(), List.of()));
        }

        BootstrapResponse response = new BootstrapResponse(
                BootstrapResponse.Mode.FULL,
                0L,
                revision,
                now,
                ks.trustedUntil(),
                factoryId,
                edgeId,
                revocations,
                keySet,
                new BootstrapResponse.PolicySnapshot(revision, policies),
                gates
        );
        return ResponseEntity.ok()
                .header("X-Revision", String.valueOf(revision))
                .header("Cache-Control", "no-store")
                .body(response);
    }

    @PostMapping("/edge/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat(
            @RequestBody(required = false) HeartbeatRequest body,
            @RequestHeader(value = "X-Edge-Id", required = false) String edgeIdHeader,
            @RequestHeader(value = "X-Factory-Id", required = false) String factoryIdHeader,
            HttpServletRequest request
    ) {
        String edgeId = (edgeIdHeader != null && !edgeIdHeader.isBlank()) ? edgeIdHeader.trim() : "EDGE-BD-01";
        String factoryId = (factoryIdHeader != null && !factoryIdHeader.isBlank()) ? factoryIdHeader.trim() : "FACTORY-BD";
        administration.recordDeviceHeartbeat(edgeId, "EDGE_NODE", factoryId, request.getRemoteAddr());
        return ResponseEntity.ok(Map.of(
                "status", "ACKNOWLEDGED",
                "edgeId", edgeId,
                "receivedAt", clock.instant().toString()
        ));
    }

    @GetMapping("/qr/keys/refresh")
    public ResponseEntity<QrKeySet> refreshKeys() {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(keySetQuery.keySet());
    }

    private static BootstrapResponse.KeyState mapKeyState(String state) {
        if (QrKeySetEntry.STATE_REVOKED.equals(state)) return BootstrapResponse.KeyState.REVOKED;
        if (QrKeySetEntry.STATE_VERIFY_ONLY.equals(state)) return BootstrapResponse.KeyState.VERIFY_ONLY;
        return BootstrapResponse.KeyState.ACTIVE;
    }

    private static UUID toUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return UUID.nameUUIDFromBytes("default".getBytes(StandardCharsets.UTF_8));
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return UUID.nameUUIDFromBytes(raw.trim().getBytes(StandardCharsets.UTF_8));
        }
    }
}
