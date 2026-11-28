package vn.tmc.central.reportingaudit.internal.model;

import java.time.*;

public final class PeriodClosingSchedule {
    public static final LocalTime OFFICIAL_CLOSE = LocalTime.of(6, 30);
    public static final LocalTime PAYROLL_DEADLINE = LocalTime.NOON;
    private PeriodClosingSchedule() {}
    public static Instant closeAt(YearMonth period, ZoneId zone) {
        return period.plusMonths(1).atDay(1).atTime(OFFICIAL_CLOSE).atZone(zone).toInstant();
    }
    public static Instant deadline(YearMonth period, ZoneId zone) {
        return period.plusMonths(1).atDay(1).atTime(PAYROLL_DEADLINE).atZone(zone).toInstant();
    }
}
