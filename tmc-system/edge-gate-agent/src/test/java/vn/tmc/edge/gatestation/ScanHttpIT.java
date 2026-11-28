package vn.tmc.edge.gatestation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.tmc.edge.edgesync.api.EventPayload.CommandMode;
import vn.tmc.edge.gatestation.domain.ActionGrantVerificationPort.VerifiedGrant;
import vn.tmc.edge.gatestation.infrastructure.EdgeHttpHandler;
import vn.tmc.edge.gatestation.infrastructure.ScanController;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static vn.tmc.edge.gatestation.GateStationFixture.*;

/** API Edge LAN: hình dạng JSON, mã lỗi ApiError, idempotency qua HTTP và cầu nối JDK HttpServer. */
class ScanHttpIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SCANS = "/edge/v1/scans";

    @TempDir
    Path dir;

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String scanBody(UUID scanId, UUID gateId, String token) {
        return "{\"scanId\":\"%s\",\"gateId\":\"%s\",\"token\":\"%s\"}".formatted(scanId, gateId, token);
    }

    private ScanController.Reply post(GateStationFixture f, String path, String body) {
        return f.controller.handle("POST", path, bytes(body), f.kiosk, null);
    }

    private static JsonNode json(ScanController.Reply reply) throws Exception {
        return JSON.readTree(reply.body());
    }

    // ---------- /scans ----------

    @Test
    void scan_returnsContractShapeWithMillisecondInstantAndNoToken() throws Exception {
        var f = new GateStationFixture(dir, true, true);
        String token = f.token(EMPLOYEE_ID);
        UUID scanId = UUID.randomUUID();
        var reply = post(f, SCANS, scanBody(scanId, GATE_ID, token));

        assertEquals(200, reply.status());
        JsonNode body = json(reply);
        assertEquals(scanId.toString(), body.get("scanId").asText());
        assertEquals("ALLOW", body.get("decision").asText());
        assertEquals("OK", body.get("reasonCode").asText());
        assertEquals("UNLOCK_ACCEPTED", body.get("commandStatus").asText());
        assertEquals("ONLINE", body.get("mode").asText());
        assertEquals("2026-11-17T02:00:00.000Z", body.get("occurredAt").asText());
        assertEquals(8, body.size(), "đúng 8 field của ScanResult");
        assertFalse(reply.body().contains(token), "response không được chứa token thô");
    }

    @Test
    void denyIsHttp200WithExplicitNullFields() throws Exception {
        var f = new GateStationFixture(dir, true, true);
        var reply = post(f, SCANS, scanBody(UUID.randomUUID(), GATE_ID, f.token(id(99))));
        assertEquals(200, reply.status());
        JsonNode body = json(reply);
        assertEquals("DENY", body.get("decision").asText());
        assertEquals("OUTSIDE_POLICY", body.get("reasonCode").asText());
        assertTrue(body.has("eventId") && body.get("eventId").isNull());
        assertTrue(body.has("commandId") && body.get("commandId").isNull());
        assertEquals("NOT_SENT", body.get("commandStatus").asText());
    }

    @Test
    void retryOverHttp_isIdempotentAndChangedContentIsConflict() throws Exception {
        var f = new GateStationFixture(dir, true, true);
        UUID scanId = UUID.randomUUID();
        String body = scanBody(scanId, GATE_ID, f.token(EMPLOYEE_ID));
        var first = post(f, SCANS, body);
        var retry = post(f, SCANS, body);
        assertEquals(first.body(), retry.body());
        assertEquals(1, f.turnstile.calls.get());

        var conflict = post(f, SCANS, scanBody(scanId, GATE_ID, f.token(EMPLOYEE_ID)));
        assertEquals(409, conflict.status());
        assertEquals("IDEMPOTENCY_CONFLICT", json(conflict).get("code").asText());
        assertFalse(json(conflict).get("retryable").asBoolean());
    }

    @Test
    void malformedAndInvalidRequests_areRejectedWithValidationErrors() throws Exception {
        var f = new GateStationFixture(dir, true, true);
        UUID scanId = UUID.randomUUID();

        var syntax = post(f, SCANS, "{not json");
        assertEquals(400, syntax.status());
        assertEquals("MALFORMED_JSON", json(syntax).get("code").asText());

        String[] invalid = {
                "{\"scanId\":\"%s\",\"gateId\":\"%s\",\"token\":\"t\",\"extra\":1}".formatted(scanId, GATE_ID),
                "{\"scanId\":\"%s\",\"gateId\":\"%s\"}".formatted(scanId, GATE_ID),
                "{\"scanId\":\"abc\",\"gateId\":\"%s\",\"token\":\"t\"}".formatted(GATE_ID),
                "{\"scanId\":\"%s\",\"gateId\":\"%s\",\"token\":\"t\"}".formatted(scanId.toString().toUpperCase(), GATE_ID),
                "{\"scanId\":\"%s\",\"gateId\":\"%s\",\"token\":\"   \"}".formatted(scanId, GATE_ID),
                "{\"scanId\":\"%s\",\"scanId\":\"%s\",\"gateId\":\"%s\",\"token\":\"t\"}".formatted(scanId, scanId, GATE_ID),
                "{\"scanId\":123,\"gateId\":\"%s\",\"token\":\"t\"}".formatted(GATE_ID),
        };
        for (String body : invalid) {
            var reply = post(f, SCANS, body);
            assertEquals(400, reply.status(), body);
            assertTrue(Set_OF_400.contains(json(reply).get("code").asText()), body);
        }
        var empty = f.controller.handle("POST", SCANS, new byte[0], f.kiosk, null);
        assertEquals(400, empty.status());
        var tooLarge = f.controller.handle("POST", SCANS, new byte[ScanController.MAX_BODY_BYTES + 1], f.kiosk, null);
        assertEquals(400, tooLarge.status());
        assertEquals(0, f.turnstile.calls.get());
    }

    private static final java.util.Set<String> Set_OF_400 = java.util.Set.of("VALIDATION_ERROR", "MALFORMED_JSON");

    @Test
    void gateOutsideKioskScope_is403NotRetryable() throws Exception {
        var f = new GateStationFixture(dir, true, true);
        var reply = post(f, SCANS, scanBody(UUID.randomUUID(), OTHER_GATE_ID, f.token(EMPLOYEE_ID)));
        assertEquals(403, reply.status());
        assertEquals("DEVICE_BINDING_MISMATCH", json(reply).get("code").asText());
        assertFalse(json(reply).get("retryable").asBoolean());
    }

    @Test
    void storageOutage_is503Retryable() throws Exception {
        var f = new GateStationFixture(dir, true, true);
        f.execute("""
                CREATE TRIGGER fail_sequence_update BEFORE UPDATE OF last_sequence_no ON edge_runtime_state
                BEGIN SELECT RAISE(ABORT, 'test write failure'); END
                """);
        var reply = post(f, SCANS, scanBody(UUID.randomUUID(), GATE_ID, f.token(EMPLOYEE_ID)));
        assertEquals(503, reply.status());
        assertEquals("STORAGE_UNAVAILABLE", json(reply).get("code").asText());
        assertTrue(json(reply).get("retryable").asBoolean());
        assertFalse(reply.body().contains("test write failure"), "không lộ chi tiết nội bộ của driver");
        assertEquals(0, f.turnstile.calls.get());
    }

    // ---------- requestId ----------

    @Test
    void requestId_isEchoedWhenValidUuidV4OtherwiseGenerated() throws Exception {
        var f = new GateStationFixture(dir, true, true);
        String supplied = UUID.randomUUID().toString();
        var echoed = f.controller.handle("POST", SCANS, bytes("{bad"), f.kiosk, supplied);
        assertEquals(supplied, echoed.requestId());
        assertEquals(supplied, json(echoed).get("requestId").asText());

        var generated = f.controller.handle("POST", SCANS, bytes("{bad"), f.kiosk, "not-a-uuid");
        assertNotEquals("not-a-uuid", generated.requestId());
        assertEquals(generated.requestId(), json(generated).get("requestId").asText());
    }

    // ---------- GET /scans/{id}, route lạ, presence ----------

    @Test
    void getScan_returnsRecentResultOr404() throws Exception {
        var f = new GateStationFixture(dir, true, true);
        UUID scanId = UUID.randomUUID();
        var scan = post(f, SCANS, scanBody(scanId, GATE_ID, f.token(EMPLOYEE_ID)));
        var fetched = f.controller.handle("GET", SCANS + "/" + scanId, null, f.kiosk, null);
        assertEquals(200, fetched.status());
        assertEquals(scan.body(), fetched.body());

        var missing = f.controller.handle("GET", SCANS + "/" + UUID.randomUUID(), null, f.kiosk, null);
        assertEquals(404, missing.status());
        assertEquals("NOT_FOUND", json(missing).get("code").asText());

        var badId = f.controller.handle("GET", SCANS + "/xyz", null, f.kiosk, null);
        assertEquals(400, badId.status());
    }

    @Test
    void unknownRouteAndWrongMethod_are404() throws Exception {
        var f = new GateStationFixture(dir, true, true);
        assertEquals(404, f.controller.handle("GET", "/edge/v1/nothing", null, f.kiosk, null).status());
        assertEquals(404, f.controller.handle("PUT", SCANS, bytes("{}"), f.kiosk, null).status());
        assertEquals(404, f.controller.handle("DELETE", "/edge/v1/presence", null, f.kiosk, null).status());
    }

    @Test
    void presence_exposesCoverageAndTrackingStart() throws Exception {
        var f = new GateStationFixture(dir, true, true);
        post(f, SCANS, scanBody(UUID.randomUUID(), GATE_ID, f.token(EMPLOYEE_ID)));
        var reply = f.controller.handle("GET", "/edge/v1/presence", null, f.kiosk, null);
        assertEquals(200, reply.status());
        JsonNode body = json(reply);
        assertEquals("LOCAL_EDGE", body.get("coverage").asText());
        assertTrue(body.get("asOf").asText().endsWith("Z"));
        assertTrue(body.has("trackedSince"));
        assertEquals(1, body.get("items").size());
        assertEquals(EMPLOYEE_ID.toString(), body.get("items").get(0).get("employeeId").asText());
        assertEquals("IN", body.get("items").get(0).get("lastDirection").asText());
    }

    // ---------- /overrides ----------

    @Test
    void overrideOverHttp_manualAndModeValidation() throws Exception {
        var f = new GateStationFixture(dir, true, true);
        String reason = "Điện thoại hết pin, đã đối chiếu thẻ";
        var grant = new VerifiedGrant(UUID.randomUUID(), OPERATOR_ID, CommandMode.MANUAL, EDGE_ID, GATE_ID,
                EMPLOYEE_ID, reason, T0.plusSeconds(60));
        f.grantVerifier.set((token, now) -> grant);

        String ok = "{\"scanId\":\"%s\",\"gateId\":\"%s\",\"employeeId\":\"%s\",\"reason\":\"%s\",\"mode\":\"MANUAL\",\"grant\":\"g\"}"
                .formatted(UUID.randomUUID(), GATE_ID, EMPLOYEE_ID, reason);
        var reply = post(f, "/edge/v1/overrides", ok);
        assertEquals(200, reply.status());
        assertEquals("ALLOW", json(reply).get("decision").asText());
        assertFalse(reply.body().contains("\"g\""), "không echo grant");

        String badMode = ok.replace("MANUAL", "QR");
        assertEquals(400, post(f, "/edge/v1/overrides", badMode).status());
    }

    // ---------- cầu nối JDK HttpServer (không cần socket: dùng HttpExchange giả trong bộ nhớ) ----------

    /** HttpExchange tối thiểu trong bộ nhớ để kiểm thử EdgeHttpHandler mà không mở cổng mạng. */
    private static final class FakeExchange extends com.sun.net.httpserver.HttpExchange {
        private final com.sun.net.httpserver.Headers requestHeaders = new com.sun.net.httpserver.Headers();
        private final com.sun.net.httpserver.Headers responseHeaders = new com.sun.net.httpserver.Headers();
        private final java.io.ByteArrayOutputStream responseBody = new java.io.ByteArrayOutputStream();
        private final String method;
        private final URI uri;
        private final byte[] body;
        int status = -1;

        FakeExchange(String method, String path, byte[] body) {
            this.method = method;
            this.uri = URI.create(path);
            this.body = body;
        }

        String responseText() {
            return responseBody.toString(StandardCharsets.UTF_8);
        }

        @Override public com.sun.net.httpserver.Headers getRequestHeaders() { return requestHeaders; }
        @Override public com.sun.net.httpserver.Headers getResponseHeaders() { return responseHeaders; }
        @Override public URI getRequestURI() { return uri; }
        @Override public String getRequestMethod() { return method; }
        @Override public com.sun.net.httpserver.HttpContext getHttpContext() { return null; }
        @Override public void close() {}
        @Override public java.io.InputStream getRequestBody() { return new java.io.ByteArrayInputStream(body); }
        @Override public java.io.OutputStream getResponseBody() { return responseBody; }
        @Override public void sendResponseHeaders(int rCode, long responseLength) { status = rCode; }
        @Override public InetSocketAddress getRemoteAddress() { return new InetSocketAddress(InetAddress.getLoopbackAddress(), 1); }
        @Override public int getResponseCode() { return status; }
        @Override public InetSocketAddress getLocalAddress() { return new InetSocketAddress(InetAddress.getLoopbackAddress(), 2); }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) {}
        @Override public void setStreams(java.io.InputStream i, java.io.OutputStream o) {}
        @Override public com.sun.net.httpserver.HttpPrincipal getPrincipal() { return null; }
    }

    @Test
    void httpHandler_writesContractHeadersAndBody() throws Exception {
        var f = new GateStationFixture(dir, true, true);
        var handler = new EdgeHttpHandler(f.controller, exchange -> Optional.of(f.kiosk));
        String requestId = UUID.randomUUID().toString();
        var exchange = new FakeExchange("POST", SCANS,
                bytes(scanBody(UUID.randomUUID(), GATE_ID, f.token(EMPLOYEE_ID))));
        exchange.getRequestHeaders().set("X-Request-Id", requestId);

        handler.handle(exchange);

        assertEquals(200, exchange.status);
        assertEquals("application/json; charset=utf-8", exchange.getResponseHeaders().getFirst("Content-Type"));
        assertEquals("no-store", exchange.getResponseHeaders().getFirst("Cache-Control"));
        assertEquals(requestId, exchange.getResponseHeaders().getFirst("X-Request-Id"));
        assertEquals("ALLOW", JSON.readTree(exchange.responseText()).get("decision").asText());
    }

    @Test
    void httpHandler_rejectsOversizedBodyWithoutRunningTheUseCase() throws Exception {
        var f = new GateStationFixture(dir, true, true);
        var handler = new EdgeHttpHandler(f.controller, exchange -> Optional.of(f.kiosk));
        var exchange = new FakeExchange("POST", SCANS, new byte[ScanController.MAX_BODY_BYTES + 100]);
        handler.handle(exchange);
        assertEquals(400, exchange.status);
        assertEquals(0, f.turnstile.calls.get());
    }

    @Test
    void httpHandler_rejectsUnrecognizedKioskWithoutTouchingTheUseCase() throws Exception {
        var f = new GateStationFixture(dir, true, true);
        var handler = new EdgeHttpHandler(f.controller, exchange -> Optional.empty());
        var exchange = new FakeExchange("POST", SCANS,
                bytes(scanBody(UUID.randomUUID(), GATE_ID, f.token(EMPLOYEE_ID))));
        handler.handle(exchange);
        assertEquals(403, exchange.status);
        assertEquals("DEVICE_BINDING_MISMATCH", JSON.readTree(exchange.responseText()).get("code").asText());
        assertEquals(0, f.turnstile.calls.get());
        assertEquals(0, f.events("attendance.event.recorded"));
    }

    /** Kiểm tra qua socket thật; tự bỏ qua trên máy không mở được kênh loopback NIO (đã gặp ở máy dev của Task 2). */
    @Test
    void httpHandler_servesOverRealSocket_whenLoopbackIsAvailable() throws Exception {
        try (var probe = java.nio.channels.Selector.open()) {
            assertNotNull(probe);
        } catch (java.io.IOException unavailable) {
            org.junit.jupiter.api.Assumptions.abort("Máy này không mở được loopback NIO: " + unavailable.getMessage());
        }
        var f = new GateStationFixture(dir, true, true);
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/edge/v1/", new EdgeHttpHandler(f.controller, exchange -> Optional.of(f.kiosk)));
        server.start();
        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + SCANS);
            var request = HttpRequest.newBuilder(uri)
                    .POST(HttpRequest.BodyPublishers.ofString(scanBody(UUID.randomUUID(), GATE_ID, f.token(EMPLOYEE_ID))))
                    .build();
            var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, response.statusCode());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        } finally {
            server.stop(0);
        }
    }
}
