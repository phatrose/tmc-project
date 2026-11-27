package vn.tmc.central;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import vn.tmc.central.attendanceprocessing.api.AttendanceQueries;
import vn.tmc.central.reportingaudit.api.ReportingSources;
import vn.tmc.central.systemadmin.api.Administration;
import vn.tmc.central.systemadmin.api.DeviceStatusView;
import vn.tmc.central.systemadmin.api.FactoryView;
import vn.tmc.central.usermgmt.api.*;

/** PHỤ TRÁCH: Task 8. Explicit adapters between existing module APIs (including Reporting/Payroll sources). */
@Configuration(proxyBeanMethods = false)
@Profile("integration-preview")
public class PreviewReportingConfiguration {

    private static final ActorContext SYSTEM_REPORTING_ACTOR = new ActorContext(
            "00000000-0000-4000-8000-000000000001",
            null,
            List.of(Role.ADMIN, Role.HR),
            List.of("employee.read", "attendance.read", "report.read", "payroll.close"),
            List.of(),
            List.of(),
            ActorContext.SCOPE_GLOBAL
    );

    static ActorContext actor(ReportingSources.ActorContext a) {
        return new ActorContext(a.userId(), a.employeeId(), a.roles(), a.permissions(),
                a.factoryIds(), a.departmentIds(), a.scope());
    }

    static UUID toUuid(String raw) {
        if (raw == null || raw.isBlank()) return UUID.nameUUIDFromBytes("unknown".getBytes(StandardCharsets.UTF_8));
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return UUID.nameUUIDFromBytes(raw.trim().getBytes(StandardCharsets.UTF_8));
        }
    }

    @Bean
    ReportingSources.SessionQuery reportSessions(Authentication auth) {
        return token -> {
            var a = auth.getActorContext(token);
            return new ReportingSources.ActorContext(a.userId(), a.employeeId(), a.roles(), a.permissions(),
                    a.factoryIds(), a.departmentIds(), a.scope());
        };
    }

    @Bean
    ReportingSources.EmployeeSource reportEmployees(EmployeeManagement employees) {
        return (a, id) -> {
            var e = employees.getEmployee(actor(a), id);
            return new ReportingSources.EmployeeInfo(e.id(), e.factoryId(), e.departmentId());
        };
    }

    @Bean
    ReportingSources.AttendanceSource reportAttendance(AttendanceQueries queries) {
        return (a, f) -> {
            var p = queries.dailyRecords(actor(a),
                    new vn.tmc.central.attendanceprocessing.api.AttendanceFilter(
                            f.factoryId(), f.employeeId(), f.from(), f.to(), f.page(), f.size()));
            return new ReportingSources.Page<>(
                    p.items().stream().map(d -> new ReportingSources.DailyAttendance(
                            d.employeeId(), d.factoryId(), d.shiftId(), d.workDate(),
                            d.firstIn(), d.lastOut(), d.workedMinutes(), d.lateMinutes(),
                            d.earlyMinutes(), d.flags(), d.version())).toList(),
                    p.page(), p.size(), p.totalElements());
        };
    }

    @Bean
    ReportingSources.ConfigurationQuery reportConfiguration(Administration administration) {
        return new ReportingSources.ConfigurationQuery() {
            @Override
            public List<ReportingSources.FactoryView> factories() {
                return administration.findAllFactories().stream()
                        .map(f -> new ReportingSources.FactoryView(toUuid(f.id()), f.zoneId()))
                        .toList();
            }

            @Override
            public ReportingSources.FactorySnapshot snapshot(UUID factoryId) {
                FactoryView match = administration.findAllFactories().stream()
                        .filter(f -> toUuid(f.id()).equals(factoryId))
                        .findFirst()
                        .orElse(null);
                String zone = match != null ? match.zoneId() : "Asia/Ho_Chi_Minh";
                return new ReportingSources.FactorySnapshot(new ReportingSources.FactoryView(factoryId, zone), 1L);
            }

            @Override
            public List<ReportingSources.EdgeHealth> edgeHealth(UUID factoryId) {
                String rawFactoryId = administration.findAllFactories().stream()
                        .filter(f -> toUuid(f.id()).equals(factoryId))
                        .map(FactoryView::id)
                        .findFirst()
                        .orElse(factoryId.toString());
                List<DeviceStatusView> devices = administration.getDeviceStatuses(rawFactoryId);
                return devices.stream()
                        .filter(d -> "EDGE_NODE".equalsIgnoreCase(d.deviceType()))
                        .map(d -> new ReportingSources.EdgeHealth(
                                toUuid(d.deviceId()),
                                "ONLINE".equalsIgnoreCase(d.status()) ? "ONLINE" : "OFFLINE",
                                0, 0))
                        .toList();
            }
        };
    }

    @Bean
    ReportingSources.RosterQuery reportRoster(EmployeeManagement employees) {
        return (factoryId, date, zoneId) -> {
            List<ReportingSources.RosterEntry> entries = new ArrayList<>();
            int page = 0;
            while (true) {
                var res = employees.listEmployees(SYSTEM_REPORTING_ACTOR, null, null, true, page, 100);
                for (EmployeeView e : res.items()) {
                    if (toUuid(e.factoryId()).equals(factoryId)) {
                        entries.add(new ReportingSources.RosterEntry(
                                toUuid(e.id()),
                                e.departmentId() == null ? null : toUuid(e.departmentId()),
                                e.active(),
                                Set.of()));
                    }
                }
                if (res.items().size() < 100 || (long) (page + 1) * 100 >= res.totalElements()) break;
                page++;
            }
            return entries;
        };
    }

    @Bean
    ReportingSources.PayrollAttendance reportPayrollAttendance(AttendanceQueries queries, Clock clock) {
        return new ReportingSources.PayrollAttendance() {
            private final Map<String, Integer> frozenVersions = new ConcurrentHashMap<>();

            @Override
            public ReportingSources.PayrollAttendanceSnapshot freeze(UUID factoryId, YearMonth period, int periodVersion) {
                LocalDate from = period.atDay(1);
                LocalDate to = period.plusMonths(1).atDay(1);
                List<ReportingSources.DailyAttendance> rows = new ArrayList<>();
                int page = 0;
                while (true) {
                    var p = queries.dailyRecords(SYSTEM_REPORTING_ACTOR,
                            new vn.tmc.central.attendanceprocessing.api.AttendanceFilter(factoryId, null, from, to, page, 100));
                    for (var d : p.items()) {
                        rows.add(new ReportingSources.DailyAttendance(
                                d.employeeId(), d.factoryId(), d.shiftId(), d.workDate(),
                                d.firstIn(), d.lastOut(), d.workedMinutes(), d.lateMinutes(),
                                d.earlyMinutes(), d.flags(), d.version()));
                    }
                    if (p.items().size() < 100 || (long) (page + 1) * 100 >= p.totalElements()) break;
                    page++;
                }
                frozenVersions.put(factoryId + ":" + period, periodVersion);
                return new ReportingSources.PayrollAttendanceSnapshot(factoryId, period, periodVersion, clock.instant(), rows);
            }

            @Override
            public void reopen(UUID factoryId, YearMonth period, int periodVersion) {
                frozenVersions.remove(factoryId + ":" + period);
            }

            @Override
            public Optional<YearMonth> firstKnownPeriod(UUID factoryId) {
                var events = queries.queryEvents(SYSTEM_REPORTING_ACTOR, null, factoryId, null, null, 0, 1);
                if (events.items().isEmpty()) {
                    return Optional.of(YearMonth.now(ZoneId.of("Asia/Ho_Chi_Minh")));
                }
                Instant occurred = events.items().get(0).envelope().occurredAt();
                return Optional.of(YearMonth.from(occurred.atZone(ZoneId.of("Asia/Ho_Chi_Minh"))));
            }
        };
    }
}
