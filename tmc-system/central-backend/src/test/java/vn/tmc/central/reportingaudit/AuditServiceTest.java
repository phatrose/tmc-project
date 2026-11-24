package vn.tmc.central.reportingaudit;

import org.junit.jupiter.api.Test;
import vn.tmc.central.reportingaudit.api.*;
import vn.tmc.central.reportingaudit.internal.service.AuditService;
import java.time.Instant;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;
import static vn.tmc.central.reportingaudit.ReportingFixtures.*;

class AuditServiceTest {
    @Test void assignsUuidAndPreservesContractFields() {
        var saved = new ArrayList<AuditEntry>();
        var service = new AuditService(saved::add);
        var id = service.append(draft());
        assertThat(id.version()).isEqualTo(4);
        assertThat(saved).singleElement().isEqualTo(new AuditEntry(id, id(1), null, id(2),
                "PERIOD_CLOSE", "PERIOD", "2026-10", AuditEntry.Result.SUCCESS, AT, id(3), "Đóng kỳ tháng 10"));
    }

    @Test void acceptsDeviceIdentityAndNullableFields() {
        var saved = new ArrayList<AuditEntry>();
        new AuditService(saved::add).append(new AuditDraft(null, id(6), null, "GATE_EMERGENCY",
                "COMMAND", id(7).toString(), AuditEntry.Result.DENIED, AT, id(3), null));
        assertThat(saved.getFirst().actorId()).isNull();
        assertThat(saved.getFirst().deviceId()).isEqualTo(id(6));
        assertThat(saved.getFirst().reason()).isNull();
    }

    @Test void rejectsSubMillisecondPrecisionBeforeStorage() {
        var saved = new ArrayList<AuditEntry>();
        var draft = new AuditDraft(id(1), null, id(2), "PERIOD_CLOSE", "PERIOD", "2026-10",
                AuditEntry.Result.SUCCESS, Instant.parse("2026-11-17T02:00:00.000001Z"), id(3), null);
        assertThatIllegalArgumentException().isThrownBy(() -> new AuditService(saved::add).append(draft));
        assertThat(saved).isEmpty();
    }

    @Test void rejectsMissingCorrelationAndEmptyResource() {
        var service = new AuditService(entry -> fail("Invalid audit reached storage"));
        assertThatNullPointerException().isThrownBy(() -> service.append(new AuditDraft(null, id(1), null,
                "GATE_MANUAL", "COMMAND", "x", AuditEntry.Result.SUCCESS, AT, null, null)));
        assertThatIllegalArgumentException().isThrownBy(() -> service.append(new AuditDraft(null, id(1), null,
                "GATE_MANUAL", "COMMAND", " ", AuditEntry.Result.SUCCESS, AT, id(3), null)));
    }

    @Test void propagatesStorageFailureWithoutReturningSuccess() {
        var failure = new IllegalStateException("storage unavailable");
        var service = new AuditService(entry -> { throw failure; });
        assertThatThrownBy(() -> service.append(draft())).isSameAs(failure);
    }
}
