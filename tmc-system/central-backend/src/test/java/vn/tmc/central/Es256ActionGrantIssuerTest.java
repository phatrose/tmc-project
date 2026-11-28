package vn.tmc.central;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.tmc.central.usermgmt.api.Role;

class Es256ActionGrantIssuerTest {

    private static final Base64.Decoder B64_URL = Base64.getUrlDecoder();

    @Test
    void issuesVerifiableEs256ActionGrantWith60sTtl() throws Exception {
        Instant fixedNow = Instant.parse("2026-11-27T03:00:00Z");
        Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        ObjectMapper mapper = new ObjectMapper();
        Es256ActionGrantIssuer issuer = new Es256ActionGrantIssuer("action-grant-es256-v1", clock, mapper);

        String token = issuer.issueToken("user-guard-1", Role.GUARD, List.of("gate.override"));
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        Map<String, Object> header = mapper.readValue(B64_URL.decode(parts[0]), new TypeReference<>() {});
        assertThat(header).containsExactlyInAnyOrderEntriesOf(Map.of(
                "alg", "ES256",
                "kid", "action-grant-es256-v1"
        ));

        Map<String, Object> payload = mapper.readValue(B64_URL.decode(parts[1]), new TypeReference<>() {});
        assertThat(payload.get("sub")).isEqualTo("user-guard-1");
        assertThat(payload.get("iss")).isEqualTo("tmc-central");
        assertThat(payload.get("aud")).isEqualTo("tmc-edge-action");
        assertThat(((Number) payload.get("iat")).longValue()).isEqualTo(fixedNow.getEpochSecond());
        assertThat(((Number) payload.get("nbf")).longValue()).isEqualTo(fixedNow.getEpochSecond());
        assertThat(((Number) payload.get("exp")).longValue()).isEqualTo(fixedNow.getEpochSecond() + 60L);
        assertThat(payload.get("jti")).isInstanceOf(String.class).asString().isNotBlank();
        assertThat(payload.get("role")).isEqualTo("GUARD");
        assertThat(payload.get("scopes")).isEqualTo(List.of("gate.override"));

        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(issuer.publicKey());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(B64_URL.decode(parts[2]))).isTrue();
    }
}
