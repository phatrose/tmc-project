package vn.tmc.central.reportingaudit.internal.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AuditFilter(UUID actorId, String action, Instant from, Instant to,
        Set<UUID> factoryIds, boolean global, int page, int size, UUID employeeId, Set<UUID> departmentIds, boolean departmental) {
    public AuditFilter { factoryIds = Set.copyOf(factoryIds);departmentIds=Set.copyOf(departmentIds); }
    public AuditFilter(UUID actorId,String action,Instant from,Instant to,Set<UUID> factories,boolean global,int page,int size) {
        this(actorId,action,from,to,factories,global,page,size,null,Set.of(),false);
    }
}
