package vn.tmc.central;
import org.springframework.context.annotation.*;
import vn.tmc.central.reportingaudit.api.ReportingSources;
import vn.tmc.central.usermgmt.api.*;
import vn.tmc.central.attendanceprocessing.api.AttendanceQueries;
/** PHỤ TRÁCH: Task 8. Explicit adapters between existing module APIs. */
@Configuration(proxyBeanMethods=false) @Profile("integration-preview")
public class PreviewReportingConfiguration {
 static ActorContext actor(ReportingSources.ActorContext a){return new ActorContext(a.userId(),a.employeeId(),a.roles(),a.permissions(),a.factoryIds(),a.departmentIds(),a.scope());}
 @Bean ReportingSources.SessionQuery reportSessions(Authentication auth){return token->{var a=auth.getActorContext(token);return new ReportingSources.ActorContext(a.userId(),a.employeeId(),a.roles(),a.permissions(),a.factoryIds(),a.departmentIds(),a.scope());};}
 @Bean ReportingSources.EmployeeSource reportEmployees(EmployeeManagement employees){return (a,id)->{var e=employees.getEmployee(actor(a),id);return new ReportingSources.EmployeeInfo(e.id(),e.factoryId(),e.departmentId());};}
 @Bean ReportingSources.AttendanceSource reportAttendance(AttendanceQueries queries){return (a,f)->{
  var p=queries.dailyRecords(actor(a),new vn.tmc.central.attendanceprocessing.api.AttendanceFilter(f.factoryId(),f.employeeId(),f.from(),f.to(),f.page(),f.size()));
  return new ReportingSources.Page<>(p.items().stream().map(d->new ReportingSources.DailyAttendance(d.employeeId(),d.factoryId(),d.shiftId(),d.workDate(),d.firstIn(),d.lastOut(),d.workedMinutes(),d.lateMinutes(),d.earlyMinutes(),d.flags(),d.version())).toList(),p.page(),p.size(),p.totalElements());
 };}
}
