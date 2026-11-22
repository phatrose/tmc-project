package vn.tmc.central.reportingaudit.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import vn.tmc.central.reportingaudit.internal.controller.ReportingHttp;
import vn.tmc.central.reportingaudit.internal.adapter.ReportingSessionFilter;
import vn.tmc.central.reportingaudit.api.ReportingSources.SessionQuery;

@Configuration(proxyBeanMethods=false)
@Profile("!platform")
public class ReportingSecurityConfiguration {
    @Bean
    @Order(20)
    SecurityFilterChain reportingSecurity(HttpSecurity http, ObjectProvider<SessionQuery> sessions,
            ObjectMapper json) throws Exception {
        http.securityMatcher("/api/v1/reports/**", "/api/v1/payroll/**", "/api/v1/audit/**")
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable()).requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable()).logout(logout -> logout.disable())
                .addFilterBefore(new ReportingSessionFilter(sessions, json), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, failure) ->
                        ReportingHttp.write(json, request, response, 401, "UNAUTHENTICATED", "Cần đăng nhập", false))
                        .accessDeniedHandler((request, response, failure) ->
                                ReportingHttp.write(json, request, response, 403, "FORBIDDEN", "Không có quyền truy cập", false)));
        return http.build();
    }
}
