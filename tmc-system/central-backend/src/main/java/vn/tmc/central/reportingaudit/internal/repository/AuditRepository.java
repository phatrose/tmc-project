package vn.tmc.central.reportingaudit.internal.repository;

import vn.tmc.central.reportingaudit.api.AuditEntry;
import vn.tmc.central.reportingaudit.api.AuditScope;

public interface AuditRepository {
    void append(AuditEntry entry);
    default void append(AuditEntry entry, AuditScope scope) { append(entry); }
}
