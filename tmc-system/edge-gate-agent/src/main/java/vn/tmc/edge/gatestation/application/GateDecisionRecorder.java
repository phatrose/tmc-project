package vn.tmc.edge.gatestation.application;

import vn.tmc.contracts.AttendancePayload.Direction;
import vn.tmc.edge.edgesync.api.CommandIntent;
import vn.tmc.edge.edgesync.api.EventDraft;
import vn.tmc.edge.edgesync.api.EventPayload;
import vn.tmc.edge.edgesync.api.EventPayload.CommandStatus;
import vn.tmc.edge.edgesync.api.EventPayload.ScanMode;
import vn.tmc.edge.edgesync.api.EventStoreException;
import vn.tmc.edge.edgesync.api.LocalEventStorePort;
import vn.tmc.edge.edgesync.api.ScanInputFingerprint;
import vn.tmc.edge.gatestation.domain.CommandResult;
import vn.tmc.edge.gatestation.domain.GateStationException;
import vn.tmc.edge.gatestation.domain.GateStationException.Kind;
import vn.tmc.edge.gatestation.domain.KioskContext;
import vn.tmc.edge.gatestation.domain.PresenceRegistry;
import vn.tmc.edge.gatestation.domain.ScanResult;
import vn.tmc.edge.gatestation.domain.TurnstileControlPort;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * PHỤ TRÁCH: Task 2.
 * Cơ chế dùng chung của ScanQrUseCase và GateOverrideUseCase (tách ra để hai use case chỉ còn điều phối luồng):
 * - đọc lại kết quả của scanId đã xử lý (idempotent retry);
 * - ghi EVT-02 khi DENY và nhớ kết quả;
 * - gửi lệnh mở cổng SAU commit, ghi EVT-03 kết quả thiết bị, cập nhật hiện diện;
 * - dịch lỗi store sang lỗi HTTP của contract.
 * Không biết gì về QR, grant hay policy.
 */
public final class GateDecisionRecorder {
    private static final System.Logger LOG = System.getLogger(GateDecisionRecorder.class.getName());

    /** Ngữ cảnh bất biến của một lượt xử lý, dựng sau khi đã có thời gian tin cậy. */
    record ScanContext(UUID scanId, UUID gateId, ScanInputFingerprint fingerprint, KioskContext kiosk, Instant at) {}

    private final LocalEventStorePort store;
    private final TurnstileControlPort turnstile;
    private final Supplier<ScanMode> mode;
    private final RecentScanResults memo;
    private final PresenceRegistry presence;
    private final Clock clock;

    public GateDecisionRecorder(LocalEventStorePort store, TurnstileControlPort turnstile, Supplier<ScanMode> mode,
                                RecentScanResults memo, PresenceRegistry presence, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.turnstile = Objects.requireNonNull(turnstile, "turnstile");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.memo = Objects.requireNonNull(memo, "memo");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** GET /edge/v1/scans/{scanId}: kết quả gần nhất trong phạm vi kiosk (chỉ từ bộ nhớ, xem RecentScanResults). */
    public Optional<ScanResult> recentResult(UUID scanId, KioskContext kiosk) {
        return memo.findForKiosk(scanId, kiosk);
    }

    ScanMode currentMode() {
        return mode.get();
    }

    static void requireScope(KioskContext kiosk, UUID gateId) {
        if (!kiosk.mayOperate(gateId)) {
            throw new GateStationException(Kind.DEVICE_BINDING_MISMATCH, "Thiết bị không thuộc phạm vi yêu cầu.");
        }
    }

    /** Retry trước khi xác minh credential: cùng input trả outcome cũ, khác input là 409. */
    Optional<ScanResult> previous(UUID scanId, ScanInputFingerprint fingerprint) {
        Optional<ScanResult> remembered = memo.find(scanId, fingerprint);
        if (remembered.isPresent()) {
            return remembered;
        }
        try {
            return store.findScanOutcome(scanId, fingerprint).map(ScanResult::from).map(memo::withKnownCommandStatus);
        } catch (EventStoreException failure) {
            throw translate(failure);
        }
    }

    /**
     * DENY nghiệp vụ: ghi EVT-02 nếu store hoạt động (contract §5), không để lỗi ghi làm hỏng phản hồi.
     * employeeId/jti/kid chỉ được truyền khi đã xác minh chữ ký; token chưa xác minh không được đưa vào event.
     */
    ScanResult deny(ScanContext ctx, String reasonCode, UUID employeeId, UUID jti, String kid) {
        ScanMode scanMode = mode.get();
        try {
            store.append(new EventDraft(ctx.kiosk().edgeId(), ctx.gateId(), ctx.kiosk().factoryId(), ctx.scanId(), ctx.at(),
                    new EventPayload.AccessDenied(employeeId, reasonCode, jti, kid, scanMode)));
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Không ghi được EVT-02 cho scan {0}: {1}", ctx.scanId(), describe(failure));
        }
        ScanResult result = ScanResult.denied(ctx.scanId(), reasonCode, ctx.at(), scanMode);
        memo.remember(ctx.scanId(), ctx.gateId(), ctx.fingerprint(), result);
        return result;
    }

    /**
     * Gửi lệnh mở cổng cho command vừa commit lần đầu rồi hoàn tất kết quả. Chỉ gọi khi store trả newCommand
     * (retry/đua không có newCommand nên không bao giờ mở lần hai). Lỗi thiết bị không ném ra: là FAILED/TIMEOUT.
     */
    ScanResult unlockAndComplete(ScanContext ctx, ScanResult committed, CommandIntent command, UUID attendanceEventId,
                                 Direction direction, UUID employeeId) {
        CommandResult outcome = unlock(command);
        recordCommandResult(ctx, command, attendanceEventId, outcome);
        memo.rememberCommand(outcome);
        ScanResult done = committed.withCommandStatus(outcome.status());
        memo.remember(ctx.scanId(), ctx.gateId(), ctx.fingerprint(), done);
        if (outcome.status() == CommandStatus.UNLOCK_ACCEPTED && employeeId != null && direction != null) {
            presence.recordPassage(employeeId, direction, ctx.at());
        }
        return done;
    }

    private CommandResult unlock(CommandIntent command) {
        try {
            CommandResult result = turnstile.unlock(command.commandId(), command.gateId());
            if (result == null || !command.commandId().equals(result.commandId())) {
                return failed(command, "INVALID_ADAPTER_RESULT");
            }
            return result;
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Turnstile adapter ném lỗi cho command {0}: {1}",
                    command.commandId(), describe(failure));
            return failed(command, "ADAPTER_ERROR");
        }
    }

    private CommandResult failed(CommandIntent command, String code) {
        return new CommandResult(command.commandId(), CommandStatus.FAILED, Instant.ofEpochMilli(clock.millis()), code);
    }

    /** EVT-03 riêng, không sửa event công đã commit (contract §6). Lỗi ghi chỉ được log. */
    private void recordCommandResult(ScanContext ctx, CommandIntent command, UUID attendanceEventId, CommandResult outcome) {
        try {
            store.append(new EventDraft(ctx.kiosk().edgeId(), ctx.gateId(), ctx.kiosk().factoryId(), ctx.scanId(),
                    outcome.completedAt(), new EventPayload.GateCommandResult(command.commandId(), attendanceEventId,
                    command.mode(), command.operatorId(), command.reason(), outcome.status(), outcome.completedAt(),
                    outcome.code())));
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Không ghi được EVT-03 cho command {0}: {1}",
                    command.commandId(), describe(failure));
        }
    }

    /** Dịch lỗi store sang ApiError. REPLAY/DUPLICATE_SCAN là DENY nghiệp vụ nên do use case xử lý trước khi gọi đây. */
    static GateStationException translate(EventStoreException failure) {
        return switch (failure.code()) {
            case IDEMPOTENCY_CONFLICT -> new GateStationException(Kind.IDEMPOTENCY_CONFLICT,
                    "scanId đã được dùng với nội dung khác");
            case STORAGE_UNAVAILABLE, REPROVISION_REQUIRED -> new GateStationException(Kind.STORAGE_UNAVAILABLE,
                    "Kho sự kiện cục bộ không khả dụng", failure);
            case REPLAY, DUPLICATE_SCAN -> new GateStationException(Kind.INTERNAL_ERROR,
                    "Lỗi nội bộ khi xử lý lượt quét", failure);
        };
    }

    /** Chỉ tên lớp lỗi + mã, không message (message của driver có thể chứa đường dẫn hoặc dữ liệu). */
    private static String describe(RuntimeException failure) {
        return failure instanceof EventStoreException store
                ? failure.getClass().getSimpleName() + "/" + store.code() : failure.getClass().getSimpleName();
    }
}
