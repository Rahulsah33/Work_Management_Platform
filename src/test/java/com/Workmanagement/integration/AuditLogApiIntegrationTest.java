package com.Workmanagement.integration;

import com.Workmanagement.audit.entity.AuditAction;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class AuditLogApiIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("GET /api/audit-logs - Admin accesses global audit logs; Non-admins receive 403 FORBIDDEN")
    void testGlobalAuditLogAccessControl() throws Exception {
        User admin = createUser("Admin", "admin@audit.com", "pass", Role.ADMIN);
        User manager = createUser("Manager", "mgr@audit.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@audit.com", "pass", Role.EMPLOYEE);

        createAuditLog(manager, AuditAction.PROJECT_CREATED, "PROJECT", 10L, "Created Project X");
        createAuditLog(emp, AuditAction.SUBMISSION_CREATED, "SUBMISSION", 20L, "Created Submission Y");

        // Admin gets all logs -> 200 OK
        mockMvc.perform(get("/api/audit-logs").cookie(createAuthCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Manager receives 403 Forbidden
        mockMvc.perform(get("/api/audit-logs").cookie(createAuthCookie(manager)))
                .andExpect(status().isForbidden());

        // Employee receives 403 Forbidden
        mockMvc.perform(get("/api/audit-logs").cookie(createAuthCookie(emp)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/audit-logs/me - User retrieves their own personal audit records")
    void testGetMyAuditLogs() throws Exception {
        User manager = createUser("Manager", "mgr@audit.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@audit.com", "pass", Role.EMPLOYEE);

        createAuditLog(manager, AuditAction.PROJECT_CREATED, "PROJECT", 10L, "Created Project X");
        createAuditLog(emp, AuditAction.SUBMISSION_CREATED, "SUBMISSION", 20L, "Created Submission Y");

        mockMvc.perform(get("/api/audit-logs/me").cookie(createAuthCookie(emp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].action").value("SUBMISSION_CREATED"))
                .andExpect(jsonPath("$[0].performedByEmail").value("emp@audit.com"));
    }

    @Test
    @DisplayName("Append-Only Protection: PUT, PATCH, DELETE on /api/audit-logs are not allowed")
    void testAuditLogImmutabilityEndpoints() throws Exception {
        User admin = createUser("Admin", "admin@audit.com", "pass", Role.ADMIN);

        mockMvc.perform(put("/api/audit-logs/1")
                        .cookie(createAuthCookie(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"tamper\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/audit-logs/1")
                        .cookie(createAuthCookie(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"tamper\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/audit-logs/1")
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isNotFound());
    }
}
