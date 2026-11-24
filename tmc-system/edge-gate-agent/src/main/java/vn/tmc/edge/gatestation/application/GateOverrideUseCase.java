package vn.tmc.edge.gatestation.application;

import vn.tmc.contracts.AttendancePayload.Direction;
import vn.tmc.edge.edgesync.api.CommandIntent;
import vn.tmc.edge.edgesync.api.CommittedCommand;
import vn.tmc.edge.edgesync.api.CommittedEvent;
import vn.tmc.edge.edgesync.api.EventDraft;
import vn.tmc.edge.edgesync.api.EventPayload;
import vn.tmc.edge.edgesync.api.EventPayload.CommandMode;
import vn.tmc.edge.edgesync.api.EventPayload.CommandStatus;
import vn.tmc.edge.edgesync.api.EventPayload.ScanMode;
import vn.tmc.edge.edgesync.api.EventStoreException;
import vn.tmc.edge.edgesync.api.LocalEventStorePort;
import vn.tmc.edge.edgesync.api.ReplayReservation;
import vn.tmc.edge.edgesync.api.ScanInputFingerprint;
import vn.tmc.edge.edgesync.api.ScanOutcome.Decision;
import vn.tmc.edge.gatestation.application.GateDecisionRecorder.ScanContext;
import vn.tmc.edge.gatestation.domain.AccessPolicyPort;
import vn.tmc.edge.gatestation.domain.ActionGrantVerificationPort;
import vn.tmc.edge.gatestation.domain.ActionGrantVerificationPort.VerifiedGrant;
import vn.tmc.edge.gatestation.domain.ClockReading;
import vn.tmc.edge.gatestation.domain.GateCatalogPort;
import vn.tmc.edge.gatestation.domain.GateInfo;
import vn.tmc.edge.gatestation.domain.GateStationException;
import vn.tmc.edge.gatestation.domain.GateStationException.Kind;
import vn.tmc.edge.gatestation.domain.KioskContext;
import vn.tmc.edge.gatestation.domain.OverrideRequest;
import vn.tmc.edge.gatestation.domain.PolicyDecision;
import vn.tmc.edge.gatestation.domain.QrVerificationRejectException;
import vn.tmc.edge.gatestation.domain.ScanResult;
import vn.tmc.edge.gatestation.domain.TrustedClockPort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 2. Mở cổng thủ công và khẩn cấp (CONTRACT-V1.md §5 "Re-auth cho override"; UC-04;
 * FR-GS-05/06). Cả hai cần action grant do Central cấp sau re-auth mật khẩu; Edge không tự re-auth.
 *
 * MANUAL: grant hợp lệ + người được mở vẫn phải qua policy (không âm thầm bỏ qua quyền khu vực) → ghi event công
 *   source=MANUAL kèm operatorId/reason → mở cổng. Event MANUAL tham gia chống quét lặp 30 giây như QR.
 * EMERGENCY: grant hợp lệ → chỉ lưu command + audit, KHÔNG tạo event công giả → mở cổng. Không kiểm tra policy
 *   hay trạng thái bật/tắt của cổng vì mục đích là thoát hiểm.
 * Grant khác chứng chỉ thiết bị (edge/gate) → 403; grant khác nội dung body → DENY INVALID_TOKEN.
 * jti của grant được reserve nguyên tử cùng transaction ghi (loại ACTION), nên một grant chỉ dùng một lần.
 */
public final class GateOverrideUseCase {
    private final ActionGrantVerificationPort grantVerifier;
    private final AccessPolicyPort accessPolicyPort;
    private final GateCatalogPort gateCatalogPort;
    private final LocalEventStorePort localEventStorePort;
    private final TrustedClockPort clock;
    private final GateDecisionRecorder recorder;

    public GateOverrideUseCase(ActionGrantVerificationPort grantVerifier, AccessPolicyPort accessPolicyPort,
                               GateCatalogPort gateCatalogPort, LocalEventStorePort localEventStorePort,
                               TrustedClockPort clock, GateDecisionRecorder recorder) {
        this.grantVerifier = Objects.requireNonNull(grantVerifier);
        this.accessPolicyPort = Objects.requireNonNull(accessPolicyPort);
        this.gateCatalogPort = Objects.requireNonNull(gateCatalogPort);
        this.localEventStorePort = Objects.requireNonNull(localEventStorePort);
        this.clock = Objects.requireNonNull(clock);
        this.recorder = Objects.requireNonNull(recorder);
    }

    public ScanResult override(OverrideRequest request, KioskContext kiosk) {
        GateDecisionRecorder.requireScope(kiosk, request.gateId());
        ScanInputFingerprint fingerprint = ScanInputFingerprint.ofAction(request.gateId(), request.mode(),
                request.employeeId(), request.reason(), request.grant());
        var previous = recorder.previous(request.scanId(), fingerprint);
        if (previous.isPresent()) {
            return previous.get();
        }

        ClockReading reading = clock.read();
        Instant now = reading.now().truncatedTo(ChronoUnit.MILLIS);
        var ctx = new ScanContext(request.scanId(), request.gateId(), fingerprint, kiosk, now);
        if (!reading.trusted()) {
            return recorder.deny(ctx, "CLOCK_UNTRUSTED", null, null, null);
        }

        VerifiedGrant grant;
        try {
            grant = grantVerifier.verify(request.grant(), now);
        } catch (QrVerificationRejectException rejected) {
            return recorder.deny(ctx, rejected.reason().name(), null, null, null);
        }
        if (!grant.edgeId().equals(kiosk.edgeId()) || !kiosk.mayOperate(grant.gateId())) {
            throw new GateStationException(Kind.DEVICE_BINDING_MISMATCH, "Thiết bị không thuộc phạm vi yêu cầu.");
        }
        if (grant.action() != request.mode() || !grant.gateId().equals(request.gateId())
                || !Objects.equals(grant.employeeId(), request.employeeId()) || !grant.reason().equals(request.reason())) {
            return recorder.deny(ctx, "INVALID_TOKEN", null, null, null);
        }
        return request.mode() == CommandMode.EMERGENCY
                ? emergency(request, kiosk, ctx, grant, fingerprint)
                : manual(request, kiosk, ctx, grant, fingerprint);
    }

    private ScanResult manual(OverrideRequest request, KioskContext kiosk, ScanContext ctx, VerifiedGrant grant,
                              ScanInputFingerprint fingerprint) {
        GateInfo gate = gateCatalogPort.find(request.gateId(), ctx.at()).orElse(null);
        if (gate == null) {
            return recorder.deny(ctx, "CACHE_UNAVAILABLE", null, null, null);
        }
        if (!gate.edgeId().equals(kiosk.edgeId()) || !gate.factoryId().equals(kiosk.factoryId())) {
            throw new GateStationException(Kind.DEVICE_BINDING_MISMATCH, "Thiết bị không thuộc phạm vi yêu cầu.");
        }
        UUID employeeId = request.employeeId();
        Direction direction = gate.direction();
        PolicyDecision decision = accessPolicyPort.evaluate(employeeId, gate.gateId(), direction, ctx.at());
        if (!decision.allowed()) {
            return recorder.deny(ctx, decision.reasonCode(), employeeId, null, null);
        }

        UUID commandId = UUID.randomUUID();
        var payload = AttendancePayloads.manual(employeeId, direction, grant.operatorId(), request.reason(), commandId);
        var draft = new EventDraft(kiosk.edgeId(), gate.gateId(), kiosk.factoryId(), request.scanId(), ctx.at(),
                new EventPayload.Attendance(payload));
        var reservation = new ReplayReservation(ReplayReservation.Kind.ACTION, grant.jti(), grant.expiresAt(),
                employeeId, direction, gate.gateId(), fingerprint);

        CommittedEvent committed;
        try {
            committed = localEventStorePort.reserveAndAppend(request.scanId(), draft, reservation);
        } catch (EventStoreException failure) {
            return switch (failure.code()) {
                case REPLAY -> recorder.deny(ctx, "REPLAY", employeeId, null, null);
                case DUPLICATE_SCAN -> recorder.deny(ctx, "DUPLICATE_SCAN", employeeId, null, null);
                default -> throw GateDecisionRecorder.translate(failure);
            };
        }

        var command = committed.newCommand();
        ScanMode mode = ScanMode.ONLINE; // manual cần Central online để lấy grant (contract §5)
        if (command.isEmpty()) {
            return recorder.previous(request.scanId(), fingerprint).orElseGet(() -> new ScanResult(request.scanId(),
                    Decision.ALLOW, "OK", committed.envelope().eventId(), null, CommandStatus.NOT_SENT, ctx.at(), mode));
        }
        var committedResult = new ScanResult(request.scanId(), Decision.ALLOW, "OK", committed.envelope().eventId(),
                command.get().commandId(), CommandStatus.NOT_SENT, ctx.at(), mode);
        return recorder.unlockAndComplete(ctx, committedResult, command.get(), committed.envelope().eventId(),
                direction, employeeId);
    }

    private ScanResult emergency(OverrideRequest request, KioskContext kiosk, ScanContext ctx, VerifiedGrant grant,
                                 ScanInputFingerprint fingerprint) {
        var intent = new CommandIntent(UUID.randomUUID(), request.gateId(), grant.operatorId(), request.reason(),
                CommandMode.EMERGENCY);
        var reservation = new ReplayReservation(ReplayReservation.Kind.ACTION, grant.jti(), grant.expiresAt(), null, null,
                request.gateId(), fingerprint);

        CommittedCommand committed;
        try {
            committed = localEventStorePort.commitEmergency(request.scanId(), intent, reservation);
        } catch (EventStoreException failure) {
            if (failure.code() == EventStoreException.Code.REPLAY) {
                return recorder.deny(ctx, "REPLAY", null, null, null);
            }
            throw GateDecisionRecorder.translate(failure);
        }

        var command = committed.newCommand();
        if (command.isEmpty()) {
            return recorder.previous(request.scanId(), fingerprint).orElseGet(() -> new ScanResult(request.scanId(),
                    Decision.EMERGENCY, "EMERGENCY", null, committed.commandId(), CommandStatus.NOT_SENT,
                    committed.committedAt(), ScanMode.ONLINE));
        }
        var committedResult = new ScanResult(request.scanId(), Decision.EMERGENCY, "EMERGENCY", null,
                committed.commandId(), CommandStatus.NOT_SENT, committed.committedAt(), ScanMode.ONLINE);
        return recorder.unlockAndComplete(ctx, committedResult, command.get(), null, null, null);
    }
}
