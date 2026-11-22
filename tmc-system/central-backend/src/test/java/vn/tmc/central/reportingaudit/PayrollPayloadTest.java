package vn.tmc.central.reportingaudit;
import vn.tmc.central.reportingaudit.api.ReportingSources.PayrollAttendanceSnapshot;

import org.junit.jupiter.api.Test;
import vn.tmc.central.reportingaudit.api.ReportingSources.*;
import vn.tmc.central.reportingaudit.internal.service.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static vn.tmc.central.reportingaudit.ReportingFixtures.*;

class PayrollPayloadTest {
    private final PayrollPayload codec=new PayrollPayload(mapper());
    @Test void fixedJcsChecksumMatchesContractFixture(){
        var row=new DailyAttendance(id(11),id(2),id(31),DAY,AT,AT.plusSeconds(480*60),480,0,0,List.of(),1);
        var prepared=codec.prepare(id(1),new PayrollAttendanceSnapshot(id(2),YearMonth.of(2026,10),1,AT,List.of(row)));
        assertThat(prepared.request().checksum()).isEqualTo("014b80cb9ba0aed2483aed44007884968a278965ffd9f899798c91a78d9f83ba");
        var differentMetadata=codec.prepare(id(8),new PayrollAttendanceSnapshot(id(2),YearMonth.of(2026,10),2,AT.plusSeconds(1),List.of(row)));
        assertThat(differentMetadata.request().checksum()).isEqualTo(prepared.request().checksum());
    }
    @Test void sortsLexicalIdsExcludesUnknownAndPreservesConfirmedZero(){
        var high=UUID.fromString("ffffffff-ffff-4fff-bfff-ffffffffffff");
        var records=List.of(row(high,480),row(id(3),null),row(id(1),0));
        var prepared=codec.prepare(id(9),new PayrollAttendanceSnapshot(id(2),YearMonth.of(2026,10),1,AT,records));
        assertThat(prepared.request().records()).extracting(value->value.employeeId()).containsExactly(id(1),high);
        assertThat(prepared.request().records().getFirst().workedMinutes()).isZero();
        assertThat(codec.read(prepared.bytes())).isEqualTo(prepared.request());
    }
    @Test void rejectsDuplicateSourceKeysAndWrongFactory(){
        var duplicate=row(id(1),480);
        assertThatThrownBy(()->codec.prepare(id(9),new PayrollAttendanceSnapshot(id(2),YearMonth.of(2026,10),1,AT,List.of(duplicate,duplicate))))
                .isInstanceOf(ReportingException.class);
        assertThatThrownBy(()->codec.prepare(id(9),new PayrollAttendanceSnapshot(id(8),YearMonth.of(2026,10),1,AT,List.of(duplicate))))
                .isInstanceOf(ReportingException.class);
    }
    @Test void retainsImmutableBytes(){
        var prepared=codec.prepare(id(9),new PayrollAttendanceSnapshot(id(2),YearMonth.of(2026,10),1,AT,List.of(row(id(1),480))));
        byte original=prepared.bytes()[0];byte[] modified=prepared.bytes();modified[0]=0;
        assertThat(prepared.bytes()[0]).isEqualTo(original);
    }
    private DailyAttendance row(UUID employee,Integer minutes){return new DailyAttendance(employee,id(2),id(4),DAY,AT,null,minutes,0,0,List.of(),1);}
}
