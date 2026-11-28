package vn.tmc.central.attendanceprocessing.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.tmc.central.attendanceprocessing.internal.repository.AttendanceAdjustmentRepository;
import vn.tmc.central.attendanceprocessing.internal.repository.AttendanceRepository;
import vn.tmc.central.attendanceprocessing.internal.repository.DailyAttendanceRepository;
import vn.tmc.central.attendanceprocessing.internal.repository.InMemoryAttendanceRepository;
import vn.tmc.central.attendanceprocessing.internal.repository.JdbcAttendanceAdjustmentRepository;
import vn.tmc.central.attendanceprocessing.internal.repository.JdbcAttendanceRepository;
import vn.tmc.central.attendanceprocessing.internal.repository.SyncConflictRepository;
import vn.tmc.central.attendanceprocessing.internal.service.*;
import vn.tmc.central.usermgmt.api.ActorContext;
import vn.tmc.central.usermgmt.api.EmployeeManagement;
import vn.tmc.central.usermgmt.api.Role;

/** PHỤ TRÁCH: Task 8 — tích hợp với Task 1 & Task 3. Events, Daily, Adjustments, SyncConflicts & Idempotency lưu PostgreSQL bền vững (V008-V010). */
@Configuration(proxyBeanMethods = false)
@Profile("!platform")
public class PreviewAttendanceConfiguration {

    private static final ActorContext SYSTEM_SCOPE_LOOKUP = new ActorContext(
            "00000000-0000-4000-8000-000000000001",
            null,
            List.of(Role.ADMIN),
            List.of("employee.read"),
            List.of(),
            List.of(),
            ActorContext.SCOPE_GLOBAL
    );

    @Bean
    @Primary
    AttendanceRepository previewAttendanceRepository(@Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return jdbcTemplate != null ? new JdbcAttendanceRepository(jdbcTemplate) : new InMemoryAttendanceRepository();
    }

    @Bean
    @Primary
    AttendanceAdjustmentRepository previewAdjustmentRepository(
            AttendanceRepository attendanceRepo,
            @Autowired(required = false) JdbcTemplate jdbcTemplate
    ) {
        if (jdbcTemplate != null) {
            return new JdbcAttendanceAdjustmentRepository(jdbcTemplate);
        }
        return attendanceRepo instanceof AttendanceAdjustmentRepository adj ? adj : new InMemoryAttendanceRepository();
    }

    @Bean
    AttendanceIdempotencyService attendanceIdempotencyService(
            @Autowired(required = false) JdbcTemplate jdbcTemplate,
            @Autowired(required = false) PlatformTransactionManager txManager,
            @Autowired(required = false) ObjectMapper objectMapper
    ) {
        return new AttendanceIdempotencyService(jdbcTemplate, txManager, objectMapper);
    }

    @Bean
    AttendanceAdjustmentService previewAdjustments(
            AttendanceAdjustmentRepository adjustmentRepo,
            DailyAttendanceRepository dailyRepo,
            EmployeeManagement employeeManagement
    ) {
        return new AttendanceAdjustmentService(adjustmentRepo, dailyRepo, employeeId -> {
            try {
                var emp = employeeManagement.getEmployee(SYSTEM_SCOPE_LOOKUP, employeeId.toString());
                return emp == null ? null : emp.factoryId();
            } catch (RuntimeException ignored) {
                return null;
            }
        });
    }

    @Bean
    AttendanceProcessingService previewAttendance(
            AttendanceRepository eventRepo,
            DailyAttendanceRepository dailyRepo,
            SyncConflictRepository conflictRepo,
            AttendanceAdjustmentService adjustments,
            EmployeeManagement employeeManagement
    ) {
        return new AttendanceProcessingService(eventRepo, dailyRepo, conflictRepo, adjustments, employeeId -> {
            try {
                var emp = employeeManagement.getEmployee(SYSTEM_SCOPE_LOOKUP, employeeId.toString());
                return emp == null ? null : emp.departmentId();
            } catch (RuntimeException ignored) {
                return null;
            }
        });
    }
}
