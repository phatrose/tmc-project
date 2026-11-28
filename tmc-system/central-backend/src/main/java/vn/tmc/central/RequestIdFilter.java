package vn.tmc.central;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** PHỤ TRÁCH: Task 8. Correlation UUIDv4 chạy trước security theo Contract §2. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    private static final Pattern UUID4 = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private final ObjectMapper mapper;
    public RequestIdFilter(ObjectMapper mapper) { this.mapper = mapper; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader("X-Request-Id");
        String id = supplied != null && UUID4.matcher(supplied).matches()
                ? supplied : UUID.randomUUID().toString();
        request.setAttribute(PlatformErrors.REQUEST_ID, id);
        response.setHeader("X-Request-Id", id);
        if (supplied != null && !UUID4.matcher(supplied).matches()) {
            PlatformErrors.write(mapper, request, response, 400, "VALIDATION_ERROR",
                    "X-Request-Id must be a lowercase UUIDv4.", false);
            return;
        }
        chain.doFilter(request, response);
    }
}
