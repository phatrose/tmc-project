package vn.tmc.central.reportingaudit;

import org.junit.jupiter.api.Test;
import vn.tmc.central.reportingaudit.api.*;
import vn.tmc.central.reportingaudit.internal.model.HrPayrollExchange.*;
import vn.tmc.central.reportingaudit.internal.model.PeriodClosingSchedule;
import vn.tmc.central.reportingaudit.internal.service.*;
import vn.tmc.central.reportingaudit.api.ReportingSources.ActorContext;
import vn.tmc.central.usermgmt.api.Role;
import vn.tmc.contracts.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;
import static vn.tmc.central.reportingaudit.ReportingFixtures.id;
import static vn.tmc.central.reportingaudit.ReportingFixtures.actor;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PayrollPostgresIT extends ReportingIntegrationFixture {
    @Test void closePersistsSnapshotBatchOutboxHistoryAndAuditAtomically(){
        night();var batch=close();
        assertThat(batch.state()).isEqualTo(PayrollBatch.State.QUEUED);assertThat(batch.recordCount()).isEqualTo(1);
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM export_batches",Long.class)).isEqualTo(1);
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM outbox_messages",Long.class)).isEqualTo(1);
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM period_history",Long.class)).isEqualTo(1);
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='PERIOD_CLOSE'",Long.class)).isEqualTo(1);
        verify(coordinator).freeze(FACTORY,MONTH,1);
        assertThat(db.jdbc.queryForObject("SELECT state FROM payroll_periods",String.class)).isEqualTo("CLOSED");
        assertThat(codec.read(db.jdbc.queryForObject("SELECT payload FROM export_batches",byte[].class)).records().getFirst().workedMinutes()).isEqualTo(480);
        assertThat(worker(prepared->new Receipt(prepared.request().batchId(),prepared.request().checksum(),1,1,List.of())).runOnce()).isTrue();
        assertThat(payroll.batch(hr,batch.id()).state()).isEqualTo(PayrollBatch.State.EXPORT_SUCCESS);
        assertThat(payroll.batch(hr,batch.id()).acceptedCount()).isEqualTo(1);
    }

    @Test void simultaneousCloseProducesOneBatchAndOneAudit()throws Exception{
        night();try(var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(this::close);var second=executor.submit(this::close);
            assertThat(first.get().id()).isEqualTo(second.get().id());
        }
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM export_batches",Long.class)).isEqualTo(1);
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='PERIOD_CLOSE'",Long.class)).isEqualTo(1);
    }
    @Test void missingSourceReturnsUnavailableWithoutClosingPeriod(){
        var service=new PayrollExportService(payrollRepository,reportsRepository,()->null,()->configuration,access,codec,audit,clock);
        assertThatThrownBy(()->service.close(hr,FACTORY,MONTH,"Chốt kỳ tháng 10",UUID.randomUUID(),UUID.randomUUID()))
                .isInstanceOf(ReportingException.class).extracting("code").isEqualTo("DEPENDENCY_UNAVAILABLE");
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM payroll_periods",Long.class)).isZero();
    }

    @Test void auditFailureRollsBackEveryTask4ClosingWrite(){
        night();AuditPort failing=draft->{throw new IllegalStateException("audit unavailable");};
        var service=new PayrollExportService(payrollRepository,reportsRepository,coordinator,configuration,access,codec,failing,clock);
        assertThatThrownBy(()->service.close(hr,FACTORY,MONTH,"Chốt kỳ kiểm tra rollback",UUID.randomUUID(),UUID.randomUUID())).hasMessage("audit unavailable");
        for(String table:List.of("export_batches","outbox_messages","payroll_periods","period_history","reporting_idempotency"))
            assertThat(db.jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class)).as(table).isZero();
    }

    @Test void rejectsEarlyCloseAndUnreadyEdges(){
        night();clock.set(PeriodClosingSchedule.closeAt(MONTH,ZoneId.of("Asia/Ho_Chi_Minh")).minusMillis(1));
        assertThatThrownBy(this::close).isInstanceOf(ReportingException.class).extracting("code").isEqualTo("INVALID_STATE");
        clock.set(Instant.parse("2026-11-01T00:00:00Z"));
        when(configuration.edgeHealth(FACTORY)).thenReturn(List.of(new vn.tmc.central.reportingaudit.api.ReportingSources.EdgeHealth(EDGE,"ONLINE",1,0)));
        assertThatThrownBy(this::close).isInstanceOf(ReportingException.class).extracting("code").isEqualTo("INVALID_STATE");
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM export_batches",Long.class)).isZero();
    }

    @Test void sourceRefusalPreventsClosing(){
        when(coordinator.freeze(eq(FACTORY),eq(MONTH),anyInt())).thenThrow(new ReportingException("INVALID_STATE","Pending adjustment",409));
        assertThatThrownBy(this::close).isInstanceOf(ReportingException.class).extracting("code").isEqualTo("INVALID_STATE");
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM payroll_periods",Long.class)).isZero();
    }

    @Test void idempotencyReplaysOutcomeAndConflictingBodyIsRejected(){
        night();UUID key=UUID.randomUUID();
        var first=payroll.close(hr,FACTORY,MONTH,"Chốt Payroll tháng 10",key,UUID.randomUUID());
        var again=payroll.close(hr,FACTORY,MONTH,"Chốt Payroll tháng 10",key,UUID.randomUUID());
        assertThat(again).isEqualTo(first);
        assertThatThrownBy(()->payroll.close(hr,FACTORY,MONTH,"Nội dung khác cùng khóa",key,UUID.randomUUID()))
                .isInstanceOf(ReportingException.class).extracting("code").isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test void sourceChangesCannotChangeClosedBatchAndReopenUsesNewSnapshot(){
        night();var first=close();byte[] original=db.jdbc.queryForObject("SELECT payload FROM export_batches WHERE id=?",byte[].class,first.id());
        worker(prepared->new Receipt(prepared.request().batchId(),prepared.request().checksum(),1,1,List.of())).runOnce();
        row(EMPLOYEE,495,0,List.of());
        var opened=payroll.reopen(hr,FACTORY,MONTH,"Đối soát sự kiện đến muộn",1,UUID.randomUUID(),UUID.randomUUID());
        assertThat(opened.version()).isEqualTo(2);assertThat(opened.state()).isEqualTo(vn.tmc.central.reportingaudit.api.Period.State.REOPENED);
        verify(coordinator).reopen(FACTORY,MONTH,2);
        new PayrollScheduler(configuration,payroll,clock).tick();
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM export_batches",Long.class)).isEqualTo(1);
        var second=close();assertThat(second.id()).isNotEqualTo(first.id());assertThat(second.periodVersion()).isEqualTo(2);
        assertThat(second.checksum()).isNotEqualTo(first.checksum());
        assertThat(db.jdbc.queryForObject("SELECT payload FROM export_batches WHERE id=?",byte[].class,first.id())).isEqualTo(original);
    }

    @Test void cannotReopenPendingBatchAndVersionConflictIsExplicit(){
        night();close();
        assertThatThrownBy(()->payroll.reopen(hr,FACTORY,MONTH,"Mở lại để đối soát kỳ công",2,UUID.randomUUID(),UUID.randomUUID()))
                .isInstanceOf(ReportingException.class).extracting("code").isEqualTo("VERSION_CONFLICT");
        assertThatThrownBy(()->payroll.reopen(hr,FACTORY,MONTH,"Mở lại để đối soát kỳ công",1,UUID.randomUUID(),UUID.randomUUID()))
                .isInstanceOf(ReportingException.class).extracting("code").isEqualTo("INVALID_STATE");
    }

    @Test void partialAndMalformedAckNeverProduceExportSuccess(){
        night();var batch=close();
        worker(prepared->new Receipt(prepared.request().batchId(),prepared.request().checksum(),1,0,
                List.of(new Rejected(EMPLOYEE,LocalDate.of(2026,10,31),SHIFT,"INVALID_RECORD")))).runOnce();
        assertThat(payroll.batch(hr,batch.id()).state()).isEqualTo(PayrollBatch.State.PARTIAL);
        payroll.reopen(hr,FACTORY,MONTH,"Sửa bản ghi HR từ chối",1,UUID.randomUUID(),UUID.randomUUID());var second=close();
        worker(prepared->new Receipt(prepared.request().batchId(),prepared.request().checksum(),2,2,List.of())).runOnce();
        assertThat(payroll.batch(hr,second.id()).state()).isEqualTo(PayrollBatch.State.FAILED);
        assertThat(payroll.batch(hr,second.id()).acceptedCount()).isZero();
    }

    @Test void transientRetryKeepsExactPayloadAndBatch(){
        night();var batch=close();byte[] original=db.jdbc.queryForObject("SELECT payload FROM export_batches",byte[].class);
        worker(prepared->{throw new HrDeliveryException("DEPENDENCY_UNAVAILABLE",true,"timeout");}).runOnce();
        assertThat(payroll.batch(hr,batch.id()).state()).isEqualTo(PayrollBatch.State.QUEUED);
        clock.advance(Duration.ofSeconds(3));
        worker(prepared->{assertThat(prepared.bytes()).isEqualTo(original);return new Receipt(batch.id(),batch.checksum(),1,1,List.of());}).runOnce();
        assertThat(payroll.batch(hr,batch.id()).state()).isEqualTo(PayrollBatch.State.EXPORT_SUCCESS);
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM export_batches",Long.class)).isEqualTo(1);
    }

    @Test void leaseFencesStaleWorkerAndDatabaseRejectsPayloadMutation(){
        night();var batch=close();var first=payrollRepository.claim(clock.instant()).orElseThrow();
        clock.advance(Duration.ofMinutes(3));var second=payrollRepository.claim(clock.instant()).orElseThrow();
        assertThat(payrollRepository.acknowledge(batch.id(),first.leaseId(),PayrollBatch.State.EXPORT_SUCCESS,1,null,clock.instant())).isFalse();
        assertThat(payrollRepository.acknowledge(batch.id(),second.leaseId(),PayrollBatch.State.EXPORT_SUCCESS,1,null,clock.instant())).isTrue();
        assertThatThrownBy(()->db.jdbc.update("UPDATE export_batches SET payload=? WHERE id=?",new byte[]{1},batch.id())).hasMessageContaining("immutable");
    }

    @Test void deadlineWarningIsDurableAndNeverSubstitutesForAck(){
        night();var batch=close();clock.set(Instant.parse("2026-11-01T05:00:00Z"));
        payroll.warnDeadline(FACTORY,MONTH);payroll.warnDeadline(FACTORY,MONTH);
        assertThat(db.jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='PAYROLL_DEADLINE_MISSED'",Long.class)).isEqualTo(1);
        assertThat(payroll.batch(hr,batch.id()).state()).isEqualTo(PayrollBatch.State.QUEUED);
        worker(prepared->new Receipt(batch.id(),batch.checksum(),1,1,List.of())).runOnce();
        assertThat(payroll.batch(hr,batch.id()).completedAt()).isEqualTo(clock.instant());
    }

    @Test void auditorDenialWinsOverHrAndSchedulerHasSystemIdentity(){
        night();var auditor=actor(id(25),null,List.of(Role.HR,Role.AUDITOR),List.of("payroll.export"),List.of(FACTORY),List.of(),"FACTORY");
        assertThatThrownBy(()->payroll.close(auditor,FACTORY,MONTH,"Không được phép chốt",UUID.randomUUID(),UUID.randomUUID()))
                .isInstanceOf(ReportingException.class).extracting("code").isEqualTo("FORBIDDEN");
        new PayrollScheduler(configuration,payroll,clock).tick();
        assertThat(db.jdbc.queryForObject("SELECT actor_id FROM period_history",UUID.class)).isNull();
        assertThat(db.jdbc.queryForObject("SELECT device_id FROM audit_logs WHERE action='PERIOD_CLOSE'",UUID.class)).isEqualTo(payrollRepository.systemIdentity());
    }
}
