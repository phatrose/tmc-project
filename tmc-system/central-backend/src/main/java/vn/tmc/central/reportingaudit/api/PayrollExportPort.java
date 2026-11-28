package vn.tmc.central.reportingaudit.api;

import vn.tmc.central.reportingaudit.api.ReportingSources.ActorContext;
import java.time.YearMonth;
import java.util.UUID;

public interface PayrollExportPort {
    PayrollBatch export(ActorContext actor,UUID factoryId,YearMonth period);
}
