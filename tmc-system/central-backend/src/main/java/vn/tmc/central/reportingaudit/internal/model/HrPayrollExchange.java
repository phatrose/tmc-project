package vn.tmc.central.reportingaudit.internal.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class HrPayrollExchange {
    private HrPayrollExchange() {}

    public record Request(UUID batchId, UUID factoryId, String period, int periodVersion,
            String checksum, List<Attendance> records) {
        public Request {
            records = List.copyOf(records);
        }
    }

    public record Attendance(UUID employeeId, @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate workDate, UUID shiftId,
            int workedMinutes, int lateMinutes, int earlyMinutes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Receipt(UUID batchId, String checksum, Integer receivedCount, Integer acceptedCount,
            List<Rejected> rejected) {
        public Receipt {
            Objects.requireNonNull(batchId);
            Objects.requireNonNull(checksum);
            Objects.requireNonNull(receivedCount);
            Objects.requireNonNull(acceptedCount);
            if (checksum.isBlank()) throw new IllegalArgumentException("checksum");
            rejected = List.copyOf(rejected);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rejected(UUID employeeId, @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate workDate, UUID shiftId, String code) {
        public Rejected {
            Objects.requireNonNull(employeeId);
            Objects.requireNonNull(workDate);
            Objects.requireNonNull(shiftId);
            if (code == null || code.isBlank()) throw new IllegalArgumentException("code");
        }
    }
}
