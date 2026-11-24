package vn.tmc.central.reportingaudit.api;

import vn.tmc.central.reportingaudit.api.ReportingSources.AttendanceFilter;
import vn.tmc.central.reportingaudit.api.ReportingSources.DailyAttendance;
import vn.tmc.central.reportingaudit.api.ReportingSources.ActorContext;
import vn.tmc.central.reportingaudit.api.ReportingSources.Page;

public interface ReportingQueries {
    Page<DailyAttendance> dailyRecords(ActorContext actor, AttendanceFilter filter);
}
