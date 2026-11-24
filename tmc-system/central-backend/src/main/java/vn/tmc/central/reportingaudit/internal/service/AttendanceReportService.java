package vn.tmc.central.reportingaudit.internal.service;

import vn.tmc.central.reportingaudit.api.ReportingSources.AttendanceFilter;
import vn.tmc.central.reportingaudit.api.ReportingSources.AttendanceSource;
import vn.tmc.central.reportingaudit.api.ReportingSources.DailyAttendance;
import vn.tmc.central.reportingaudit.api.ReportingQueries;
import vn.tmc.central.reportingaudit.api.ReportingSources.ActorContext;
import vn.tmc.central.reportingaudit.api.ReportingSources.Page;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class AttendanceReportService implements ReportingQueries {
    private final Supplier<AttendanceSource> source;
    private final ReportingAccess access;

    public AttendanceReportService(Supplier<AttendanceSource> source, ReportingAccess access) {
        this.source = Objects.requireNonNull(source);
        this.access = Objects.requireNonNull(access);
    }

    @Override
    public Page<DailyAttendance> dailyRecords(ActorContext actor, AttendanceFilter filter) {
        access.require(actor, "report.read");
        var records = records(actor, filter);
        int start = (int) Math.min((long) filter.page() * filter.size(), records.size());
        int end = Math.min(start + filter.size(), records.size());
        return new Page<>(records.subList(start, end), filter.page(), filter.size(), records.size());
    }

    public List<DailyAttendance> records(ActorContext actor, AttendanceFilter filter) {
        access.factory(actor, filter.factoryId());
        UUID employeeId = filter.employeeId();
        if ("SELF".equals(actor.scope())) {
            if (actor.employeeId() == null || (employeeId != null && !employeeId.toString().equals(actor.employeeId())))
                throw new ReportingException("SCOPE_MISMATCH", "Chỉ được đọc công của bản thân", 403);
            employeeId = UUID.fromString(actor.employeeId());
        }
        if (employeeId != null && !access.employee(actor, filter.factoryId(), employeeId))
            throw new ReportingException("SCOPE_MISMATCH", "Nhân viên nằm ngoài phạm vi được cấp", 403);
        AttendanceSource queries = source.get();
        if (queries == null) throw ReportingException.unavailable("AttendanceSource");
        var records = new ArrayList<DailyAttendance>();
        var keys = new HashSet<String>();
        long total = -1;
        long received = 0;
        for (int page = 0; total < 0 || received < total; page++) {
            var result = queries.dailyRecords(actor, new AttendanceFilter(filter.factoryId(), employeeId,
                    filter.from(), filter.to(), page, 100));
            if (result == null || result.page() != page || result.size() != 100 || result.totalElements() < 0
                    || (total >= 0 && total != result.totalElements()) || result.items().size() > 100
                    || (result.items().isEmpty() && received < result.totalElements()))
                throw ReportingException.unavailable("snapshot truy vấn công nhất quán");
            total = result.totalElements();
            received += result.items().size();
            if (received > total) throw ReportingException.unavailable("count nguồn công");
            for (var row : result.items()) {
                if (row == null) throw ReportingException.unavailable("dòng công");
                if (!filter.factoryId().equals(row.factoryId()) || (employeeId != null && !employeeId.equals(row.employeeId()))) continue;
                if (row.workDate().isBefore(filter.from()) || !row.workDate().isBefore(filter.to()))
                    throw ReportingException.unavailable("khoảng ngày công");
                if (!access.employee(actor, filter.factoryId(), row.employeeId())) continue;
                String key = row.employeeId() + "/" + row.workDate() + "/" + row.shiftId();
                if (!keys.add(key)) throw ReportingException.unavailable("dòng công trùng khóa");
                records.add(new DailyAttendance(row.employeeId(), row.factoryId(), row.shiftId(), row.workDate(),
                        row.firstIn(), row.lastOut(), row.workedMinutes(), row.lateMinutes(), row.earlyMinutes(),
                        List.copyOf(row.flags()), row.version()));
            }
        }
        records.sort(Comparator.comparing((DailyAttendance row) -> row.employeeId().toString())
                .thenComparing(DailyAttendance::workDate).thenComparing(row -> Objects.toString(row.shiftId(), "")));
        return List.copyOf(records);
    }
}
