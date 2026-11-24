package vn.tmc.central.reportingaudit.internal.service;

import vn.tmc.central.reportingaudit.api.PayrollBatch;
import vn.tmc.central.reportingaudit.internal.repository.PayrollRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.function.LongSupplier;

public final class PayrollOutboxWorker {
    private final PayrollRepository repository;
    private final HrPayrollPort hr;
    private final PayrollPayload payload;
    private final HrReceiptValidator validator;
    private final PayrollExportService payroll;
    private final Clock clock;
    private final LongSupplier jitter;
    public PayrollOutboxWorker(PayrollRepository repository,HrPayrollPort hr,PayrollPayload payload,
            HrReceiptValidator validator,PayrollExportService payroll,Clock clock,LongSupplier jitter) {
        this.repository=repository;this.hr=hr;this.payload=payload;this.validator=validator;this.payroll=payroll;this.clock=clock;this.jitter=jitter;
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${tmc.reporting.payroll-poll-ms:1000}",initialDelayString="${tmc.reporting.initial-delay-ms:1000}")
    public void tick() {
        try{runOnce();}catch(RuntimeException failure){System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Payroll worker storage unavailable");}
    }
    public boolean runOnce() {
        var claimed=repository.claim(clock.instant());if(claimed.isEmpty())return false;
        var job=claimed.get();var batch=job.view();
        try {
            payroll.warnDeadline(batch.factoryId(),YearMonth.parse(batch.period()));
            var request=payload.read(job.payload());
            if(!batch.id().equals(request.batchId())||!batch.factoryId().equals(request.factoryId())||!batch.period().equals(request.period())
                    ||batch.periodVersion()!=request.periodVersion()||batch.recordCount()!=request.records().size()
                    ||!batch.checksum().equals(request.checksum())||!request.checksum().equals(payload.checksum(request.records())))
                throw new HrDeliveryException("STORAGE_UNAVAILABLE",false,"Payload batch không khớp metadata đã lưu");
            var receipt=hr.send(new PayrollPayload.Prepared(request,job.payload()));
            var outcome=validator.validate(request,receipt);
            var state=switch(outcome){case CONFIRMED->PayrollBatch.State.EXPORT_SUCCESS;case HAS_REJECTIONS->PayrollBatch.State.PARTIAL;case INVALID->PayrollBatch.State.FAILED;};
            String error=outcome==HrReceiptValidator.Outcome.CONFIRMED?null:outcome==HrReceiptValidator.Outcome.HAS_REJECTIONS?"INVALID_STATE":"VALIDATION_ERROR";
            repository.acknowledge(batch.id(),job.leaseId(),state,outcome==HrReceiptValidator.Outcome.INVALID?0:receipt.acceptedCount(),error,
                    clock.instant().truncatedTo(ChronoUnit.MILLIS));
        }catch(HrDeliveryException failure) {
            if(failure.retryable()) {
                long seconds=Math.min(300L,1L<<Math.min(job.attempts(),8));long millis=Math.floorMod(jitter.getAsLong(),1000);
                repository.retry(batch.id(),job.leaseId(),failure.code(),clock.instant().plusSeconds(seconds).plusMillis(millis));
            } else repository.acknowledge(batch.id(),job.leaseId(),PayrollBatch.State.FAILED,0,failure.code(),clock.instant().truncatedTo(ChronoUnit.MILLIS));
        }catch(RuntimeException failure) {
            repository.retry(batch.id(),job.leaseId(),"DEPENDENCY_UNAVAILABLE",clock.instant().plus(Duration.ofSeconds(30)));
        }
        return true;
    }
}
