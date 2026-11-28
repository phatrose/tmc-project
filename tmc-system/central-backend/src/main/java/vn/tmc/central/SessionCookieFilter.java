package vn.tmc.central;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * PHỤ TRÁCH: Task 8 — hỗ trợ khôi phục phiên an toàn qua cookie HttpOnly; SameSite=Strict khi reload trang (F5).
 * Nếu request chưa có header Authorization nhưng có cookie tmc_session, filter ánh xạ thành Authorization: Bearer <token>
 * để toàn bộ SecurityFilterChain và controller dùng chung một luồng xác thực duy nhất.
 * Header Authorization (nếu client truyền trực tiếp) luôn được ưu tiên hơn cookie.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public final class SessionCookieFilter extends OncePerRequestFilter {

    public static final String SESSION_COOKIE_NAME = "tmc_session";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (req.getHeader("Authorization") == null) {
            String cookieToken = extractSessionCookie(req);
            if (cookieToken != null && !cookieToken.isBlank()) {
                final String bearer = "Bearer " + cookieToken.trim();
                HttpServletRequest wrapped = new HttpServletRequestWrapper(req) {
                    @Override
                    public String getHeader(String name) {
                        if ("Authorization".equalsIgnoreCase(name)) {
                            return bearer;
                        }
                        return super.getHeader(name);
                    }

                    @Override
                    public Enumeration<String> getHeaders(String name) {
                        if ("Authorization".equalsIgnoreCase(name)) {
                            return Collections.enumeration(List.of(bearer));
                        }
                        return super.getHeaders(name);
                    }
                };
                chain.doFilter(wrapped, res);
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private static String extractSessionCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
