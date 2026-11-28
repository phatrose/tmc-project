package vn.tmc.central.reportingaudit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import vn.tmc.central.reportingaudit.api.ReportingSources.*;
import vn.tmc.central.reportingaudit.api.*;
import vn.tmc.central.reportingaudit.api.ReportingSources.*;
import vn.tmc.central.reportingaudit.internal.adapter.*;
import vn.tmc.central.reportingaudit.internal.service.*;
import vn.tmc.central.usermgmt.api.Role;
import vn.tmc.central.reportingaudit.api.ReportingSources.*;
import vn.tmc.central.reportingaudit.api.ReportingSources.Page;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static vn.tmc.central.reportingaudit.ReportingFixtures.*;

abstract class ReportingIntegrationFixture {
    protected static final UUID FACTORY=id(2),EMPLOYEE=id(3),SHIFT=id(4),EDGE=id(6),DEPARTMENT=id(7);
    protected static final YearMonth MONTH=YearMonth.of(2026,10);
    protected ReportingDatabase db;
    protected ObjectMapper json;
    protected MutableClock clock;
    protected ConfigurationQuery configuration;
    protected RosterQuery roster;
    protected PayrollAttendance coordinator;
    protected AttendanceSource source;
    protected final List<DailyAttendance> sourceRows=new ArrayList<>();
    protected final Map<UUID,RosterEntry> people=new LinkedHashMap<>();
    protected PostgresReportingRepository reportsRepository;
    protected PostgresPayrollRepository payrollRepository;
    protected AuditService audit;
    protected ReportingAccess access;
    protected AttendanceReportService reports;
    protected PayrollPayload codec;
    protected PayrollExportService payroll;
    protected ReportExportService exports;
    protected ReportExportWorker exportWorker;
    protected AuditQueryService auditQueries;
    protected DashboardService dashboard;
    protected EmployeeSource employees;
    protected ActorContext hr;

    @BeforeEach void createFixture(){
        db=new ReportingDatabase();db.migrate();json=mapper();clock=new MutableClock(Instant.parse("2026-11-01T00:00:00Z"));
        configuration=mock(ConfigurationQuery.class);roster=mock(RosterQuery.class);coordinator=mock(PayrollAttendance.class);
        var factory=new FactoryView(FACTORY,"Asia/Ho_Chi_Minh");
        when(configuration.factories()).thenReturn(List.of(factory));
        when(configuration.snapshot(FACTORY)).thenReturn(new FactorySnapshot(factory,1));
        when(configuration.edgeHealth(FACTORY)).thenReturn(List.of(new EdgeHealth(EDGE,"ONLINE",0,0)));
        when(roster.roster(eq(FACTORY),any(),any())).thenAnswer(call->List.copyOf(people.values()));
        when(coordinator.firstKnownPeriod(FACTORY)).thenReturn(Optional.of(MONTH));
        when(coordinator.freeze(eq(FACTORY),eq(MONTH),anyInt())).thenAnswer(call->
                new PayrollAttendanceSnapshot(FACTORY,MONTH,call.getArgument(2),clock.instant(),List.copyOf(sourceRows)));
        source=mock(AttendanceSource.class);
        when(source.dailyRecords(any(),any())).thenAnswer(call->{
            AttendanceFilter f=call.getArgument(1);
            var rows=sourceRows.stream().filter(row->row.factoryId().equals(f.factoryId())
                    &&!row.workDate().isBefore(f.from())&&row.workDate().isBefore(f.to())
                    &&(f.employeeId()==null||f.employeeId().equals(row.employeeId()))).toList();
            int start=Math.min(f.page()*f.size(),rows.size()),end=Math.min(start+f.size(),rows.size());
            return new Page<>(rows.subList(start,end),f.page(),f.size(),rows.size());
        });
        audit=new AuditService(new PostgresAuditRepository(db.named));
        reportsRepository=new PostgresReportingRepository(db.named,db.transactions,json);
        payrollRepository=new PostgresPayrollRepository(db.named,db.transactions,json);
        employees=mock(EmployeeSource.class);
        when(employees.getEmployee(any(),anyString())).thenAnswer(call->{
            UUID key=UUID.fromString(call.getArgument(1));var person=people.get(key);
            return new EmployeeInfo(key.toString(),FACTORY.toString(),person.departmentId()==null?null:person.departmentId().toString());
        });
        access=new ReportingAccess(()->employees);reports=new AttendanceReportService(()->source,access);codec=new PayrollPayload(json);
        payroll=new PayrollExportService(payrollRepository,reportsRepository,coordinator,configuration,access,codec,audit,clock);
        exports=new ReportExportService(reportsRepository,access,clock);
        exportWorker=new ReportExportWorker(reportsRepository,reports,new ReportFileExporter(""),clock);
        auditQueries=new AuditQueryService(reportsRepository,access,clock);dashboard=new DashboardService(reports,configuration,roster,access,clock);
        hr=actor(id(1),null,List.of(Role.HR),List.of("report.read","report.export","payroll.export","audit.read","audit.export"),List.of(FACTORY),List.of(),"FACTORY");
        employee(EMPLOYEE,DEPARTMENT);
    }
    @AfterEach void closeFixture(){if(db!=null)db.close();}
    protected void employee(UUID employee,UUID department){people.put(employee,new RosterEntry(employee,department,true,Set.of(SHIFT)));}
    protected void row(UUID employee,Integer minutes,int late,List<String> flags){
        sourceRows.removeIf(value->value.employeeId().equals(employee));
        sourceRows.add(new DailyAttendance(employee,FACTORY,SHIFT,LocalDate.of(2026,10,31),
                Instant.parse("2026-10-31T15:00:00Z"),minutes==null?null:Instant.parse("2026-10-31T23:00:00Z"),minutes,late,0,flags,1));
    }
    protected void night(){row(EMPLOYEE,480,0,List.of());}
    protected PayrollBatch close(){return payroll.close(hr,FACTORY,MONTH,"Chốt kỳ để xuất Payroll",UUID.randomUUID(),UUID.randomUUID());}
    protected PayrollOutboxWorker worker(HrPayrollPort transport){
        return new PayrollOutboxWorker(payrollRepository,transport,codec,new HrReceiptValidator(),payroll,clock,()->0L);
    }
}
