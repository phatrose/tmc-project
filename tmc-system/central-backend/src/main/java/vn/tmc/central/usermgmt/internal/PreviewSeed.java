package vn.tmc.central.usermgmt.internal;
import org.springframework.context.annotation.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.tmc.central.usermgmt.internal.model.*;
import vn.tmc.central.usermgmt.internal.repository.*;
import vn.tmc.central.usermgmt.api.Role;
import java.util.List;
/** PHỤ TRÁCH: Task 8. Explicit test account only; password required from environment. */
@Configuration(proxyBeanMethods=false) @Profile("integration-preview")
public class PreviewSeed {
 @Bean @org.springframework.core.annotation.Order(10) ApplicationRunner seedPreview(UserAccountRepository accounts,EmployeeRepository employees,PasswordEncoder encoder,
  @Value("${TMC_PREVIEW_PASSWORD}") String password){return args->{
   if(password.length()<12)throw new IllegalArgumentException("Preview password must contain at least 12 characters");
   if(accounts.findByUsername("preview.admin").isEmpty()){
    var employee=employees.save(new Employee("PREVIEW-001","Nhân viên thử nghiệm","00000000-0000-4000-8000-000000000023",null,true));
    var account=accounts.save(new UserAccount("preview.admin",encoder.encode(password),employee.getId(),List.of(Role.ADMIN,Role.HR,Role.EMPLOYEE),List.of(employee.getFactoryId()),List.of(),"GLOBAL"));
   }
  };}
}
