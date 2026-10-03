package com.Workmanagement.integration;

import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class AiAgentApiIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("POST /api/ai/agent/chat - Manager can interact with AI agent assistant")
    void testAiAgentChatManagerSuccess() throws Exception {
        User manager = createUser("Manager", "mgr@agent.com", "pass", Role.MANAGER);

        when(aiAgentService.chat(anyString()))
                .thenReturn("You have 3 active tasks across 2 projects.");

        mockMvc.perform(post("/api/ai/agent/chat")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("What is the status of my tasks?"))
                .andExpect(status().isOk())
                .andExpect(content().string("You have 3 active tasks across 2 projects."));
    }

    @Test
    @DisplayName("POST /api/ai/agent/chat - Employee is forbidden (403 FORBIDDEN)")
    void testAiAgentChatEmployeeForbidden() throws Exception {
        User emp = createUser("Emp", "emp@agent.com", "pass", Role.EMPLOYEE);

        mockMvc.perform(post("/api/ai/agent/chat")
                        .cookie(createAuthCookie(emp))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Show all projects"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /api/ai/agent/chat - Unauthenticated request is rejected")
    void testAiAgentChatUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/ai/agent/chat")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Show system info"))
                .andExpect(status().isForbidden());
    }
}
