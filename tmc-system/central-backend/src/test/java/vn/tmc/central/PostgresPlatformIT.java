package vn.tmc.central;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

/** PHỤ TRÁCH: Task 8. DB/Flyway thật, schema test riêng; không làm migration nghiệp vụ. */
@SpringBootTest
@Testcontainers
class PostgresPlatformIT {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("spring.flyway.locations", () -> "classpath:db/platform-test");
    }
    @Autowired JdbcTemplate jdbc;
    @Test void realDatabaseAndFlywayAreWired() {
        assertThat(jdbc.queryForObject("select count(*) from platform_probe", Integer.class)).isZero();
        jdbc.update("insert into platform_probe(id) values (?)", 1);
        assertThat(jdbc.queryForObject("select count(*) from platform_probe", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)).isPositive();
    }
}
