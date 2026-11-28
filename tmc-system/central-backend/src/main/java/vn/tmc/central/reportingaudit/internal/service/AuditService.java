package vn.tmc.central.reportingaudit.internal.service;

import vn.tmc.central.reportingaudit.api.AuditDraft;
import vn.tmc.central.reportingaudit.api.AuditEntry;
import vn.tmc.central.reportingaudit.api.AuditPort;
import vn.tmc.central.reportingaudit.api.AuditScope;
import vn.tmc.central.reportingaudit.internal.repository.AuditRepository;
import java.util.Objects;
import java.util.UUID;

public final class AuditService implements AuditPort {
    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public UUID append(AuditDraft draft) {
        return append(draft,new AuditScope(draft.factoryId(),null,null));
    }

    @Override
    public UUID append(AuditDraft draft,AuditScope scope) {
        Objects.requireNonNull(draft);
        Objects.requireNonNull(scope);
        if(!Objects.equals(draft.factoryId(),scope.factoryId())) throw new IllegalArgumentException("Audit factory scope mismatch");
        uuid(scope.employeeId());uuid(scope.departmentId());
        uuid(draft.actorId());
        uuid(draft.deviceId());
        uuid(draft.factoryId());
        Objects.requireNonNull(draft.correlationId());
        uuid(draft.correlationId());
        nonempty(draft.action());
        nonempty(draft.resourceType());
        nonempty(draft.resourceId());
        Objects.requireNonNull(draft.result());
        Objects.requireNonNull(draft.occurredAt());
        if (draft.occurredAt().getNano() % 1_000_000 != 0)
            throw new IllegalArgumentException("occurredAt phải có độ chính xác milliseconds");
        if (draft.reason() != null) nonempty(draft.reason());
        UUID id = UUID.randomUUID();
        repository.append(new AuditEntry(id, draft.actorId(), draft.deviceId(), draft.factoryId(),
                draft.action(), draft.resourceType(), draft.resourceId(), draft.result(),
                draft.occurredAt(), draft.correlationId(), draft.reason()),scope);
        return id;
    }

    private static void nonempty(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Trường chuỗi không được rỗng");
    }

    private static void uuid(UUID value) {
        if (value != null && (value.version() != 4 || value.variant() != 2))
            throw new IllegalArgumentException("Định danh phải là UUIDv4");
    }
}
