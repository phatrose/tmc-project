package vn.tmc.central.usermgmt.internal;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.tmc.central.usermgmt.api.Role;
import vn.tmc.central.usermgmt.internal.model.Employee;
import vn.tmc.central.usermgmt.internal.model.UserAccount;
import vn.tmc.central.usermgmt.internal.repository.EmployeeRepository;
import vn.tmc.central.usermgmt.internal.repository.UserAccountRepository;

/** PHỤ TRÁCH: Task 8. Tài khoản test cho profile integration-preview (không dùng cho production). */
@Configuration(proxyBeanMethods = false)
@Profile("integration-preview")
public class PreviewSeed {

    private static final String FACTORY_BD = "00000000-0000-4000-8000-000000000023";

    @Bean
    @org.springframework.core.annotation.Order(10)
    ApplicationRunner seedPreview(
            UserAccountRepository accounts,
            EmployeeRepository employees,
            PasswordEncoder encoder,
            @Value("${TMC_PREVIEW_PASSWORD:123456}") String password
    ) {
        return args -> {
            if (password == null || password.length() < 6) {
                throw new IllegalArgumentException("Preview password must contain at least 6 characters");
            }
            String hash = encoder.encode(password);

            seedAccount(accounts, employees, "preview.admin", "PREVIEW-001", "Nhân viên thử nghiệm",
                    hash, List.of(Role.ADMIN, Role.HR, Role.EMPLOYEE), "GLOBAL");
            seedAccount(accounts, employees, "admin", "ADM-001", "Quản trị viên hệ thống",
                    hash, List.of(Role.ADMIN, Role.HR, Role.EMPLOYEE), "GLOBAL");
            seedAccount(accounts, employees, "hr", "HR-001", "Chuyên viên Nhân sự",
                    hash, List.of(Role.HR, Role.EMPLOYEE), "GLOBAL");
            seedAccount(accounts, employees, "guard", "GRD-001", "Bảo vệ cổng chính",
                    hash, List.of(Role.GUARD, Role.EMPLOYEE), "FACTORY");
            seedAccount(accounts, employees, "auditor", "AUD-001", "Kiểm toán viên",
                    hash, List.of(Role.AUDITOR), "GLOBAL");
            seedAccount(accounts, employees, "emp", "EMP-001", "Nguyễn Văn An",
                    hash, List.of(Role.EMPLOYEE), "SELF");
        };
    }

    private static void seedAccount(
            UserAccountRepository accounts,
            EmployeeRepository employees,
            String username,
            String code,
            String fullName,
            String passwordHash,
            List<Role> roles,
            String scope
    ) {
        if (accounts.findByUsername(username).isPresent()) {
            return;
        }
        var employee = employees.save(new Employee(code, fullName, FACTORY_BD, null, true));
        accounts.save(new UserAccount(
                username,
                passwordHash,
                employee.getId(),
                roles,
                List.of(employee.getFactoryId()),
                List.of(),
                scope
        ));
    }
}
