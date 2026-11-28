package vn.tmc.central;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/** PHỤ TRÁCH: Task 8. Wiring thực, không mock auth để giả định nghiệp vụ đã sẵn sàng. */
@SpringBootTest
@ActiveProfiles("platform")
@AutoConfigureMockMvc
class PlatformHttpTest {
    @Autowired MockMvc mvc;
    @Test void healthIsPublicAndEchoesCorrelation() throws Exception {
        String id = "00000000-0000-4000-8000-000000000041";
        mvc.perform(get("/actuator/health").header("X-Request-Id", id))
            .andExpect(status().isOk()).andExpect(header().string("X-Request-Id", id))
            .andExpect(jsonPath("$.status").value("UP"));
    }
    @Test void businessRoutesFailClosedEvenWithInventedBearer() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .header("Authorization", "Bearer NOT_A_REAL_SESSION").content("{}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.retryable").value(false))
            .andExpect(jsonPath("$.requestId").isString()).andExpect(jsonPath("$.trace").doesNotExist());
    }
    @Test void invalidRequestIdIsRejectedWithoutEchoingInput() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Request-Id", "secret-invalid"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(header().string("X-Request-Id", not("secret-invalid")));
    }
}
