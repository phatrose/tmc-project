package vn.tmc.edge.edgesync.infrastructure;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.tmc.contracts.SyncBatchAck;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;

class WireJsonTest {
    @ParameterizedTest
    @ValueSource(strings = {"0, \"2026-11-17T02:10:00.000Z\"", "\"ACCEPTED\", 0",
            "\"ACCEPTED\", \"2026-11-17T02:10:00Z\""})
    void malformedWireEnumOrTimestampIsRejected(String fields) {
        var pair = fields.split(", ", 2);
        var json = """
                {"syncBatchId":"00000000-0000-4000-8000-000000000031",
                  "acks":[{"eventId":"01a1e73b-8500-7000-8000-000000000001",
                           "status":%s,"acceptedAt":%s,"code":null}],
                  "lastContiguousSequenceNo":1}
                """.formatted(pair[0], pair[1]);
        assertThrows(IOException.class, () -> WireJson.read(json.getBytes(StandardCharsets.UTF_8), SyncBatchAck.class));
    }
}
