package vn.tmc.central;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.tmc.central.usermgmt.api.TokenIssuerPort;
/** PHỤ TRÁCH: Task 8. Test-only wiring; attendance remains in memory. */
@Configuration(proxyBeanMethods=false) @Profile("integration-preview")
public class PreviewConfiguration {
 @Bean PasswordEncoder passwordEncoder(){return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();}
 @Bean TokenIssuerPort actionGrantIssuer(Clock clock, ObjectMapper objectMapper){return new Es256ActionGrantIssuer("action-grant-es256-v1", clock, objectMapper);}
}
