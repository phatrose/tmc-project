package vn.tmc.central.reportingaudit.api;

import java.util.UUID;

public interface AuditPort {
    UUID append(AuditDraft draft);
    default UUID append(AuditDraft draft, AuditScope scope) { return append(draft); }
}
