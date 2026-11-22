package vn.tmc.central.reportingaudit.internal.service;

import vn.tmc.central.reportingaudit.internal.model.HrPayrollExchange.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;

public final class HrReceiptValidator {
    public enum Outcome { CONFIRMED, HAS_REJECTIONS, INVALID }

    public Outcome validate(Request request, Receipt receipt) {
        Objects.requireNonNull(request);
        if (receipt == null || request.batchId() == null || request.checksum() == null
                || request.checksum().isBlank() || !request.batchId().equals(receipt.batchId())
                || !request.checksum().equals(receipt.checksum())
                || receipt.receivedCount() != request.records().size()
                || receipt.acceptedCount() < 0 || receipt.acceptedCount() > receipt.receivedCount()
                || (long) receipt.acceptedCount() + receipt.rejected().size() != receipt.receivedCount())
            return Outcome.INVALID;
        var sent = new HashSet<Key>();
        for (var record : request.records()) {
            if (record.employeeId() == null || record.workDate() == null || record.shiftId() == null
                    || !sent.add(new Key(record.employeeId(), record.workDate(), record.shiftId())))
                return Outcome.INVALID;
        }
        var rejected = new HashSet<Key>();
        for (var row : receipt.rejected()) {
            var key = new Key(row.employeeId(), row.workDate(), row.shiftId());
            if (row.code() == null || row.code().isBlank() || !sent.contains(key) || !rejected.add(key))
                return Outcome.INVALID;
        }
        return receipt.rejected().isEmpty() ? Outcome.CONFIRMED : Outcome.HAS_REJECTIONS;
    }

    private record Key(UUID employeeId, LocalDate workDate, UUID shiftId) {}
}
