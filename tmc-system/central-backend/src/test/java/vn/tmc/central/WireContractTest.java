package vn.tmc.central;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** PHỤ TRÁCH: Task 8. Controller fixture chỉ nằm trong test, không được expose production. */
class WireContractTest {
    private final ObjectMapper mapper;
    private final MockMvc mvc;
    WireContractTest() {
        var builder = new Jackson2ObjectMapperBuilder().failOnUnknownProperties(true);
        new PlatformConfiguration().wireTime().customize(builder);
        mapper = builder.build();
        mvc = MockMvcBuilders.standaloneSetup(new FixtureController())
            .setControllerAdvice(new ApiExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
            .addFilters(new RequestIdFilter(mapper)).build();
    }
    record Input(String name) {}
    @RestController static class FixtureController {
        @PostMapping("/fixture") Input echo(@RequestBody Input input) { return input; }
        @GetMapping("/crash") void crash() { throw new IllegalStateException("SECRET_MUST_NOT_ESCAPE"); }
        @GetMapping("/dependency") void unavailable() {
            throw new ApiException(503, "DEPENDENCY_UNAVAILABLE", "Service unavailable.", true);
        }
        @GetMapping("/forbidden") void forbidden() {
            throw new org.springframework.security.access.AccessDeniedException("PRIVATE_SCOPE");
        }
        @GetMapping("/unauthenticated") void unauthenticated() {
            throw new org.springframework.security.authentication.BadCredentialsException("PRIVATE_CREDENTIAL");
        }
    }
    @Test void unknownFieldsFailAndNullIsPreserved() throws Exception {
        mvc.perform(post("/fixture").contentType("application/json").content("{\"name\":\"a\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
        assertThat(mapper.writeValueAsString(new Input(null))).isEqualTo("{\"name\":null}");
        assertThat(mapper.writeValueAsString(Instant.parse("2026-11-17T02:00:00Z")))
            .isEqualTo("\"2026-11-17T02:00:00.000Z\"");
    }
    @Test void internalErrorsDoNotLeakAndCorrelationMatches() throws Exception {
        var response = mvc.perform(get("/crash")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.retryable").value(true)).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("SECRET_MUST_NOT_ESCAPE", "IllegalStateException");
        assertThat(mapper.readTree(response.getContentAsString()).get("requestId").asText())
            .isEqualTo(response.getHeader("X-Request-Id"));
    }
    @Test void declaredDependencyErrorPreservesContract() throws Exception {
        mvc.perform(get("/dependency")).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"))
            .andExpect(jsonPath("$.retryable").value(true));
    }
    @Test void methodSecurityErrorsAreNotConvertedToInternalErrors() throws Exception {
        mvc.perform(get("/forbidden")).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/unauthenticated")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
