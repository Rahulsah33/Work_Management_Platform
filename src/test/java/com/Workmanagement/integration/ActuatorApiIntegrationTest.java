package com.Workmanagement.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ActuatorApiIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("GET /actuator/health - Public health check returns UP status and does not leak credentials")
    void testActuatorHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.db.details.password").doesNotExist())
                .andExpect(jsonPath("$.components.db.details.username").doesNotExist());
    }

    @Test
    @DisplayName("GET /actuator/info - Public info returns application metadata and hides environment details")
    void testActuatorInfo() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("AI-Powered Work Management Platform"))
                .andExpect(jsonPath("$.app.version").value("1.0.0"))
                .andExpect(jsonPath("$.env").doesNotExist())
                .andExpect(jsonPath("$.system").doesNotExist());
    }

    @Test
    @DisplayName("GET /actuator/env - Sensitive actuator endpoints are disabled/not exposed (404/401)")
    void testActuatorSensitiveEndpointsNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().is(anyOf(is(401), is(403), is(404))));
    }
}
