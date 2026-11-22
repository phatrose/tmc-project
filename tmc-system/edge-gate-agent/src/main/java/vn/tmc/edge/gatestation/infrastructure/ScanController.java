package vn.tmc.edge.gatestation.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import vn.tmc.contracts.ApiError;
import vn.tmc.contracts.AttendancePayload.Direction;
import vn.tmc.edge.edgesync.api.EventPayload.CommandMode;
import vn.tmc.edge.gatestation.application.GateDecisionRecorder;
import vn.tmc.edge.gatestation.application.GateOverrideUseCase;
import vn.tmc.edge.gatestation.application.ScanQrUseCase;
import vn.tmc.edge.gatestation.domain.GateStationException;
import vn.tmc.edge.gatestation.domain.GateStationException.Kind;
import vn.tmc.edge.gatestation.domain.KioskContext;
import vn.tmc.edge.gatestation.domain.OverrideRequest;
import vn.tmc.edge.gatestation.domain.PresenceRegistry;
import vn.tmc.edge.gatestation.domain.ScanRequest;
import vn.tmc.edge.gatestation.domain.Uuids;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * PHỤ TRÁCH: Task 2. Adapter HTTP của API Edge LAN (CONTRACT-V1.md §5), độc lập với server cụ thể:
 * nhận method/path/body/KioskContext (đã xác thực mTLS ở lớp ngoài) và trả Reply(status, JSON, requestId).
 * Nhờ đó dùng được với JDK HttpServer (EdgeHttpHandler), kiểm thử không cần mở cổng mạng.
 *
 * Route: POST /edge/v1/scans · GET /edge/v1/scans/{scanId} · POST /edge/v1/overrides · GET /edge/v1/presence.
 * Quy ước: DENY là 200 ScanResult; lỗi request/hạ tầng là ApiError {code,message,requestId,retryable}.
 * Lỗi bất ngờ trả 500 INTERNAL_ERROR không kèm stack trace; token/grant không bao giờ được log hay echo.
 * Giới hạn: body tối đa MAX_BODY_BYTES, field lạ là 400, trường nullable được phép vắng mặt (nới lỏng so với
 * "phải gửi null" của contract §2 vì Jackson không phân biệt vắng với null cho String).
 */
public final class ScanController {
    public static final int MAX_BODY_BYTES = 16 * 1024;
    private static final String BASE = "/edge/v1";
    private static final Pattern REQUEST_ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final System.Logger LOG = System.getLogger(ScanController.class.getName());

    public record Reply(int status, String body, String requestId) {}

    private record ScanBody(String scanId, String gateId, String token) {}

    private record OverrideBody(String scanId, String gateId, String employeeId, String reason, String mode, String grant) {}

    private record PresenceItem(UUID employeeId, Direction lastDirection, Instant lastOccurredAt) {}

    private record PresenceBody(Instant asOf, String coverage, Instant trackedSince, List<PresenceItem> items) {}

    private final ScanQrUseCase scanUseCase;
    private final GateOverrideUseCase overrideUseCase;
    private final GateDecisionRecorder recorder;
    private final PresenceRegistry presence;

    public ScanController(ScanQrUseCase scanUseCase, GateOverrideUseCase overrideUseCase, GateDecisionRecorder recorder,
                          PresenceRegistry presence) {
        this.scanUseCase = Objects.requireNonNull(scanUseCase);
        this.overrideUseCase = Objects.requireNonNull(overrideUseCase);
        this.recorder = Objects.requireNonNull(recorder);
        this.presence = Objects.requireNonNull(presence);
    }

    public Reply handle(String method, String path, byte[] body, KioskContext kiosk, String requestIdHeader) {
        String requestId = requestIdHeader != null && REQUEST_ID.matcher(requestIdHeader).matches()
                ? requestIdHeader : UUID.randomUUID().toString();
        try {
            return route(method, path, body, kiosk, requestId);
        } catch (GateStationException failure) {
            return error(failure.kind().status(), failure.kind().name(), failure.getMessage(), requestId,
                    failure.kind().retryable());
        } catch (RuntimeException unexpected) {
            // Chỉ ghi tên lớp lỗi: message có thể chứa đường dẫn/dữ liệu nhạy cảm.
            LOG.log(System.Logger.Level.ERROR, "Lỗi bất ngờ ở API Edge (requestId={0}): {1}", requestId,
                    unexpected.getClass().getSimpleName());
            return error(500, Kind.INTERNAL_ERROR.name(), "Lỗi nội bộ, vui lòng thử lại.", requestId, true);
        }
    }

    private Reply route(String method, String path, byte[] body, KioskContext kiosk, String requestId) {
        if ("POST".equals(method) && (BASE + "/scans").equals(path)) {
            ScanBody in = parse(body, ScanBody.class);
            var request = new ScanRequest(Uuids.parse(in.scanId(), "scanId"), Uuids.parse(in.gateId(), "gateId"), in.token());
            return ok(scanUseCase.scan(request, kiosk), requestId);
        }
        if ("GET".equals(method) && path.startsWith(BASE + "/scans/")) {
            UUID scanId = Uuids.parse(path.substring((BASE + "/scans/").length()), "scanId");
            return recorder.recentResult(scanId, kiosk).map(result -> ok(result, requestId))
                    .orElseThrow(() -> new GateStationException(Kind.NOT_FOUND, "Không tìm thấy lượt quét"));
        }
        if ("POST".equals(method) && (BASE + "/overrides").equals(path)) {
            OverrideBody in = parse(body, OverrideBody.class);
            var request = new OverrideRequest(Uuids.parse(in.scanId(), "scanId"), Uuids.parse(in.gateId(), "gateId"),
                    in.employeeId() == null ? null : Uuids.parse(in.employeeId(), "employeeId"), in.reason(),
                    mode(in.mode()), in.grant());
            return ok(overrideUseCase.override(request, kiosk), requestId);
        }
        if ("GET".equals(method) && (BASE + "/presence").equals(path)) {
            var snapshot = presence.snapshot();
            var items = snapshot.items().stream()
                    .map(item -> new PresenceItem(item.employeeId(), item.lastDirection(), item.lastOccurredAt())).toList();
            return ok(new PresenceBody(snapshot.asOf(), "LOCAL_EDGE", snapshot.trackedSince(), items), requestId);
        }
        throw new GateStationException(Kind.NOT_FOUND, "Không tìm thấy tài nguyên");
    }

    private static CommandMode mode(String text) {
        if ("MANUAL".equals(text)) {
            return CommandMode.MANUAL;
        }
        if ("EMERGENCY".equals(text)) {
            return CommandMode.EMERGENCY;
        }
        throw new GateStationException(Kind.VALIDATION_ERROR, "mode phải là MANUAL hoặc EMERGENCY");
    }

    private static <T> T parse(byte[] body, Class<T> type) {
        if (body == null || body.length == 0 || body.length > MAX_BODY_BYTES) {
            throw new GateStationException(Kind.VALIDATION_ERROR, "Body bắt buộc và tối đa " + MAX_BODY_BYTES + " byte");
        }
        try {
            T parsed = ScanWireJson.MAPPER.readValue(body, type);
            if (parsed == null) {
                throw new GateStationException(Kind.VALIDATION_ERROR, "Body không được là null");
            }
            return parsed;
        } catch (JsonMappingException mapping) {
            // Sai kiểu, field lạ, key trùng: request sai nội dung. Message của Jackson có thể trích dữ liệu nên không echo.
            throw new GateStationException(Kind.VALIDATION_ERROR, "Body có trường không hợp lệ hoặc không được hỗ trợ");
        } catch (JsonProcessingException syntax) {
            throw new GateStationException(Kind.MALFORMED_JSON, "Body không phải JSON hợp lệ");
        } catch (IOException unreadable) {
            throw new GateStationException(Kind.MALFORMED_JSON, "Không đọc được body");
        }
    }

    private static Reply ok(Object value, String requestId) {
        return new Reply(200, write(value), requestId);
    }

    private static Reply error(int status, String code, String message, String requestId, boolean retryable) {
        return new Reply(status, write(new ApiError(code, message, requestId, retryable)), requestId);
    }

    private static String write(Object value) {
        try {
            return ScanWireJson.MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Không serialize được phản hồi", failure);
        }
    }
}
