package vn.tmc.central.reportingaudit.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.tmc.central.reportingaudit.api.ReportingSources.AttendanceSource;
import vn.tmc.central.reportingaudit.api.ReportingSources.PayrollAttendance;
import vn.tmc.central.reportingaudit.api.ReportingSources.EmployeeSource;
import vn.tmc.central.reportingaudit.api.ReportingSources.RosterQuery;
import vn.tmc.central.reportingaudit.api.ReportingSources.ConfigurationQuery;
import vn.tmc.central.reportingaudit.internal.adapter.PostgresPayrollRepository;
import vn.tmc.central.reportingaudit.internal.adapter.HrPayrollAdapter;
import vn.tmc.central.reportingaudit.internal.adapter.PostgresReportingRepository;
import vn.tmc.central.reportingaudit.internal.adapter.PostgresAuditRepository;
import vn.tmc.central.reportingaudit.internal.service.*;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@org.springframework.context.annotation.Profile("!platform")
public class ReportingConfiguration {
    @Bean
    PostgresAuditRepository auditRepository(NamedParameterJdbcTemplate jdbc) {
        return new PostgresAuditRepository(jdbc);
    }

    @Bean
    AuditService auditService(PostgresAuditRepository repository) {
        return new AuditService(repository);
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(Clock.class)
    Clock reportingClock() { return Clock.systemUTC(); }

    @Bean
    PostgresReportingRepository reportingRepository(NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactions, ObjectMapper json) {
        return new PostgresReportingRepository(jdbc, transactions, json);
    }

    @Bean
    ReportingAccess reportingAccess(ObjectProvider<EmployeeSource> employees) {
        return new ReportingAccess(employees::getIfAvailable);
    }

    @Bean
    AttendanceReportService attendanceReportService(ObjectProvider<AttendanceSource> source, ReportingAccess access) {
        return new AttendanceReportService(source::getIfAvailable, access);
    }

    @Bean
    ReportExportService reportExportService(PostgresReportingRepository repository, ReportingAccess access, Clock clock) {
        return new ReportExportService(repository, access, clock);
    }

    @Bean
    AuditQueryService auditQueryService(PostgresReportingRepository repository, ReportingAccess access, Clock clock) {
        return new AuditQueryService(repository, access, clock);
    }

    @Bean
    ReportFileExporter reportFileExporter(@Value("${tmc.reporting.pdf-font:}") String font) {
        return new ReportFileExporter(font);
    }

    @Bean
    ReportExportWorker reportExportWorker(PostgresReportingRepository repository, AttendanceReportService reports,
            ReportFileExporter exporter, Clock clock) {
        return new ReportExportWorker(repository, reports, exporter, clock);
    }

    @Bean PayrollPayload payrollPayload(ObjectMapper json) {return new PayrollPayload(json);}
    @Bean HrReceiptValidator hrReceiptValidator() {return new HrReceiptValidator();}
    @Bean PostgresPayrollRepository payrollRepository(NamedParameterJdbcTemplate jdbc,PlatformTransactionManager transactions,ObjectMapper json) {
        return new PostgresPayrollRepository(jdbc,transactions,json);
    }
    @Bean PayrollExportService payrollExportService(PostgresPayrollRepository repository,PostgresReportingRepository idempotency,
            ObjectProvider<PayrollAttendance> attendance,ObjectProvider<ConfigurationQuery> configuration,ReportingAccess access,PayrollPayload payload,AuditService audit,Clock clock) {
        return new PayrollExportService(repository,idempotency,attendance::getIfAvailable,configuration::getIfAvailable,access,payload,audit,clock);
    }
    @Bean DashboardService dashboardService(AttendanceReportService reports,ObjectProvider<ConfigurationQuery> configuration,ObjectProvider<RosterQuery> roster,
            ReportingAccess access,Clock clock) {return new DashboardService(reports,configuration::getIfAvailable,roster::getIfAvailable,access,clock);}
    @Bean HrPayrollPort hrPayrollPort(@Value("${tmc.reporting.hr-base-url:${HR_BASE_URL:}}") String url,
            @Value("${tmc.reporting.hr-service-token:${HR_SERVICE_TOKEN:}}") String token,@Value("${tmc.reporting.hr-timeout-ms:${HR_TIMEOUT_MS:15000}}") long timeout,ObjectMapper json) {
        var client=java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5))
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        return new HrPayrollAdapter(client,url.isBlank()?null:java.net.URI.create(url),token,java.time.Duration.ofMillis(timeout),json);
    }
    @Bean PayrollOutboxWorker payrollOutboxWorker(PostgresPayrollRepository repository,HrPayrollPort hr,PayrollPayload payload,
            HrReceiptValidator validator,PayrollExportService payroll,Clock clock) {
        return new PayrollOutboxWorker(repository,hr,payload,validator,payroll,clock,()->java.util.concurrent.ThreadLocalRandom.current().nextLong(1000));
    }
    @Bean PayrollScheduler payrollScheduler(ObjectProvider<ConfigurationQuery> configuration,PayrollExportService payroll,Clock clock) {
        return new PayrollScheduler(configuration::getIfAvailable,payroll,clock);
    }
}
