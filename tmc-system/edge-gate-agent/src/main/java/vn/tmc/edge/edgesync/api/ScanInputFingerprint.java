package vn.tmc.edge.edgesync.api;

import vn.tmc.edge.edgesync.api.EventPayload.CommandMode;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** SHA-256 of the request input, excluding its scanId idempotency key. */
public record ScanInputFingerprint(String sha256) {
    public ScanInputFingerprint {
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected a lowercase SHA-256 fingerprint");
        }
    }

    public static ScanInputFingerprint ofQr(UUID gateId, String token) {
        Objects.requireNonNull(gateId, "gateId");
        Objects.requireNonNull(token, "token");
        var digest = sha256Digest();
        // Preserve the existing QR fingerprint format for persisted scans.
        digest.update(gateId.toString().getBytes(StandardCharsets.UTF_8));
        return new ScanInputFingerprint(HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8))));
    }

    public static ScanInputFingerprint ofAction(UUID gateId, CommandMode mode, UUID employeeId, String reason, String grant) {
        Objects.requireNonNull(gateId, "gateId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(grant, "grant");
        if (mode != CommandMode.MANUAL && mode != CommandMode.EMERGENCY) {
            throw new IllegalArgumentException("An ACTION fingerprint requires MANUAL or EMERGENCY mode");
        }
        var digest = sha256Digest();
        digest.update("ACTION".getBytes(StandardCharsets.UTF_8));
        updateField(digest, gateId.toString());
        updateField(digest, mode.name());
        updateField(digest, employeeId == null ? "" : employeeId.toString());
        updateField(digest, reason);
        updateField(digest, grant);
        return new ScanInputFingerprint(HexFormat.of().formatHex(digest.digest()));
    }

    private static void updateField(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("JDK must support SHA-256", failure);
        }
    }
}
