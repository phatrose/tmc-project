package vn.tmc.central;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatterBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/** PHỤ TRÁCH: Task 8. Nền tảng HTTP; Task 1 cung cấp chain xác thực thật thay fallback. */
@Configuration(proxyBeanMethods = false)
public class PlatformConfiguration {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer wireTime() {
        return builder -> builder.serializerByType(Instant.class, new JsonSerializer<Instant>() {
            @Override public void serialize(Instant value, JsonGenerator gen, SerializerProvider provider)
                    throws IOException {
                gen.writeString(new DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(value));
            }
        });
    }
    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    UserDetailsService noDefaultUsers() {
        return username -> { throw new UsernameNotFoundException("Authentication not configured"); };
    }
    @Bean
    @org.springframework.context.annotation.Profile("!integration-preview")
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain platformSecurity(HttpSecurity http, ObjectMapper mapper) throws Exception {
        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(c -> c.disable()) // Không cookie auth; fallback chặn toàn bộ mutation.
            .requestCache(c -> c.disable())
            .formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable())
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                .anyRequest().denyAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> PlatformErrors.write(mapper, req, res,
                    401, "UNAUTHENTICATED", "Authentication is required.", false))
                .accessDeniedHandler((req, res, ex) -> PlatformErrors.write(mapper, req, res,
                    403, "FORBIDDEN", "Access is denied.", false)));
        return http.build();
    }
}
