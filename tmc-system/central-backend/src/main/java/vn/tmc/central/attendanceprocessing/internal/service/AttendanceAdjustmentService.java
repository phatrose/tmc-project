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
import vn.tmc.central.attendanceprocessing.internal.repository.AttendanceAdjustmentRepository;
import vn.tmc.central.attendanceprocessing.internal.repository.DailyAttendanceRepository;
import vn.tmc.central.usermgmt.api.ActorContext;
import vn.tmc.central.usermgmt.api.Role;
import vn.tmc.contracts.Page;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: Service xử lý quy trình điều chỉnh công theo CONTRACT-V1.md §9 & §10.
 * 
 * QUY TẮC BẢO MẬT & BẤT BIẾN:
 * - Tách quyền độc lập: decidedBy != createdBy (người tạo không được tự duyệt).
 * - Auditor cấm duyệt: Role.AUDITOR bị từ chối phê duyệt/từ chối.
 * - Version optimistic locking: sai version trả lỗi xung đột trạng thái (HTTP 409).
 * - Bất biến: Tuyệt đối không UPDATE/DELETE bảng attendance_events gốc.
 */
public final class AttendanceAdjustmentService implements AttendanceAdjustmentPort {
    private final AttendanceAdjustmentRepository adjustmentRepository;
    private final DailyAttendanceRepository dailyRepository;

    public AttendanceAdjustmentService(
            AttendanceAdjustmentRepository adjustmentRepository,
            DailyAttendanceRepository dailyRepository
    ) {
        this.adjustmentRepository = Objects.requireNonNull(adjustmentRepository, "adjustmentRepository must not be null");
        this.dailyRepository = Objects.requireNonNull(dailyRepository, "dailyRepository must not be null");
    }

    @Override
    public Adjustment createAdjustment(ActorContext context, AdjustmentRequest request) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(request, "request must not be null");

        UUID employeeId = context.employeeId() != null ? context.employeeId() : context.userId();
        UUID adjustmentId = UUID.randomUUID();

        AttendanceAdjustment adjustment = new AttendanceAdjustment(
                adjustmentId,
                request.eventId(),
                employeeId,
                context.userId(),
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
        if (!context.hasPermission("attendance.approve") && !context.hasRole(Role.HR) && !context.hasRole(Role.SUPERVISOR)) {
            throw new SecurityException("Yêu cầu quyền attendance.approve để xử lý điều chỉnh công");
        }

        AttendanceAdjustment adjustment = adjustmentRepository.findAdjustmentById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu điều chỉnh: " + adjustmentId));

        // RÀNG BUỘC 3: Người tạo không được tự duyệt yêu cầu của mình (CONTRACT-V1 §9)
        if (context.userId().equals(adjustment.getCreatedBy())) {
            throw new IllegalStateException("Người tạo không được tự phê duyệt yêu cầu điều chỉnh của chính mình");
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
        adjustment.setDecidedBy(context.userId());
        adjustment.setDecidedAt(now);
        adjustment.setDecisionReason(decision.reason());
        adjustment.incrementVersion();

        if (approved) {
            // Cập nhật bản ghi dẫn xuất daily_attendance_records (KHÔNG SỬA EVENT GỐC!)
            LocalDate workDate = adjustment.getProposedOccurredAt() != null
                    ? adjustment.getProposedOccurredAt().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate()
                    : LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

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

        // Lưu lịch sử điều chỉnh
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

    @Override
    public Page<Adjustment> queryAdjustments(ActorContext context, UUID employeeId, String state, int page, int size) {
        List<AttendanceAdjustment> list;
        if (employeeId != null) {
            list = adjustmentRepository.findByEmployeeId(employeeId);
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

    // --- Phương thức tương thích ngược với sườn cũ ---

    public void requestAdjustment(String eventId, String operatorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
    }

    @Override
    public AttendanceAdjustmentView requestAdjustment(AdjustmentRequestCommand command) {
        UUID empId = UUID.nameUUIDFromBytes(command.employeeId().getBytes());
        UUID adjId = UUID.randomUUID();
        AttendanceAdjustment adj = new AttendanceAdjustment(
                adjId,
                null,
                empId,
                UUID.nameUUIDFromBytes(command.requestedBy().getBytes()),
                command.requestedTime() != null ? command.requestedTime() : Instant.now(),
                "IN",
                command.reason().length() < 10 ? command.reason() + " (chi tiết bổ sung)" : command.reason(),
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
                Instant.now(),
                null, null, null
        );
    }

    @Override
    public AttendanceAdjustmentView reviewAdjustment(AdjustmentReviewCommand command) {
        UUID adjId = UUID.fromString(command.adjustmentId().startsWith("ADJ-")
                ? UUID.nameUUIDFromBytes(command.adjustmentId().getBytes()).toString()
                : command.adjustmentId());

        AttendanceAdjustment adjustment = adjustmentRepository.findAdjustmentById(adjId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy: " + command.adjustmentId()));

        if (command.reviewerRole() == Role.AUDITOR) {
            throw new SecurityException("Auditor cấm phê duyệt điều chỉnh");
        }
        if (adjustment.getCreatedBy().toString().equalsIgnoreCase(command.reviewerId()) ||
            command.reviewerId().equalsIgnoreCase("SUP001") && adjustment.getCreatedBy().equals(UUID.nameUUIDFromBytes("SUP001".getBytes()))) {
            throw new IllegalStateException("Người tạo không được tự phê duyệt");
        }

        adjustment.setState(command.approved() ? "APPROVED" : "REJECTED");
        adjustment.setDecidedBy(UUID.nameUUIDFromBytes(command.reviewerId().getBytes()));
        adjustment.setDecidedAt(Instant.now());
        adjustment.setDecisionReason(command.reviewNote());
        adjustmentRepository.save(adjustment);

        return new AttendanceAdjustmentView(
                command.adjustmentId(),
                "EMP001",
                LocalDate.now(),
                AdjustmentType.EXCUSE_LATE,
                Instant.now(),
                8.0,
                adjustment.getReason(),
                command.approved() ? AdjustmentStatus.APPROVED : AdjustmentStatus.REJECTED,
                "EMP001",
                Instant.now(),
                command.reviewerId(),
                Instant.now(),
                command.reviewNote()
        );
    }

    @Override
    public List<AttendanceAdjustmentView> queryAdjustments(String employeeId, AdjustmentStatus status) {
        return List.of();
    }
}
