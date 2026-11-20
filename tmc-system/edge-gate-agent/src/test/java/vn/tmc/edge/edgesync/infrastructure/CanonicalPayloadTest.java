package vn.tmc.edge.edgesync.infrastructure;

import org.junit.jupiter.api.Test;
import vn.tmc.edge.edgesync.api.EventPayload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static vn.tmc.edge.edgesync.infrastructure.SqliteEventStoreIT.*;

class CanonicalPayloadTest {
    @Test
    void contractFixture_canonicalPayloadProducesExpectedSha256() throws IOException {
        // Known-good canonical bytes and digest from the valid Contract v1 attendance example.
        var expected = """
                {"commandId":"00000000-0000-4000-8000-000000000013","direction":"IN","employeeId":"00000000-0000-4000-8000-000000000011","jti":"00000000-0000-4000-8000-000000000012","kid":"qr-key-2026-11","operatorId":null,"reason":null,"source":"OFFLINE","tokenIssuedAt":"2026-11-17T01:59:45.000Z"}
                """.stripTrailing();
        var actual = CanonicalPayload.encode(new EventPayload.Attendance(attendance(JTI, COMMAND_ID)));
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), actual.text().getBytes(StandardCharsets.UTF_8));
        assertEquals(EXPECTED_HASH, actual.sha256());
    }
}
