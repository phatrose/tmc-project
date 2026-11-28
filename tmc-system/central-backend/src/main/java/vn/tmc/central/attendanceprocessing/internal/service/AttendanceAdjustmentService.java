package vn.tmc.central.attendanceprocessing.internal.service;

import vn.tmc.central.attendanceprocessing.api.Adjustment;
import vn.tmc.central.attendanceprocessing.api.AdjustmentDecision;
import vn.tmc.central.attendanceprocessing.api.AdjustmentRequest;
import vn.tmc.central.attendanceprocessing.api.AdjustmentRequestCommand;
import vn.tmc.central.attendanceprocessing.api.AdjustmentReviewCommand;
import vn.tmc.central.attendanceprocessing.api.AdjustmentStatus;
import vn.tmc.central.attendanceprocessing.api.AdjustmentType;
import vn.tmc.central.attendanceprocessing.api.AttendanceAdjustmentPort;
import vn.tmc.central.attendanceprocessing.api.AttendanceAdjustmentView;
import vn.tmc.central.attendanceprocessing.internal.model.AdjustmentHistory;
import vn.tmc.central.attendanceprocessing.internal.model.AttendanceAdjustment;
import vn.tmc.central.attendanceprocessing.internal.model.DailyAttendanceRecord;
import vn.tmc.central.attendanceprocessing.internal.model.ShiftDefinition;
import vn.tmc.central.attendanceprocessing.internal.repository.AttendanceAdjustmentRepository;
import vn.tmc.central.attendanceprocessing.internal.repository.DailyAttendanceRepository;
import vn.tmc.central.usermgmt.api.ActorContext;
import vn.tmc.central.usermgmt.api.Role;
import vn.tmc.contracts.Page;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: Service xử lý quy trình điều chỉnh công theo CONTRACT-V1.md §9 & §10.
 *
 * QUY TẮC BẢO MẬT & BẤT BIẾN:
 * - Tách quyền độc lập: decidedBy != createdBy (người tạo không được tự duyệt).
 * - Auditor cấm duyệt hoặc tạo điều chỉnh: Role.AUDITOR bị từ chối.
 * - Version optimistic locking: sai version trả lỗi xung đột trạng thái (HTTP 409).
 * - Bất biến: Tuyệt đối không UPDATE/DELETE bảng attendance_events gốc.
 */
public final class AttendanceAdjustmentService implements AttendanceAdjustmentPort {
    private static final ZoneId DEFAULT_FACTORY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AttendanceAdjustmentRepository adjustmentRepository;
    private final DailyAttendanceRepository dailyRepository;
    private final Function<UUID, String> employeeFactoryLookup;

    public AttendanceAdjustmentService(
            AttendanceAdjustmentRepository adjustmentRepository,
            DailyAttendanceRepository dailyRepository,
            Function<UUID, String> employeeFactoryLookup
    ) {
        this.adjustmentRepository = Objects.requireNonNull(adjustmentRepository, "adjustmentRepository must not be null");
        this.dailyRepository = Objects.requireNonNull(dailyRepository, "dailyRepository must not be null");
        this.employeeFactoryLookup = employeeFactoryLookup;
    }

    public AttendanceAdjustmentService(
            AttendanceAdjustmentRepository adjustmentRepository,
            DailyAttendanceRepository dailyRepository
    ) {
        this(adjustmentRepository, dailyRepository, null);
    }

    @Override
    public Adjustment createAdjustment(ActorContext context, AdjustmentRequest request) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(request, "request must not be null");

        if (context.isAuditor()) {
            throw new SecurityException("Auditor là vai trò chỉ đọc; cấm gửi yêu cầu điều chỉnh công");
        }
        if (request.proposedDirection() != null
                && !"IN".equals(request.proposedDirection())
                && !"OUT".equals(request.proposedDirection())) {
            throw new IllegalArgumentException("proposedDirection phải là IN, OUT hoặc null");
        }

        UUID employeeId = UUID.fromString(context.employeeId() != null ? context.employeeId() : context.userId());
        UUID adjustmentId = UUID.randomUUID();

        AttendanceAdjustment adjustment = new AttendanceAdjustment(
                adjustmentId,
                request.eventId(),
                employeeId,
                UUID.fromString(context.userId()),
                request.proposedOccurredAt(),
                request.proposedDirection(),
                request.reason(),
                Instant.now()
        );

        adjustmentRepository.save(adjustment);
        return adjustment.toContractDto();
    }

    @Override
    public Adjustment decideAdjustment(ActorContext context, UUID adjustmentId, AdjustmentDecision decision) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(adjustmentId, "adjustmentId must not be null");
        Objects.requireNonNull(decision, "decision must not be null");

        // RÀNG BUỘC 1: Auditor là vai trò chỉ đọc; cấm duyệt công (CONTRACT-V1 §3, DECISIONS §8)
        if (context.hasRole(Role.AUDITOR)) {
            throw new SecurityException("Auditor là vai trò chỉ đọc; cấm phê duyệt hoặc từ chối điều chỉnh công");
        }

        // RÀNG BUỘC 2: Kiểm tra quyền attendance.approve
        if (!context.hasPermission("attendance.approve")) {
            throw new SecurityException("Yêu cầu quyền attendance.approve để xử lý điều chỉnh công");
        }

        AttendanceAdjustment adjustment = adjustmentRepository.findAdjustmentById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu điều chỉnh: " + adjustmentId));

        // RÀNG BUỘC 3: Người tạo không được tự duyệt yêu cầu của mình (CONTRACT-V1 §9)
        if (UUID.fromString(context.userId()).equals(adjustment.getCreatedBy())) {
            throw new IllegalStateException("Người tạo không được tự phê duyệt yêu cầu điều chỉnh của chính mình");
        }

        // RÀNG BUỘC 3b: Kiểm tra phạm vi nhà máy của nhân viên đích (SCOPE-10, CONTRACT-V1 §3/§9)
        if (!ActorContext.SCOPE_GLOBAL.equals(context.scope())) {
            if (context.factoryIds().isEmpty()) {
                throw new SecurityException("Tài khoản chưa được gán phạm vi nhà máy để duyệt điều chỉnh công.");
            }
            if (employeeFactoryLookup != null) {
                String targetFactoryId = employeeFactoryLookup.apply(adjustment.getEmployeeId());
                if (targetFactoryId == null || !context.factoryIds().contains(targetFactoryId)) {
                    throw new SecurityException("Không có quyền phê duyệt điều chỉnh công cho nhân viên ngoài nhà máy được gán.");
                }
            }
        }

        // RÀNG BUỘC 4: Chỉ quyết định từ trạng thái PENDING
        if (!"PENDING".equals(adjustment.getState())) {
            throw new IllegalStateException("Yêu cầu điều chỉnh đã được xử lý trước đó với trạng thái: " + adjustment.getState());
        }

        // RÀNG BUỘC 5: Optimistic locking version
        if (adjustment.getVersion() != decision.version()) {
            throw new IllegalStateException("VERSION_CONFLICT: Phiên bản điều chỉnh hiện tại (" + adjustment.getVersion() + ") không khớp với version gửi lên (" + decision.version() + ")");
        }

        Instant now = Instant.now();
        boolean approved = "APPROVE".equalsIgnoreCase(decision.decision());
        adjustment.setState(approved ? "APPROVED" : "REJECTED");
        adjustment.setDecidedBy(UUID.fromString(context.userId()));
        adjustment.setDecidedAt(now);
        adjustment.setDecisionReason(decision.reason());
        adjustment.incrementVersion();

        if (approved) {
            LocalDate workDate = resolveAdjustedWorkDate(adjustment);

            dailyRepository.findByEmployeeIdAndWorkDate(adjustment.getEmployeeId(), workDate)
                    .ifPresent(daily -> {
                        daily.setAdjusted(true);
                        if ("IN".equalsIgnoreCase(adjustment.getProposedDirection()) && adjustment.getProposedOccurredAt() != null) {
                            daily.setFirstIn(adjustment.getProposedOccurredAt());
                        } else if ("OUT".equalsIgnoreCase(adjustment.getProposedDirection()) && adjustment.getProposedOccurredAt() != null) {
                            daily.setLastOut(adjustment.getProposedOccurredAt());
                        }

                        if (daily.getFirstIn() != null && daily.getLastOut() != null) {
                            long minutes = Duration.between(daily.getFirstIn(), daily.getLastOut()).toMinutes();
                            daily.setWorkedMinutes((int) Math.max(0, minutes));
                            daily.setMissingSwipe(false);
                            daily.removeFlag("MISSING_OUT");
                            daily.removeFlag("MISSING_IN");
                        }
                        daily.incrementVersion();
                        dailyRepository.save(daily);
                    });
        }

        AdjustmentHistory history = new AdjustmentHistory(
                "HIST-" + UUID.randomUUID(),
                adjustment.getId().toString(),
                adjustment.getState(),
                context.userId().toString(),
                now,
                decision.reason()
        );

        adjustmentRepository.save(adjustment);
        adjustmentRepository.saveHistory(history);

        return adjustment.toContractDto();
    }

    private static LocalDate resolveAdjustedWorkDate(AttendanceAdjustment adjustment) {
        if (adjustment.getProposedOccurredAt() == null) {
            return LocalDate.now(DEFAULT_FACTORY_ZONE);
        }
        ZonedDateTime zdt = adjustment.getProposedOccurredAt().atZone(DEFAULT_FACTORY_ZONE);
        if (adjustment.getProposedDirection() != null) {
            boolean isCheckIn = "IN".equalsIgnoreCase(adjustment.getProposedDirection());
            return ShiftDefinition.determineShiftAndWorkDate(zdt, isCheckIn).workDate();
        }
        return zdt.toLocalDate();
    }

    @Override
    public Page<Adjustment> queryAdjustments(ActorContext context, UUID employeeId, String state, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page phải >= 0 và size phải từ 1 đến 100.");
        }
        UUID effectiveEmployeeId = employeeId;
        if (context != null && ActorContext.SCOPE_SELF.equals(context.scope())) {
            if (context.employeeId() == null) {
                return Page.empty(page, size);
            }
            UUID selfId = UUID.fromString(context.employeeId());
            if (employeeId != null && !selfId.equals(employeeId)) {
                throw new SecurityException("Tài khoản SELF chỉ được xem yêu cầu điều chỉnh của chính mình.");
            }
            effectiveEmployeeId = selfId;
        }

        List<AttendanceAdjustment> list;
        if (effectiveEmployeeId != null) {
            list = adjustmentRepository.findByEmployeeId(effectiveEmployeeId);
            if (state != null) {
                list = list.stream().filter(a -> a.getState().equalsIgnoreCase(state)).toList();
            }
        } else if (state != null) {
            list = adjustmentRepository.findByState(state);
        } else {
            list = adjustmentRepository.findAll();
        }

        int start = Math.min(page * size, list.size());
        int end = Math.min(start + size, list.size());
        List<Adjustment> pagedItems = list.subList(start, end).stream()
                .map(AttendanceAdjustment::toContractDto)
                .toList();

        return new Page<>(pagedItems, page, size, list.size());
    }

    @Override
    public AttendanceAdjustmentView requestAdjustment(AdjustmentRequestCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID adjId = UUID.randomUUID();
        UUID empUuid = parseUuidSafe(command.employeeId());
        UUID creatorUuid = parseUuidSafe(command.requestedBy());

        AttendanceAdjustment adj = new AttendanceAdjustment(
                adjId,
                null,
                empUuid,
                creatorUuid,
                command.requestedTime(),
                command.adjustmentType() != null ? command.adjustmentType().name() : "MODIFY",
                command.reason(),
                Instant.now()
        );
        adjustmentRepository.save(adj);

        return new AttendanceAdjustmentView(
                adj.getId().toString(),
                command.employeeId(),
                command.workDate(),
                command.adjustmentType(),
                command.requestedTime(),
                command.adjustedHours(),
                command.reason(),
                AdjustmentStatus.PENDING,
                command.requestedBy(),
                adj.getCreatedAt(),
                null,
                null,
                null
        );
    }

    @Override
    public AttendanceAdjustmentView reviewAdjustment(AdjustmentReviewCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID adjId = UUID.fromString(command.adjustmentId());
        AttendanceAdjustment adj = adjustmentRepository.findAdjustmentById(adjId)
                .orElseThrow(() -> new IllegalArgumentException("Adjustment not found: " + command.adjustmentId()));

        UUID reviewerUuid = parseUuidSafe(command.reviewerId());
        if (reviewerUuid.equals(adj.getCreatedBy())) {
            throw new IllegalStateException("Người tạo không được tự phê duyệt yêu cầu của chính mình");
        }

        adj.setState(command.approved() ? "APPROVED" : "REJECTED");
        adj.setDecidedBy(reviewerUuid);
        adj.setDecidedAt(Instant.now());
        adj.setDecisionReason(command.reviewNote());
        adj.incrementVersion();
        adjustmentRepository.save(adj);

        return new AttendanceAdjustmentView(
                adj.getId().toString(),
                adj.getEmployeeId().toString(),
                LocalDate.now(DEFAULT_FACTORY_ZONE),
                AdjustmentType.MODIFY_HOURS,
                adj.getProposedOccurredAt(),
                null,
                adj.getReason(),
                command.approved() ? AdjustmentStatus.APPROVED : AdjustmentStatus.REJECTED,
                adj.getCreatedBy().toString(),
                adj.getCreatedAt(),
                command.reviewerId(),
                adj.getDecidedAt(),
                command.reviewNote()
        );
    }

    @Override
    public List<AttendanceAdjustmentView> queryAdjustments(String employeeId, AdjustmentStatus status) {
        UUID empUuid = parseUuidSafe(employeeId);
        return adjustmentRepository.findByEmployeeId(empUuid).stream()
                .filter(a -> status == null || a.getState().equalsIgnoreCase(status.name()))
                .map(a -> new AttendanceAdjustmentView(
                        a.getId().toString(),
                        a.getEmployeeId().toString(),
                        LocalDate.now(DEFAULT_FACTORY_ZONE),
                        AdjustmentType.MODIFY_HOURS,
                        a.getProposedOccurredAt(),
                        null,
                        a.getReason(),
                        AdjustmentStatus.valueOf(a.getState()),
                        a.getCreatedBy().toString(),
                        a.getCreatedAt(),
                        a.getDecidedBy() != null ? a.getDecidedBy().toString() : null,
                        a.getDecidedAt(),
                        a.getDecisionReason()
                ))
                .toList();
    }

    private static UUID parseUuidSafe(String val) {
        if (val == null) return UUID.randomUUID();
        try {
            return UUID.fromString(val);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(val.getBytes());
        }
    }
}
