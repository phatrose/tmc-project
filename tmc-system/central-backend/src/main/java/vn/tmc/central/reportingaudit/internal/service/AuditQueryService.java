package vn.tmc.central.reportingaudit.internal.service;

import vn.tmc.central.reportingaudit.api.AuditEntry;
import vn.tmc.central.reportingaudit.internal.model.AuditFilter;
import vn.tmc.central.reportingaudit.internal.repository.ReportingRepository;
import vn.tmc.central.reportingaudit.api.ReportingSources.ActorContext;
import vn.tmc.central.usermgmt.api.Role;
import vn.tmc.central.reportingaudit.api.ReportingSources.Page;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class AuditQueryService {
    private final ReportingRepository repository;
    private final ReportingAccess access;
    private final Clock clock;

    public AuditQueryService(ReportingRepository repository, ReportingAccess access, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.clock = clock;
    }

    public Page<AuditEntry> events(ActorContext actor, UUID actorId, String action,
            Instant from, Instant to, int page, int size) {
        access.require(actor, "audit.read");
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page/size không hợp lệ");
        if (action != null && action.isBlank()) throw new IllegalArgumentException("action không được rỗng");
        if (from == null && to == null) {
            to = clock.instant().truncatedTo(ChronoUnit.MILLIS);
            from = to.minus(Duration.ofDays(7));
        }
        if (from != null && to != null && !from.isBefore(to)) throw new IllegalArgumentException("from phải trước to");
        boolean global = "GLOBAL".equals(actor.scope()) && actor.roles().contains(Role.ADMIN);
        if ("GLOBAL".equals(actor.scope()) && !global)
            throw new ReportingException("SCOPE_MISMATCH", "GLOBAL yêu cầu Admin được cấp rõ", 403);
        Set<UUID> factories = actor.factoryIds().stream().map(UUID::fromString).collect(Collectors.toUnmodifiableSet());
        return repository.audit(scoped(actor,actorId,action,from,to,factories,global,page,size));
    }

    public static AuditFilter scoped(ActorContext actor,UUID actorId,String action,Instant from,Instant to,
            Set<UUID> factories,boolean global,int page,int size) {
        if(!Set.of("SELF","DEPARTMENT","FACTORY","GLOBAL").contains(actor.scope()))
            throw new ReportingException("SCOPE_MISMATCH","Scope không hợp lệ",403);
        UUID employee=null;
        if("SELF".equals(actor.scope())) {
            if(actor.employeeId()==null) throw new ReportingException("SCOPE_MISMATCH","Phiên SELF chưa gắn nhân viên",403);
            employee=UUID.fromString(actor.employeeId());
        }
        return new AuditFilter(actorId,action,from,to,factories,global,page,size,employee,
                actor.departmentIds().stream().map(UUID::fromString).collect(Collectors.toUnmodifiableSet()),"DEPARTMENT".equals(actor.scope()));
    }
}
