package vn.tmc.central.reportingaudit;

import org.junit.jupiter.api.Test;
import vn.tmc.central.reportingaudit.internal.model.PeriodClosingSchedule;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class PeriodClosingScheduleTest {
    @Test void closesAfterNightShiftAndSetsNoonDeadlineInFactoryTimezone() {
        var month = YearMonth.of(2026, 10);
        var zone = ZoneId.of("Asia/Ho_Chi_Minh");
        assertThat(PeriodClosingSchedule.closeAt(month, zone)).isEqualTo(Instant.parse("2026-10-31T23:30:00Z"));
        assertThat(PeriodClosingSchedule.deadline(month, zone)).isEqualTo(Instant.parse("2026-11-01T05:00:00Z"));
    }

    @Test void honorsFactoryZoneAndYearBoundary() {
        var month = YearMonth.of(2026, 12);
        var zone = ZoneId.of("Asia/Tokyo");
        assertThat(PeriodClosingSchedule.closeAt(month, zone)).isEqualTo(Instant.parse("2026-12-31T21:30:00Z"));
        assertThat(PeriodClosingSchedule.deadline(month, zone)).isEqualTo(Instant.parse("2027-01-01T03:00:00Z"));
    }
}
