package vn.tmc.central.reportingaudit.internal.service;

import vn.tmc.central.reportingaudit.api.ReportingSources.AttendanceFilter;
import vn.tmc.central.reportingaudit.api.Dashboard;
import vn.tmc.central.reportingaudit.api.ReportingSources.ConfigurationQuery;
import vn.tmc.central.reportingaudit.api.ReportingSources.ActorContext;
import vn.tmc.central.reportingaudit.api.ReportingSources.RosterQuery;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class DashboardService {
    private final AttendanceReportService reports;
    private final java.util.function.Supplier<ConfigurationQuery> configuration;
    private final java.util.function.Supplier<RosterQuery> roster;
    private final ReportingAccess access;
    private final Clock clock;
    public DashboardService(AttendanceReportService reports,ConfigurationQuery configuration,RosterQuery roster,ReportingAccess access,Clock clock) {
        this(reports,()->configuration,()->roster,access,clock);
    }
    public DashboardService(AttendanceReportService reports,java.util.function.Supplier<ConfigurationQuery> configuration,
            java.util.function.Supplier<RosterQuery> roster,ReportingAccess access,Clock clock) {
        this.reports=reports;this.configuration=configuration;this.roster=roster;this.access=access;this.clock=clock;
    }
    public Dashboard dashboard(ActorContext actor,UUID factory,LocalDate date) {
        access.require(actor,"report.read");access.factory(actor,factory);
        var configuration=this.configuration.get();var roster=this.roster.get();
        if(configuration==null||roster==null)throw ReportingException.unavailable("ConfigurationQuery/RosterQuery");
        var snapshot=configuration.snapshot(factory);
        var expected=new HashSet<UUID>();
        for(var employee:roster.roster(factory,date,ZoneId.of(snapshot.factory().zoneId()))) {
            if(employee.active()&&!employee.shiftIds().isEmpty()&&access.employee(actor,factory,employee.employeeId())) expected.add(employee.employeeId());
        }
        var present=new HashSet<UUID>();var anomalies=new HashSet<UUID>();
        for(var row:reports.records(actor,new AttendanceFilter(factory,null,date,date.plusDays(1),0,100))) {
            if(row.firstIn()!=null)present.add(row.employeeId());
            if(!row.flags().isEmpty())anomalies.add(row.employeeId());
        }
        int absent=(int)expected.stream().filter(id->!present.contains(id)).count();
        var offline=configuration.edgeHealth(factory).stream().filter(edge->!"ONLINE".equals(edge.mode())).map(edge->edge.edgeId()).toList();
        return new Dashboard(clock.instant().truncatedTo(ChronoUnit.MILLIS),expected.size(),present.size(),absent,anomalies.size(),offline);
    }
}
