package vn.tmc.central;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.List;
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
 * PHỤ TRÁCH: Task 8 (DEVICE-01, DEVICE-03, DEVICE-04).
 * Real Task 1 sessions cho người dùng + mTLS X.509 SAN URI bắt buộc cho thiết bị Edge.
 * KHÔNG chấp nhận header tự khai (X-Edge-Auth / X-Edge-Id / X-Factory-Id) làm fallback.
 */
@Configuration(proxyBeanMethods = false)
@Profile("integration-preview")
public class PreviewSecurityConfiguration {

    @Bean
    @Order(30)
    SecurityFilterChain previewSecurity(
            HttpSecurity http,
            Authentication sessions,
            CentralMtlsDeviceAuthenticator mtlsAuthenticator,
            ObjectMapper json
    ) throws Exception {
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
                        X509Certificate[] clientCerts = (X509Certificate[]) req.getAttribute("jakarta.servlet.request.X509Certificate");
                        if (clientCerts != null && clientCerts.length > 0) {
                            var device = mtlsAuthenticator.authenticate(clientCerts);
                            if (device.isEmpty()) {
                                PlatformErrors.write(json, req, res, 403, "DEVICE_BINDING_MISMATCH",
                                        "Client certificate is not provisioned or missing valid SAN URI.", false);
                                return;
                            }
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(
                                            device.get(),
                                            clientCerts[0],
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
}
