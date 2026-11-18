package vn.tmc.edge.edgesync.infrastructure;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.erdtman.jcs.JsonCanonicalizer;
import vn.tmc.edge.edgesync.api.EventPayload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HexFormat;

record CanonicalPayload(String text, String sha256) {
    private static final DateTimeFormatter INSTANT_FORMAT = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(
            new JavaTimeModule().addSerializer(Instant.class, new JsonSerializer<>() {
                @Override
                public void serialize(Instant value, JsonGenerator generator, SerializerProvider provider) throws IOException {
                    generator.writeString(INSTANT_FORMAT.format(value));
                }
            }));

    static CanonicalPayload encode(EventPayload payload) throws IOException {
        var canonical = new JsonCanonicalizer(MAPPER.writeValueAsString(payload)).getEncodedString();
        return new CanonicalPayload(canonical, sha256(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("JDK must support SHA-256", failure);
        }
    }
}
