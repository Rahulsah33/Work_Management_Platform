package com.Workmanagement.integration;

import com.Workmanagement.auth.dto.RegisterRequest;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ErrorContractIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Error Contract 400: VALIDATION_ERROR contains standard schema and field errors")
    void test400ValidationErrorContract() throws Exception {
        RegisterRequest invalid = new RegisterRequest();
        invalid.setName("");
        invalid.setEmail("not-an-email");
        invalid.setPassword("123");
        invalid.setRole(Role.EMPLOYEE);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.validationErrors").isMap());
    }

    @Test
    @DisplayName("Error Contract 403: FORBIDDEN contains standard schema and does not leak sensitive information")
    void test403ForbiddenErrorContract() throws Exception {
        User emp = createUser("Emp", "emp@error.com", "pass", Role.EMPLOYEE);

        mockMvc.perform(get("/api/users/admin").cookie(createAuthCookie(emp)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/users/admin"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.message", not(containsString("password"))));
    }

    @Test
    @DisplayName("Error Contract 404: NOT_FOUND contains standard schema and resource path")
    void test404NotFoundErrorContract() throws Exception {
        User admin = createUser("Admin", "admin@error.com", "pass", Role.ADMIN);

        mockMvc.perform(get("/api/projects/999999").cookie(createAuthCookie(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Project not found with id: 999999"))
                .andExpect(jsonPath("$.path").value("/api/projects/999999"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Error Contract 409: CONFLICT contains standard schema on duplicate resource creation")
    void test409ConflictErrorContract() throws Exception {
        createUser("Existing", "dup@error.com", "pass", Role.EMPLOYEE);

        RegisterRequest duplicate = new RegisterRequest();
        duplicate.setName("New User");
        duplicate.setEmail("dup@error.com");
        duplicate.setPassword("Password123!");
        duplicate.setRole(Role.EMPLOYEE);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(containsString("Email is already registered: dup@error.com")))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
