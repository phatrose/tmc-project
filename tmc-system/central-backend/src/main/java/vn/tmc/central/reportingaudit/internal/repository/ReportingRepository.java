package vn.tmc.central.reportingaudit.internal.repository;

import vn.tmc.central.reportingaudit.api.AuditEntry;
import vn.tmc.central.reportingaudit.api.ReportExport;
import vn.tmc.central.reportingaudit.internal.model.AuditFilter;
import vn.tmc.central.reportingaudit.internal.model.ExportJob;
import vn.tmc.central.reportingaudit.internal.model.ReportFile;
import vn.tmc.central.reportingaudit.api.ReportingSources.Page;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public interface ReportingRepository {
    void create(ExportJob job);
    Optional<ExportJob> find(UUID id);
    Optional<ExportJob> claim(Instant now);
    boolean complete(UUID id, UUID leaseId, ReportFile file);
    boolean fail(UUID id, UUID leaseId, String errorCode);
    Optional<ReportFile> file(UUID id);
    Page<AuditEntry> audit(AuditFilter filter);
    List<AuditEntry> auditExport(AuditFilter filter);
    <T> T idempotent(UUID actorId, String path, UUID key, String fingerprint, Instant now,
            Class<T> type, Supplier<T> operation);
}
