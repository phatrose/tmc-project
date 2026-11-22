package vn.tmc.central.reportingaudit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vn.tmc.central.reportingaudit.api.ReportingSources.AttendanceFilter;
import vn.tmc.central.reportingaudit.api.*;
import vn.tmc.central.reportingaudit.internal.model.ExportJob;
import vn.tmc.central.reportingaudit.internal.service.ReportingException;
import vn.tmc.central.reportingaudit.api.ReportingSources.ActorContext;
import vn.tmc.central.usermgmt.api.Role;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static vn.tmc.central.reportingaudit.ReportingFixtures.id;
import static vn.tmc.central.reportingaudit.ReportingFixtures.actor;

class ReportExportsPostgresIT extends ReportingIntegrationFixture {
    @ParameterizedTest @EnumSource(ReportExport.Format.class)
    void createsRealFileAndExpiresExactlyAfter24Hours(ReportExport.Format format){
        night();var job=exports.attendance(hr,FACTORY,"2026-10",format,UUID.randomUUID());
        assertThatThrownBy(()->exports.file(hr,job.id(),ExportJob.Kind.ATTENDANCE)).isInstanceOf(ReportingException.class)
                .extracting("code").isEqualTo("INVALID_STATE");
        assertThat(exportWorker.runOnce()).isTrue();var ready=exports.status(hr,job.id(),ExportJob.Kind.ATTENDANCE);
        assertThat(ready.state()).isEqualTo(ReportExport.State.READY);assertThat(ready.expiresAt()).isEqualTo(job.createdAt().plus(Duration.ofHours(24)));
        assertThat(exports.file(hr,job.id(),ExportJob.Kind.ATTENDANCE).bytes()).isNotEmpty();
        clock.set(ready.expiresAt());
        assertThatThrownBy(()->exports.file(hr,job.id(),ExportJob.Kind.ATTENDANCE)).isInstanceOf(ReportingException.class)
                .extracting("code").isEqualTo("NOT_FOUND");
    }
    @Test void exportIdempotencyPersistsAcrossRepositoryInstances(){
        UUID key=UUID.randomUUID();var first=exports.attendance(hr,FACTORY,"2026-10",ReportExport.Format.CSV,key);
        assertThat(exports.attendance(hr,FACTORY,"2026-10",ReportExport.Format.CSV,key)).isEqualTo(first);
        assertThatThrownBy(()->exports.attendance(hr,FACTORY,"2026-11",ReportExport.Format.CSV,key))
                .isInstanceOf(ReportingException.class).extracting("code").isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(db.jdbc.queryForObject("SELECT expires_at FROM reporting_idempotency",java.sql.Timestamp.class).toInstant())
                .isEqualTo(clock.instant().plus(Duration.ofHours(48)));
    }
    @Test void attendanceAndDashboardFilterDepartmentBeforeCounting(){
        night();employee(id(8),id(9));
        row(id(8),null,30,List.of("MISSING_OUT"));
        var supervisor=actor(id(12),null,List.of(Role.SUPERVISOR),List.of("report.read"),List.of(FACTORY),List.of(DEPARTMENT),"DEPARTMENT");
        var rows=reports.dailyRecords(supervisor,new AttendanceFilter(FACTORY,null,LocalDate.of(2026,10,31),LocalDate.of(2026,11,1),0,20));
        assertThat(rows.totalElements()).isEqualTo(1);assertThat(rows.items()).extracting(row->row.employeeId()).containsExactly(EMPLOYEE);
        var value=dashboard.dashboard(supervisor,FACTORY,LocalDate.of(2026,10,31));
        assertThat(value.expectedCount()).isEqualTo(1);assertThat(value.presentCount()).isEqualTo(1);assertThat(value.absentCount()).isZero();
        assertThat(value.anomalyCount()).isZero();
        var all=dashboard.dashboard(hr,FACTORY,LocalDate.of(2026,10,31));
        assertThat(all.expectedCount()).isEqualTo(2);assertThat(all.presentCount()).isEqualTo(2);assertThat(all.anomalyCount()).isEqualTo(1);
    }
    @Test void auditMetadataRestrictsDepartmentSelfAndSystemScope(){
        var at=clock.instant();
        audit.append(new AuditDraft(id(1),null,FACTORY,"ATTENDANCE_ADJUSTMENT","ADJUSTMENT",id(31).toString(),AuditEntry.Result.SUCCESS,at,id(32),"Điều chỉnh công"),
                new AuditScope(FACTORY,EMPLOYEE,DEPARTMENT));
        audit.append(new AuditDraft(id(1),null,FACTORY,"PERIOD_CLOSE","PERIOD","2026-10",AuditEntry.Result.SUCCESS,at,id(33),"Chốt kỳ"));
        audit.append(new AuditDraft(null,id(40),null,"SYSTEM_CHECK","SYSTEM","central",AuditEntry.Result.SUCCESS,at,id(34),null));
        clock.advance(Duration.ofMillis(1));
        var dept=actor(id(13),null,List.of(Role.AUDITOR),List.of("audit.read","audit.export"),List.of(FACTORY),List.of(DEPARTMENT),"DEPARTMENT");
        assertThat(auditQueries.events(dept,null,null,null,null,0,20).totalElements()).isEqualTo(1);
        var self=actor(id(14),EMPLOYEE,List.of(Role.AUDITOR),List.of("audit.read"),List.of(FACTORY),List.of(),"SELF");
        assertThat(auditQueries.events(self,null,null,null,null,0,20).totalElements()).isEqualTo(1);
        assertThat(auditQueries.events(hr,null,null,null,null,0,20).totalElements()).isEqualTo(2);
        var global=actor(id(15),null,List.of(Role.ADMIN,Role.AUDITOR),List.of("audit.read"),List.of(),List.of(),"GLOBAL");
        assertThat(auditQueries.events(global,null,null,null,null,0,20).totalElements()).isEqualTo(3);
        var job=exports.audit(dept,FACTORY,at.minusSeconds(1),at.plusSeconds(1),ReportExport.Format.CSV,UUID.randomUUID());
        exportWorker.runOnce();String content=new String(exports.file(dept,job.id(),ExportJob.Kind.AUDIT).bytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(content).contains("ATTENDANCE_ADJUSTMENT").doesNotContain("PERIOD_CLOSE","SYSTEM_CHECK");
    }
    @Test void ownerWithNarrowerScopeCannotDownloadEarlierFactoryExport(){
        night();var job=exports.attendance(hr,FACTORY,"2026-10",ReportExport.Format.CSV,UUID.randomUUID());exportWorker.runOnce();
        var narrowed=new ActorContext(hr.userId(),EMPLOYEE.toString(),hr.roles(),hr.permissions(),hr.factoryIds(),List.of(),"SELF");
        assertThatThrownBy(()->exports.file(narrowed,job.id(),ExportJob.Kind.ATTENDANCE)).isInstanceOf(ReportingException.class)
                .extracting("code").isEqualTo("NOT_FOUND");
    }
    @Test void expiredLeasePreventsStaleExporterFromCompletingJob(){
        var job=exports.attendance(hr,FACTORY,"2026-10",ReportExport.Format.CSV,UUID.randomUUID());
        var first=reportsRepository.claim(clock.instant()).orElseThrow();clock.advance(Duration.ofMinutes(11));
        var second=reportsRepository.claim(clock.instant()).orElseThrow();
        var file=new vn.tmc.central.reportingaudit.internal.model.ReportFile(new byte[]{1},"text/csv","csv");
        assertThat(reportsRepository.complete(job.id(),first.leaseId(),file)).isFalse();
        assertThat(reportsRepository.complete(job.id(),second.leaseId(),file)).isTrue();
    }
}
