package vn.tmc.central.attendanceprocessing.internal.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.tmc.central.attendanceprocessing.api.AttendanceSync;
import vn.tmc.central.qrsecurity.api.QrKeySetQuery;
import vn.tmc.central.qrsecurity.api.QrRevocationQuery;
import vn.tmc.contracts.*;

/**
 * PHỤ TRÁCH: Task 2 / Task 8. Endpoint trạm quét Kiosk trong profile integration-preview:
 * xác minh chữ ký ES256 thật qua QrKeySetQuery, kiểm tra thu hồi (QrRevocationQuery),
 * chống replay (jti) và đồng bộ sự kiện attendance.event.recorded hợp lệ vào AttendanceSync.
 */
@RestController
@Profile("integration-preview")
@RequestMapping("/api/v1/preview")
public final class PreviewKioskScanController {

    private static final UUID PREVIEW_FACTORY_ID = UUID.fromString("00000000-0000-4000-8000-000000000023");
    private static final UUID PREVIEW_EDGE_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    private static final long CLOCK_SKEW_SECONDS = 5L;

    private final AttendanceSync attendanceSync;
    private final QrKeySetQuery keySetQuery;
    private final QrRevocationQuery revocationQuery;
    private final ObjectMapper objectMapper;
    private final Set<String> usedJtis = ConcurrentHashMap.newKeySet();
    private final Map<String, AttendancePayload.Direction> lastDirection = new ConcurrentHashMap<>();
    private final AtomicLong sequenceCounter = new AtomicLong(1L);

    public PreviewKioskScanController(
            AttendanceSync attendanceSync,
            QrKeySetQuery keySetQuery,
            QrRevocationQuery revocationQuery,
            ObjectMapper objectMapper
    ) {
        this.attendanceSync = Objects.requireNonNull(attendanceSync, "attendanceSync required");
        this.keySetQuery = Objects.requireNonNull(keySetQuery, "keySetQuery required");
        this.revocationQuery = Objects.requireNonNull(revocationQuery, "revocationQuery required");
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

        Map<String, Object> header;
        Map<String, Object> claims;
        byte[] sigBytes;
        try {
            header = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), new TypeReference<>() {});
            claims = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), new TypeReference<>() {});
            sigBytes = Base64.getUrlDecoder().decode(parts[2]);
        } catch (Exception e) {
            return ResponseEntity.ok(deny("INVALID_TOKEN"));
        }

        String alg = asString(header.get("alg"));
        String kid = asString(header.get("kid"));
        if (!"ES256".equals(alg) || kid == null) {
            return ResponseEntity.ok(deny("INVALID_TOKEN"));
        }

        QrKeySet keySet = keySetQuery.keySet();
        QrKeySetEntry keyEntry = keySet.keys().stream()
                .filter(k -> kid.equals(k.kid()) && !QrKeySetEntry.STATE_REVOKED.equals(k.state()))
                .findFirst()
                .orElse(null);
        if (keyEntry == null || !verifyEs256(parts[0] + "." + parts[1], sigBytes, keyEntry)) {
            return ResponseEntity.ok(deny("INVALID_SIGNATURE"));
        }

        String sub = asString(claims.get("sub"));
        String iss = asString(claims.get("iss"));
        String aud = asString(claims.get("aud"));
        String jti = asString(claims.get("jti"));
        long iat = claims.get("iat") instanceof Number n ? n.longValue() : 0L;
        long nbf = claims.get("nbf") instanceof Number n ? n.longValue() : iat;
        long exp = claims.get("exp") instanceof Number n ? n.longValue() : 0L;
        if (sub == null || jti == null || iat <= 0 || exp <= 0
                || !QrClaims.ISSUER.equals(iss) || !QrClaims.AUDIENCE.equals(aud)) {
            return ResponseEntity.ok(deny("INVALID_TOKEN"));
        }

        Instant now = Instant.now();
        long nowSec = now.getEpochSecond();
        if (nowSec + CLOCK_SKEW_SECONDS < nbf) {
            return ResponseEntity.ok(deny("TOKEN_NOT_YET_VALID"));
        }
        if (nowSec - CLOCK_SKEW_SECONDS > exp) {
            return ResponseEntity.ok(deny("TOKEN_EXPIRED"));
        }

        Instant tokenIssuedAt = Instant.ofEpochSecond(iat);
        if (isRevoked(sub, jti, kid, tokenIssuedAt)) {
            return ResponseEntity.ok(deny("CREDENTIAL_REVOKED"));
        }
        if (!usedJtis.add(jti)) {
            return ResponseEntity.ok(deny("REPLAY"));
        }

        UUID employeeId = parseUuidOrDeterministic(sub);
        UUID gateId = parseUuidOrDeterministic(req.gateId());
        UUID eventId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        UUID jtiUuid = parseUuidOrDeterministic(jti);
        AttendancePayload.Direction nextDir = lastDirection.compute(sub, (k, prev) ->
                prev == AttendancePayload.Direction.IN ? AttendancePayload.Direction.OUT : AttendancePayload.Direction.IN);

        AttendancePayload payload = AttendancePayload.ofQr(
                employeeId,
                nextDir,
                AttendancePayload.Source.ONLINE,
                jtiUuid,
                kid,
                tokenIssuedAt,
                commandId
        );
        String canonicalPayload = payload.toCanonicalJson();
        String sha = EventEnvelope.computeSha256(canonicalPayload);
        EventEnvelope envelope = new EventEnvelope(
                eventId,
                EventEnvelope.EVT_ATTENDANCE_RECORDED,
                EventEnvelope.CURRENT_SCHEMA_VERSION,
                PREVIEW_EDGE_ID,
                gateId,
                PREVIEW_FACTORY_ID,
                sequenceCounter.getAndIncrement(),
                commandId,
                now,
                sha,
                canonicalPayload
        );
        DeviceContext device = new DeviceContext(PREVIEW_EDGE_ID, PREVIEW_FACTORY_ID);
        SyncBatchAck ack = attendanceSync.synchronize(device, new SyncBatchRequest(UUID.randomUUID(), List.of(envelope)));
        boolean accepted = ack != null && ack.acks() != null && !ack.acks().isEmpty()
                && (ack.acks().get(0).status() == SyncBatchAck.Status.ACCEPTED
                || ack.acks().get(0).status() == SyncBatchAck.Status.DUPLICATE);
        if (!accepted) {
            usedJtis.remove(jti);
            return ResponseEntity.ok(deny("SYNC_REJECTED"));
        }

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

    private boolean isRevoked(String sub, String jti, String kid, Instant tokenIssuedAt) {
        for (RevocationChange rev : revocationQuery.activeRevocations()) {
            if (!RevocationChange.OP_UPSERT.equals(rev.op())) continue;
            if (RevocationChange.KIND_KID.equals(rev.kind()) && kid.equals(rev.value())) return true;
            if (RevocationChange.KIND_JTI.equals(rev.kind()) && jti.equals(rev.value())) return true;
            if (RevocationChange.KIND_SUB.equals(rev.kind()) && sub.equals(rev.value())) {
                if (rev.revokedBefore() == null || !tokenIssuedAt.isAfter(rev.revokedBefore())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean verifyEs256(String signingInput, byte[] signatureBytes, QrKeySetEntry key) {
        if (signatureBytes == null || signatureBytes.length != 64) {
            return false;
        }
        try {
            BigInteger x = new BigInteger(1, Base64.getUrlDecoder().decode(key.x()));
            BigInteger y = new BigInteger(1, Base64.getUrlDecoder().decode(key.y()));
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec spec = params.getParameterSpec(ECParameterSpec.class);
            ECPublicKey pubKey = (ECPublicKey) KeyFactory.getInstance("EC")
                    .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), spec));
            Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
            sig.initVerify(pubKey);
            sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return sig.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
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
}
