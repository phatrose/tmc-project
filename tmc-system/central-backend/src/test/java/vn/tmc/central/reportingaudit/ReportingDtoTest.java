package vn.tmc.central.reportingaudit;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.junit.jupiter.api.Test;
import vn.tmc.central.reportingaudit.api.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static vn.tmc.central.reportingaudit.ReportingFixtures.*;

class ReportingDtoTest {
    @Test void sharedActorContextRoundTripsWithoutHelperProperties()throws Exception {
        var actor=actor(id(1),null,java.util.List.of(vn.tmc.central.usermgmt.api.Role.HR),
                java.util.List.of("report.read"),java.util.List.of(id(2)),java.util.List.of(),"FACTORY");
        var json=mapper().writeValueAsString(actor);
        assertThat(json).doesNotContain("auditor");
        assertThat(mapper().readValue(json,vn.tmc.central.reportingaudit.api.ReportingSources.ActorContext.class)).isEqualTo(actor);
    }
    @Test void responseDtosKeepNullableFieldsAndExactMillisecondsWithoutEnvelope() {
        var mapper = mapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
        var export = mapper.valueToTree(new ReportExport(id(1), ReportExport.State.QUEUED,
                ReportExport.Format.CSV, AT, null, null));
        assertThat(export.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder(
                "id", "state", "format", "createdAt", "expiresAt", "errorCode");
        assertThat(export.get("createdAt").asText()).isEqualTo("2026-11-17T02:00:00.000Z");
        assertThat(export.get("expiresAt").isNull()).isTrue();
        assertThat(export.get("errorCode").isNull()).isTrue();
        assertThat(export.get("format").asText()).isEqualTo("CSV");
        var period = mapper.valueToTree(new Period(id(2), "2026-10", Period.State.OPEN, 1, null));
        assertThat(period.get("closedAt").isNull()).isTrue();
        var history = mapper.valueToTree(new PeriodHistory(id(1), id(2), "2026-10",
                PeriodHistory.Action.CLOSE, null, "Scheduled close", AT, 1));
        assertThat(history.get("actorId").isNull()).isTrue();
        var batch = mapper.valueToTree(new PayrollBatch(id(1), id(2), "2026-10", 1,
                PayrollBatch.State.QUEUED, 2, 0, CHECKSUM, AT, null, null));
        assertThat(batch.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder(
                "id", "factoryId", "period", "periodVersion", "state", "recordCount", "acceptedCount",
                "checksum", "createdAt", "completedAt", "errorCode");
        assertThat(batch.get("completedAt").isNull()).isTrue();
        assertThat(batch.get("errorCode").isNull()).isTrue();
    }

    @Test void auditWireHasV1FieldsAndNullableDeviceIdentity() {
        var json = mapper().setSerializationInclusion(JsonInclude.Include.NON_NULL).valueToTree(
                new AuditEntry(id(1), null, id(3), null, "GATE_EMERGENCY", "COMMAND",
                        id(4).toString(), AuditEntry.Result.DENIED, AT.plusMillis(123), id(5), null));
        assertThat(json.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder(
                "id", "actorId", "deviceId", "factoryId", "action", "resourceType", "resourceId",
                "result", "occurredAt", "correlationId", "reason");
        assertThat(json.get("occurredAt").asText()).isEqualTo("2026-11-17T02:00:00.123Z");
        assertThat(json.get("actorId").isNull()).isTrue();
        assertThat(json.get("factoryId").isNull()).isTrue();
        assertThat(json.get("reason").isNull()).isTrue();
        assertThat(json.get("result").asText()).isEqualTo("DENIED");
    }
}
