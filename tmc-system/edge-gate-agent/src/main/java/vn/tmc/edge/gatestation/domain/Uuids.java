package vn.tmc.edge.gatestation.domain;

import vn.tmc.edge.gatestation.domain.GateStationException.Kind;

import java.util.UUID;

/** PHỤ TRÁCH: Task 2. Kiểm tra ID wire theo CONTRACT-V1.md §2 (scan/command/correlation là UUIDv4). */
public final class Uuids {
    private Uuids() {}

    public static UUID requireV4(UUID value, String field) {
        if (value == null || value.version() != 4 || value.variant() != 2) {
            throw new GateStationException(Kind.VALIDATION_ERROR, field + " phải là UUIDv4");
        }
        return value;
    }

    /** Parse chuỗi UUID chữ thường; sai định dạng là VALIDATION_ERROR chứ không phải 500. */
    public static UUID parse(String text, String field) {
        if (text == null) {
            throw new GateStationException(Kind.VALIDATION_ERROR, field + " là bắt buộc");
        }
        try {
            UUID parsed = UUID.fromString(text);
            if (!parsed.toString().equals(text)) {
                throw new GateStationException(Kind.VALIDATION_ERROR, field + " phải là UUID chữ thường");
            }
            return parsed;
        } catch (IllegalArgumentException malformed) {
            throw new GateStationException(Kind.VALIDATION_ERROR, field + " không phải UUID");
        }
    }
}
