package com.Workmanagement.audit;

import com.Workmanagement.audit.controller.AuditLogController;
import com.Workmanagement.audit.model.AuditLogResponse;
import com.Workmanagement.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class AuditLogControllerSecurityTest {

    private MockMvc mockMvc;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AuditLogController auditLogController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(auditLogController).build();
    }

    @Test
    @DisplayName("GET /api/audit-logs returns all logs")
    void testGetAllLogsEndpoint() throws Exception {
        AuditLogResponse logResponse = new AuditLogResponse(
                1L, 2L, "Admin", "admin@example.com",
                "PROJECT_CREATED", "PROJECT", 10L, "Created project", LocalDateTime.now()
        );
        when(auditLogService.getAllLogs()).thenReturn(List.of(logResponse));

        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("PROJECT_CREATED"))
                .andExpect(jsonPath("$[0].entityType").value("PROJECT"))
                .andExpect(jsonPath("$[0].entityId").value(10));
    }

    @Test
    @DisplayName("GET /api/audit-logs/me returns user logs")
    void testGetMyLogsEndpoint() throws Exception {
        AuditLogResponse logResponse = new AuditLogResponse(
                1L, 2L, "Employee", "employee@example.com",
                "SUBMISSION_CREATED", "SUBMISSION", 10L, "Created submission", LocalDateTime.now()
        );
        when(auditLogService.getMyLogs()).thenReturn(List.of(logResponse));

        mockMvc.perform(get("/api/audit-logs/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("SUBMISSION_CREATED"))
                .andExpect(jsonPath("$[0].performedByEmail").value("employee@example.com"));
    }

    @Test
    @DisplayName("GET /api/audit-logs/entity/{entityType}/{entityId} returns entity logs")
    void testGetEntityLogsEndpoint() throws Exception {
        AuditLogResponse logResponse = new AuditLogResponse(
                1L, 2L, "Employee", "employee@example.com",
                "TASK_CREATED", "TASK", 15L, "Created task", LocalDateTime.now()
        );
        when(auditLogService.getEntityLogs("TASK", 15L)).thenReturn(List.of(logResponse));

        mockMvc.perform(get("/api/audit-logs/entity/TASK/15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("TASK_CREATED"))
                .andExpect(jsonPath("$[0].entityId").value(15));
    }

    @Test
    @DisplayName("PUT /api/audit-logs/{id} is not supported (Append-only verification)")
    void testPutAuditLogNotAllowed() throws Exception {
        mockMvc.perform(put("/api/audit-logs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"new\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /api/audit-logs/{id} is not supported (Append-only verification)")
    void testPatchAuditLogNotAllowed() throws Exception {
        mockMvc.perform(patch("/api/audit-logs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"new\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/audit-logs/{id} is not supported (Append-only verification)")
    void testDeleteAuditLogNotAllowed() throws Exception {
        mockMvc.perform(delete("/api/audit-logs/1"))
                .andExpect(status().isNotFound());
    }
}

