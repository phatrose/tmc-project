package vn.tmc.central.attendanceprocessing.internal.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PHỤ TRÁCH: Task 3.
 * VAI TRÒ: Service xử lý nghiệp vụ chấm công và tiếp nhận sự kiện theo CONTRACT-V1.md.
 *
 * QUY TẮC CỐT LÕI:
 * - Per-event transaction: Một sự kiện lỗi không rollback cả batch.
 * - Idempotency: Trùng eventId cùng envelope & hash trả DUPLICATE (code = null, acceptedAt = commit time).
 * - Xung đột: Trùng eventId khác hash hoặc metadata trả CONFLICT (code = "EVENT_CONTENT_CONFLICT", acceptedAt = null).
 * - Ca đêm: Bắt đầu 22:00 ngày cuối tháng trước, kết thúc 06:00 ngày 01, vẫn thuộc tháng trước (workDate = ngày bắt đầu ca).
 * - Bất biến: attendance_events không bao giờ bị UPDATE hoặc DELETE.
 */
public final class AttendanceProcessingService implements AttendanceQueries, AttendanceSync {
    public static final ZoneId FACTORY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            EventEnvelope.EVT_ATTENDANCE_RECORDED,
            EventEnvelope.EVT_ACCESS_DENIED,
            EventEnvelope.EVT_COMMAND_RESULT,
            EventEnvelope.EVT_REPLAY_REPORTED
    );

    private static final ObjectMapper STRICT_JSON = new ObjectMapper(
            JsonFactory.builder().configure(StreamReadFeature.STRICT_DUPLICATE_DETECTION, true).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);

    private final AttendanceRepository attendanceRepository;
    private final DailyAttendanceRepository dailyRepository;
    private final SyncConflictRepository conflictRepository;
    private final AttendanceAdjustmentPort adjustmentPort;
    private final Map<String, String> syncBatchFingerprints = new ConcurrentHashMap<>();

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

        if (deviceContext != null) {
            for (EventEnvelope env : batch.events()) {
                if (!deviceContext.edgeId().equals(env.edgeId())
                        || !deviceContext.factoryId().equals(env.factoryId())) {
                    throw new DeviceBindingException("Thiết bị không thuộc phạm vi yêu cầu.");
                }
            }
        }

        String edgeIdStr = deviceContext != null
                ? deviceContext.edgeId().toString()
                : batch.events().getFirst().edgeId().toString();

        // CONTRACT-V1 §7: (edgeId, syncBatchId) phải giữ nguyên thành phần lô khi retry; đổi nội dung trả 409.
        String batchKey = edgeIdStr + "|" + batch.syncBatchId();
        String batchFingerprint = computeBatchFingerprint(batch);
        String previousFingerprint = syncBatchFingerprints.putIfAbsent(batchKey, batchFingerprint);
        if (previousFingerprint != null && !previousFingerprint.equals(batchFingerprint)) {
            throw new IdempotencyConflictException("syncBatchId đã được dùng cho một lô sự kiện khác.");
        }

        List<EventAck> acks = new ArrayList<>(batch.events().size());
        for (EventEnvelope env : batch.events()) {
            EventAck ack = processSingleEvent(env);
            acks.add(ack);
        }

        long lastContiguousSeq = 0;
        if (conflictRepository != null) {
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

        if (env.eventId() == null || env.eventType() == null || env.payloadSha256() == null || env.payload() == null) {
            return EventAck.rejected(env.eventId() != null ? env.eventId() : UUID.randomUUID(), SyncBatchAck.CODE_INVALID_PAYLOAD);
        }

        if (env.schemaVersion() != EventEnvelope.CURRENT_SCHEMA_VERSION) {
            return EventAck.rejected(env.eventId(), SyncBatchAck.CODE_UNSUPPORTED_SCHEMA);
        }

        if (!SUPPORTED_EVENT_TYPES.contains(env.eventType())) {
            return EventAck.rejected(env.eventId(), SyncBatchAck.CODE_UNSUPPORTED_EVENT_TYPE);
        }

        if (!env.isHashValid()) {
            return EventAck.rejected(env.eventId(), SyncBatchAck.CODE_HASH_MISMATCH);
        }

        // 3. Kiểm tra Idempotency & Conflict toàn bộ envelope bất biến
        Optional<AttendanceEvent> existingOpt = attendanceRepository.findByEventId(env.eventId());
        if (existingOpt.isPresent()) {
            AttendanceEvent existing = existingOpt.get();
            if (isSameImmutableEnvelope(existing, env)) {
                return EventAck.duplicate(env.eventId(), existing.getReceivedAt());
            }
            recordConflict(env, existing.getPayloadSha256(), now,
                    "Different payload or immutable envelope metadata for existing eventId");
            return EventAck.conflict(env.eventId(), SyncBatchAck.CODE_EVENT_CONTENT_CONFLICT);
        }

        // 4. Kiểm tra trùng sequence theo edgeId cho eventId khác
        Optional<AttendanceEvent> seqConflictOpt = attendanceRepository.findByEdgeIdAndSequenceNo(env.edgeId(), env.sequenceNo());
        if (seqConflictOpt.isPresent() && !seqConflictOpt.get().getEventId().equals(env.eventId())) {
            recordConflict(env, seqConflictOpt.get().getPayloadSha256(), now,
                    "Sequence number already used by another eventId for edge");
            return EventAck.conflict(env.eventId(), SyncBatchAck.CODE_SEQUENCE_CONFLICT);
        }

        // 5. Parse & validate payload JSON nghiêm ngặt (ví dụ direction=SIDEWAYS phải bị REJECTED INVALID_PAYLOAD)
        ParsedPayload parsed;
        try {
            parsed = parseAndValidatePayload(env.eventType(), env.payload());
        } catch (IllegalArgumentException invalid) {
            return EventAck.rejected(env.eventId(), SyncBatchAck.CODE_INVALID_PAYLOAD);
        }

        if (!EventEnvelope.EVT_ATTENDANCE_RECORDED.equals(env.eventType())) {
            List<String> nonAttendanceFlags = EventEnvelope.EVT_ACCESS_DENIED.equals(env.eventType())
                    ? List.of("ACCESS_DENIED")
                    : List.of();
            AttendanceEvent auxiliaryEvent = new AttendanceEvent(
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
                    parsed.commandId,
                    parsed.operatorId,
                    parsed.reason,
                    nonAttendanceFlags,
                    env.payloadSha256(),
                    env.payload()
            );
            attendanceRepository.saveEvent(auxiliaryEvent);
            return EventAck.accepted(env.eventId(), now);
        }

        // 6. Phát hiện bất thường (Anomalies) cho attendance.event.recorded
        List<String> flags = new ArrayList<>();

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

        Optional<AttendanceEvent> lastEventOpt = attendanceRepository.findLastByEmployeeId(parsed.employeeId);
        if (lastEventOpt.isPresent()) {
            Duration diff = Duration.between(lastEventOpt.get().getObservedAt(), env.occurredAt()).abs();
            if (diff.getSeconds() < 30) {
                flags.add("RAPID_REPEAT_SCAN");
            }
        }

        // 7. Ghi nhận AttendanceEvent bất biến
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

        if (dailyRepository != null) {
            updateDailyAttendance(newEvent);
        }

        return EventAck.accepted(env.eventId(), now);
    }

    private void recordConflict(EventEnvelope env, String existingHash, Instant now, String details) {
        if (conflictRepository == null) {
            return;
        }
        conflictRepository.recordConflict(new SyncConflict(
                "CONF-" + UUID.randomUUID(),
                env.eventId(),
                env.edgeId().toString(),
                existingHash,
                env.payloadSha256(),
                env.occurredAt(),
                now,
                details
        ));
    }

    private static boolean isSameImmutableEnvelope(AttendanceEvent existing, EventEnvelope env) {
        return existing.getPayloadSha256().equalsIgnoreCase(env.payloadSha256())
                && Objects.equals(existing.getEventType(), env.eventType())
                && existing.getSchemaVersion() == env.schemaVersion()
                && Objects.equals(existing.getEdgeId(), env.edgeId())
                && Objects.equals(existing.getGateId(), env.gateId())
                && Objects.equals(existing.getFactoryId(), env.factoryId())
                && existing.getSequenceNo() == env.sequenceNo()
                && Objects.equals(existing.getCorrelationId(), env.correlationId())
                && Objects.equals(existing.getObservedAt(), env.occurredAt());
    }

    private static String computeBatchFingerprint(SyncBatchRequest batch) {
        StringBuilder sb = new StringBuilder();
        for (EventEnvelope env : batch.events()) {
            sb.append(env.eventId()).append('|')
                    .append(env.sequenceNo()).append('|')
                    .append(env.payloadSha256()).append('\n');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private void updateDailyAttendance(AttendanceEvent event) {
        ZonedDateTime zdt = event.getObservedAt().atZone(FACTORY_ZONE);
        boolean isCheckIn = event.getDirection() == Direction.IN;

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
            if (daily.getFirstIn() == null) {
                daily.addFlag("CONSECUTIVE_EXIT");
                daily.addFlag("MISSING_IN");
            }
            if (daily.getLastOut() == null || event.getObservedAt().isAfter(daily.getLastOut())) {
                daily.setLastOut(event.getObservedAt());
            }

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
            } else if (seq > current + 1) {
                break;
            }
        }
        return current;
    }

    // --- CONTRACT-V1.md §9 QUERIES & PRESENCE ---

    @Override
    public Page<DailyAttendance> dailyRecords(ActorContext context, AttendanceFilter filter) {
        Objects.requireNonNull(filter, "filter must not be null");
        UUID scopedEmployeeId = enforceEmployeeScope(context, filter.employeeId());
        enforceFactoryScope(context, filter.factoryId());

        List<DailyAttendanceRecord> list = dailyRepository.findByEmployeeIdAndDateRange(
                scopedEmployeeId,
                filter.from(),
                filter.to()
        ).stream()
                .filter(r -> filter.factoryId() == null || filter.factoryId().equals(r.getFactoryId()))
                .filter(r -> isFactoryAllowed(context, r.getFactoryId()))
                .toList();

        int start = Math.min(filter.page() * filter.size(), list.size());
        int end = Math.min(start + filter.size(), list.size());
        List<DailyAttendance> paged = list.subList(start, end).stream()
                .map(DailyAttendanceRecord::toContractDto)
                .toList();

        return new Page<>(paged, filter.page(), filter.size(), list.size());
    }

    @Override
    public Page<AttendanceEventView> queryEvents(ActorContext context, UUID employeeId, UUID factoryId, Instant from, Instant to, int page, int size) {
        validatePaging(page, size);
        UUID scopedEmployeeId = enforceEmployeeScope(context, employeeId);
        enforceFactoryScope(context, factoryId);

        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(7, ChronoUnit.DAYS);
        if (!effectiveFrom.isBefore(effectiveTo)) {
            throw new IllegalArgumentException("from phải nhỏ hơn to.");
        }
        if (Duration.between(effectiveFrom, effectiveTo).toDays() > 366) {
            throw new IllegalArgumentException("Khoảng thời gian tra cứu không được vượt quá 366 ngày.");
        }

        List<AttendanceEvent> raw = attendanceRepository.findByEmployeeIdAndDateRange(scopedEmployeeId, effectiveFrom, effectiveTo).stream()
                .filter(e -> factoryId == null || factoryId.equals(e.getFactoryId()))
                .filter(e -> isFactoryAllowed(context, e.getFactoryId()))
                .toList();

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
            if (EventEnvelope.EVT_ATTENDANCE_RECORDED.equals(e.getEventType())
                    && e.getEmployeeId() != null && e.getDirection() != null) {
                latestByEmp.putIfAbsent(e.getEmployeeId(), e);
            }
        }

        List<PresenceItem> items = latestByEmp.values().stream()
                .sorted(Comparator.comparing(AttendanceEvent::getEmployeeId))
                .map(e -> new PresenceItem(e.getEmployeeId(), e.getDirection().name(), e.getObservedAt()))
                .toList();

        return new PresenceView(Instant.now(), "CENTRAL_SYNCED", items, List.of());
    }

    @Override
    public Page<Adjustment> queryAdjustments(ActorContext context, UUID employeeId, String state, int page, int size) {
        validatePaging(page, size);
        UUID scopedEmployeeId = enforceEmployeeScope(context, employeeId);
        if (adjustmentPort != null) {
            return adjustmentPort.queryAdjustments(context, scopedEmployeeId, state, page, size);
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
        LocalDate end = period.atEndOfMonth().plusDays(1);

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

    private static void validatePaging(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page phải >= 0 và size phải từ 1 đến 100.");
        }
    }

    private static UUID enforceEmployeeScope(ActorContext context, UUID requestedEmployeeId) {
        if (context == null || !ActorContext.SCOPE_SELF.equals(context.scope())) {
            return requestedEmployeeId;
        }
        if (context.employeeId() == null || context.employeeId().isBlank()) {
            throw new SecurityException("Tài khoản SELF chưa liên kết nhân viên.");
        }
        UUID selfId = UUID.fromString(context.employeeId());
        if (requestedEmployeeId != null && !selfId.equals(requestedEmployeeId)) {
            throw new SecurityException("Tài khoản SELF chỉ được xem dữ liệu chấm công của chính mình.");
        }
        return selfId;
    }

    private static void enforceFactoryScope(ActorContext context, UUID requestedFactoryId) {
        if (context == null || ActorContext.SCOPE_GLOBAL.equals(context.scope()) || requestedFactoryId == null) {
            return;
        }
        if (!context.factoryIds().isEmpty() && !context.factoryIds().contains(requestedFactoryId.toString())) {
            throw new SecurityException("Không có quyền truy cập nhà máy ngoài phạm vi được gán.");
        }
    }

    private static boolean isFactoryAllowed(ActorContext context, UUID factoryId) {
        if (context == null || ActorContext.SCOPE_GLOBAL.equals(context.scope()) || context.factoryIds().isEmpty()) {
            return true;
        }
        return factoryId != null && context.factoryIds().contains(factoryId.toString());
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

    private static ParsedPayload parseAndValidatePayload(String eventType, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Payload rỗng");
        }
        JsonNode root;
        try {
            root = STRICT_JSON.readTree(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException("Payload không phải JSON hợp lệ", e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Payload phải là JSON object");
        }

        return switch (eventType) {
            case EventEnvelope.EVT_ATTENDANCE_RECORDED -> parseAttendancePayload(root);
            case EventEnvelope.EVT_ACCESS_DENIED -> parseAccessDeniedPayload(root);
            case EventEnvelope.EVT_COMMAND_RESULT -> parseCommandResultPayload(root);
            case EventEnvelope.EVT_REPLAY_REPORTED -> parseReplayReportPayload(root);
            default -> throw new IllegalArgumentException("Loại sự kiện không được hỗ trợ");
        };
    }

    private static ParsedPayload parseAttendancePayload(JsonNode root) {
        UUID employeeId = requireUuidField(root, "employeeId");
        String dirStr = requireTextField(root, "direction");
        Direction direction;
        try {
            direction = Direction.valueOf(dirStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("direction phải là IN hoặc OUT");
        }

        Source source = Source.ONLINE;
        JsonNode srcNode = root.get("source");
        if (srcNode != null && !srcNode.isNull()) {
            if (!srcNode.isTextual()) {
                throw new IllegalArgumentException("source phải là chuỗi");
            }
            try {
                source = Source.valueOf(srcNode.asText());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("source không hợp lệ");
            }
        }

        UUID jti = optionalUuidField(root, "jti");
        String kid = optionalTextField(root, "kid");
        Instant tokenIssuedAt = optionalInstantField(root, "tokenIssuedAt");
        UUID commandId = optionalUuidField(root, "commandId");
        UUID operatorId = optionalUuidField(root, "operatorId");
        String reason = optionalTextField(root, "reason");

        if (source == Source.MANUAL) {
            if (operatorId == null || reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Sự kiện MANUAL yêu cầu operatorId và reason");
            }
            if (jti != null || kid != null || tokenIssuedAt != null) {
                throw new IllegalArgumentException("Sự kiện MANUAL không được chứa trường token");
            }
        } else {
            if (operatorId != null || reason != null) {
                throw new IllegalArgumentException("Sự kiện QR không được chứa operatorId hoặc reason");
            }
        }

        return new ParsedPayload(employeeId, direction, source, jti, kid, tokenIssuedAt, commandId, operatorId, reason);
    }

    private static ParsedPayload parseAccessDeniedPayload(JsonNode root) {
        requireTextField(root, "reasonCode");
        UUID employeeId = optionalUuidField(root, "employeeId");
        UUID jti = optionalUuidField(root, "jti");
        String kid = optionalTextField(root, "kid");
        Source source = Source.ONLINE;
        JsonNode srcNode = root.get("source");
        if (srcNode != null && !srcNode.isNull()) {
            if (!srcNode.isTextual()) {
                throw new IllegalArgumentException("source phải là chuỗi");
            }
            String srcText = srcNode.asText();
            if (!"ONLINE".equals(srcText) && !"OFFLINE".equals(srcText)) {
                throw new IllegalArgumentException("source của access.attempt.denied phải là ONLINE hoặc OFFLINE");
            }
            source = Source.valueOf(srcText);
        }
        return new ParsedPayload(employeeId, null, source, jti, kid, null, null, null, null);
    }

    private static ParsedPayload parseCommandResultPayload(JsonNode root) {
        UUID commandId = requireUuidField(root, "commandId");
        optionalUuidField(root, "attendanceEventId");
        String mode = requireTextField(root, "mode");
        if (!"QR".equals(mode) && !"MANUAL".equals(mode) && !"EMERGENCY".equals(mode)) {
            throw new IllegalArgumentException("mode không hợp lệ");
        }
        String status = requireTextField(root, "status");
        if (!"UNLOCK_ACCEPTED".equals(status) && !"FAILED".equals(status) && !"TIMEOUT".equals(status)) {
            throw new IllegalArgumentException("status không hợp lệ");
        }
        if (optionalInstantField(root, "completedAt") == null) {
            throw new IllegalArgumentException("completedAt bắt buộc");
        }
        UUID operatorId = optionalUuidField(root, "operatorId");
        String reason = optionalTextField(root, "reason");
        return new ParsedPayload(null, null, Source.ONLINE, null, null, null, commandId, operatorId, reason);
    }

    private static ParsedPayload parseReplayReportPayload(JsonNode root) {
        JsonNode items = root.get("items");
        if (items == null || !items.isArray() || items.size() > 50) {
            throw new IllegalArgumentException("replay.cache.reported yêu cầu mảng items tối đa 50 phần tử");
        }
        for (JsonNode item : items) {
            if (!item.isObject()) {
                throw new IllegalArgumentException("Mỗi phần tử replay report phải là object");
            }
            requireUuidField(item, "jti");
            requireUuidField(item, "firstEventId");
            if (optionalInstantField(item, "seenAt") == null || optionalInstantField(item, "expiresAt") == null) {
                throw new IllegalArgumentException("seenAt và expiresAt bắt buộc");
            }
        }
        return new ParsedPayload(null, null, Source.ONLINE, null, null, null, null, null, null);
    }

    private static UUID requireUuidField(JsonNode root, String field) {
        String text = requireTextField(root, field);
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " phải là UUID hợp lệ", e);
        }
    }

    private static UUID optionalUuidField(JsonNode root, String field) {
        String text = optionalTextField(root, field);
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " phải là UUID hợp lệ", e);
        }
    }

    private static String requireTextField(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new IllegalArgumentException("Thiếu trường bắt buộc: " + field);
        }
        return node.asText();
    }

    private static String optionalTextField(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException(field + " phải là chuỗi");
        }
        return node.asText();
    }

    private static Instant optionalInstantField(JsonNode root, String field) {
        String text = optionalTextField(root, field);
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(field + " phải là Instant ISO-8601 hợp lệ", e);
        }
    }
}
