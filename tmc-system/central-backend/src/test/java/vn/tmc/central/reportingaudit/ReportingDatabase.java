package vn.tmc.central.reportingaudit;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import java.util.UUID;

public final class ReportingDatabase implements AutoCloseable {
    private static DriverManagerDataSource admin;
    private static PostgreSQLContainer<?> container;
    public final String schema;
    public final DriverManagerDataSource dataSource;
    public final JdbcTemplate jdbc;
    public final NamedParameterJdbcTemplate named;
    public final JdbcTransactionManager transactions;

    public ReportingDatabase(){
        connect();schema="reporting_it_"+UUID.randomUUID().toString().replace("-","");
        new JdbcTemplate(admin).execute("CREATE SCHEMA "+schema);
        dataSource=new DriverManagerDataSource(admin.getUrl()+(admin.getUrl().contains("?")?"&":"?")+"currentSchema="+schema,
                admin.getUsername(),admin.getPassword());
        jdbc=new JdbcTemplate(dataSource);named=new NamedParameterJdbcTemplate(dataSource);transactions=new JdbcTransactionManager(dataSource);
    }
    public void migrate(){Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();}
    private static synchronized void connect(){
        if(admin!=null)return;
        String url=System.getenv("REPORTING_TEST_JDBC_URL");
        String user=System.getenv().getOrDefault("REPORTING_TEST_DB_USER","reporting_test");
        String password=System.getenv().getOrDefault("REPORTING_TEST_DB_PASSWORD","");
        if(url==null||url.isBlank()) {
            container=new PostgreSQLContainer<>("postgres:16-alpine");container.start();
            url=container.getJdbcUrl();user=container.getUsername();password=container.getPassword();
            Runtime.getRuntime().addShutdownHook(new Thread(()->container.stop()));
        }
        admin=new DriverManagerDataSource(url,user,password);
    }
    @Override public void close(){
        if(!schema.matches("reporting_it_[0-9a-f]{32}"))throw new IllegalStateException("Unsafe test schema");
        new JdbcTemplate(admin).execute("DROP SCHEMA "+schema+" CASCADE");
    }
}
