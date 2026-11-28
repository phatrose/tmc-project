package vn.tmc.edge.gatestation.infrastructure;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/**
 * PHỤ TRÁCH: Task 2. JSON của API Edge theo CONTRACT-V1.md §2: UTF-8, camelCase, enum UPPER_SNAKE_CASE,
 * Instant là UTC ISO 8601 đúng 3 chữ số mili giây (ví dụ 2026-11-17T02:00:00.000Z), field null vẫn xuất hiện.
 * Request nghiêm ngặt: field lạ bị từ chối, key trùng bị từ chối, không có token thừa sau JSON, không ép kiểu ngầm.
 */
final class ScanWireJson {
    private static final DateTimeFormatter INSTANT_FORMAT = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

    static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .addModule(new SimpleModule().addSerializer(Instant.class, new JsonSerializer<>() {
                @Override
                public void serialize(Instant value, JsonGenerator generator, SerializerProvider provider) throws IOException {
                    generator.writeString(INSTANT_FORMAT.format(value));
                }
            }))
            .build();

    private ScanWireJson() {}
}
