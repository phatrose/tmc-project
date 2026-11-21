package vn.tmc.central.usermgmt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.tmc.central.usermgmt.api.*;
import vn.tmc.central.usermgmt.api.EmployeeManagement.CreateEmployeeCommand;
import vn.tmc.central.usermgmt.api.EmployeeManagement.UpdateEmployeeCommand;
import vn.tmc.central.usermgmt.internal.model.Employee;
import vn.tmc.central.usermgmt.internal.repository.DepartmentRepository;
import vn.tmc.central.usermgmt.internal.repository.EmployeeRepository;
import vn.tmc.central.usermgmt.internal.repository.PolicyAssignmentRepository;
import vn.tmc.central.usermgmt.internal.repository.UserAccountRepository;
import vn.tmc.central.usermgmt.internal.service.EmployeeNotFoundException;
import vn.tmc.central.usermgmt.internal.service.EmployeeService;
import vn.tmc.central.usermgmt.internal.service.ForbiddenException;
import vn.tmc.central.usermgmt.internal.service.VersionConflictException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PHỤ TRÁCH: Task 1.
 * Unit tests cho EmployeeService theo CONTRACT-V1.md §4.
 */
@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private PolicyAssignmentRepository policyAssignmentRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private EmployeeService employeeService;

    private ActorContext adminActor;
    private ActorContext selfActor;

    @BeforeEach
    void setUp() {
        employeeService = new EmployeeService(
                employeeRepository,
                policyAssignmentRepository,
                departmentRepository,
                userAccountRepository,
                passwordEncoder
        );

        adminActor = new ActorContext(
                "user-admin", null, List.of(Role.ADMIN),
                List.of("employee.manage", "access-policy.manage", "system.manage"),
                List.of("FAC-01"), List.of(), "GLOBAL"
        );

        selfActor = new ActorContext(
                "user-emp", "emp-001", List.of(Role.EMPLOYEE),
                List.of("employee.self"),
                List.of("FAC-01"), List.of(), "SELF"
        );
    }

    @Test
    @DisplayName("getEmployee: tìm thấy nhân viên trả về EmployeeView khớp version")
    void getEmployee_found() {
        Employee employee = new Employee("EMP001", "Nguyễn Văn A", "FAC-01", "DEPT-01", true);

        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));

        EmployeeView view = employeeService.getEmployee(adminActor, employee.getId());

        assertThat(view).isNotNull();
        assertThat(view.employeeCode()).isEqualTo("EMP001");
        assertThat(view.displayName()).isEqualTo("Nguyễn Văn A");
        assertThat(view.version()).isEqualTo(1);
        assertThat(view.active()).isTrue();
    }

    @Test
    @DisplayName("getEmployee: SELF không được xem nhân viên khác")
    void getEmployee_selfScope_forbiddenOther() {
        Employee other = new Employee("EMP002", "Trần Thị B", "FAC-01", "DEPT-01", true);
        when(employeeRepository.findById(other.getId())).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> employeeService.getEmployee(selfActor, other.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("createEmployee: tạo nhân viên thành công với version khởi tạo là 1")
    void createEmployee_success() {
        CreateEmployeeCommand cmd = new CreateEmployeeCommand("EMP003", "Lê Văn C", "FAC-01", "DEPT-01", true);

        when(employeeRepository.existsByEmployeeCode("EMP003")).thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeView created = employeeService.createEmployee(adminActor, cmd);

        assertThat(created).isNotNull();
        assertThat(created.employeeCode()).isEqualTo("EMP003");
        assertThat(created.version()).isEqualTo(1);
        assertThat(created.active()).isTrue();
        verify(employeeRepository).save(any(Employee.class));
    }

    @Test
    @DisplayName("updateEmployee: ném VersionConflictException nếu version không khớp")
    void updateEmployee_versionMismatch_throwsException() {
        Employee employee = new Employee("EMP004", "Phạm Văn D", "FAC-01", "DEPT-01", true);
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));

        UpdateEmployeeCommand cmd = new UpdateEmployeeCommand("EMP004", "Phạm Văn D (Mới)", "FAC-01", "DEPT-01", true, 99);

        assertThatThrownBy(() -> employeeService.updateEmployee(adminActor, employee.getId(), cmd))
                .isInstanceOf(VersionConflictException.class);
    }

    @Test
    @DisplayName("listEmployees: trả về PageResponse đúng cấu hình phân trang")
    void listEmployees_pagedResult() {
        Employee emp = new Employee("EMP005", "Hoàng Văn E", "FAC-01", null, true);
        when(employeeRepository.findByFactoryId(eq("FAC-01"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(emp)));

        PageResponse<EmployeeView> result = employeeService.listEmployees(adminActor, "FAC-01", null, null, 0, 20);

        assertThat(result).isNotNull();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).employeeCode()).isEqualTo("EMP005");
    }
}
