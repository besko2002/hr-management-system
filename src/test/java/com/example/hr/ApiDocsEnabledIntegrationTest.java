package com.example.hr;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With SPRINGDOC_ENABLED=true the API docs are served without authentication. */
@TestPropertySource(properties = "SPRINGDOC_ENABLED=true")
class ApiDocsEnabledIntegrationTest extends AbstractIntegrationTest {

    @Test
    void theApiDocsArePublicWhenEnabled() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
