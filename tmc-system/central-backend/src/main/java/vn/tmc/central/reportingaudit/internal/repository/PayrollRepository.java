package vn.tmc.central.reportingaudit.internal.repository;

import vn.tmc.central.reportingaudit.api.ReportingSources.PayrollAttendanceSnapshot;
import vn.tmc.central.reportingaudit.api.*;
import vn.tmc.central.reportingaudit.internal.model.OutboxBatch;
import vn.tmc.central.reportingaudit.api.ReportingSources.Page;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public interface PayrollRepository {
    <T>T transaction(Supplier<T> work);
    Period lock(UUID factory,YearMonth period);
    void period(Period period);
    void create(PayrollBatch batch,byte[] payload,PayrollAttendanceSnapshot snapshot,String factoryZone,Instant deadline);
    Optional<PayrollBatch> latest(UUID factory,YearMonth period,int version);
    Optional<PayrollBatch> find(UUID id);
    Page<PeriodHistory> history(UUID factory,YearMonth period,int page,int size);
    void history(PeriodHistory history);
    Optional<OutboxBatch> claim(Instant now);
    boolean acknowledge(UUID id,UUID leaseId,PayrollBatch.State state,int accepted,String errorCode,Instant completedAt);
    boolean retry(UUID id,UUID leaseId,String errorCode,Instant nextAttempt);
    boolean deadlineAlert(UUID factory,YearMonth period,int version,Instant now);
    UUID systemIdentity();
    Optional<YearMonth> firstPeriod(UUID factory);
}
