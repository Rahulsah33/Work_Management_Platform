package com.Workmanagement.integration;

import com.Workmanagement.auth.dto.LoginRequest;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class EmployeeTaskExecutionIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Test 1: Login as EMPLOYEE → PASS: Authenticates employee and issues JWT cookie")
    void test1_EmployeeLogin() throws Exception {
        User employee = createUser("Alex Employee", "alex.emp@company.com", "EmpPass123!", Role.EMPLOYEE);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("alex.emp@company.com");
        loginRequest.setPassword("EmpPass123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("jwt=")))
                .andExpect(jsonPath("$.userId").value(employee.getId()))
                .andExpect(jsonPath("$.email").value("alex.emp@company.com"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Test 2: GET /api/tasks/my → 200 OK: Only returns tasks assigned to authenticated employee")
    void test2_GetMyTasks() throws Exception {
        User manager = createUser("Manager One", "mgr1@company.com", "MgrPass123!", Role.MANAGER);
        User empA = createUser("Employee A", "empa@company.com", "EmpPass123!", Role.EMPLOYEE);
        User empB = createUser("Employee B", "empb@company.com", "EmpPass123!", Role.EMPLOYEE);

        Project project = createProject("Mobile App", "Native Mobile Client", manager, ProjectStatus.IN_PROGRESS);

        Task taskA1 = createTask("Design Login Screen", "Figma UI to React Native", project, empA, TaskStatus.IN_PROGRESS);
        Task taskA2 = createTask("Setup Redux Store", "State management architecture", project, empA, TaskStatus.ASSIGNED);
        createTask("API Gateway Setup", "Backend router integration", project, empB, TaskStatus.ASSIGNED);

        Cookie cookieEmpA = createAuthCookie(empA);

        // Test GET /api/tasks/my
        mockMvc.perform(get("/api/tasks/my").cookie(cookieEmpA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(taskA1.getId().intValue(), taskA2.getId().intValue())))
                .andExpect(jsonPath("$[*].title", containsInAnyOrder("Design Login Screen", "Setup Redux Store")));

        // Also test GET /api/tasks/me alias
        mockMvc.perform(get("/api/tasks/me").cookie(cookieEmpA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("Test 3: Employee Isolation → PASS: Employee A does not receive Employee B's tasks")
    void test3_EmployeeTaskIsolation() throws Exception {
        User manager = createUser("Manager One", "mgr1@company.com", "MgrPass123!", Role.MANAGER);
        User empA = createUser("Employee A", "empa@company.com", "EmpPass123!", Role.EMPLOYEE);
        User empB = createUser("Employee B", "empb@company.com", "EmpPass123!", Role.EMPLOYEE);

        Project project = createProject("Cloud Infrastructure", "Kubernetes cluster migration", manager, ProjectStatus.IN_PROGRESS);

        createTask("Setup Ingress Controller", "NGINX ingress config", project, empA, TaskStatus.IN_PROGRESS);
        Task taskB = createTask("Deploy Postgres Operator", "High availability database", project, empB, TaskStatus.ASSIGNED);

        Cookie cookieEmpA = createAuthCookie(empA);
        Cookie cookieEmpB = createAuthCookie(empB);

        // Employee A's tasks
        mockMvc.perform(get("/api/tasks/my").cookie(cookieEmpA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Setup Ingress Controller"));

        // Employee B's tasks
        mockMvc.perform(get("/api/tasks/my").cookie(cookieEmpB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Deploy Postgres Operator"));

        // Employee A attempting to query Employee B's tasks via employee ID endpoint -> 403 Forbidden
        mockMvc.perform(get("/api/tasks/employee/" + empB.getId()).cookie(cookieEmpA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Test 4: Employee attempts to access unassigned task (GET /api/tasks/{id}) → 403 Forbidden")
    void test4_EmployeeCannotAccessUnassignedTask() throws Exception {
        User manager = createUser("Manager One", "mgr1@company.com", "MgrPass123!", Role.MANAGER);
        User empA = createUser("Employee A", "empa@company.com", "EmpPass123!", Role.EMPLOYEE);
        User empB = createUser("Employee B", "empb@company.com", "EmpPass123!", Role.EMPLOYEE);

        Project project = createProject("Security Audit", "Penetration testing", manager, ProjectStatus.IN_PROGRESS);

        Task taskA = createTask("Audit Auth Flow", "Review OAuth & JWT", project, empA, TaskStatus.IN_PROGRESS);
        Task taskB = createTask("Audit DB Queries", "Check SQL injection vectors", project, empB, TaskStatus.IN_PROGRESS);

        Cookie cookieEmpA = createAuthCookie(empA);

        // Employee A accesses own task -> 200 OK
        mockMvc.perform(get("/api/tasks/" + taskA.getId()).cookie(cookieEmpA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskA.getId()))
                .andExpect(jsonPath("$.title").value("Audit Auth Flow"));

        // Employee A accesses Employee B's task -> 403 Forbidden
        mockMvc.perform(get("/api/tasks/" + taskB.getId()).cookie(cookieEmpA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Test 5: Unauthenticated request to employee task endpoint → 403/401 Forbidden")
    void test5_UnauthenticatedAccessRejected() throws Exception {
        mockMvc.perform(get("/api/tasks/my"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 6: MANAGER login & existing Manager Dashboard → PASS")
    void test6_ManagerLoginAndDashboard() throws Exception {
        User manager = createUser("Sarah Manager", "sarah@company.com", "MgrPass123!", Role.MANAGER);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("sarah@company.com");
        loginRequest.setPassword("MgrPass123!");

        // Manager Login
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));

        Cookie managerCookie = createAuthCookie(manager);

        // Manager Dashboard
        mockMvc.perform(get("/api/dashboard/manager").cookie(managerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProjects").exists());
    }

    @Test
    @DisplayName("Test 7: ADMIN login & existing Admin functionality → PASS")
    void test7_AdminLoginAndFunctionality() throws Exception {
        User admin = createUser("Super Admin", "admin@company.com", "AdminPass123!", Role.ADMIN);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("admin@company.com");
        loginRequest.setPassword("AdminPass123!");

        // Admin Login
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        Cookie adminCookie = createAuthCookie(admin);

        // Admin verification endpoint
        mockMvc.perform(get("/api/users/admin").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(content().string("Welcome ADMIN"));

        // Admin global user directory
        mockMvc.perform(get("/api/users").cookie(adminCookie))
                .andExpect(status().isOk());
    }
}
