package vn.tmc.edge.gatestation.application;

import vn.tmc.contracts.AttendancePayload.Direction;
import vn.tmc.edge.edgesync.api.CommittedEvent;
import vn.tmc.edge.edgesync.api.EventDraft;
import vn.tmc.edge.edgesync.api.EventPayload;
import vn.tmc.edge.edgesync.api.EventPayload.CommandStatus;
import vn.tmc.edge.edgesync.api.EventPayload.ScanMode;
import vn.tmc.edge.edgesync.api.EventStoreException;
import vn.tmc.edge.edgesync.api.LocalEventStorePort;
import vn.tmc.edge.edgesync.api.ReplayReservation;
import vn.tmc.edge.edgesync.api.ScanInputFingerprint;
import vn.tmc.edge.edgesync.api.ScanOutcome.Decision;
import vn.tmc.edge.gatestation.application.GateDecisionRecorder.ScanContext;
import vn.tmc.edge.gatestation.domain.AccessPolicyPort;
import vn.tmc.edge.gatestation.domain.ClockReading;
import vn.tmc.edge.gatestation.domain.GateCatalogPort;
import vn.tmc.edge.gatestation.domain.GateInfo;
import vn.tmc.edge.gatestation.domain.GateStationException;
import vn.tmc.edge.gatestation.domain.GateStationException.Kind;
import vn.tmc.edge.gatestation.domain.KioskContext;
import vn.tmc.edge.gatestation.domain.PolicyDecision;
import vn.tmc.edge.gatestation.domain.QrVerificationPort;
import vn.tmc.edge.gatestation.domain.QrVerificationRejectException;
import vn.tmc.edge.gatestation.domain.ScanRequest;
import vn.tmc.edge.gatestation.domain.ScanResult;
import vn.tmc.edge.gatestation.domain.TrustedClockPort;
import vn.tmc.edge.gatestation.domain.VerifiedQr;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 2. Use case quét QR tại cổng (CONTRACT-V1.md §5, §10; UC-02/03; FR-GS-02/03, FR-OS-01).
 *
 * Thứ tự cố định (verify → policy → reserve/lưu → mở cổng):
 * 1. kiosk có quyền với gate (mTLS binding) — sai là 403, không phải DENY;
 * 2. retry: cùng scanId/cùng input trả outcome cũ, khác input 409 — trước mọi kiểm tra credential;
 * 3. thời gian tin cậy — không có thì DENY CLOCK_UNTRUSTED (fail closed);
 * 4. cấu hình cổng từ cache (hướng vào/ra do Edge quyết, không tin scanner);
 * 5. QrVerificationPort.verify (Task 6) — từ chối mang reasonCode hợp đồng;
 * 6. AccessPolicyPort.evaluate — quyền theo nhà máy/cổng/ca/hướng từ cache;
 * 7. LocalEventStorePort.reserveAndAppend (Task 7) — MỘT transaction: replay jti + chống quét lặp 30 giây + sequence
 *    + event + delivery + command intent. Lỗi ghi thì KHÔNG mở cổng (503);
 * 8. chỉ khi store trả newCommand mới gọi TurnstileControlPort.unlock, rồi ghi EVT-03 kết quả thiết bị.
 * Không chờ Central và không ping mạng trong scan. DENY nghiệp vụ là ScanResult 200, không phải lỗi HTTP.
 */
public final class ScanQrUseCase {
    private final QrVerificationPort qrVerificationPort;
    private final AccessPolicyPort accessPolicyPort;
    private final GateCatalogPort gateCatalogPort;
    private final LocalEventStorePort localEventStorePort;
    private final TrustedClockPort clock;
    private final GateDecisionRecorder recorder;

    public ScanQrUseCase(QrVerificationPort qrVerificationPort, AccessPolicyPort accessPolicyPort,
                         GateCatalogPort gateCatalogPort, LocalEventStorePort localEventStorePort,
                         TrustedClockPort clock, GateDecisionRecorder recorder) {
        this.qrVerificationPort = Objects.requireNonNull(qrVerificationPort);
        this.accessPolicyPort = Objects.requireNonNull(accessPolicyPort);
        this.gateCatalogPort = Objects.requireNonNull(gateCatalogPort);
        this.localEventStorePort = Objects.requireNonNull(localEventStorePort);
        this.clock = Objects.requireNonNull(clock);
        this.recorder = Objects.requireNonNull(recorder);
    }

    public ScanResult scan(ScanRequest request, KioskContext kiosk) {
        GateDecisionRecorder.requireScope(kiosk, request.gateId());
        ScanInputFingerprint fingerprint = ScanInputFingerprint.ofQr(request.gateId(), request.token());
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

        GateInfo gate = gateCatalogPort.find(request.gateId(), now).orElse(null);
        if (gate == null) {
            return recorder.deny(ctx, "CACHE_UNAVAILABLE", null, null, null);
        }
        if (!gate.edgeId().equals(kiosk.edgeId()) || !gate.factoryId().equals(kiosk.factoryId())) {
            throw new GateStationException(Kind.DEVICE_BINDING_MISMATCH, "Thiết bị không thuộc phạm vi yêu cầu.");
        }

        VerifiedQr verified;
        try {
            verified = qrVerificationPort.verify(request.token(), now);
        } catch (QrVerificationRejectException rejected) {
            return recorder.deny(ctx, rejected.reason().name(), null, null, null);
        }
        UUID employeeId;
        UUID jti;
        try {
            employeeId = UUID.fromString(verified.employeeId());
            jti = UUID.fromString(verified.jti());
        } catch (IllegalArgumentException malformed) {
            // Verifier đã chuẩn hóa UUID; nếu vẫn sai thì coi như token không hợp lệ chứ không phải lỗi 500.
            return recorder.deny(ctx, "INVALID_TOKEN", null, null, null);
        }

        Direction direction = gate.direction();
        PolicyDecision decision = accessPolicyPort.evaluate(employeeId, gate.gateId(), direction, now);
        if (!decision.allowed()) {
            return recorder.deny(ctx, decision.reasonCode(), employeeId, jti, verified.kid());
        }

        ScanMode mode = recorder.currentMode();
        UUID commandId = UUID.randomUUID();
        var payload = AttendancePayloads.qr(employeeId, direction, mode, jti, verified.kid(),
                verified.tokenIssuedAt(), commandId);
        var draft = new EventDraft(kiosk.edgeId(), gate.gateId(), kiosk.factoryId(), request.scanId(), now,
                new EventPayload.Attendance(payload));
        var reservation = new ReplayReservation(ReplayReservation.Kind.QR, jti, verified.replayReservationExpiresAt(),
                employeeId, direction, gate.gateId(), fingerprint);

        CommittedEvent committed;
        try {
            committed = localEventStorePort.reserveAndAppend(request.scanId(), draft, reservation);
        } catch (EventStoreException failure) {
            return switch (failure.code()) {
                case REPLAY -> recorder.deny(ctx, "REPLAY", employeeId, jti, verified.kid());
                case DUPLICATE_SCAN -> recorder.deny(ctx, "DUPLICATE_SCAN", employeeId, jti, verified.kid());
                default -> throw GateDecisionRecorder.translate(failure);
            };
        }

        var command = committed.newCommand();
        if (command.isEmpty()) {
            // Retry hoặc đua cùng scanId: lần commit đầu đã/đang gửi lệnh, lần này tuyệt đối không mở thêm.
            return recorder.previous(request.scanId(), fingerprint).orElseGet(() -> new ScanResult(request.scanId(),
                    Decision.ALLOW, "OK", committed.envelope().eventId(), null, CommandStatus.NOT_SENT, now, mode));
        }
        var committedResult = new ScanResult(request.scanId(), Decision.ALLOW, "OK", committed.envelope().eventId(),
                command.get().commandId(), CommandStatus.NOT_SENT, now, mode);
        return recorder.unlockAndComplete(ctx, committedResult, command.get(), committed.envelope().eventId(),
                direction, employeeId);
    }
}
