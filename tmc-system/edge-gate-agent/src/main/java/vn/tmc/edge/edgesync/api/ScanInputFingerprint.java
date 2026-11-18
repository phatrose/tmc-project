package vn.tmc.edge.edgesync.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** SHA-256 of the QR request input, excluding its scanId idempotency key. */
public record ScanInputFingerprint(String sha256) {
    public ScanInputFingerprint {
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected a lowercase SHA-256 fingerprint");
        }
    }

    public static ScanInputFingerprint ofQr(UUID gateId, String token) {
        Objects.requireNonNull(gateId, "gateId");
        Objects.requireNonNull(token, "token");
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            // The UUID prefix has fixed width, so gate and token cannot alias at the boundary.
            digest.update(gateId.toString().getBytes(StandardCharsets.UTF_8));
            return new ScanInputFingerprint(HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("JDK must support SHA-256", failure);
        }
    }
}
