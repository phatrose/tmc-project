package vn.tmc.central.reportingaudit.internal.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.tmc.central.reportingaudit.api.AuditEntry;
import vn.tmc.central.reportingaudit.api.ReportExport;
import vn.tmc.central.reportingaudit.internal.model.AuditFilter;
import vn.tmc.central.reportingaudit.internal.model.ExportJob;
import vn.tmc.central.reportingaudit.internal.model.ReportFile;
import vn.tmc.central.reportingaudit.internal.repository.ReportingRepository;
import vn.tmc.central.reportingaudit.internal.service.ReportingException;
import vn.tmc.central.reportingaudit.api.ReportingSources.ActorContext;
import vn.tmc.central.reportingaudit.api.ReportingSources.Page;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class PostgresReportingRepository implements ReportingRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;

    public PostgresReportingRepository(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactions,
            ObjectMapper json) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactions);
        this.json = json;
    }

    @Override public void create(ExportJob job) {
        var p = new MapSqlParameterSource().addValue("id", job.view().id()).addValue("kind", job.kind().name())
                .addValue("factory", job.factoryId()).addValue("owner", job.ownerId())
                .addValue("actor", encode(job.actor())).addValue("period", job.period())
                .addValue("from", timestamp(job.from())).addValue("to", timestamp(job.to()))
                .addValue("format", job.view().format().name()).addValue("at", timestamp(job.view().createdAt()));
        jdbc.update("""
                INSERT INTO report_exports(id,kind,factory_id,owner_id,actor_context,period,from_at,to_at,format,state,created_at)
                VALUES(:id,:kind,:factory,:owner,CAST(:actor AS jsonb),:period,:from,:to,:format,'QUEUED',:at)
                """, p);
    }

    @Override public Optional<ExportJob> find(UUID id) {
        return jdbc.query("SELECT * FROM report_exports WHERE id=:id", Map.of("id", id), jobMapper()).stream().findFirst();
    }

    @Override public Optional<ExportJob> claim(Instant now) {
        return transactions.execute(status -> {
            var p = new MapSqlParameterSource().addValue("now", timestamp(now));
            var found = jdbc.query("""
                    SELECT * FROM report_exports WHERE state='QUEUED' OR (state='RUNNING' AND lease_until<=:now)
                    ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
                    """, p, jobMapper());
            if (found.isEmpty()) return Optional.empty();
            var old = found.getFirst();
            UUID lease = UUID.randomUUID();
            p.addValue("id", old.view().id()).addValue("lease", lease)
                    .addValue("until", timestamp(now.plus(Duration.ofMinutes(10))));
            jdbc.update("UPDATE report_exports SET state='RUNNING',lease_id=:lease,lease_until=:until WHERE id=:id", p);
            return find(old.view().id());
        });
    }

    @Override public boolean complete(UUID id, UUID leaseId, ReportFile file) {
        return jdbc.update("""
                UPDATE report_exports SET state='READY',expires_at=created_at+interval '24 hours',
                    file_bytes=:bytes,media_type=:media,extension=:extension,error_code=NULL,lease_until=NULL
                WHERE id=:id AND state='RUNNING' AND lease_id=:lease
                """, new MapSqlParameterSource().addValue("id", id).addValue("lease", leaseId)
                .addValue("bytes", file.bytes()).addValue("media", file.mediaType()).addValue("extension", file.extension())) == 1;
    }

    @Override public boolean fail(UUID id, UUID leaseId, String code) {
        return jdbc.update("""
                UPDATE report_exports SET state='FAILED',error_code=:code,lease_until=NULL
                WHERE id=:id AND state='RUNNING' AND lease_id=:lease
                """, Map.of("id", id, "lease", leaseId, "code", code)) == 1;
    }

    @Override public Optional<ReportFile> file(UUID id) {
        return jdbc.query("SELECT file_bytes,media_type,extension FROM report_exports WHERE id=:id AND state='READY'",
                Map.of("id", id), (rs, row) -> new ReportFile(rs.getBytes(1), rs.getString(2), rs.getString(3)))
                .stream().findFirst();
    }

    @Override public Page<AuditEntry> audit(AuditFilter filter) {
        var p = parameters(filter).addValue("size", filter.size()).addValue("offset", (long) filter.page() * filter.size());
        String where = where(filter);
        long count = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE " + where, p, Long.class);
        var rows = jdbc.query("SELECT * FROM audit_logs WHERE " + where
                + " ORDER BY occurred_at DESC,audit_id DESC LIMIT :size OFFSET :offset", p, auditMapper());
        return new Page<>(rows, filter.page(), filter.size(), count);
    }

    @Override public List<AuditEntry> auditExport(AuditFilter filter) {
        return jdbc.query("SELECT * FROM audit_logs WHERE " + where(filter)
                + " ORDER BY occurred_at DESC,audit_id DESC", parameters(filter), auditMapper());
    }

    @Override public <T> T idempotent(UUID actor, String path, UUID key, String fingerprint,
            Instant now, Class<T> type, Supplier<T> operation) {
        return transactions.execute(status -> {
            var p = new MapSqlParameterSource().addValue("actor", actor).addValue("path", "POST " + path)
                    .addValue("key", key).addValue("hash", fingerprint).addValue("now", timestamp(now))
                    .addValue("until", timestamp(now.plus(Duration.ofHours(48))));
            jdbc.update("DELETE FROM reporting_idempotency WHERE actor_id=:actor AND path=:path AND key=:key AND expires_at<=:now", p);
            jdbc.update("""
                    INSERT INTO reporting_idempotency(actor_id,path,key,input_hash,expires_at)
                    VALUES(:actor,:path,:key,:hash,:until) ON CONFLICT DO NOTHING
                    """, p);
            var saved = jdbc.queryForMap("SELECT input_hash,outcome::text FROM reporting_idempotency "
                    + "WHERE actor_id=:actor AND path=:path AND key=:key FOR UPDATE", p);
            if (!fingerprint.equals(saved.get("input_hash")))
                throw new ReportingException("IDEMPOTENCY_CONFLICT", "Idempotency-Key đã dùng cho nội dung khác", 409);
            if (saved.get("outcome") != null) return decode(saved.get("outcome").toString(), type);
            T result = operation.get();
            p.addValue("outcome", encode(result));
            jdbc.update("UPDATE reporting_idempotency SET outcome=CAST(:outcome AS jsonb) "
                    + "WHERE actor_id=:actor AND path=:path AND key=:key", p);
            return result;
        });
    }

    private MapSqlParameterSource parameters(AuditFilter filter) {
        return new MapSqlParameterSource().addValue("actor", filter.actorId()).addValue("action", filter.action())
                .addValue("from", timestamp(filter.from())).addValue("to", timestamp(filter.to()))
                .addValue("factories", filter.factoryIds()).addValue("employee",filter.employeeId()).addValue("departments",filter.departmentIds());
    }

    private String where(AuditFilter filter) {
        StringBuilder sql = new StringBuilder("1=1");
        if (!filter.global()) sql.append(filter.factoryIds().isEmpty() ? " AND 1=0" : " AND factory_id IN (:factories)");
        if(filter.employeeId()!=null) sql.append(" AND employee_id=:employee");
        if(filter.departmental()) sql.append(filter.departmentIds().isEmpty()?" AND 1=0":" AND department_id IN (:departments)");
        if (filter.actorId() != null) sql.append(" AND actor_id=:actor");
        if (filter.action() != null) sql.append(" AND action=:action");
        if (filter.from() != null) sql.append(" AND occurred_at>=:from");
        if (filter.to() != null) sql.append(" AND occurred_at<:to");
        return sql.toString();
    }

    private RowMapper<ExportJob> jobMapper() {
        return (rs, row) -> new ExportJob(new ReportExport(rs.getObject("id", UUID.class),
                ReportExport.State.valueOf(rs.getString("state")), ReportExport.Format.valueOf(rs.getString("format")),
                instant(rs, "created_at"), instant(rs, "expires_at"), rs.getString("error_code")),
                ExportJob.Kind.valueOf(rs.getString("kind")), rs.getObject("factory_id", UUID.class),
                rs.getObject("owner_id", UUID.class), decode(rs.getString("actor_context"), ActorContext.class),
                rs.getString("period"), instant(rs, "from_at"), instant(rs, "to_at"), rs.getObject("lease_id", UUID.class));
    }

    private static RowMapper<AuditEntry> auditMapper() {
        return (rs, row) -> new AuditEntry(rs.getObject("audit_id", UUID.class), rs.getObject("actor_id", UUID.class),
                rs.getObject("device_id", UUID.class), rs.getObject("factory_id", UUID.class), rs.getString("action"),
                rs.getString("resource_type"), rs.getString("resource_id"), AuditEntry.Result.valueOf(rs.getString("result")),
                instant(rs, "occurred_at"), rs.getObject("correlation_id", UUID.class), rs.getString("reason"));
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException failure) { throw new ReportingException("INTERNAL_ERROR", "Không lưu được dữ liệu báo cáo", 500); }
    }

    private <T> T decode(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (JsonProcessingException failure) { throw new ReportingException("STORAGE_UNAVAILABLE", "Dữ liệu báo cáo không đọc được", 503); }
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(ResultSet rs, String name) throws SQLException {
        Timestamp value = rs.getTimestamp(name);
        return value == null ? null : value.toInstant();
    }
}
