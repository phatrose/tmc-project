package vn.tmc.central.reportingaudit.internal.service;

import vn.tmc.central.reportingaudit.api.ReportingSources.PayrollAttendance;
import vn.tmc.central.reportingaudit.api.*;
import vn.tmc.central.reportingaudit.api.Period;
import vn.tmc.central.reportingaudit.internal.model.PeriodClosingSchedule;
import vn.tmc.central.reportingaudit.internal.repository.PayrollRepository;
import vn.tmc.central.reportingaudit.internal.repository.ReportingRepository;
import vn.tmc.central.reportingaudit.api.ReportingSources.ConfigurationQuery;
import vn.tmc.central.reportingaudit.api.ReportingSources.ActorContext;
import vn.tmc.central.usermgmt.api.Role;
import vn.tmc.central.reportingaudit.api.ReportingSources.Page;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class PayrollExportService implements PayrollExportPort {
    private final PayrollRepository repository;
    private final ReportingRepository idempotency;
    private final java.util.function.Supplier<PayrollAttendance> attendance;
    private final java.util.function.Supplier<ConfigurationQuery> configuration;
    private final ReportingAccess access;
    private final PayrollPayload payload;
    private final AuditPort audit;
    private final Clock clock;

    public PayrollExportService(PayrollRepository repository,ReportingRepository idempotency,PayrollAttendance attendance,
            ConfigurationQuery configuration,ReportingAccess access,PayrollPayload payload,AuditPort audit,Clock clock) {
        this(repository,idempotency,()->attendance,()->configuration,access,payload,audit,clock);
    }
    public PayrollExportService(PayrollRepository repository,ReportingRepository idempotency,
            java.util.function.Supplier<PayrollAttendance> attendance,java.util.function.Supplier<ConfigurationQuery> configuration,
            ReportingAccess access,PayrollPayload payload,AuditPort audit,Clock clock) {
        this.repository=repository;this.idempotency=idempotency;this.attendance=attendance;this.configuration=configuration;
        this.access=access;this.payload=payload;this.audit=audit;this.clock=clock;
    }
    @Override public PayrollBatch export(ActorContext actor,UUID factory,YearMonth period) {
        access.payroll(actor,factory);
        return repository.transaction(()->closeCore(factory,period,"Xuất Payroll",UUID.fromString(actor.userId()),null,UUID.randomUUID(),false).orElseThrow());
    }
    public PayrollBatch close(ActorContext actor,UUID factory,YearMonth period,String reason,UUID key,UUID correlation) {
        access.payroll(actor,factory);validate(reason,key);
        return idempotency.idempotent(UUID.fromString(actor.userId()),"/api/v1/payroll/periods/"+period+"/close",key,
                hash(factory+"\n"+reason),now(),PayrollBatch.class,()->repository.transaction(()->
                        closeCore(factory,period,reason,UUID.fromString(actor.userId()),null,correlation,false).orElseThrow()));
    }
    public Optional<PayrollBatch> scheduledClose(UUID factory,YearMonth period) {
        return repository.transaction(()->closeCore(factory,period,"Chốt kỳ theo lịch",null,repository.systemIdentity(),UUID.randomUUID(),true));
    }
    private Optional<PayrollBatch> closeCore(UUID factory,YearMonth month,String reason,UUID actor,UUID device,UUID correlation,boolean scheduled) {
        var snapshot=configuration().snapshot(factory);ZoneId zone=ZoneId.of(snapshot.factory().zoneId());
        if(now().isBefore(PeriodClosingSchedule.closeAt(month,zone))) throw invalid("Chưa tới thời điểm chốt chính thức");
        var period=repository.lock(factory,month);
        if(period.state()==Period.State.CLOSED) return Optional.of(repository.latest(factory,month,period.version())
                .orElseThrow(()->ReportingException.unavailable("batch của kỳ đã đóng")));
        if(scheduled&&period.state()==Period.State.REOPENED)return Optional.empty();
        if(period.state()!=Period.State.OPEN&&period.state()!=Period.State.REOPENED)throw invalid("Kỳ đang được xử lý");
        for(var edge:configuration().edgeHealth(factory)) {
            if(!"ONLINE".equals(edge.mode())||edge.pendingCount()!=0||edge.failedCount()!=0)
                throw invalid("Dữ liệu Edge chưa sẵn sàng để chốt");
        }
        repository.period(new Period(factory,month.toString(),Period.State.CLOSING,period.version(),null));
        var source=attendance().freeze(factory,month,period.version());
        if(!factory.equals(source.factoryId())||!month.equals(source.period())||period.version()!=source.periodVersion())
            throw ReportingException.unavailable("snapshot nguồn không khớp kỳ");
        UUID id=UUID.randomUUID();var prepared=payload.prepare(id,source);Instant now=now();
        var batch=new PayrollBatch(id,factory,month.toString(),period.version(),PayrollBatch.State.QUEUED,
                prepared.request().records().size(),0,prepared.request().checksum(),now,null,null);
        repository.create(batch,prepared.bytes(),source,zone.getId(),PeriodClosingSchedule.deadline(month,zone));
        repository.history(new PeriodHistory(UUID.randomUUID(),factory,month.toString(),PeriodHistory.Action.CLOSE,actor,reason,now,period.version()));
        audit.append(new AuditDraft(actor,device,factory,"PERIOD_CLOSE","PERIOD",factory+"/"+month,
                AuditEntry.Result.SUCCESS,now,correlation,reason));
        repository.period(new Period(factory,month.toString(),Period.State.CLOSED,period.version(),now));
        return Optional.of(batch);
    }
    public Period reopen(ActorContext actor,UUID factory,YearMonth month,String reason,int version,UUID key,UUID correlation) {
        access.payroll(actor,factory);validate(reason,key);
        if(version<1)throw new IllegalArgumentException("version phải từ 1");
        return idempotency.idempotent(UUID.fromString(actor.userId()),"/api/v1/payroll/periods/"+month+"/reopen",key,
                hash(factory+"\n"+reason+"\n"+version),now(),Period.class,()->repository.transaction(()->{
                    var old=repository.lock(factory,month);
                    if(old.version()!=version)throw new ReportingException("VERSION_CONFLICT","Version kỳ đã thay đổi",409);
                    if(old.state()!=Period.State.CLOSED)throw invalid("Chỉ mở lại kỳ đã đóng");
                    var batch=repository.latest(factory,month,version).orElseThrow(()->ReportingException.unavailable("batch kỳ công"));
                    if(batch.state()==PayrollBatch.State.QUEUED||batch.state()==PayrollBatch.State.SENDING)
                        throw invalid("Batch hiện tại chưa hoàn tất đối soát");
                    int next=Math.incrementExact(version);
                    attendance().reopen(factory,month,next);
                    var period=new Period(factory,month.toString(),Period.State.REOPENED,next,null);
                    repository.period(period);Instant at=now();UUID user=UUID.fromString(actor.userId());
                    repository.history(new PeriodHistory(UUID.randomUUID(),factory,month.toString(),PeriodHistory.Action.REOPEN,user,reason,at,next));
                    audit.append(new AuditDraft(user,null,factory,"PERIOD_REOPEN","PERIOD",factory+"/"+month,AuditEntry.Result.SUCCESS,at,correlation,reason));
                    return period;
                }));
    }
    public PayrollBatch batch(ActorContext actor,UUID id) {
        if(actor==null)throw new ReportingException("UNAUTHENTICATED","Cần đăng nhập",401);
        if(!actor.roles().contains(Role.HR))throw new ReportingException("FORBIDDEN","Yêu cầu vai trò HR",403);
        var batch=repository.find(id).orElseThrow(()->new ReportingException("NOT_FOUND","Không tìm thấy batch",404));
        try{wholeFactory(actor,batch.factoryId());}catch(ReportingException denied){throw new ReportingException("NOT_FOUND","Không tìm thấy batch trong scope",404);}
        return batch;
    }
    public Page<PeriodHistory> history(ActorContext actor,UUID factory,YearMonth month,int page,int size) {
        if(actor==null)throw new ReportingException("UNAUTHENTICATED","Cần đăng nhập",401);
        if(!actor.roles().contains(Role.HR))access.require(actor,"audit.read");
        wholeFactory(actor,factory);
        if(page<0||size<1||size>100)throw new IllegalArgumentException("page/size không hợp lệ");
        return repository.history(factory,month,page,size);
    }
    public void warnDeadline(UUID factory,YearMonth month) {
        var snapshot=configuration().snapshot(factory);
        if(now().isBefore(PeriodClosingSchedule.deadline(month,ZoneId.of(snapshot.factory().zoneId()))))return;
        repository.transaction(()->{
            var period=repository.lock(factory,month);var batch=repository.latest(factory,month,period.version());
            Instant deadline=PeriodClosingSchedule.deadline(month,ZoneId.of(snapshot.factory().zoneId()));
            if(batch.isPresent()&&batch.get().state()==PayrollBatch.State.EXPORT_SUCCESS&&batch.get().completedAt().isBefore(deadline))return null;
            Instant at=now();
            if(repository.deadlineAlert(factory,month,period.version(),at)) audit.append(new AuditDraft(null,repository.systemIdentity(),factory,
                    "PAYROLL_DEADLINE_MISSED","PERIOD",factory+"/"+month,AuditEntry.Result.FAILURE,at,UUID.randomUUID(),"Đối soát Payroll chưa hoàn tất trước 12:00"));
            return null;
        });
    }
    public YearMonth firstPeriod(UUID factory,YearMonth fallback) {
        return java.util.stream.Stream.of(repository.firstPeriod(factory),attendance().firstKnownPeriod(factory))
                .flatMap(Optional::stream).min(Comparator.naturalOrder()).orElse(fallback);
    }
    private ConfigurationQuery configuration() {
        var value=configuration.get();if(value==null)throw ReportingException.unavailable("ConfigurationQuery");return value;
    }
    private PayrollAttendance attendance() {
        var value=attendance.get();if(value==null)throw ReportingException.unavailable("PayrollAttendance");return value;
    }
    private void wholeFactory(ActorContext actor,UUID factory) {
        access.factory(actor,factory);
        if(!Set.of("FACTORY","GLOBAL").contains(actor.scope()))throw new ReportingException("SCOPE_MISMATCH","Kỳ công thuộc phạm vi nhà máy",403);
    }
    private Instant now(){return clock.instant().truncatedTo(ChronoUnit.MILLIS);}
    private static ReportingException invalid(String message){return new ReportingException("INVALID_STATE",message,409);}
    private static void validate(String reason,UUID key) {
        if(reason==null||reason.isBlank()||key==null||key.version()!=4||key.variant()!=2)throw new IllegalArgumentException("reason và Idempotency-Key hợp lệ bắt buộc");
    }
    private static String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
