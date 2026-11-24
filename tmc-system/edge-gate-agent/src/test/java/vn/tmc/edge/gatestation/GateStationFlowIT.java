package vn.tmc.edge.gatestation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.tmc.contracts.RevocationChange;
import vn.tmc.edge.edgesync.api.EventPayload.CommandMode;
import vn.tmc.edge.edgesync.api.EventPayload.CommandStatus;
import vn.tmc.edge.edgesync.api.EventPayload.ScanMode;
import vn.tmc.edge.edgesync.api.ScanOutcome.Decision;
import vn.tmc.edge.gatestation.domain.ActionGrantVerificationPort.VerifiedGrant;
import vn.tmc.edge.gatestation.domain.GateStationException;
import vn.tmc.edge.gatestation.domain.GateStationException.Kind;
import vn.tmc.edge.gatestation.domain.KioskContext;
import vn.tmc.edge.gatestation.domain.OverrideRequest;
import vn.tmc.edge.gatestation.domain.QrRejectReason;
import vn.tmc.edge.gatestation.domain.QrVerificationRejectException;
import vn.tmc.edge.gatestation.domain.ScanRequest;
import vn.tmc.edge.gatestation.domain.ScanResult;
import vn.tmc.edge.gatestation.infrastructure.MockTurnstileAdapter.Behavior;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static vn.tmc.edge.gatestation.GateStationFixture.*;

/**
 * Kiểm thử end-to-end luồng Gate Station của Task 2 với SQLite thật (Task 7) và verifier thật (Task 6).
 * Bám điều kiện kiểm thử CONTRACT-V1.md §11: (2) sai alg/kid/time bị từ chối, race jti; (3) crash trước commit không
 * mở, sau commit trước command không tự mở lại, retry scanId không command lặp.
 */
class GateStationFlowIT {
    @TempDir
    Path dir;

    private GateStationFixture fixture() {
        return new GateStationFixture(dir, true, true);
    }

    // ---------- ALLOW và thứ tự commit → unlock ----------

    @Test
    void allow_persistsEventThenUnlocksAndRecordsDeviceResult() {
        var f = fixture();
        ScanResult result = f.scan(scanId(), f.token(EMPLOYEE_ID));

        assertEquals(Decision.ALLOW, result.decision());
        assertEquals("OK", result.reasonCode());
        assertNotNull(result.eventId());
        assertNotNull(result.commandId());
        assertEquals(CommandStatus.UNLOCK_ACCEPTED, result.commandStatus());
        assertEquals(ScanMode.ONLINE, result.mode());
        assertEquals(1, f.turnstile.calls.get());
        assertEquals(1, f.events("attendance.event.recorded"));
        assertEquals(1, f.events("gate.command.result"), "kết quả thiết bị là EVT-03 riêng, không sửa EVT-01");
        assertEquals(0, f.events("access.attempt.denied"));
    }

    @Test
    void storageFailure_doesNotUnlockAndReturns503() {
        var f = fixture();
        f.execute("""
                CREATE TRIGGER fail_sequence_update BEFORE UPDATE OF last_sequence_no ON edge_runtime_state
                BEGIN SELECT RAISE(ABORT, 'test write failure'); END
                """);
        var failure = assertThrows(GateStationException.class, () -> f.scan(scanId(), f.token(EMPLOYEE_ID)));
        assertEquals(Kind.STORAGE_UNAVAILABLE, failure.kind());
        assertEquals(0, f.turnstile.calls.get(), "lỗi ghi thì tuyệt đối không mở cổng");
        assertEquals(0, f.events("attendance.event.recorded"));
    }

    @Test
    void offlineMode_isReflectedInEventSourceAndResult() {
        var f = fixture();
        f.mode.set(ScanMode.OFFLINE);
        ScanResult result = f.scan(scanId(), f.token(EMPLOYEE_ID));
        assertEquals(ScanMode.OFFLINE, result.mode());
        assertTrue(f.lastPayload("attendance.event.recorded").contains("\"source\":\"OFFLINE\""));
    }

    // ---------- replay, quét lặp, retry ----------

    @Test
    void reusedJti_isDeniedAsReplayAndDoesNotUnlockAgain() {
        var f = fixture();
        UUID jti = UUID.randomUUID();
        assertEquals(Decision.ALLOW, f.scan(scanId(), f.token(EMPLOYEE_ID, jti)).decision());
        ScanResult replay = f.scan(scanId(), f.token(EMPLOYEE_ID, jti));
        assertEquals(Decision.DENY, replay.decision());
        assertEquals("REPLAY", replay.reasonCode());
        assertEquals(1, f.turnstile.calls.get());
        assertEquals(1, f.events("access.attempt.denied"));
    }

    @Test
    void businessDuplicateWithin30Seconds_isDeniedThenAllowedAfterwards() {
        var f = fixture();
        assertEquals(Decision.ALLOW, f.scan(scanId(), f.token(EMPLOYEE_ID)).decision());
        f.clock.advance(Duration.ofSeconds(10));
        ScanResult duplicate = f.scan(scanId(), f.token(EMPLOYEE_ID));
        assertEquals("DUPLICATE_SCAN", duplicate.reasonCode());
        assertEquals(1, f.turnstile.calls.get());

        f.clock.advance(Duration.ofSeconds(25));
        assertEquals(Decision.ALLOW, f.scan(scanId(), f.token(EMPLOYEE_ID)).decision());
        assertEquals(2, f.turnstile.calls.get());
    }

    @Test
    void retrySameScanId_returnsSameOutcomeWithoutSecondUnlock() {
        var f = fixture();
        UUID scanId = scanId();
        String token = f.token(EMPLOYEE_ID);
        ScanResult first = f.scan(scanId, token);
        ScanResult retry = f.scan(scanId, token);
        assertEquals(first, retry);
        assertEquals(1, f.turnstile.calls.get());
        assertEquals(1, f.events("attendance.event.recorded"));
    }

    @Test
    void retryAfterRestart_readsStoredOutcomeAndNeverUnlocksAgain() {
        var f = fixture();
        UUID scanId = scanId();
        String token = f.token(EMPLOYEE_ID);
        ScanResult first = f.scan(scanId, token);
        f.restart();

        ScanResult retry = f.scan(scanId, token);
        assertEquals(first.eventId(), retry.eventId());
        assertEquals(first.commandId(), retry.commandId());
        // Sau restart store coi command chưa có kết quả là TIMEOUT (an toàn, không tự mở lại).
        assertEquals(CommandStatus.TIMEOUT, retry.commandStatus());
        assertEquals(1, f.turnstile.calls.get());
    }

    @Test
    void sameScanIdWithDifferentInput_isIdempotencyConflict() {
        var f = fixture();
        UUID scanId = scanId();
        f.scan(scanId, f.token(EMPLOYEE_ID));
        var conflict = assertThrows(GateStationException.class, () -> f.scan(scanId, f.token(EMPLOYEE_ID)));
        assertEquals(Kind.IDEMPOTENCY_CONFLICT, conflict.kind());
        assertEquals(1, f.turnstile.calls.get());
    }

    @Test
    void deniedScanRetry_returnsSameDenyAndRecordsSingleEvidence() {
        var f = fixture();
        UUID scanId = scanId();
        String token = f.token(id(99)); // nhân viên không có trong policy
        ScanResult first = f.scan(scanId, token);
        ScanResult retry = f.scan(scanId, token);
        assertEquals("OUTSIDE_POLICY", first.reasonCode());
        assertEquals(first, retry);
        assertEquals(1, f.events("access.attempt.denied"));
    }

    // ---------- DENY: mọi lý do đều không mở cổng và không tạo công ----------

    @Test
    void outsidePolicy_isDeniedWithoutUnlockOrAttendance() {
        var f = fixture();
        ScanResult result = f.scan(scanId(), f.token(id(99)));
        assertEquals(Decision.DENY, result.decision());
        assertEquals("OUTSIDE_POLICY", result.reasonCode());
        assertNull(result.eventId());
        assertNull(result.commandId());
        assertEquals(CommandStatus.NOT_SENT, result.commandStatus());
        assertEquals(0, f.turnstile.calls.get());
        assertEquals(0, f.events("attendance.event.recorded"));
        assertEquals(1, f.events("access.attempt.denied"));
    }

    @Test
    void tokenProblems_areMappedToContractReasonCodes() {
        var f = fixture();
        long now = f.clock.instant().getEpochSecond();

        String tampered = f.token(EMPLOYEE_ID);
        tampered = tampered.substring(0, tampered.length() - 4) + (tampered.endsWith("AAAA") ? "BBBB" : "AAAA");
        assertEquals("INVALID_TOKEN", f.scan(scanId(), tampered).reasonCode());

        String unknownKid = f.token(EMPLOYEE_ID, UUID.randomUUID(), now, "other-kid", f.signingKey);
        assertEquals("UNKNOWN_KID", f.scan(scanId(), unknownKid).reasonCode());

        String expired = f.token(EMPLOYEE_ID, UUID.randomUUID(), now - 120, KID, f.signingKey);
        assertEquals("TOKEN_EXPIRED", f.scan(scanId(), expired).reasonCode());

        UUID revokedJti = UUID.randomUUID();
        f.revocationCache.replaceAll(List.of(new RevocationChange(UUID.randomUUID().toString(),
                RevocationChange.OP_UPSERT, RevocationChange.KIND_JTI, revokedJti.toString(), null,
                f.clock.instant().minusSeconds(5))));
        assertEquals("REVOKED", f.scan(scanId(), f.token(EMPLOYEE_ID, revokedJti)).reasonCode());

        assertEquals(0, f.turnstile.calls.get());
        assertEquals(0, f.events("attendance.event.recorded"));
        assertEquals(4, f.events("access.attempt.denied"));
    }

    @Test
    void untrustedClock_failsClosed() {
        var f = fixture();
        f.clockHealthy.set(false);
        ScanResult result = f.scan(scanId(), f.token(EMPLOYEE_ID));
        assertEquals("CLOCK_UNTRUSTED", result.reasonCode());
        assertEquals(0, f.turnstile.calls.get());
    }

    @Test
    void disabledGate_isDenied() {
        var f = new GateStationFixture(dir, false, true);
        ScanResult result = f.scan(scanId(), f.token(EMPLOYEE_ID));
        assertEquals("GATE_DISABLED", result.reasonCode());
        assertEquals(0, f.turnstile.calls.get());
    }

    @Test
    void missingBootstrapCache_failsClosedWithCacheUnavailable() {
        var f = new GateStationFixture(dir, true, false);
        ScanResult result = f.scan(scanId(), f.token(EMPLOYEE_ID));
        assertEquals("CACHE_UNAVAILABLE", result.reasonCode());
        assertEquals(0, f.turnstile.calls.get());
    }

    @Test
    void cacheLosingTrust_failsClosed() {
        var f = fixture();
        f.clock.advance(Duration.ofHours(49)); // quá trust 48 giờ, không tự gia hạn khi mất WAN
        ScanResult result = f.scan(scanId(), f.token(EMPLOYEE_ID, UUID.randomUUID(), f.clock.instant().getEpochSecond(), KID, f.signingKey));
        assertEquals("CACHE_UNAVAILABLE", result.reasonCode());
        assertEquals(0, f.turnstile.calls.get());
    }

    @Test
    void gateOutsideKioskScope_isForbiddenNotDeny() {
        var f = fixture();
        var failure = assertThrows(GateStationException.class,
                () -> f.scanUseCase.scan(new ScanRequest(scanId(), OTHER_GATE_ID, f.token(EMPLOYEE_ID)), f.kiosk));
        assertEquals(Kind.DEVICE_BINDING_MISMATCH, failure.kind());
        assertEquals(0, f.turnstile.calls.get());
    }

    @Test
    void kioskOfAnotherEdge_isForbidden() {
        var f = fixture();
        var foreign = new KioskContext("kiosk-x", id(88), FACTORY_ID, Set.of(GATE_ID));
        var failure = assertThrows(GateStationException.class,
                () -> f.scanUseCase.scan(new ScanRequest(scanId(), GATE_ID, f.token(EMPLOYEE_ID)), foreign));
        assertEquals(Kind.DEVICE_BINDING_MISMATCH, failure.kind());
    }

    // ---------- thiết bị lỗi: ALLOW vẫn đã lưu, trạng thái lệnh phải trung thực ----------

    @Test
    void deviceTimeout_isReportedAsTimeoutAndNeverRetriedBlindly() {
        var f = fixture();
        f.turnstile.mock.script(GATE_ID, Behavior.TIMEOUT);
        UUID scanId = scanId();
        String token = f.token(EMPLOYEE_ID);

        ScanResult result = f.scan(scanId, token);
        assertEquals(Decision.ALLOW, result.decision());
        assertEquals(CommandStatus.TIMEOUT, result.commandStatus());
        assertTrue(f.lastPayload("gate.command.result").contains("\"status\":\"TIMEOUT\""));

        ScanResult retry = f.scan(scanId, token);
        assertEquals(CommandStatus.TIMEOUT, retry.commandStatus());
        assertEquals(1, f.turnstile.calls.get(), "retry cùng scanId không được gửi lệnh lần hai");
    }

    @Test
    void deviceRejection_isReportedAsFailed() {
        var f = fixture();
        f.turnstile.mock.script(GATE_ID, Behavior.FAIL);
        ScanResult result = f.scan(scanId(), f.token(EMPLOYEE_ID));
        assertEquals(Decision.ALLOW, result.decision());
        assertEquals(CommandStatus.FAILED, result.commandStatus());
        assertEquals(0, f.presence.snapshot().items().size(), "lệnh không được nhận thì không tính hiện diện");
    }

    @Test
    void adapterException_becomesFailedAndEventStaysCommitted() {
        var f = fixture();
        f.turnstile.failure.set(new IllegalStateException("device bus down"));
        ScanResult result = f.scan(scanId(), f.token(EMPLOYEE_ID));
        assertEquals(CommandStatus.FAILED, result.commandStatus());
        assertEquals(1, f.events("attendance.event.recorded"));
        assertTrue(f.lastPayload("gate.command.result").contains("ADAPTER_ERROR"));
    }

    @Test
    void acceptedUnlock_updatesLocalPresence() {
        var f = fixture();
        f.scan(scanId(), f.token(EMPLOYEE_ID));
        var snapshot = f.presence.snapshot();
        assertEquals(1, snapshot.items().size());
        assertEquals(EMPLOYEE_ID, snapshot.items().get(0).employeeId());
    }

    // ---------- manual / emergency ----------

    private VerifiedGrant grant(CommandMode action, UUID employeeId, String reason) {
        return new VerifiedGrant(UUID.randomUUID(), OPERATOR_ID, action, EDGE_ID, GATE_ID, employeeId, reason,
                T0.plusSeconds(60));
    }

    private static final String REASON = "Điện thoại hết pin, đã đối chiếu thẻ";

    private OverrideRequest manualRequest(UUID scanId, UUID employeeId) {
        return new OverrideRequest(scanId, GATE_ID, employeeId, REASON, CommandMode.MANUAL, "grant-token");
    }

    @Test
    void manual_withValidGrant_recordsManualEventWithOperatorAndUnlocks() {
        var f = fixture();
        var grant = grant(CommandMode.MANUAL, EMPLOYEE_ID, REASON);
        f.grantVerifier.set((token, now) -> grant);

        ScanResult result = f.overrideUseCase.override(manualRequest(scanId(), EMPLOYEE_ID), f.kiosk);
        assertEquals(Decision.ALLOW, result.decision());
        assertEquals(CommandStatus.UNLOCK_ACCEPTED, result.commandStatus());
        String payload = f.lastPayload("attendance.event.recorded");
        assertTrue(payload.contains("\"source\":\"MANUAL\""));
        assertTrue(payload.contains(OPERATOR_ID.toString()));
        assertEquals(1, f.turnstile.calls.get());
        assertTrue(f.lastPayload("gate.command.result").contains("\"mode\":\"MANUAL\""));
    }

    @Test
    void manual_stillRequiresPolicyForTheEmployee() {
        var f = fixture();
        UUID stranger = id(99);
        var grant = grant(CommandMode.MANUAL, stranger, REASON);
        f.grantVerifier.set((token, now) -> grant);

        ScanResult result = f.overrideUseCase.override(manualRequest(scanId(), stranger), f.kiosk);
        assertEquals("OUTSIDE_POLICY", result.reasonCode());
        assertEquals(0, f.turnstile.calls.get());
        assertEquals(0, f.events("attendance.event.recorded"));
    }

    @Test
    void manual_grantIsSingleUse() {
        var f = fixture();
        var grant = grant(CommandMode.MANUAL, EMPLOYEE_ID, REASON);
        f.grantVerifier.set((token, now) -> grant);
        assertEquals(Decision.ALLOW, f.overrideUseCase.override(manualRequest(scanId(), EMPLOYEE_ID), f.kiosk).decision());

        ScanResult reused = f.overrideUseCase.override(manualRequest(scanId(), EMPLOYEE_ID), f.kiosk);
        assertEquals("REPLAY", reused.reasonCode());
        assertEquals(1, f.turnstile.calls.get());
    }

    @Test
    void emergency_createsNoAttendanceAndWorksEvenWhenGateIsDisabled() {
        var f = new GateStationFixture(dir, false, true);
        var grant = grant(CommandMode.EMERGENCY, null, REASON);
        f.grantVerifier.set((token, now) -> grant);
        var request = new OverrideRequest(scanId(), GATE_ID, null, REASON, CommandMode.EMERGENCY, "grant-token");

        ScanResult result = f.overrideUseCase.override(request, f.kiosk);
        assertEquals(Decision.EMERGENCY, result.decision());
        assertNull(result.eventId(), "emergency không tạo công giả");
        assertEquals(CommandStatus.UNLOCK_ACCEPTED, result.commandStatus());
        assertEquals(0, f.events("attendance.event.recorded"));
        assertEquals(1, f.events("gate.command.result"));
        assertTrue(f.lastPayload("gate.command.result").contains("\"mode\":\"EMERGENCY\""));

        ScanResult retry = f.overrideUseCase.override(request, f.kiosk);
        assertEquals(result.commandId(), retry.commandId());
        assertEquals(1, f.turnstile.calls.get(), "retry emergency không mở lần hai");
    }

    @Test
    void override_rejectedGrantIsDeniedWithItsReason() {
        var f = fixture();
        f.grantVerifier.set((token, now) -> { throw new QrVerificationRejectException(QrRejectReason.TOKEN_EXPIRED, "hết hạn"); });
        ScanResult result = f.overrideUseCase.override(manualRequest(scanId(), EMPLOYEE_ID), f.kiosk);
        assertEquals("TOKEN_EXPIRED", result.reasonCode());
        assertEquals(0, f.turnstile.calls.get());
    }

    @Test
    void override_grantNotMatchingBodyIsDenied() {
        var f = fixture();
        var grant = grant(CommandMode.MANUAL, EMPLOYEE_ID, "Lý do khác hẳn với yêu cầu gửi lên");
        f.grantVerifier.set((token, now) -> grant);
        ScanResult result = f.overrideUseCase.override(manualRequest(scanId(), EMPLOYEE_ID), f.kiosk);
        assertEquals("INVALID_TOKEN", result.reasonCode());
        assertEquals(0, f.turnstile.calls.get());
    }

    @Test
    void override_grantForAnotherEdgeIsForbidden() {
        var f = fixture();
        var foreign = new VerifiedGrant(UUID.randomUUID(), OPERATOR_ID, CommandMode.MANUAL, id(88), GATE_ID,
                EMPLOYEE_ID, REASON, T0.plusSeconds(60));
        f.grantVerifier.set((token, now) -> foreign);
        var failure = assertThrows(GateStationException.class,
                () -> f.overrideUseCase.override(manualRequest(scanId(), EMPLOYEE_ID), f.kiosk));
        assertEquals(Kind.DEVICE_BINDING_MISMATCH, failure.kind());
    }

    @Test
    void override_withoutConfiguredGrantVerifier_failsClosedWith503() {
        var f = fixture();
        f.grantVerifier.set(new vn.tmc.edge.gatestation.infrastructure.UnavailableActionGrantVerifier());
        var failure = assertThrows(GateStationException.class,
                () -> f.overrideUseCase.override(manualRequest(scanId(), EMPLOYEE_ID), f.kiosk));
        assertEquals(Kind.DEPENDENCY_UNAVAILABLE, failure.kind());
        assertEquals(0, f.turnstile.calls.get());
    }

    @Test
    void overrideRequestValidation_rejectsBadShapes() {
        UUID scan = scanId();
        assertThrows(GateStationException.class,
                () -> new OverrideRequest(scan, GATE_ID, null, REASON, CommandMode.MANUAL, "g"));
        assertThrows(GateStationException.class,
                () -> new OverrideRequest(scan, GATE_ID, EMPLOYEE_ID, REASON, CommandMode.EMERGENCY, "g"));
        assertThrows(GateStationException.class,
                () -> new OverrideRequest(scan, GATE_ID, EMPLOYEE_ID, "ngắn", CommandMode.MANUAL, "g"));
        assertThrows(GateStationException.class,
                () -> new OverrideRequest(scan, GATE_ID, EMPLOYEE_ID, REASON, CommandMode.QR, "g"));
        assertThrows(GateStationException.class, () -> new ScanRequest(UUID.nameUUIDFromBytes(new byte[]{1}), GATE_ID, "t"));
    }

    @Test
    void raceOnSameScanId_loserSeesNoNewCommandAndNeverUnlocks() {
        var f = fixture();
        UUID scanId = scanId();
        String token = f.token(EMPLOYEE_ID);
        // Hai use case với bộ nhớ riêng và store "mù": cả hai đều vượt bước kiểm tra retry rồi mới tranh commit.
        ScanResult winner = f.scanUseCaseOver(f.blindStore()).scan(new ScanRequest(scanId, GATE_ID, token), f.kiosk);
        ScanResult loser = f.scanUseCaseOver(f.blindStore()).scan(new ScanRequest(scanId, GATE_ID, token), f.kiosk);

        assertEquals(Decision.ALLOW, winner.decision());
        assertEquals(Decision.ALLOW, loser.decision());
        assertEquals(winner.eventId(), loser.eventId(), "cùng một event, không tạo bản ghi thứ hai");
        assertEquals(1, f.turnstile.calls.get(), "chỉ lần commit đầu được phép gửi lệnh mở");
        assertEquals(1, f.events("attendance.event.recorded"));
    }

    @Test
    void raceOnSameEmergencyScanId_loserNeverUnlocks() {
        var f = fixture();
        var grant = grant(CommandMode.EMERGENCY, null, REASON);
        f.grantVerifier.set((token, now) -> grant);
        var request = new OverrideRequest(scanId(), GATE_ID, null, REASON, CommandMode.EMERGENCY, "grant-token");

        var winner = f.overrideUseCaseOver(f.blindStore()).override(request, f.kiosk);
        var loser = f.overrideUseCaseOver(f.blindStore()).override(request, f.kiosk);

        assertEquals(Decision.EMERGENCY, winner.decision());
        assertEquals(winner.commandId(), loser.commandId());
        assertEquals(1, f.turnstile.calls.get());
    }

    @Test
    void raceOnSameManualScanId_loserNeverUnlocks() {
        var f = fixture();
        var grant = grant(CommandMode.MANUAL, EMPLOYEE_ID, REASON);
        f.grantVerifier.set((token, now) -> grant);
        var request = manualRequest(scanId(), EMPLOYEE_ID);

        var winner = f.overrideUseCaseOver(f.blindStore()).override(request, f.kiosk);
        var loser = f.overrideUseCaseOver(f.blindStore()).override(request, f.kiosk);

        assertEquals(winner.eventId(), loser.eventId());
        assertEquals(1, f.turnstile.calls.get());
        assertEquals(1, f.events("attendance.event.recorded"));
    }

    @Test
    void concurrentScansWithSameJti_exactlyOneUnlocks() throws Exception {
        var f = fixture();
        UUID jti = UUID.randomUUID();
        String tokenA = f.token(EMPLOYEE_ID, jti);
        String tokenB = f.token(EMPLOYEE_ID, jti);
        var start = new java.util.concurrent.CyclicBarrier(2);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); return f.scan(scanId(), tokenA); });
            var b = pool.submit(() -> { start.await(); return f.scan(scanId(), tokenB); });
            var results = List.of(a.get(10, java.util.concurrent.TimeUnit.SECONDS), b.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(r -> r.decision() == Decision.ALLOW).count());
            assertEquals(1, results.stream().filter(r -> r.decision() == Decision.DENY).count());
        }
        assertEquals(1, f.turnstile.calls.get());
        assertEquals(1, f.events("attendance.event.recorded"));
    }
}
