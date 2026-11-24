package vn.tmc.central.reportingaudit.internal.adapter;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import vn.tmc.central.reportingaudit.api.AuditEntry;
import vn.tmc.central.reportingaudit.api.AuditScope;
import vn.tmc.central.reportingaudit.internal.repository.AuditRepository;
import java.sql.Timestamp;
import java.util.Objects;

public final class PostgresAuditRepository implements AuditRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public PostgresAuditRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void append(AuditEntry entry) {
        append(entry,new AuditScope(entry.factoryId(),null,null));
    }

    @Override
    public void append(AuditEntry entry,AuditScope scope) {
        var parameters = new MapSqlParameterSource()
                .addValue("id", entry.id()).addValue("actor", entry.actorId())
                .addValue("device", entry.deviceId()).addValue("factory", entry.factoryId())
                .addValue("action", entry.action()).addValue("type", entry.resourceType())
                .addValue("resource", entry.resourceId()).addValue("result", entry.result().name())
                .addValue("at", Timestamp.from(entry.occurredAt()))
                .addValue("correlation", entry.correlationId()).addValue("reason", entry.reason())
                .addValue("employee",scope.employeeId()).addValue("department",scope.departmentId());
        jdbc.update("""
                INSERT INTO audit_logs(audit_id,actor_id,device_id,factory_id,action,resource_type,
                    resource_id,result,occurred_at,correlation_id,reason,employee_id,department_id)
                VALUES(:id,:actor,:device,:factory,:action,:type,:resource,:result,:at,:correlation,:reason,:employee,:department)
                """, parameters);
    }
}
