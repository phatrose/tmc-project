package vn.tmc.central.reportingaudit.internal.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.tmc.central.reportingaudit.internal.controller.ReportingHttp;
import vn.tmc.central.reportingaudit.api.ReportingSources.ActorContext;
import vn.tmc.central.reportingaudit.api.ReportingSources.SessionQuery;
import java.io.IOException;

public final class ReportingSessionFilter extends OncePerRequestFilter {
    private final ObjectProvider<SessionQuery> sessions;
    private final ObjectMapper json;

    public ReportingSessionFilter(ObjectProvider<SessionQuery> sessions, ObjectMapper json) {
        this.sessions = sessions;
        this.json = json;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            if (!header.startsWith("Bearer ") || header.length() <= 7) {
                ReportingHttp.write(json, request, response, 401, "UNAUTHENTICATED", "Cần phiên Bearer hợp lệ", false);
                return;
            }
            var authentication = sessions.getIfAvailable();
            if (authentication == null) {
                ReportingHttp.write(json, request, response, 503, "DEPENDENCY_UNAVAILABLE", "Dịch vụ phiên chưa khả dụng", true);
                return;
            }
            ActorContext actor;
            try {
                actor = authentication.getActorContext(header);
                if (actor == null) throw new IllegalStateException();
            } catch (RuntimeException failure) {
                SecurityContextHolder.clearContext();
                ReportingHttp.write(json, request, response, 401, "UNAUTHENTICATED", "Phiên không hợp lệ hoặc đã hết hạn", false);
                return;
            }
            var authorities = actor.permissions().stream().map(SimpleGrantedAuthority::new).toList();
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor, null, authorities));
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
