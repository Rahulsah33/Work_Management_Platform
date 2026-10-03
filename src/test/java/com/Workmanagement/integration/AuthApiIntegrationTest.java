package com.Workmanagement.integration;

import com.Workmanagement.auth.dto.LoginRequest;
import com.Workmanagement.auth.dto.RegisterRequest;
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

public class AuthApiIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("POST /api/auth/register - Successfully registers user with encrypted password")
    void testRegisterSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName("John Doe");
        request.setEmail("john.doe@example.com");
        request.setPassword("SecurePass123!");
        request.setRole(Role.EMPLOYEE);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.password").doesNotExist());

        User user = userRepository.findByEmail("john.doe@example.com").orElse(null);
        assertNotNull(user);
        assertEquals("John Doe", user.getName());
        assertEquals(Role.EMPLOYEE, user.getRole());
        assertTrue(passwordEncoder.matches("SecurePass123!", user.getPassword()));
        assertNotEquals("SecurePass123!", user.getPassword());
    }

    @Test
    @DisplayName("POST /api/auth/register - Duplicate email returns 409 CONFLICT")
    void testRegisterDuplicateEmail() throws Exception {
        createUser("Existing User", "existing@example.com", "Password123!", Role.EMPLOYEE);

        RegisterRequest request = new RegisterRequest();
        request.setName("Duplicate User");
        request.setEmail("existing@example.com");
        request.setPassword("NewPassword123!");
        request.setRole(Role.EMPLOYEE);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(containsString("Email is already registered")));
    }

    @Test
    @DisplayName("POST /api/auth/register - Missing required fields returns 400 VALIDATION_ERROR")
    void testRegisterValidationFailure() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName("");
        request.setEmail("invalid-email");
        request.setPassword("123");
        request.setRole(Role.EMPLOYEE);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    @DisplayName("POST /api/auth/login - Valid credentials for MANAGER issue HttpOnly JWT cookie and return MANAGER role")
    void testLoginSuccess() throws Exception {
        User user = createUser("Jane Manager", "jane.manager@example.com", "SecretPass123!", Role.MANAGER);

        LoginRequest request = new LoginRequest();
        request.setEmail("jane.manager@example.com");
        request.setPassword("SecretPass123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("jwt=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.name").value("Jane Manager"))
                .andExpect(jsonPath("$.email").value("jane.manager@example.com"))
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/login - Valid credentials for ADMIN issue HttpOnly JWT cookie and return ADMIN role")
    void testAdminLoginSuccess() throws Exception {
        User user = createUser("Super Admin", "admin.test@example.com", "AdminPass123!", Role.ADMIN);

        LoginRequest request = new LoginRequest();
        request.setEmail("admin.test@example.com");
        request.setPassword("AdminPass123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("jwt=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.name").value("Super Admin"))
                .andExpect(jsonPath("$.email").value("admin.test@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/login - Valid credentials for EMPLOYEE issue HttpOnly JWT cookie and return EMPLOYEE role")
    void testEmployeeLoginSuccess() throws Exception {
        User user = createUser("Regular Employee", "employee.test@example.com", "EmpPass123!", Role.EMPLOYEE);

        LoginRequest request = new LoginRequest();
        request.setEmail("employee.test@example.com");
        request.setPassword("EmpPass123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("jwt=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.name").value("Regular Employee"))
                .andExpect(jsonPath("$.email").value("employee.test@example.com"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/login - Invalid password returns 400 BAD_REQUEST")
    void testLoginInvalidPassword() throws Exception {
        createUser("Jane Manager", "jane.manager@example.com", "SecretPass123!", Role.MANAGER);

        LoginRequest request = new LoginRequest();
        request.setEmail("jane.manager@example.com");
        request.setPassword("WrongPassword!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Non-existent email returns 400 BAD_REQUEST")
    void testLoginNonExistentEmail() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("nonexistent@example.com");
        request.setPassword("SecretPass123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("GET /api/users/me - Authenticated user with JWT cookie receives own profile")
    void testCurrentUserWithCookie() throws Exception {
        User employee = createUser("Alice Emp", "alice@example.com", "Pass123!", Role.EMPLOYEE);
        Cookie authCookie = createAuthCookie(employee);

        mockMvc.perform(get("/api/users/me")
                        .cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(employee.getId()))
                .andExpect(jsonPath("$.name").value("Alice Emp"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/users/me - Authenticated user with Bearer header receives own profile")
    void testCurrentUserWithBearerHeader() throws Exception {
        User admin = createUser("Super Admin", "admin@example.com", "Pass123!", Role.ADMIN);
        String token = jwtService.generateToken(admin.getEmail());

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(admin.getId()))
                .andExpect(jsonPath("$.name").value("Super Admin"))
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("GET /api/users/me - Unauthenticated request is rejected")
    void testCurrentUserUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/auth/logout - Invalidate JWT cookie returns Set-Cookie header with max-age 0")
    void testLogoutSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("jwt=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }
}
