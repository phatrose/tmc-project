package vn.tmc.central.reportingaudit.internal.model;

import vn.tmc.central.reportingaudit.api.ReportExport;
import vn.tmc.central.reportingaudit.api.ReportingSources.ActorContext;
import java.time.Instant;
import java.util.UUID;

public record ExportJob(ReportExport view, Kind kind, UUID factoryId, UUID ownerId,
        ActorContext actor, String period, Instant from, Instant to, UUID leaseId) {
    public enum Kind { ATTENDANCE, AUDIT }
}
