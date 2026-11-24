package vn.tmc.edge.gatestation.infrastructure;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * PHỤ TRÁCH: Task 2. Cầu nối JDK HttpServer ↔ ScanController. Gắn bằng
 * server.createContext("/edge/v1/", new EdgeHttpHandler(controller, resolver)); server dùng HttpsServer với
 * client auth bắt buộc do EdgeApplication (Task 7) dựng. Handler không tự quyết danh tính, không đọc quá
 * MAX_BODY_BYTES, luôn trả JSON UTF-8, Cache-Control: no-store và echo X-Request-Id.
 */
public final class EdgeHttpHandler implements HttpHandler {
    private static final String NOT_RECOGNIZED = "{\"code\":\"DEVICE_BINDING_MISMATCH\","
            + "\"message\":\"Thiết bị không được nhận diện.\",\"requestId\":\"%s\",\"retryable\":false}";

    private final ScanController controller;
    private final KioskResolver resolver;

    public EdgeHttpHandler(ScanController controller, KioskResolver resolver) {
        this.controller = Objects.requireNonNull(controller);
        this.resolver = Objects.requireNonNull(resolver);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String requestIdHeader = exchange.getRequestHeaders().getFirst("X-Request-Id");
            var kiosk = resolver.resolve(exchange);
            ScanController.Reply reply;
            if (kiosk.isEmpty()) {
                String requestId = java.util.UUID.randomUUID().toString();
                reply = new ScanController.Reply(403, NOT_RECOGNIZED.formatted(requestId), requestId);
            } else {
                // Đọc tối đa MAX+1 byte để biết body có vượt giới hạn mà không nạp cả body lớn vào bộ nhớ.
                byte[] body = exchange.getRequestBody().readNBytes(ScanController.MAX_BODY_BYTES + 1);
                reply = controller.handle(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), body,
                        kiosk.get(), requestIdHeader);
            }
            byte[] payload = reply.body().getBytes(StandardCharsets.UTF_8);
            var headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/json; charset=utf-8");
            headers.set("Cache-Control", "no-store");
            headers.set("X-Request-Id", reply.requestId());
            exchange.sendResponseHeaders(reply.status(), payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        }
    }
}
