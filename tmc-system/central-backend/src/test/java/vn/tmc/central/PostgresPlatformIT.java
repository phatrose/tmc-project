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
@org.springframework.test.context.ActiveProfiles("integration-preview")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PostgresPlatformIT {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("TMC_PREVIEW_PASSWORD", () -> PASSWORD);
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/platform-test");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.boot.test.web.client.TestRestTemplate http;
    private static final String PASSWORD = java.util.UUID.randomUUID().toString();
    @Test void previewLoginAndQrUseRealHttpAndDatabase() {
        var login=http.postForEntity("/api/v1/auth/login", java.util.Map.of("username","preview.admin","password",PASSWORD),com.fasterxml.jackson.databind.JsonNode.class);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        String token=login.getBody().get("accessToken").asText();
        var headers=new org.springframework.http.HttpHeaders();headers.setBearerAuth(token);
        var entity=new org.springframework.http.HttpEntity<>(headers);
        var people=http.exchange("/api/v1/employees",org.springframework.http.HttpMethod.GET,entity,com.fasterxml.jackson.databind.JsonNode.class);
        assertThat(people.getStatusCode().value()).isEqualTo(200);
        assertThat(people.getBody().get("items").size()).isGreaterThan(0);
        var qr=http.exchange("/api/v1/me/qr-token",org.springframework.http.HttpMethod.GET,entity,com.fasterxml.jackson.databind.JsonNode.class);
        assertThat(qr.getStatusCode().value()).isEqualTo(200);
        assertThat(qr.getBody().get("token").asText().split("\\.")).hasSize(3);
        assertThat(http.getForEntity("/api/v1/employees",String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(http.exchange("/api/v1/qr/keys/refresh",org.springframework.http.HttpMethod.GET,entity,String.class).getStatusCode().value()).isEqualTo(403);
    }

    @Test void realDatabaseAndFlywayAreWired() {
        assertThat(jdbc.queryForObject("select count(*) from platform_probe", Integer.class)).isZero();
        jdbc.update("insert into platform_probe(id) values (?)", 1);
        assertThat(jdbc.queryForObject("select count(*) from platform_probe", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)).isPositive();
    }
}
