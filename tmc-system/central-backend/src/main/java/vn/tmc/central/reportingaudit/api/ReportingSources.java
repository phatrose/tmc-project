package vn.tmc.central.reportingaudit.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import vn.tmc.central.usermgmt.api.Role;
import java.time.*;
import java.util.*;

public final class ReportingSources {
    private ReportingSources() {}

    public interface SessionQuery {
        ActorContext getActorContext(String authorization);
    }
    public interface AttendanceSource {
        Page<DailyAttendance> dailyRecords(ActorContext actor, AttendanceFilter filter);
    }
    public interface EmployeeSource {
        EmployeeInfo getEmployee(ActorContext actor, String employeeId);
    }
    public record EmployeeInfo(String id, String factoryId, String departmentId) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ActorContext(String userId, String employeeId, List<Role> roles, List<String> permissions,
            List<String> factoryIds, List<String> departmentIds, String scope) {
        public ActorContext {
            roles=List.copyOf(roles);permissions=List.copyOf(permissions);
            factoryIds=List.copyOf(factoryIds);departmentIds=List.copyOf(departmentIds);
        }
    }
    public record Page<T>(List<T> items, int page, int size, long totalElements) {
        public Page { items=List.copyOf(items); }
    }
    public record AttendanceFilter(UUID factoryId, UUID employeeId, LocalDate from, LocalDate to, int page, int size) {
        public AttendanceFilter {
            if(from==null||to==null||!from.isBefore(to)||java.time.temporal.ChronoUnit.DAYS.between(from,to)>366
                    ||page<0||size<1||size>100)throw new IllegalArgumentException("Bộ lọc công không hợp lệ");
        }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DailyAttendance(UUID employeeId, UUID factoryId, UUID shiftId, LocalDate workDate,
            @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",timezone="UTC") Instant firstIn,
            @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",timezone="UTC") Instant lastOut,
            Integer workedMinutes, int lateMinutes, int earlyMinutes, List<String> flags, int version) {
        public DailyAttendance {
            Objects.requireNonNull(employeeId);Objects.requireNonNull(factoryId);Objects.requireNonNull(workDate);
            flags=List.copyOf(flags);
        }
    }

    public interface ConfigurationQuery {
        List<FactoryView> factories();
        FactorySnapshot snapshot(UUID factoryId);
        List<EdgeHealth> edgeHealth(UUID factoryId);
    }

    public interface RosterQuery {
        List<RosterEntry> roster(UUID factoryId, LocalDate date, ZoneId zoneId);
    }

    public interface PayrollAttendance {
        PayrollAttendanceSnapshot freeze(UUID factoryId, YearMonth period, int periodVersion);
        void reopen(UUID factoryId, YearMonth period, int periodVersion);
        Optional<YearMonth> firstKnownPeriod(UUID factoryId);
    }

    public record FactoryView(UUID id, String zoneId) {}
    public record FactorySnapshot(FactoryView factory, long version) {}
    public record EdgeHealth(UUID edgeId, String mode, int pendingCount, int failedCount) {}
    public record RosterEntry(UUID employeeId, UUID departmentId, boolean active, Set<UUID> shiftIds) {
        public RosterEntry { shiftIds = Set.copyOf(shiftIds); }
    }
    public record PayrollAttendanceSnapshot(UUID factoryId, YearMonth period, int periodVersion,
            Instant capturedAt, List<DailyAttendance> records) {
        public PayrollAttendanceSnapshot {
            records = records.stream().map(row -> new DailyAttendance(row.employeeId(), row.factoryId(), row.shiftId(),
                    row.workDate(), row.firstIn(), row.lastOut(), row.workedMinutes(), row.lateMinutes(), row.earlyMinutes(),
                    List.copyOf(row.flags()), row.version())).toList();
        }
    }
}
