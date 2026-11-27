package vn.tmc.central.attendanceprocessing.internal.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.tmc.central.attendanceprocessing.api.AttendanceSync;
import vn.tmc.contracts.*;

/**
 * PHỤ TRÁCH: Task 2 / Task 8. Endpoint mô phỏng trạm quét Kiosk trong profile integration-preview,
 * xác minh cơ bản token QR (exp/jti replay) và ghi nhận sự kiện chấm công thật vào AttendanceSync.
 */
@RestController
@Profile("integration-preview")
@RequestMapping("/api/v1/preview")
public final class PreviewKioskScanController {

    private static final UUID PREVIEW_FACTORY_ID = UUID.fromString("00000000-0000-4000-8000-000000000023");
    private static final UUID PREVIEW_EDGE_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e1");

    private final AttendanceSync attendanceSync;
    private final ObjectMapper objectMapper;
    private final Set<String> usedJtis = ConcurrentHashMap.newKeySet();
    private final Map<String, AttendancePayload.Direction> lastDirection = new ConcurrentHashMap<>();

    public PreviewKioskScanController(AttendanceSync attendanceSync, ObjectMapper objectMapper) {
        this.attendanceSync = Objects.requireNonNull(attendanceSync, "attendanceSync required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper required");
    }

    public record ScanRequest(String scanId, String gateId, String token) {}

    @PostMapping("/scans")
    public ResponseEntity<Map<String, Object>> scan(@RequestBody ScanRequest req) {
        if (req == null || req.token() == null || req.token().isBlank()) {
            return ResponseEntity.ok(deny("INVALID_TOKEN"));
        }
        String[] parts = req.token().trim().split("\\.");
        if (parts.length != 3) {
            return ResponseEntity.ok(deny("INVALID_TOKEN"));
        }
        Map<String, Object> claims;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            claims = objectMapper.readValue(decoded, new TypeReference<>() {});
        } catch (Exception e) {
            return ResponseEntity.ok(deny("INVALID_TOKEN"));
        }

        String sub = asString(claims.get("sub"));
        String jti = asString(claims.get("jti"));
        long exp = claims.get("exp") instanceof Number n ? n.longValue() : 0L;
        if (sub == null || jti == null || exp <= 0) {
            return ResponseEntity.ok(deny("INVALID_TOKEN"));
        }
        Instant now = Instant.now();
        if (now.getEpochSecond() > exp) {
            return ResponseEntity.ok(deny("TOKEN_EXPIRED"));
        }
        if (!usedJtis.add(jti)) {
            return ResponseEntity.ok(deny("REPLAY"));
        }

        UUID employeeId = parseUuidOrDeterministic(sub);
        UUID gateId = parseUuidOrDeterministic(req.gateId());
        UUID eventId = UUID.randomUUID();
        UUID jtiUuid = parseUuidOrDeterministic(jti);
        AttendancePayload.Direction nextDir = lastDirection.compute(sub, (k, prev) ->
                prev == AttendancePayload.Direction.IN ? AttendancePayload.Direction.OUT : AttendancePayload.Direction.IN);

        AttendancePayload payload = new AttendancePayload(
                employeeId,
                nextDir,
                AttendancePayload.Source.ONLINE,
                jtiUuid,
                null,
                now,
                PREVIEW_FACTORY_ID,
                gateId,
                null
        );
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        String sha = sha256(payloadJson);
        EventEnvelope envelope = new EventEnvelope(
                eventId,
                "ATTENDANCE_RECORDED",
                1,
                PREVIEW_FACTORY_ID,
                gateId,
                PREVIEW_EDGE_ID,
                System.currentTimeMillis(),
                eventId,
                now,
                payloadJson,
                sha
        );
        DeviceContext device = new DeviceContext(PREVIEW_EDGE_ID, PREVIEW_FACTORY_ID);
        attendanceSync.synchronize(device, new SyncBatchRequest(UUID.randomUUID(), List.of(envelope)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("decision", "ALLOW");
        result.put("commandStatus", "UNLOCK_ACCEPTED");
        result.put("mode", "ONLINE");
        result.put("eventId", eventId.toString());
        result.put("employeeId", employeeId.toString());
        result.put("direction", nextDir.name());
        result.put("occurredAt", now.toString());
        return ResponseEntity.ok(result);
    }

    private static Map<String, Object> deny(String reasonCode) {
        return Map.of("decision", "DENY", "reasonCode", reasonCode, "mode", "ONLINE");
    }

    private static String asString(Object v) {
        return v instanceof String s && !s.isBlank() ? s : null;
    }

    private static UUID parseUuidOrDeterministic(String raw) {
        if (raw != null && !raw.isBlank()) {
            try {
                return UUID.fromString(raw.trim());
            } catch (IllegalArgumentException ignored) {
                return UUID.nameUUIDFromBytes(raw.trim().getBytes(StandardCharsets.UTF_8));
            }
        }
        return UUID.fromString("00000000-0000-4000-8000-000000000011");
    }

    private static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
