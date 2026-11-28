package vn.tmc.central.reportingaudit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import vn.tmc.central.reportingaudit.api.*;
import vn.tmc.central.reportingaudit.internal.adapter.PostgresAuditRepository;
import vn.tmc.central.reportingaudit.internal.service.AuditService;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static vn.tmc.central.reportingaudit.ReportingFixtures.*;

class ReportingPostgresIT {
    private static PostgreSQLContainer<?> container;
    private static DriverManagerDataSource admin;
    private String schema;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private AuditService service;

    @BeforeAll static void connect() {
        String url = System.getenv("REPORTING_TEST_JDBC_URL");
        String user = System.getenv().getOrDefault("REPORTING_TEST_DB_USER", "reporting_test");
        String password = System.getenv().getOrDefault("REPORTING_TEST_DB_PASSWORD", "");
        if (url == null || url.isBlank()) {
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
            url = container.getJdbcUrl();
            user = container.getUsername();
            password = container.getPassword();
        }
        admin = new DriverManagerDataSource(url, user, password);
    }

    @BeforeEach void createSchema() {
        schema = "reporting_it_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + schema);
        String url = admin.getUrl() + (admin.getUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema;
        dataSource = new DriverManagerDataSource(url, admin.getUsername(), admin.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        service = new AuditService(new PostgresAuditRepository(new NamedParameterJdbcTemplate(dataSource)));
    }

    @AfterEach void dropSchema() {
        if (schema != null && schema.matches("reporting_it_[0-9a-f]{32}"))
            new JdbcTemplate(admin).execute("DROP SCHEMA " + schema + " CASCADE");
    }

    @AfterAll static void stopContainer() {
        if (container != null) container.stop();
    }

    @Test void persistsV1AuditAndDeviceOnlyIdentity() {
        var id = service.append(draft());
        var row = jdbc.queryForMap("SELECT * FROM audit_logs WHERE audit_id=?", id);
        assertThat(row).containsEntry("resource_type", "PERIOD").containsEntry("resource_id", "2026-10")
                .containsEntry("result", "SUCCESS").containsEntry("correlation_id", id(3))
                .containsEntry("device_id", null).containsEntry("actor_id", id(1));
        service.append(new AuditDraft(null, id(6), null, "GATE_EMERGENCY", "COMMAND", "command-1",
                AuditEntry.Result.DENIED, AT, id(3), null));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE actor_id IS NULL AND device_id=? AND reason IS NULL",
                Long.class, id(6))).isEqualTo(1);
    }

    @Test void auditParticipatesInCallerTransactionRollback() {
        var tx = new TransactionTemplate(new JdbcTransactionManager(dataSource));
        assertThatThrownBy(() -> tx.execute(status -> {
            service.append(draft());
            throw new IllegalStateException("business rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPDATE audit_logs SET action='changed'", "DELETE FROM audit_logs", "TRUNCATE audit_logs",
            "UPDATE %s SET action='changed'", "DELETE FROM %s", "TRUNCATE %s"})
    void rejectsMutationThroughParentAndPartition(String statement) {
        service.append(draft());
        String partition = jdbc.queryForObject("SELECT tableoid::regclass::text FROM audit_logs LIMIT 1", String.class);
        assertThat(partition).matches("(?:reporting_it_[0-9a-f]{32}\\.)?audit_logs_(?:[0-9]{6}|default)");
        assertThatThrownBy(() -> jdbc.execute(statement.formatted(partition)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class).hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class)).isEqualTo(1);
    }

    @Test void keepsOutOfRangeAuditInDefaultPartition() {
        var earlier = draft();
        service.append(new AuditDraft(earlier.actorId(), null, earlier.factoryId(), earlier.action(),
                earlier.resourceType(), earlier.resourceId(), earlier.result(), AT.minus(java.time.Duration.ofDays(2000)),
                earlier.correlationId(), earlier.reason()));
        assertThat(jdbc.queryForObject("SELECT tableoid::regclass::text FROM audit_logs", String.class))
                .endsWith("audit_logs_default");
    }
}
