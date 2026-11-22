package vn.tmc.central.reportingaudit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import vn.tmc.central.reportingaudit.api.AuditDraft;
import vn.tmc.central.reportingaudit.api.AuditEntry;
import vn.tmc.central.reportingaudit.internal.model.HrPayrollExchange.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class ReportingFixtures {
    static vn.tmc.central.reportingaudit.api.ReportingSources.ActorContext actor(UUID user,UUID employee,
            List<vn.tmc.central.usermgmt.api.Role> roles,List<String> permissions,List<UUID> factories,List<UUID> departments,String scope) {
        return new vn.tmc.central.reportingaudit.api.ReportingSources.ActorContext(user.toString(),employee==null?null:employee.toString(),roles,permissions,
                factories.stream().map(UUID::toString).toList(),departments.stream().map(UUID::toString).toList(),scope);
    }
    static final Instant AT = Instant.parse("2026-11-17T02:00:00.000Z");
    static final LocalDate DAY = LocalDate.of(2026, 10, 31);
    static final String CHECKSUM = "a".repeat(64);

    static UUID id(int n) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(n));
    }

    static AuditDraft draft() {
        return new AuditDraft(id(1), null, id(2), "PERIOD_CLOSE", "PERIOD", "2026-10",
                AuditEntry.Result.SUCCESS, AT, id(3), "Đóng kỳ tháng 10");
    }

    static Request request() {
        return new Request(id(1), id(2), "2026-10", 1, CHECKSUM,
                List.of(new Attendance(id(3), DAY, id(4), 480, 0, 0),
                        new Attendance(id(5), DAY, id(4), 0, 15, 10)));
    }

    static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
