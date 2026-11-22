package vn.tmc.central.reportingaudit.internal.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tmc.central.reportingaudit.api.ReportingSources.PayrollAttendanceSnapshot;
import vn.tmc.central.reportingaudit.api.*;
import vn.tmc.central.reportingaudit.internal.model.OutboxBatch;
import vn.tmc.central.reportingaudit.internal.repository.PayrollRepository;
import vn.tmc.central.reportingaudit.api.ReportingSources.Page;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Supplier;

public final class PostgresPayrollRepository implements PayrollRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    public PostgresPayrollRepository(NamedParameterJdbcTemplate jdbc,PlatformTransactionManager transactions,ObjectMapper json) {
        this.jdbc=jdbc;this.transactions=new TransactionTemplate(transactions);this.json=json;
    }
    @Override public <T>T transaction(Supplier<T> work){return transactions.execute(status->work.get());}

    @Override public Period lock(UUID factory,YearMonth period) {
        var p=Map.of("factory",factory,"period",period.toString());
        jdbc.update("INSERT INTO payroll_periods(factory_id,period,state,version) VALUES(:factory,:period,'OPEN',1) ON CONFLICT DO NOTHING",p);
        return jdbc.queryForObject("SELECT * FROM payroll_periods WHERE factory_id=:factory AND period=:period FOR UPDATE",p,
                (rs,row)->new Period(factory,period.toString(),Period.State.valueOf(rs.getString("state")),rs.getInt("version"),instant(rs,"closed_at")));
    }
    @Override public void period(Period value) {
        jdbc.update("UPDATE payroll_periods SET state=:state,version=:version,closed_at=:at WHERE factory_id=:factory AND period=:period",
                new MapSqlParameterSource().addValue("state",value.state().name()).addValue("version",value.version())
                        .addValue("at",timestamp(value.closedAt())).addValue("factory",value.factoryId()).addValue("period",value.period()));
    }
    @Override public void create(PayrollBatch batch,byte[] payload,PayrollAttendanceSnapshot snapshot,String zone,Instant deadline) {
        String source;
        try{source=json.writeValueAsString(snapshot);}catch(JsonProcessingException failure){throw new IllegalStateException(failure);}
        var p=new MapSqlParameterSource().addValue("id",batch.id()).addValue("factory",batch.factoryId()).addValue("period",batch.period())
                .addValue("version",batch.periodVersion()).addValue("count",batch.recordCount()).addValue("checksum",batch.checksum())
                .addValue("payload",payload).addValue("snapshot",source).addValue("zone",zone).addValue("deadline",timestamp(deadline))
                .addValue("now",timestamp(batch.createdAt()));
        jdbc.update("""
                INSERT INTO export_batches(id,factory_id,period,period_version,state,record_count,checksum,payload,source_snapshot,factory_zone,deadline,created_at)
                VALUES(:id,:factory,:period,:version,'QUEUED',:count,:checksum,:payload,CAST(:snapshot AS jsonb),:zone,:deadline,:now)
                """,p);
        p.addValue("outbox",UUID.randomUUID());
        jdbc.update("INSERT INTO outbox_messages(id,batch_id,next_attempt_at) VALUES(:outbox,:id,:now)",p);
    }
    @Override public Optional<PayrollBatch> latest(UUID factory,YearMonth period,int version) {
        return jdbc.query("SELECT * FROM export_batches WHERE factory_id=:factory AND period=:period AND period_version=:version",
                Map.of("factory",factory,"period",period.toString(),"version",version),batchMapper()).stream().findFirst();
    }
    @Override public Optional<PayrollBatch> find(UUID id) {
        return jdbc.query("SELECT * FROM export_batches WHERE id=:id",Map.of("id",id),batchMapper()).stream().findFirst();
    }
    @Override public void history(PeriodHistory value) {
        jdbc.update("INSERT INTO period_history VALUES(:id,:factory,:period,:action,:actor,:reason,:at,:version)",
                new MapSqlParameterSource().addValue("id",value.id()).addValue("factory",value.factoryId()).addValue("period",value.period())
                        .addValue("action",value.action().name()).addValue("actor",value.actorId()).addValue("reason",value.reason())
                        .addValue("at",timestamp(value.occurredAt())).addValue("version",value.version()));
    }
    @Override public Page<PeriodHistory> history(UUID factory,YearMonth period,int page,int size) {
        var p=Map.of("factory",factory,"period",period.toString(),"size",size,"offset",(long)page*size);
        long count=jdbc.queryForObject("SELECT count(*) FROM period_history WHERE factory_id=:factory AND period=:period",p,Long.class);
        var rows=jdbc.query("SELECT * FROM period_history WHERE factory_id=:factory AND period=:period ORDER BY occurred_at DESC,id DESC LIMIT :size OFFSET :offset",p,
                (rs,row)->new PeriodHistory(rs.getObject("id",UUID.class),factory,period.toString(),PeriodHistory.Action.valueOf(rs.getString("action")),
                        rs.getObject("actor_id",UUID.class),rs.getString("reason"),instant(rs,"occurred_at"),rs.getInt("version")));
        return new Page<>(rows,page,size,count);
    }
    @Override public Optional<OutboxBatch> claim(Instant now) {
        return transaction(()->{
            var p=new MapSqlParameterSource().addValue("now",timestamp(now));
            var ids=jdbc.query("""
                    SELECT batch_id FROM outbox_messages WHERE NOT done AND next_attempt_at<=:now
                    AND (lease_until IS NULL OR lease_until<=:now) ORDER BY next_attempt_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
                    """,p,(rs,row)->rs.getObject(1,UUID.class));
            if(ids.isEmpty())return Optional.empty();
            UUID id=ids.getFirst();UUID lease=UUID.randomUUID();
            p.addValue("id",id).addValue("lease",lease).addValue("until",timestamp(now.plus(Duration.ofMinutes(2))));
            jdbc.update("UPDATE outbox_messages SET lease_id=:lease,lease_until=:until,attempts=attempts+1 WHERE batch_id=:id",p);
            jdbc.update("UPDATE export_batches SET state='SENDING' WHERE id=:id",p);
            var batch=find(id).orElseThrow();
            return Optional.of(jdbc.queryForObject("SELECT b.payload,b.factory_zone,b.deadline,o.attempts FROM export_batches b JOIN outbox_messages o ON b.id=o.batch_id WHERE b.id=:id",p,
                    (rs,row)->new OutboxBatch(batch,rs.getBytes("payload"),rs.getString("factory_zone"),instant(rs,"deadline"),lease,rs.getInt("attempts"))));
        });
    }
    @Override public boolean acknowledge(UUID id,UUID lease,PayrollBatch.State state,int accepted,String error,Instant completed) {
        return transaction(()->{
            var p=new MapSqlParameterSource().addValue("id",id).addValue("lease",lease).addValue("state",state.name())
                    .addValue("accepted",accepted).addValue("error",error).addValue("at",timestamp(completed));
            if(jdbc.update("UPDATE outbox_messages SET done=true,lease_until=NULL WHERE batch_id=:id AND lease_id=:lease AND NOT done",p)!=1) return false;
            jdbc.update("UPDATE export_batches SET state=:state,accepted_count=:accepted,error_code=:error,completed_at=:at WHERE id=:id",p);
            return true;
        });
    }
    @Override public boolean retry(UUID id,UUID lease,String error,Instant next) {
        return transaction(()->{
            var p=new MapSqlParameterSource().addValue("id",id).addValue("lease",lease).addValue("error",error).addValue("next",timestamp(next));
            if(jdbc.update("UPDATE outbox_messages SET lease_until=NULL,lease_id=NULL,next_attempt_at=:next WHERE batch_id=:id AND lease_id=:lease AND NOT done",p)!=1)return false;
            jdbc.update("UPDATE export_batches SET state='QUEUED',error_code=:error WHERE id=:id",p);return true;
        });
    }
    @Override public boolean deadlineAlert(UUID factory,YearMonth period,int version,Instant now) {
        return jdbc.update("INSERT INTO reporting_deadline_alerts VALUES(:factory,:period,:version,:at) ON CONFLICT DO NOTHING",
                Map.of("factory",factory,"period",period.toString(),"version",version,"at",timestamp(now)))==1;
    }
    @Override public UUID systemIdentity() {
        return jdbc.queryForObject("SELECT id FROM reporting_system_identity WHERE name='payroll-scheduler'",Map.of(),UUID.class);
    }
    @Override public Optional<YearMonth> firstPeriod(UUID factory) {
        return jdbc.query("SELECT min(period) FROM payroll_periods WHERE factory_id=:factory",Map.of("factory",factory),(rs,row)->rs.getString(1))
                .stream().filter(Objects::nonNull).map(YearMonth::parse).findFirst();
    }
    private static RowMapper<PayrollBatch> batchMapper(){return (rs,row)->new PayrollBatch(rs.getObject("id",UUID.class),rs.getObject("factory_id",UUID.class),
            rs.getString("period"),rs.getInt("period_version"),PayrollBatch.State.valueOf(rs.getString("state")),rs.getInt("record_count"),rs.getInt("accepted_count"),
            rs.getString("checksum"),instant(rs,"created_at"),instant(rs,"completed_at"),rs.getString("error_code"));}
    private static Timestamp timestamp(Instant value){return value==null?null:Timestamp.from(value);}
    private static Instant instant(ResultSet rs,String name)throws SQLException{var value=rs.getTimestamp(name);return value==null?null:value.toInstant();}
}
