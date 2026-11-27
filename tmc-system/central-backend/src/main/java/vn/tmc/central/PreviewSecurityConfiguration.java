package vn.tmc.central;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.tmc.central.usermgmt.api.Authentication;

/**
 * PHỤ TRÁCH: Task 8. Real Task 1 sessions + optional env-configured Edge device auth (fail-closed when unset).
 */
@Configuration(proxyBeanMethods = false)
@Profile("integration-preview")
public class PreviewSecurityConfiguration {

    @Bean
    @Order(30)
    SecurityFilterChain previewSecurity(
            HttpSecurity http,
            Authentication sessions,
            ObjectMapper json,
            @Value("${TMC_EDGE_SHARED_TOKEN:}") String edgeSharedToken
    ) throws Exception {
        final String expectedEdgeToken = edgeSharedToken == null ? "" : edgeSharedToken.trim();
        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(c -> c.disable())
                .requestCache(c -> c.disable())
                .formLogin(c -> c.disable())
                .httpBasic(c -> c.disable())
                .logout(c -> c.disable())
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                            throws ServletException, IOException {
                        String edgeAuth = req.getHeader("X-Edge-Auth");
                        if (edgeAuth != null) {
                            if (expectedEdgeToken.isEmpty() || !constantTimeEquals(expectedEdgeToken, edgeAuth)) {
                                PlatformErrors.write(json, req, res, 401, "UNAUTHENTICATED", "Invalid edge device credentials.", false);
                                return;
                            }
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(
                                            "edge-device",
                                            null,
                                            List.of(new SimpleGrantedAuthority("ROLE_EDGE_DEVICE"))));
                            chain.doFilter(req, res);
                            return;
                        }
                        String bearer = req.getHeader("Authorization");
                        if (bearer != null) {
                            try {
                                if (!bearer.startsWith("Bearer ")) throw new IllegalArgumentException();
                                var actor = sessions.getActorContext(bearer);
                                SecurityContextHolder.getContext().setAuthentication(
                                        new UsernamePasswordAuthenticationToken(actor, bearer, List.of()));
                            } catch (RuntimeException ex) {
                                PlatformErrors.write(json, req, res, 401, "UNAUTHENTICATED", "Invalid session.", false);
                                return;
                            }
                        }
                        chain.doFilter(req, res);
                    }
                }, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/attendance/adjustments/legacy-*").denyAll()
                        .requestMatchers("/api/v1/attendance/sync", "/api/v1/qr/keys/refresh", "/api/v1/edge/**")
                        .hasAuthority("ROLE_EDGE_DEVICE")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                PlatformErrors.write(json, req, res, 401, "UNAUTHENTICATED", "Login required.", false))
                        .accessDeniedHandler((req, res, ex) ->
                                PlatformErrors.write(json, req, res, 403, "FORBIDDEN", "Access denied.", false)));
        return http.build();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
