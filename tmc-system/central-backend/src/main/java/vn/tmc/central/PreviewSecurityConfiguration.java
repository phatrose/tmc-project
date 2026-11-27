package vn.tmc.central;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.tmc.central.usermgmt.api.Authentication;
/** PHỤ TRÁCH: Task 8. Real Task 1 sessions; test profile only. Device routes stay closed until mTLS wiring. */
@Configuration(proxyBeanMethods=false) @Profile("integration-preview")
public class PreviewSecurityConfiguration {
 @Bean @Order(30) SecurityFilterChain previewSecurity(HttpSecurity http,Authentication sessions,ObjectMapper json)throws Exception{
  http.sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .csrf(c->c.disable()).requestCache(c->c.disable()).formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable())
   .addFilterBefore(new OncePerRequestFilter(){
    protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
     String bearer=req.getHeader("Authorization");
     if(bearer!=null){try{if(!bearer.startsWith("Bearer "))throw new IllegalArgumentException();var actor=sessions.getActorContext(bearer);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,bearer,java.util.List.of()));}
      catch(RuntimeException ex){PlatformErrors.write(json,req,res,401,"UNAUTHENTICATED","Invalid session.",false);return;}}
     chain.doFilter(req,res);
    }
   },UsernamePasswordAuthenticationFilter.class)
   .authorizeHttpRequests(a->a.requestMatchers("/actuator/health","/api/v1/auth/login").permitAll()
    .requestMatchers("/api/v1/attendance/sync","/api/v1/qr/keys/refresh","/api/v1/edge/**","/api/v1/attendance/adjustments/legacy-*").denyAll().anyRequest().authenticated())
   .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->PlatformErrors.write(json,req,res,401,"UNAUTHENTICATED","Login required.",false))
    .accessDeniedHandler((req,res,ex)->PlatformErrors.write(json,req,res,403,"FORBIDDEN","Access denied.",false)));
  return http.build();
 }
}
