package vn.tmc.central;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import vn.tmc.contracts.ApiError;

/** PHỤ TRÁCH: Task 8. Cùng shape lỗi cho MVC và security; không lộ exception/secret. */
public final class PlatformErrors {
    public static final String REQUEST_ID = PlatformErrors.class.getName() + ".requestId";
    private PlatformErrors() {}
    public static String requestId(HttpServletRequest request) {
        Object existing = request.getAttribute(REQUEST_ID);
        if (existing != null) return existing.toString();
        String id = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID, id);
        return id;
    }
    public static void write(ObjectMapper mapper, HttpServletRequest request,
                             HttpServletResponse response, int status, String code,
                             String message, boolean retryable) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("X-Request-Id", requestId(request));
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(),
                new ApiError(code, message, requestId(request), retryable));
    }
}
