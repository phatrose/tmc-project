package vn.tmc.central.reportingaudit.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.PlatformTransactionManager;
import vn.tmc.central.reportingaudit.internal.service.*;

@Configuration(proxyBeanMethods=false)
@Profile("hr-mock")
public class HrMockConfiguration {
    @Bean HrMockService hrMockService(NamedParameterJdbcTemplate jdbc,PlatformTransactionManager transactions,ObjectMapper json,
            PayrollPayload codec,@Value("${tmc.reporting.hr-service-token:${HR_SERVICE_TOKEN:}}") String token,@Value("${tmc.hr-mock.mode:SUCCESS}") HrMockService.Mode mode) {
        return new HrMockService(jdbc,transactions,json,codec,token,()->mode);
    }
    @Bean @Order(10) SecurityFilterChain hrMockSecurity(HttpSecurity http)throws Exception {
        http.securityMatcher("/hr/v1/payroll/batches").csrf(csrf->csrf.disable()).requestCache(cache->cache.disable())
                .sessionManagement(session->session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth->auth.anyRequest().permitAll());
        return http.build();
    }
}
