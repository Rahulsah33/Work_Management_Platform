package com.Workmanagement.integration;

import com.Workmanagement.auth.dto.LoginRequest;
import com.Workmanagement.user.dto.CreateUserRequest;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class ManagerAuthenticationLifecycleIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("1. ADMIN login → PASS: Valid admin credentials return token & set HttpOnly cookie")
    void test1_AdminLogin() throws Exception {
        User admin = createUser("Admin User", "admin@company.com", "AdminPass123!", Role.ADMIN);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("admin@company.com");
        loginRequest.setPassword("AdminPass123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("jwt=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(jsonPath("$.userId").value(admin.getId()))
                .andExpect(jsonPath("$.email").value("admin@company.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("2. ADMIN creates MANAGER → PASS: Admin creates manager via /api/users/manager")
    void test2_AdminCreatesManager() throws Exception {
        User admin = createUser("Admin User", "admin@company.com", "AdminPass123!", Role.ADMIN);
        Cookie adminCookie = createAuthCookie(admin);

        CreateUserRequest createMgr = new CreateUserRequest();
        createMgr.setName("Sarah Manager");
        createMgr.setEmail("sarah.manager@company.com");
        createMgr.setPassword("ManagerPass123!");

        mockMvc.perform(post("/api/users/manager")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createMgr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sarah Manager"))
                .andExpect(jsonPath("$.email").value("sarah.manager@company.com"))
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        User created = userRepository.findByEmail("sarah.manager@company.com").orElse(null);
        assertNotNull(created);
        assertEquals(Role.MANAGER, created.getRole());
        assertTrue(passwordEncoder.matches("ManagerPass123!", created.getPassword()));
    }

    @Test
    @DisplayName("3. MANAGER login → PASS: Manager logs in with email and password")
    void test3_ManagerLogin() throws Exception {
        createUser("Sarah Manager", "sarah.manager@company.com", "ManagerPass123!", Role.MANAGER);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("sarah.manager@company.com");
        loginRequest.setPassword("ManagerPass123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("jwt=")))
                .andExpect(jsonPath("$.email").value("sarah.manager@company.com"))
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("4. MANAGER JWT/cookie authentication → PASS: Authenticated manager accesses /api/users/me")
    void test4_ManagerJwtCookieAuth() throws Exception {
        User manager = createUser("Sarah Manager", "sarah.manager@company.com", "ManagerPass123!", Role.MANAGER);
        Cookie managerCookie = createAuthCookie(manager);

        mockMvc.perform(get("/api/users/me")
                        .cookie(managerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(manager.getId()))
                .andExpect(jsonPath("$.name").value("Sarah Manager"))
                .andExpect(jsonPath("$.email").value("sarah.manager@company.com"))
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    @DisplayName("5. MANAGER accesses Manager Dashboard → PASS: Manager accesses /api/dashboard/manager")
    void test5_ManagerAccessesDashboard() throws Exception {
        User manager = createUser("Sarah Manager", "sarah.manager@company.com", "ManagerPass123!", Role.MANAGER);
        Cookie managerCookie = createAuthCookie(manager);

        mockMvc.perform(get("/api/dashboard/manager")
                        .cookie(managerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProjects").exists())
                .andExpect(jsonPath("$.totalTasks").exists());
    }

    @Test
    @DisplayName("6. EMPLOYEE cannot access Manager APIs → PASS: 403 Forbidden")
    void test6_EmployeeCannotAccessManagerApis() throws Exception {
        User employee = createUser("Bob Employee", "bob@company.com", "EmpPass123!", Role.EMPLOYEE);
        Cookie employeeCookie = createAuthCookie(employee);

        // Dashboard
        mockMvc.perform(get("/api/dashboard/manager")
                        .cookie(employeeCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        // Manager verification endpoint
        mockMvc.perform(get("/api/users/manager")
                        .cookie(employeeCookie))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("7. EMPLOYEE cannot create MANAGER → PASS: 403 Forbidden")
    void test7_EmployeeCannotCreateManager() throws Exception {
        User employee = createUser("Bob Employee", "bob@company.com", "EmpPass123!", Role.EMPLOYEE);
        Cookie employeeCookie = createAuthCookie(employee);

        CreateUserRequest req = new CreateUserRequest("Malicious Mgr", "malicious@company.com", "Pass123!", Role.MANAGER);

        mockMvc.perform(post("/api/users/manager")
                        .cookie(employeeCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/users")
                        .cookie(employeeCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("8. MANAGER cannot access ADMIN-only APIs → PASS: 403 Forbidden on /api/users/admin and /api/audit-logs")
    void test8_ManagerCannotAccessAdminApis() throws Exception {
        User manager = createUser("Sarah Manager", "sarah.manager@company.com", "ManagerPass123!", Role.MANAGER);
        Cookie managerCookie = createAuthCookie(manager);

        mockMvc.perform(get("/api/users/admin")
                        .cookie(managerCookie))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/audit-logs")
                        .cookie(managerCookie))
                .andExpect(status().isForbidden());

        CreateUserRequest req = new CreateUserRequest("Sub Mgr", "submgr@company.com", "Pass123!", Role.MANAGER);
        mockMvc.perform(post("/api/users/manager")
                        .cookie(managerCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("9. Duplicate Manager email → proper error: 409 Conflict")
    void test9_DuplicateManagerEmail() throws Exception {
        User admin = createUser("Admin User", "admin@company.com", "AdminPass123!", Role.ADMIN);
        createUser("Existing Manager", "dup.manager@company.com", "ManagerPass123!", Role.MANAGER);
        Cookie adminCookie = createAuthCookie(admin);

        CreateUserRequest dupReq = new CreateUserRequest("Dup Manager", "dup.manager@company.com", "NewPass123!", Role.MANAGER);

        mockMvc.perform(post("/api/users/manager")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(containsString("Email is already registered")));
    }

    @Test
    @DisplayName("10. Invalid Manager data → proper validation: 400 Bad Request")
    void test10_InvalidManagerData() throws Exception {
        User admin = createUser("Admin User", "admin@company.com", "AdminPass123!", Role.ADMIN);
        Cookie adminCookie = createAuthCookie(admin);

        CreateUserRequest invalidReq = new CreateUserRequest("", "not-an-email", "123", Role.MANAGER);

        mockMvc.perform(post("/api/users/manager")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    @DisplayName("11. Password stored hashed → PASS: Password in DB is BCrypt encoded and never plaintext")
    void test11_PasswordStoredHashed() throws Exception {
        User admin = createUser("Admin User", "admin@company.com", "AdminPass123!", Role.ADMIN);
        Cookie adminCookie = createAuthCookie(admin);

        String rawPassword = "ComplexRawPassword99!";
        CreateUserRequest createMgr = new CreateUserRequest("Hashed Manager", "hashed.manager@company.com", rawPassword, Role.MANAGER);

        mockMvc.perform(post("/api/users/manager")
                        .cookie(adminCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createMgr)))
                .andExpect(status().isOk());

        User user = userRepository.findByEmail("hashed.manager@company.com").orElse(null);
        assertNotNull(user);
        assertNotEquals(rawPassword, user.getPassword());
        assertTrue(user.getPassword().startsWith("$2a$") || user.getPassword().startsWith("$2b$") || passwordEncoder.matches(rawPassword, user.getPassword()));
        assertTrue(passwordEncoder.matches(rawPassword, user.getPassword()));
    }

    @Test
    @DisplayName("12. Existing Milestone 5 functionality still works → PASS: Manager projects & dashboard metrics work seamlessly")
    void test12_Milestone5FunctionalityWorks() throws Exception {
        User manager = createUser("Sarah Manager", "sarah.manager@company.com", "ManagerPass123!", Role.MANAGER);
        Cookie managerCookie = createAuthCookie(manager);

        createProject("AI Engine Project", "Core AI Project", manager, null);

        mockMvc.perform(get("/api/projects")
                        .cookie(managerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("AI Engine Project"));

        mockMvc.perform(get("/api/dashboard/manager")
                        .cookie(managerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProjects").value(1));
    }
}
