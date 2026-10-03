package com.Workmanagement.integration;

import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class RoleSecurityIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Role Access: /api/users/admin allows ADMIN and denies MANAGER & EMPLOYEE")
    void testAdminEndpointRoleAccess() throws Exception {
        User admin = createUser("Admin", "admin@sec.com", "pass", Role.ADMIN);
        User manager = createUser("Manager", "manager@sec.com", "pass", Role.MANAGER);
        User employee = createUser("Employee", "employee@sec.com", "pass", Role.EMPLOYEE);

        Cookie adminCookie = createAuthCookie(admin);
        Cookie managerCookie = createAuthCookie(manager);
        Cookie employeeCookie = createAuthCookie(employee);

        // ADMIN -> 200 OK
        mockMvc.perform(get("/api/users/admin").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(content().string("Welcome ADMIN"));

        // MANAGER -> 403 Forbidden
        mockMvc.perform(get("/api/users/admin").cookie(managerCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        // EMPLOYEE -> 403 Forbidden
        mockMvc.perform(get("/api/users/admin").cookie(employeeCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Role Access: /api/users/manager allows MANAGER and denies EMPLOYEE")
    void testManagerEndpointRoleAccess() throws Exception {
        User manager = createUser("Manager", "manager@sec.com", "pass", Role.MANAGER);
        User employee = createUser("Employee", "employee@sec.com", "pass", Role.EMPLOYEE);

        Cookie managerCookie = createAuthCookie(manager);
        Cookie employeeCookie = createAuthCookie(employee);

        mockMvc.perform(get("/api/users/manager").cookie(managerCookie))
                .andExpect(status().isOk())
                .andExpect(content().string("Logged in as manager"));

        mockMvc.perform(get("/api/users/manager").cookie(employeeCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Role Access: /api/users/employee allows EMPLOYEE and denies MANAGER")
    void testEmployeeEndpointRoleAccess() throws Exception {
        User manager = createUser("Manager", "manager@sec.com", "pass", Role.MANAGER);
        User employee = createUser("Employee", "employee@sec.com", "pass", Role.EMPLOYEE);

        Cookie managerCookie = createAuthCookie(manager);
        Cookie employeeCookie = createAuthCookie(employee);

        mockMvc.perform(get("/api/users/employee").cookie(employeeCookie))
                .andExpect(status().isOk())
                .andExpect(content().string("Logged in as employee"));

        mockMvc.perform(get("/api/users/employee").cookie(managerCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Directory Access: /api/users, /api/users/employees, /api/users/managers allow ADMIN & MANAGER and deny EMPLOYEE")
    void testDirectoryEndpointsRoleAccess() throws Exception {
        User admin = createUser("Admin", "admin@sec.com", "pass", Role.ADMIN);
        User manager = createUser("Manager", "manager@sec.com", "pass", Role.MANAGER);
        User employee = createUser("Employee", "employee@sec.com", "pass", Role.EMPLOYEE);

        Cookie adminCookie = createAuthCookie(admin);
        Cookie managerCookie = createAuthCookie(manager);
        Cookie employeeCookie = createAuthCookie(employee);

        // GET /api/users
        mockMvc.perform(get("/api/users").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        mockMvc.perform(get("/api/users").cookie(managerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        mockMvc.perform(get("/api/users").cookie(employeeCookie))
                .andExpect(status().isForbidden());

        // GET /api/users/employees
        mockMvc.perform(get("/api/users/employees").cookie(managerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value("employee@sec.com"));

        mockMvc.perform(get("/api/users/employees").cookie(employeeCookie))
                .andExpect(status().isForbidden());

        // GET /api/users/managers
        mockMvc.perform(get("/api/users/managers").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/users/managers").cookie(employeeCookie))
                .andExpect(status().isForbidden());
    }
}
