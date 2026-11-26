package vn.tmc.edge.edgesync.infrastructure;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;

final class WireJson {
    private static final DateTimeFormatter INSTANT_FORMAT = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .withCoercionConfig(LogicalType.Textual, config -> config
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .addModule(new JavaTimeModule().addSerializer(Instant.class, new JsonSerializer<>() {
                @Override
                public void serialize(Instant value, JsonGenerator generator, SerializerProvider provider) throws IOException {
                    generator.writeString(INSTANT_FORMAT.format(value));
                }
            }).addDeserializer(Instant.class, new JsonDeserializer<>() {
                @Override
                public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                    if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                        return context.reportInputMismatch(Instant.class, "Expected a UTC millisecond timestamp");
                    }
                    String text = parser.getText();
                    try {
                        var instant = Instant.parse(text);
                        if (INSTANT_FORMAT.format(instant).equals(text)) {
                            return instant;
                        }
                    } catch (DateTimeParseException invalid) {
                        throw context.weirdStringException(text, Instant.class, "Invalid timestamp");
                    }
                    throw context.weirdStringException(text, Instant.class, "Expected a UTC millisecond timestamp");
                }
            })).build();
    private static final ObjectReader RESPONSE_READER = MAPPER.reader()
            .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private WireJson() {}

    static byte[] write(Object value) throws IOException {
        return MAPPER.writeValueAsBytes(value);
    }

    static <T> T read(byte[] body, Class<T> type) throws IOException {
        return readStrict(body, type);
    }

    static <T> T readStrict(byte[] body, Class<T> type) throws IOException {
        return MAPPER.readValue(body, type);
    }

    static <T> T readResponse(byte[] body, Class<T> type) throws IOException {
        return RESPONSE_READER.forType(type).readValue(body);
    }
}
