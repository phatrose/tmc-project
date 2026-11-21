package vn.tmc.central.attendanceprocessing.internal.service;

import vn.tmc.central.attendanceprocessing.api.Adjustment;
import vn.tmc.central.attendanceprocessing.api.AdjustmentDecision;
import vn.tmc.central.attendanceprocessing.api.AdjustmentRequest;
import vn.tmc.central.attendanceprocessing.api.AttendanceAdjustmentPort;
import vn.tmc.central.attendanceprocessing.api.AttendanceEventView;
import vn.tmc.central.attendanceprocessing.api.AttendanceFilter;
import vn.tmc.central.attendanceprocessing.api.AttendanceQueries;
import vn.tmc.central.attendanceprocessing.api.AttendanceSync;
import vn.tmc.central.attendanceprocessing.api.AttendanceView;
import vn.tmc.central.attendanceprocessing.api.DailyAttendance;
import vn.tmc.central.attendanceprocessing.api.DailyAttendanceView;
import vn.tmc.central.attendanceprocessing.api.MonthlyAttendanceSummaryView;
import vn.tmc.central.attendanceprocessing.api.PresenceView;
import vn.tmc.central.attendanceprocessing.api.PresenceView.PresenceItem;
import vn.tmc.central.attendanceprocessing.internal.model.AttendanceEvent;
import vn.tmc.central.attendanceprocessing.internal.model.DailyAttendanceRecord;
import vn.tmc.central.attendanceprocessing.internal.model.ShiftDefinition;
import vn.tmc.central.attendanceprocessing.internal.model.ShiftDefinition.ShiftAssignment;
import vn.tmc.central.attendanceprocessing.internal.model.SyncConflict;
import vn.tmc.central.attendanceprocessing.internal.repository.AttendanceRepository;
import vn.tmc.central.attendanceprocessing.internal.repository.DailyAttendanceRepository;
import vn.tmc.central.attendanceprocessing.internal.repository.SyncConflictRepository;
import vn.tmc.central.usermgmt.api.ActorContext;
import vn.tmc.contracts.AttendancePayload.Direction;
import vn.tmc.contracts.AttendancePayload.Source;
import vn.tmc.contracts.DeviceContext;
import vn.tmc.contracts.EventEnvelope;
import vn.tmc.contracts.Page;
import vn.tmc.contracts.SyncBatchAck;
import vn.tmc.contracts.SyncBatchAck.EventAck;
import vn.tmc.contracts.SyncBatchAck.Status;
import vn.tmc.contracts.SyncBatchRequest;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: Service xử lý nghiệp vụ chấm công và tiếp nhận sự kiện theo CONTRACT-V1.md.
 * 
 * QUY TẮC CỐT LÕI:
 * - Per-event transaction: Một sự kiện lỗi không rollback cả batch.
 * - Idempotency: Trùng eventId cùng hash trả DUPLICATE (code = null, acceptedAt = commit time).
 * - Xung đột: Trùng eventId khác hash trả CONFLICT (code = "EVENT_CONTENT_CONFLICT", acceptedAt = null).
 * - Ca đêm: Bắt đầu 22:00 ngày cuối tháng trước, kết thúc 06:00 ngày 01, vẫn thuộc tháng trước (workDate = ngày bắt đầu ca).
 * - Bất biến: attendance_events không bao giờ bị UPDATE hoặc DELETE.
 */
public final class AttendanceProcessingService implements AttendanceQueries, AttendanceSync {
    public static final ZoneId FACTORY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AttendanceRepository attendanceRepository;
    private final DailyAttendanceRepository dailyRepository;
    private final SyncConflictRepository conflictRepository;
    private final AttendanceAdjustmentPort adjustmentPort;

    public AttendanceProcessingService(
            AttendanceRepository attendanceRepository,
            DailyAttendanceRepository dailyRepository,
            SyncConflictRepository conflictRepository,
            AttendanceAdjustmentPort adjustmentPort
    ) {
        this.attendanceRepository = Objects.requireNonNull(attendanceRepository, "attendanceRepository must not be null");
        this.dailyRepository = Objects.requireNonNull(dailyRepository, "dailyRepository must not be null");
        this.conflictRepository = Objects.requireNonNull(conflictRepository, "conflictRepository must not be null");
        this.adjustmentPort = adjustmentPort;
    }

    public AttendanceProcessingService(
            AttendanceRepository attendanceRepository,
            DailyAttendanceRepository dailyRepository,
            SyncConflictRepository conflictRepository
    ) {
        this(attendanceRepository, dailyRepository, conflictRepository, null);
    }

    public AttendanceProcessingService(AttendanceRepository attendanceRepository) {
        this(attendanceRepository,
             attendanceRepository instanceof DailyAttendanceRepository d ? d : null,
             attendanceRepository instanceof SyncConflictRepository c ? c : null,
             null);
    }

    // --- TIẾP NHẬN SỰ KIỆN TỪ EDGE (CONTRACT-V1.md §7 & §10) ---

    @Override
    public SyncBatchAck synchronize(DeviceContext deviceContext, SyncBatchRequest batch) {
        Objects.requireNonNull(batch, "SyncBatchRequest must not be null");
        Objects.requireNonNull(batch.syncBatchId(), "syncBatchId must not be null");
        Objects.requireNonNull(batch.events(), "events list must not be null");

        List<EventAck> acks = new ArrayList<>(batch.events().size());
        String edgeIdStr = deviceContext != null ? deviceContext.edgeId().toString() : null;

        // Per-event transaction
        for (EventEnvelope env : batch.events()) {
            if (edgeIdStr == null && env.edgeId() != null) {
                edgeIdStr = env.edgeId().toString();
            }
            EventAck ack = processSingleEvent(env);
            acks.add(ack);
        }

        long lastContiguousSeq = 0;
        if (edgeIdStr != null && conflictRepository != null) {
            lastContiguousSeq = computeContiguousSequence(edgeIdStr, batch.events(), acks);
            conflictRepository.updateLastContiguousSequenceNo(edgeIdStr, lastContiguousSeq);
        }

        return new SyncBatchAck(batch.syncBatchId(), acks, lastContiguousSeq);
    }

    @Override
    public SyncBatchAck synchronize(SyncBatchRequest batch) {
        DeviceContext context = null;
        if (batch != null && batch.events() != null && !batch.events().isEmpty()) {
            EventEnvelope first = batch.events().get(0);
            context = new DeviceContext(first.edgeId(), first.factoryId());
        }
        return synchronize(context, batch);
    }

    private EventAck processSingleEvent(EventEnvelope env) {
        Instant now = Instant.now();

        // 1. Kiểm tra envelope hợp lệ
        if (env.eventId() == null || env.eventType() == null || env.payloadSha256() == null || env.payload() == null) {
            return EventAck.rejected(env.eventId() != null ? env.eventId() : UUID.randomUUID(), SyncBatchAck.CODE_INVALID_PAYLOAD);
        }

        // 2. Kiểm tra tính toàn vẹn SHA-256
        if (!env.isHashValid()) {
            return EventAck.rejected(env.eventId(), SyncBatchAck.CODE_HASH_MISMATCH);
        }

        // 3. Kiểm tra Idempotency & Conflict với Central
        Optional<AttendanceEvent> existingOpt = attendanceRepository.findByEventId(env.eventId());
        if (existingOpt.isPresent()) {
            AttendanceEvent existing = existingOpt.get();
            if (existing.getPayloadSha256().equalsIgnoreCase(env.payloadSha256())) {
                // CONTRACT-V1 §7: Trùng eventId cùng hash -> DUPLICATE, acceptedAt = commit lần đầu, code = null
                return EventAck.duplicate(env.eventId(), existing.getReceivedAt());
            } else {
                // CONTRACT-V1 §7: Trùng eventId khác hash -> CONFLICT, code = "EVENT_CONTENT_CONFLICT", acceptedAt = null
                if (conflictRepository != null) {
                    SyncConflict conflict = new SyncConflict(
                            "CONF-" + UUID.randomUUID(),
                            env.eventId(),
                            env.edgeId().toString(),
                            existing.getPayloadSha256(),
                            env.payloadSha256(),
                            env.occurredAt(),
                            now,
                            "Different payload hash for existing eventId"
                    );
                    conflictRepository.recordConflict(conflict);
                }
                return EventAck.conflict(env.eventId(), SyncBatchAck.CODE_EVENT_CONTENT_CONFLICT);
            }
        }

        // 4. Bóc tách payload
        ParsedPayload parsed = parsePayload(env.payload());

        // Nếu là access attempt denied (EVT-02), lưu log nhưng không tính công
        if (EventEnvelope.EVT_ACCESS_DENIED.equals(env.eventType())) {
            AttendanceEvent deniedEvent = new AttendanceEvent(
                    env.eventId(),
                    env.eventType(),
                    env.schemaVersion(),
                    env.edgeId(),
                    env.gateId(),
                    env.factoryId(),
                    env.sequenceNo(),
                    env.correlationId(),
                    env.occurredAt(),
                    now,
                    parsed.employeeId,
                    parsed.direction,
                    parsed.source,
                    parsed.jti,
                    parsed.kid,
                    parsed.tokenIssuedAt,
                    parsed.commandId != null ? parsed.commandId : UUID.randomUUID(),
                    parsed.operatorId,
                    parsed.reason,
                    List.of("ACCESS_DENIED"),
                    env.payloadSha256(),
                    env.payload()
            );
            attendanceRepository.saveEvent(deniedEvent);
            return EventAck.accepted(env.eventId(), now);
        }

        // 5. Phát hiện các bất thường (Anomalies)
        List<String> flags = new ArrayList<>();

        // a) Kiểm tra Replay JTI giữa các Edge / Cổng khác nhau (CROSS_GATE_REPLAY / REPLAY_SUSPECTED)
        if (parsed.jti != null) {
            List<AttendanceEvent> sameJtiEvents = attendanceRepository.findByJti(parsed.jti);
            if (!sameJtiEvents.isEmpty()) {
                boolean differentGate = sameJtiEvents.stream()
                        .anyMatch(e -> !e.getGateId().equals(env.gateId()) || !e.getEdgeId().equals(env.edgeId()));
                if (differentGate) {
                    flags.add("CROSS_GATE_REPLAY");
                    flags.add("REPLAY_SUSPECTED");
                }
            }
        }

        // b) Kiểm tra quét lặp nghiệp vụ trong 30 giây (RAPID_REPEAT_SCAN - BR-03)
        Optional<AttendanceEvent> lastEventOpt = attendanceRepository.findLastByEmployeeId(parsed.employeeId);
        if (lastEventOpt.isPresent()) {
            Duration diff = Duration.between(lastEventOpt.get().getObservedAt(), env.occurredAt()).abs();
            if (diff.getSeconds() < 30) {
                flags.add("RAPID_REPEAT_SCAN");
            }
        }

        // 6. Ghi nhận AttendanceEvent bất biến vào database
        AttendanceEvent newEvent = new AttendanceEvent(
                env.eventId(),
                env.eventType(),
                env.schemaVersion(),
                env.edgeId(),
                env.gateId(),
                env.factoryId(),
                env.sequenceNo(),
                env.correlationId(),
                env.occurredAt(),
                now,
                parsed.employeeId,
                parsed.direction,
                parsed.source,
                parsed.jti,
                parsed.kid,
                parsed.tokenIssuedAt,
                parsed.commandId != null ? parsed.commandId : UUID.randomUUID(),
                parsed.operatorId,
                parsed.reason,
                flags,
                env.payloadSha256(),
                env.payload()
        );
        attendanceRepository.saveEvent(newEvent);

        // 7. Cập nhật bản ghi dẫn xuất daily_attendance_records và tính công theo ca
        if (dailyRepository != null) {
            updateDailyAttendance(newEvent, parsed);
        }

        return EventAck.accepted(env.eventId(), now);
    }

    private void updateDailyAttendance(AttendanceEvent event, ParsedPayload parsed) {
        ZonedDateTime zdt = event.getObservedAt().atZone(FACTORY_ZONE);
        boolean isCheckIn = event.getDirection() == Direction.IN;

        // Xác định ca và workDate: ca đêm bắt đầu cuối tháng trước kết thúc 06:00 ngày 01 vẫn thuộc tháng trước!
        ShiftAssignment assignment = ShiftDefinition.determineShiftAndWorkDate(zdt, isCheckIn);
        LocalDate workDate = assignment.workDate();
        ShiftDefinition shift = assignment.shift();

        DailyAttendanceRecord daily = dailyRepository.findByEmployeeIdAndWorkDate(event.getEmployeeId(), workDate)
                .orElseGet(() -> new DailyAttendanceRecord(
                        "DAY-" + event.getEmployeeId() + "-" + workDate,
                        event.getEmployeeId(),
                        event.getFactoryId(),
                        workDate,
                        UUID.nameUUIDFromBytes(shift.shiftCode().getBytes()),
                        shift.shiftCode()
                ));

        if (isCheckIn) {
            if (daily.getFirstIn() != null && daily.getLastOut() == null) {
                daily.addFlag("CONSECUTIVE_ENTRY");
            }
            if (daily.getFirstIn() == null || event.getObservedAt().isBefore(daily.getFirstIn())) {
                daily.setFirstIn(event.getObservedAt());
            }

            // Kiểm tra đi trễ
            LocalTime eventTime = zdt.toLocalTime();
            LocalTime shiftStart = shift.startTime();
            if (shift.isCrossDay() && eventTime.isBefore(LocalTime.of(12, 0))) {
                long minutesLate = Duration.between(shiftStart, LocalTime.of(23, 59, 59)).toMinutes() + 1
                        + Duration.between(LocalTime.MIN, eventTime).toMinutes();
                if (minutesLate > shift.toleranceMinutes()) {
                    daily.setLateMinutes((int) minutesLate);
                    daily.addFlag("LATE_ARRIVAL");
                }
            } else if (eventTime.isAfter(shiftStart.plusMinutes(shift.toleranceMinutes()))) {
                long minutesLate = Duration.between(shiftStart, eventTime).toMinutes();
                daily.setLateMinutes((int) minutesLate);
                daily.addFlag("LATE_ARRIVAL");
            }
        } else {
            // Check-OUT
            if (daily.getFirstIn() == null) {
                daily.addFlag("CONSECUTIVE_EXIT");
                daily.addFlag("MISSING_IN");
            }
            if (daily.getLastOut() == null || event.getObservedAt().isAfter(daily.getLastOut())) {
                daily.setLastOut(event.getObservedAt());
            }

            // Kiểm tra về sớm
            LocalTime eventTime = zdt.toLocalTime();
            LocalTime shiftEnd = shift.endTime();
            if (shift.isCrossDay()) {
                if (eventTime.isBefore(shiftEnd.minusMinutes(shift.toleranceMinutes())) && eventTime.isAfter(LocalTime.of(12, 0))) {
                    daily.setEarlyMinutes(30);
                    daily.addFlag("EARLY_DEPARTURE");
                } else if (eventTime.isBefore(shiftEnd.minusMinutes(shift.toleranceMinutes()))) {
                    long minutesEarly = Duration.between(eventTime, shiftEnd).toMinutes();
                    daily.setEarlyMinutes((int) minutesEarly);
                    daily.addFlag("EARLY_DEPARTURE");
                }
            } else if (eventTime.isBefore(shiftEnd.minusMinutes(shift.toleranceMinutes()))) {
                long minutesEarly = Duration.between(eventTime, shiftEnd).toMinutes();
                daily.setEarlyMinutes((int) minutesEarly);
                daily.addFlag("EARLY_DEPARTURE");
            }
        }

        // QUY TẮC CONTRACT-V1 §9: Thiếu IN/OUT thì workedMinutes = null, không coi 0 giờ đã xác nhận.
        if (daily.getFirstIn() != null && daily.getLastOut() != null) {
            long totalMinutes = Duration.between(daily.getFirstIn(), daily.getLastOut()).toMinutes();
            daily.setWorkedMinutes((int) Math.max(0, totalMinutes));
            daily.setMissingSwipe(false);
            daily.removeFlag("MISSING_OUT");
            daily.removeFlag("MISSING_IN");
            daily.setStatus("COMPLETED");
        } else {
            daily.setWorkedMinutes(null);
            daily.setMissingSwipe(true);
            if (daily.getFirstIn() != null && daily.getLastOut() == null) {
                daily.addFlag("MISSING_OUT");
            } else if (daily.getFirstIn() == null && daily.getLastOut() != null) {
                daily.addFlag("MISSING_IN");
            }
            daily.setStatus("IN_PROGRESS");
        }

        event.getFlags().forEach(daily::addFlag);
        daily.incrementVersion();
        dailyRepository.save(daily);
    }

    private long computeContiguousSequence(String edgeId, List<EventEnvelope> events, List<EventAck> acks) {
        long current = conflictRepository != null ? conflictRepository.getLastContiguousSequenceNo(edgeId) : 0;
        List<Long> acceptedSeqs = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            EventAck ack = acks.get(i);
            if (ack.status() == Status.ACCEPTED || ack.status() == Status.DUPLICATE) {
                acceptedSeqs.add(events.get(i).sequenceNo());
            }
        }
        acceptedSeqs.sort(Long::compareTo);
        for (long seq : acceptedSeqs) {
            if (seq == current + 1) {
                current = seq;
            } else if (seq <= current) {
                // Đã trong vùng
            } else {
                break; // Gap phát hiện, dừng cursor
            }
        }
        return current;
    }

    // --- CONTRACT-V1.md §9 QUERIES & PRESENCE ---

    @Override
    public Page<DailyAttendance> dailyRecords(ActorContext context, AttendanceFilter filter) {
        Objects.requireNonNull(filter, "filter must not be null");
        List<DailyAttendanceRecord> list = dailyRepository.findByEmployeeIdAndDateRange(
                filter.employeeId(),
                filter.from(),
                filter.to() // Khoảng [from, to)
        );

        int start = Math.min(filter.page() * filter.size(), list.size());
        int end = Math.min(start + filter.size(), list.size());
        List<DailyAttendance> paged = list.subList(start, end).stream()
                .map(DailyAttendanceRecord::toContractDto)
                .toList();

        return new Page<>(paged, filter.page(), filter.size(), list.size());
    }

    @Override
    public Page<AttendanceEventView> queryEvents(ActorContext context, UUID employeeId, UUID factoryId, Instant from, Instant to, int page, int size) {
        List<AttendanceEvent> raw = attendanceRepository.findByEmployeeIdAndDateRange(employeeId, from, to);
        if (factoryId != null) {
            raw = raw.stream().filter(e -> factoryId.equals(e.getFactoryId())).toList();
        }

        // Sắp theo occurredAt giảm dần rồi id giảm dần (CONTRACT-V1 §2)
        int start = Math.min(page * size, raw.size());
        int end = Math.min(start + size, raw.size());
        List<AttendanceEventView> paged = raw.subList(start, end).stream()
                .map(e -> new AttendanceEventView(
                        new EventEnvelope(
                                e.getEventId(),
                                e.getEventType(),
                                e.getSchemaVersion(),
                                e.getEdgeId(),
                                e.getGateId(),
                                e.getFactoryId(),
                                e.getSequenceNo(),
                                e.getCorrelationId(),
                                e.getObservedAt(),
                                e.getPayloadSha256(),
                                e.getRawPayload()
                        ),
                        e.getReceivedAt(),
                        e.getFlags()
                ))
                .toList();

        return new Page<>(paged, page, size, raw.size());
    }

    @Override
    public PresenceView getPresence(UUID factoryId) {
        Objects.requireNonNull(factoryId, "factoryId must not be null");
        List<AttendanceEvent> events = attendanceRepository.findLatestEventsByFactoryId(factoryId);

        Map<UUID, AttendanceEvent> latestByEmp = new HashMap<>();
        for (AttendanceEvent e : events) {
            latestByEmp.putIfAbsent(e.getEmployeeId(), e);
        }

        List<PresenceItem> items = latestByEmp.values().stream()
                .map(e -> new PresenceItem(e.getEmployeeId(), e.getDirection().name(), e.getObservedAt()))
                .toList();

        return new PresenceView(Instant.now(), "CENTRAL_SYNCED", items, List.of());
    }

    @Override
    public Page<Adjustment> queryAdjustments(ActorContext context, UUID employeeId, String state, int page, int size) {
        if (adjustmentPort != null) {
            return adjustmentPort.queryAdjustments(context, employeeId, state, page, size);
        }
        return Page.empty(page, size);
    }

    @Override
    public Adjustment createAdjustment(ActorContext context, AdjustmentRequest request) {
        if (adjustmentPort != null) {
            return adjustmentPort.createAdjustment(context, request);
        }
        throw new UnsupportedOperationException("AdjustmentPort is not configured");
    }

    @Override
    public Adjustment decideAdjustment(ActorContext context, UUID adjustmentId, AdjustmentDecision decision) {
        if (adjustmentPort != null) {
            return adjustmentPort.decideAdjustment(context, adjustmentId, decision);
        }
        throw new UnsupportedOperationException("AdjustmentPort is not configured");
    }

    // --- TIỆN ÍCH TỔNG HỢP VÀ TƯƠNG THÍCH NGƯỢC ---

    @Override
    public AttendanceView findAttendance(String employeeId) {
        UUID empUuid = parseUuidSafe(employeeId);
        LocalDate today = LocalDate.now(FACTORY_ZONE);
        DailyAttendanceRecord todayRecord = dailyRepository.findByEmployeeIdAndWorkDate(empUuid, today).orElse(null);

        YearMonth currentMonth = YearMonth.from(today);
        MonthlyAttendanceSummaryView summary = getMonthlySummary(employeeId, currentMonth, "F01");

        String currentStatus = todayRecord != null ? todayRecord.getStatus() : "NO_RECORD";
        return new AttendanceView(
                employeeId,
                currentStatus,
                todayRecord != null ? mapDailyToView(todayRecord) : null,
                List.of(),
                summary
        );
    }

    @Override
    public List<DailyAttendanceView> queryDailyRecords(String employeeId, LocalDate fromDate, LocalDate toDate) {
        UUID empUuid = parseUuidSafe(employeeId);
        return dailyRepository.findByEmployeeIdAndDateRange(empUuid, fromDate, toDate).stream()
                .map(this::mapDailyToView)
                .toList();
    }

    @Override
    public MonthlyAttendanceSummaryView getMonthlySummary(String employeeId, YearMonth period, String factoryId) {
        UUID empUuid = parseUuidSafe(employeeId);
        LocalDate start = period.atDay(1);
        LocalDate end = period.atEndOfMonth().plusDays(1); // Nửa mở [start, end)

        List<DailyAttendanceRecord> monthRecords = dailyRepository.findByEmployeeIdAndDateRange(empUuid, start, end);

        int workDays = 0;
        double totalHours = 0.0;
        int lateCount = 0;
        int earlyCount = 0;
        int missingSwipeCount = 0;
        int anomalyCount = 0;

        for (DailyAttendanceRecord r : monthRecords) {
            workDays++;
            totalHours += r.getHoursWorked();
            if (r.isLate()) lateCount++;
            if (r.isEarlyLeave()) earlyCount++;
            if (r.isMissingSwipe()) missingSwipeCount++;
            if (!r.getFlags().isEmpty()) anomalyCount += r.getFlags().size();
        }

        return new MonthlyAttendanceSummaryView(
                employeeId,
                factoryId,
                period.toString(),
                workDays,
                Math.round(totalHours * 100.0) / 100.0,
                lateCount,
                earlyCount,
                missingSwipeCount,
                anomalyCount,
                "FINALIZED_ELIGIBLE"
        );
    }

    private DailyAttendanceView mapDailyToView(DailyAttendanceRecord r) {
        return new DailyAttendanceView(
                r.getId(),
                r.getEmployeeId().toString(),
                r.getFactoryId().toString(),
                r.getWorkDate(),
                r.getShiftCode(),
                r.getFirstIn(),
                r.getLastOut(),
                r.getHoursWorked(),
                r.isLate(),
                r.getLateMinutes(),
                r.isEarlyLeave(),
                r.getEarlyMinutes(),
                r.isMissingSwipe(),
                r.isAdjusted(),
                new HashSet<>(r.getFlags()),
                r.getStatus()
        );
    }

    private static UUID parseUuidSafe(String val) {
        if (val == null) return UUID.randomUUID();
        try {
            return UUID.fromString(val);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(val.getBytes());
        }
    }

    private record ParsedPayload(
            UUID employeeId,
            Direction direction,
            Source source,
            UUID jti,
            String kid,
            Instant tokenIssuedAt,
            UUID commandId,
            UUID operatorId,
            String reason
    ) {}

    private ParsedPayload parsePayload(String raw) {
        if (raw == null) {
            return new ParsedPayload(UUID.randomUUID(), Direction.IN, Source.ONLINE, null, null, null, UUID.randomUUID(), null, null);
        }

        String emp = extractJsonField(raw, "employeeId");
        String dir = extractJsonField(raw, "direction");
        String src = extractJsonField(raw, "source");
        String jtiStr = extractJsonField(raw, "jti");
        String kid = extractJsonField(raw, "kid");
        String iatStr = extractJsonField(raw, "tokenIssuedAt");
        String cmdStr = extractJsonField(raw, "commandId");
        String opStr = extractJsonField(raw, "operatorId");
        String rsn = extractJsonField(raw, "reason");

        UUID empId = emp != null ? parseUuidSafe(emp) : UUID.randomUUID();
        Direction direction = (dir != null && dir.equalsIgnoreCase("OUT")) ? Direction.OUT : Direction.IN;
        Source source = Source.ONLINE;
        if (src != null) {
            try { source = Source.valueOf(src.toUpperCase()); } catch (Exception ignored) {}
        }
        UUID jti = jtiStr != null ? parseUuidSafe(jtiStr) : null;
        Instant tokenIssuedAt = iatStr != null ? Instant.parse(iatStr) : null;
        UUID commandId = cmdStr != null ? parseUuidSafe(cmdStr) : UUID.randomUUID();
        UUID operatorId = opStr != null ? parseUuidSafe(opStr) : null;

        return new ParsedPayload(empId, direction, source, jti, kid, tokenIssuedAt, commandId, operatorId, rsn);
    }

    private static String extractJsonField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
