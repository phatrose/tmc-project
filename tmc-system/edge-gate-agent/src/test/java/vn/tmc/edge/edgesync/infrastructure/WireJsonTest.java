package vn.tmc.edge.edgesync.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.tmc.contracts.BootstrapResponse;
import vn.tmc.contracts.BootstrapResponse.KeySet;
import vn.tmc.contracts.BootstrapResponse.KeyState;
import vn.tmc.contracts.BootstrapResponse.Mode;
import vn.tmc.contracts.BootstrapResponse.PolicySnapshot;
import vn.tmc.contracts.EventEnvelope;
import vn.tmc.contracts.SyncBatchAck;
import vn.tmc.contracts.SyncBatchAck.Status;
import vn.tmc.contracts.SyncBatchRequest;
import vn.tmc.edge.edgesync.domain.HeartbeatPort.Heartbeat;
import vn.tmc.edge.edgesync.domain.HeartbeatPort.Receipt;
import vn.tmc.edge.edgesync.infrastructure.SqliteKeyRefreshIT.StubResponse;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static vn.tmc.edge.edgesync.infrastructure.SqliteEventStoreIT.*;

class WireJsonTest {
    private static final String SYNC_ACK_JSON = """
            {"syncBatchId":"00000000-0000-4000-8000-000000000031",
             "serverRegion":"ap-southeast-1",
             "acks":[{"eventId":"01a1e73b-8500-7000-8000-000000000001",
                      "status":"ACCEPTED",
                      "acceptedAt":"2026-11-17T02:10:00.000Z",
                      "code":null,
                      "traceMetadata":{"shard":3}}],
             "lastContiguousSequenceNo":1}
            """;

    private static final String KEY_SET_JSON = """
            {"version":2,
             "generatedAt":"2026-11-17T02:00:00.000Z",
             "trustedUntil":"2026-11-19T02:00:00.000Z",
             "rotationPolicy":"90D",
             "keys":[{"kid":"qr-key-2026-12",
                      "kty":"EC","crv":"P-256","alg":"ES256","use":"sig",
                      "x":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                      "y":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                      "state":"ACTIVE",
                      "notBefore":"2026-11-16T00:00:00.000Z",
                      "notAfter":"2026-11-20T00:00:00.000Z",
                      "algorithmNote":"added-in-v1.1"}]}
            """;

    private static final String BOOTSTRAP_JSON = """
            {"mode":"FULL",
             "baseRevision":0,
             "revision":1,
             "generatedAt":"2026-11-17T02:00:00.000Z",
             "trustedUntil":"2026-11-19T02:00:00.000Z",
             "factoryId":"00000000-0000-4000-8000-000000000023",
             "edgeId":"00000000-0000-4000-8000-000000000021",
             "contractBanner":{"additive":true},
             "revocations":[{"id":"00000000-0000-4000-8000-000000000051",
                             "op":"UPSERT","kind":"KID","value":"old-kid",
                             "revokedBefore":null,"effectiveAt":"2026-11-17T02:00:00.000Z",
                             "actorNote":"revoked"}],
             "keySet":%s,
             "policy":{"version":1,
                       "policyFlavor":"STANDARD",
                       "employees":[{"employeeId":"00000000-0000-4000-8000-000000000011",
                                     "active":true,
                                     "badgeTier":"FULL",
                                     "windows":[{"gateId":"00000000-0000-4000-8000-000000000022",
                                                 "direction":"IN",
                                                 "startsAt":"2026-11-17T00:00:00.000Z",
                                                 "endsAt":"2026-11-19T00:00:00.000Z",
                                                 "shiftCode":"SHIFT_A"}]}]},
             "gates":[{"id":"00000000-0000-4000-8000-000000000022",
                       "version":1,"name":"North","factoryId":"00000000-0000-4000-8000-000000000023",
                       "edgeId":"00000000-0000-4000-8000-000000000021","direction":"IN",
                       "turnstileApiId":"north","enabled":true,"laneLabel":"Lane-1"}]}
            """.formatted(KEY_SET_JSON);

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

    @Test
    void syncAck_withUnknownAdditiveResponseField_isAccepted() throws IOException {
        byte[] bytes = SYNC_ACK_JSON.getBytes(StandardCharsets.UTF_8);
        var parsed = WireJson.readResponse(bytes, SyncBatchAck.class);
        assertEquals(UUID.fromString("00000000-0000-4000-8000-000000000031"), parsed.syncBatchId());
        assertEquals(1, parsed.acks().size());
        assertEquals(Status.ACCEPTED, parsed.acks().getFirst().status());
        assertEquals(1, parsed.lastContiguousSequenceNo());

        var envelope = new EventEnvelope(UUID.fromString("01a1e73b-8500-7000-8000-000000000001"),
                "access.attempt.denied", 1, EDGE_ID.toString(), GATE_ID.toString(), FACTORY_ID.toString(),
                1, SCAN_ID.toString(), OCCURRED_AT, "0".repeat(64), "{}");
        var client = new RestSyncClient(URI.create("https://central.example.test/api/v1/attendance/sync"),
                req -> new StubResponse(200, bytes));
        assertEquals(parsed, client.send(new SyncBatchRequest(UUID.fromString("00000000-0000-4000-8000-000000000031"),
                List.of(envelope))));
    }

    @Test
    void bootstrap_withUnknownAdditiveResponseField_isAccepted() throws IOException {
        byte[] bytes = BOOTSTRAP_JSON.getBytes(StandardCharsets.UTF_8);
        var parsed = WireJson.readResponse(bytes, BootstrapResponse.class);
        assertEquals(Mode.FULL, parsed.mode());
        assertEquals(1, parsed.revision());
        assertEquals(1, parsed.revocations().size());
        assertEquals(1, parsed.keySet().keys().size());
        assertEquals(KeyState.ACTIVE, parsed.keySet().keys().getFirst().state());
        assertEquals(1, parsed.policy().employees().size());
        assertEquals(1, parsed.gates().size());

        var client = new RestBootstrapClient(URI.create("https://central.example.test/api/v1/edge/bootstrap"),
                req -> new StubResponse(200, bytes));
        var result = client.fetch(0);
        assertFalse(result.notModified());
        assertEquals(parsed, result.bundle());
    }

    @Test
    void keyRefresh_withUnknownAdditiveResponseField_isAccepted() throws IOException {
        byte[] bytes = KEY_SET_JSON.getBytes(StandardCharsets.UTF_8);
        var parsed = WireJson.readResponse(bytes, KeySet.class);
        assertEquals(2, parsed.version());
        assertEquals("qr-key-2026-12", parsed.keys().getFirst().kid());

        var client = new RestKeyRefreshClient(URI.create("https://central.example.test/api/v1/qr/keys/refresh"),
                req -> new StubResponse(200, bytes));
        assertEquals(parsed, client.fetch());
    }

    @Test
    void heartbeatReceipt_withUnknownAdditiveResponseField_isAccepted() throws IOException {
        byte[] bytes = """
                {"serverTime":"2026-11-17T02:00:00.000Z","serverVersion":"1.2.0"}
                """.getBytes(StandardCharsets.UTF_8);
        var parsed = WireJson.readResponse(bytes, Receipt.class);
        assertEquals(Instant.parse("2026-11-17T02:00:00.000Z"), parsed.serverTime());

        var client = new RestHeartbeatClient(URI.create("https://central.example.test/api/v1/edge/heartbeat"),
                req -> new StubResponse(200, bytes));
        assertEquals(parsed, client.send(new Heartbeat(1, 0, 0, 8192, null, OCCURRED_AT)));
    }

    @Test
    void strictRead_rejectsUnknownField() {
        byte[] bytes = SYNC_ACK_JSON.getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> WireJson.read(bytes, SyncBatchAck.class));
        assertThrows(IOException.class, () -> WireJson.readStrict(bytes, SyncBatchAck.class));
    }

    @Test
    void responseRead_rejectsUnknownEnumValue() {
        var json = """
                {"syncBatchId":"00000000-0000-4000-8000-000000000031",
                 "acks":[{"eventId":"01a1e73b-8500-7000-8000-000000000001",
                          "status":"UNKNOWN_STATUS","acceptedAt":"2026-11-17T02:10:00.000Z","code":null}],
                 "lastContiguousSequenceNo":1}
                """;
        assertThrows(IOException.class, () -> WireJson.readResponse(bytes(json), SyncBatchAck.class));
    }

    @Test
    void responseRead_rejectsNumericEnum() {
        var json = """
                {"syncBatchId":"00000000-0000-4000-8000-000000000031",
                 "acks":[{"eventId":"01a1e73b-8500-7000-8000-000000000001",
                          "status":0,"acceptedAt":"2026-11-17T02:10:00.000Z","code":null}],
                 "lastContiguousSequenceNo":1}
                """;
        assertThrows(IOException.class, () -> WireJson.readResponse(bytes(json), SyncBatchAck.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"serverTime\":\"2026-11-17T02:00:00.000Z\",\"serverTime\":\"2026-11-17T02:00:01.000Z\"}",
            "{\"serverTime\":\"2026-11-17T02:00:00.000Z\",\"extra\":1,\"extra\":2}"
    })
    void responseRead_rejectsDuplicateJsonKey(String json) {
        assertThrows(IOException.class, () -> WireJson.readResponse(bytes(json), Receipt.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"syncBatchId\":\"00000000-0000-4000-8000-000000000031\",\"lastContiguousSequenceNo\":1}",
            "{\"syncBatchId\":\"00000000-0000-4000-8000-000000000031\",\"acks\":[]}"
    })
    void responseRead_rejectsMissingRequiredField(String json) {
        assertThrows(IOException.class, () -> WireJson.readResponse(bytes(json), SyncBatchAck.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"2026-11-17T02:00:00Z\"",
            "\"2026-11-17T02:00:00.000000Z\"",
            "\"2026-11-17T09:00:00.000+07:00\"",
            "1794880800000",
            "\"not-a-timestamp\""
    })
    void responseRead_rejectsInvalidOrNonMillisecondTimestamp(String value) {
        var json = "{\"serverTime\":" + value + "}";
        assertThrows(IOException.class, () -> WireJson.readResponse(bytes(json), Receipt.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"serverTime\":\"2026-11-17T02:00:00.000Z\"} {\"extra\":1}",
            "{\"serverTime\":\"2026-11-17T02:00:00.000Z\"} trailing"
    })
    void responseRead_rejectsTrailingJsonToken(String json) {
        assertThrows(IOException.class, () -> WireJson.readResponse(bytes(json), Receipt.class));
    }

    @Test
    void responseRead_rejectsNullPrimitive() {
        var json = """
                {"syncBatchId":"00000000-0000-4000-8000-000000000031",
                 "acks":[],
                 "lastContiguousSequenceNo":null}
                """;
        assertThrows(IOException.class, () -> WireJson.readResponse(bytes(json), SyncBatchAck.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"syncBatchId\":\"00000000-0000-4000-8000-000000000031\",\"acks\":[],\"lastContiguousSequenceNo\":\"1\"}",
            "{\"syncBatchId\":\"00000000-0000-4000-8000-000000000031\",\"acks\":[],\"lastContiguousSequenceNo\":1.5}",
            "{\"syncBatchId\":\"00000000-0000-4000-8000-000000000031\",\"acks\":[],\"lastContiguousSequenceNo\":1.0}",
            "{\"syncBatchId\":123,\"acks\":[],\"lastContiguousSequenceNo\":1}"
    })
    void responseRead_rejectsWrongScalarType(String json) {
        assertThrows(IOException.class, () -> WireJson.readResponse(bytes(json), SyncBatchAck.class));
    }

    @Test
    void responseRead_rejectsIntForStringAndStringForBoolean() {
        var intForString = """
                {"version":1,"generatedAt":"2026-11-17T02:00:00.000Z","trustedUntil":"2026-11-19T02:00:00.000Z",
                 "keys":[{"kid":123,"kty":"EC","crv":"P-256","alg":"ES256","use":"sig",
                          "x":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                          "y":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                          "state":"ACTIVE","notBefore":"2026-11-16T00:00:00.000Z","notAfter":"2026-11-20T00:00:00.000Z"}]}
                """;
        assertThrows(IOException.class, () -> WireJson.readResponse(bytes(intForString), KeySet.class));

        var stringForBoolean = """
                {"version":1,"employees":[{"employeeId":"00000000-0000-4000-8000-000000000011","active":"true","windows":[]}]}
                """;
        assertThrows(IOException.class, () -> WireJson.readResponse(bytes(stringForBoolean), PolicySnapshot.class));
    }

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
