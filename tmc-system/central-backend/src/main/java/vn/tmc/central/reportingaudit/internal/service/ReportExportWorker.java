package vn.tmc.central.reportingaudit.internal.service;

import vn.tmc.central.reportingaudit.api.ReportingSources.AttendanceFilter;
import vn.tmc.central.reportingaudit.internal.model.AuditFilter;
import vn.tmc.central.reportingaudit.internal.model.ExportJob;
import vn.tmc.central.reportingaudit.internal.repository.ReportingRepository;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class ReportExportWorker {
    private final ReportingRepository repository;
    private final AttendanceReportService reports;
    private final ReportFileExporter exporter;
    private final Clock clock;

    public ReportExportWorker(ReportingRepository repository, AttendanceReportService reports, ReportFileExporter exporter, Clock clock) {
        this.repository = repository;
        this.reports = reports;
        this.exporter = exporter;
        this.clock = clock;
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${tmc.reporting.export-poll-ms:1000}",initialDelayString="${tmc.reporting.initial-delay-ms:1000}")
    public void tick() {
        try { runOnce(); }
        catch (RuntimeException failure) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING, "Export worker storage unavailable");
        }
    }

    public boolean runOnce() {
        var claimed = repository.claim(clock.instant());
        if (claimed.isEmpty()) return false;
        var job = claimed.get();
        try {
            var file = job.kind() == ExportJob.Kind.AUDIT
                    ? exporter.audit(repository.auditExport(AuditQueryService.scoped(job.actor(),null,null,job.from(),job.to(),
                            Set.of(job.factoryId()),false,0,100)), job.view().format())
                    : attendance(job);
            repository.complete(job.view().id(), job.leaseId(), file);
        } catch (RuntimeException failure) {
            String code = failure instanceof ReportingException known ? known.code()
                    : failure instanceof org.springframework.dao.DataAccessException ? "STORAGE_UNAVAILABLE" : "INTERNAL_ERROR";
            repository.fail(job.view().id(), job.leaseId(), code);
        }
        return true;
    }

    private vn.tmc.central.reportingaudit.internal.model.ReportFile attendance(ExportJob job) {
        YearMonth period = YearMonth.parse(job.period());
        var records = reports.records(job.actor(), new AttendanceFilter(job.factoryId(), null,
                period.atDay(1), period.plusMonths(1).atDay(1), 0, 100));
        var table = new ArrayList<List<?>>();
        table.add(List.of("employeeId", "factoryId", "shiftId", "workDate", "firstIn", "lastOut",
                "workedMinutes", "lateMinutes", "earlyMinutes", "flags", "version"));
        for (var row : records) table.add(Arrays.asList(row.employeeId(), row.factoryId(), row.shiftId(), row.workDate(),
                row.firstIn(), row.lastOut(), row.workedMinutes(), row.lateMinutes(), row.earlyMinutes(),
                String.join(";", row.flags()), row.version()));
        return exporter.encode(table, job.view().format(), "Attendance " + period);
    }
}
