package vn.tmc.central.reportingaudit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.tmc.central.reportingaudit.internal.model.HrPayrollExchange.*;
import vn.tmc.central.reportingaudit.internal.service.HrReceiptValidator;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static vn.tmc.central.reportingaudit.ReportingFixtures.*;
import static vn.tmc.central.reportingaudit.internal.service.HrReceiptValidator.Outcome.*;

class HrReceiptValidatorTest {
    private final HrReceiptValidator validator = new HrReceiptValidator();

    @Test void confirmsOnlyMatchingCompleteReceiptAndDuplicateAck() {
        var ack = new Receipt(id(1), CHECKSUM, 2, 2, List.of());
        assertThat(validator.validate(request(), ack)).isEqualTo(CONFIRMED);
        assertThat(validator.validate(request(), ack)).isEqualTo(CONFIRMED);
    }

    @Test void partialReceiptCannotConfirmEntireBatch() {
        var ack = new Receipt(id(1), CHECKSUM, 2, 1, List.of(new Rejected(id(5), DAY, id(4), "INVALID_RECORD")));
        assertThat(validator.validate(request(), ack)).isEqualTo(HAS_REJECTIONS);
    }

    @ParameterizedTest
    @CsvSource({"1,1", "3,3", "2,-1", "2,3", "2,1"})
    void countMismatchCannotConfirmBatch(int received, int accepted) {
        assertThat(validator.validate(request(), new Receipt(id(1), CHECKSUM, received, accepted, List.of())))
                .isEqualTo(INVALID);
    }

    @Test void rejectsChecksumOrBatchMismatchAndMissingReceipt() {
        assertThat(validator.validate(request(), new Receipt(id(1), "b".repeat(64), 2, 2, List.of()))).isEqualTo(INVALID);
        assertThat(validator.validate(request(), new Receipt(id(9), CHECKSUM, 2, 2, List.of()))).isEqualTo(INVALID);
        assertThat(validator.validate(request(), null)).isEqualTo(INVALID);
    }

    @Test void rejectsUnknownAndDuplicateRejectedKeys() {
        var foreign = new Rejected(id(9), DAY, id(4), "INVALID_RECORD");
        assertThat(validator.validate(request(), new Receipt(id(1), CHECKSUM, 2, 1, List.of(foreign)))).isEqualTo(INVALID);
        var duplicate = new Rejected(id(5), DAY, id(4), "INVALID_RECORD");
        assertThat(validator.validate(request(), new Receipt(id(1), CHECKSUM, 2, 0, List.of(duplicate, duplicate)))).isEqualTo(INVALID);
    }

    @Test void snapshotsInputListsToKeepBatchStableForRetry() {
        var records = new ArrayList<>(request().records());
        var request = new Request(id(1), id(2), "2026-10", 1, CHECKSUM, records);
        records.clear();
        assertThat(request.records()).hasSize(2);
        assertThatThrownBy(() -> request.records().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void typedRequestUsesAgreedWireAndConfirmedZeroMinutes() {
        var json = mapper().valueToTree(request());
        assertThat(json.properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("batchId", "factoryId", "period", "periodVersion", "checksum", "records");
        assertThat(json.get("records").get(0).get("workDate").asText()).isEqualTo("2026-10-31");
        assertThat(json.get("records").get(1).get("workedMinutes").intValue()).isZero();
        assertThat(json.toString()).doesNotContain("employeeCode", "supersedes", "earlyLeaveMinutes");
    }

    @Test void receiptIgnoresAdditionalResponseFieldsButRequiresMandatoryCounts() throws Exception {
        String json = mapper().writeValueAsString(new Receipt(id(1), CHECKSUM, 2, 2, List.of()));
        assertThat(mapper().readValue(json.replace("}", ",\"extra\":true}"), Receipt.class).receivedCount()).isEqualTo(2);
        assertThatThrownBy(() -> mapper().readValue(json.replace("\"receivedCount\":2,", ""), Receipt.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
        assertThatThrownBy(() -> mapper().readValue(json.replace("\"acceptedCount\":2", "\"acceptedCount\":null"), Receipt.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }
}
