package vn.tmc.central;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.tmc.central.usermgmt.api.TokenIssuerPort;
/** PHỤ TRÁCH: Task 8. Test-only wiring; attendance remains in memory. */
@Configuration(proxyBeanMethods=false) @Profile("integration-preview")
public class PreviewConfiguration {
 @Bean PasswordEncoder passwordEncoder(){return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();}
 @Bean TokenIssuerPort actionGrantIssuer(){return (id,role,scopes)->{throw new ApiException(503,"DEPENDENCY_UNAVAILABLE","Action-grant signer is not integrated.",true);};}
}
