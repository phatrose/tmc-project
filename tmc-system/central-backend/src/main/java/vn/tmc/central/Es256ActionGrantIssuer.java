package vn.tmc.central;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import vn.tmc.central.usermgmt.api.Role;
import vn.tmc.central.usermgmt.api.TokenIssuerPort;

/**
 * Ký Action Grant JWS ES256 (hạn 60 giây, aud=tmc-edge-action) cho re-auth override.
 */
public final class Es256ActionGrantIssuer implements TokenIssuerPort {

    private static final long GRANT_TTL_SECONDS = 60L;
    private static final Base64.Encoder B64_URL = Base64.getUrlEncoder().withoutPadding();

    private final KeyPair keyPair;
    private final String keyId;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public Es256ActionGrantIssuer(String keyId, Clock clock, ObjectMapper objectMapper) {
        this.keyId = Objects.requireNonNull(keyId, "keyId required");
        this.clock = Objects.requireNonNull(clock, "clock required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper required");
        this.keyPair = generateEcP256KeyPair();
    }

    public PublicKey publicKey() {
        return keyPair.getPublic();
    }

    public String keyId() {
        return keyId;
    }

    @Override
    public String issueToken(String userId, Role role, List<String> scopes) {
        Objects.requireNonNull(userId, "userId required");
        Objects.requireNonNull(role, "role required");
        List<String> safeScopes = scopes == null ? List.of() : List.copyOf(scopes);

        Instant now = clock.instant();
        long iat = now.getEpochSecond();
        long exp = iat + GRANT_TTL_SECONDS;

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "ES256");
        header.put("kid", keyId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", userId);
        payload.put("iss", "tmc-central");
        payload.put("aud", "tmc-edge-action");
        payload.put("iat", iat);
        payload.put("nbf", iat);
        payload.put("exp", exp);
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("role", role.name());
        payload.put("scopes", safeScopes);

        try {
            String encodedHeader = B64_URL.encodeToString(objectMapper.writeValueAsBytes(header));
            String encodedPayload = B64_URL.encodeToString(objectMapper.writeValueAsBytes(payload));
            String signingInput = encodedHeader + "." + encodedPayload;

            Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
            signer.initSign(keyPair.getPrivate());
            signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            String encodedSig = B64_URL.encodeToString(signer.sign());

            return signingInput + "." + encodedSig;
        } catch (Exception e) {
            throw new IllegalStateException("Không thể ký Action Grant ES256.", e);
        }
    }

    private static KeyPair generateEcP256KeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Không thể khởi tạo khóa EC P-256 cho Action Grant.", e);
        }
    }
}
