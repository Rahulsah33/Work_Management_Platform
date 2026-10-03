package com.Workmanagement.integration;

import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class ProjectApiIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("POST /api/projects - Manager creates project assigned to self")
    void testCreateProjectByManager() throws Exception {
        User manager = createUser("Manager One", "mgr1@proj.com", "pass", Role.MANAGER);
        Cookie cookie = createAuthCookie(manager);

        Project payload = new Project();
        payload.setName("Cloud Migration");
        payload.setDescription("Migrate legacy apps to cloud");
        payload.setStatus(ProjectStatus.PLANNED);

        mockMvc.perform(post("/api/projects")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Cloud Migration"))
                .andExpect(jsonPath("$.manager.email").value("mgr1@proj.com"))
                .andExpect(jsonPath("$.status").value("PLANNED"));

        assertEquals(1, projectRepository.count());
    }

    @Test
    @DisplayName("POST /api/projects - Employee cannot create project (403 FORBIDDEN)")
    void testCreateProjectByEmployeeForbidden() throws Exception {
        User employee = createUser("Emp One", "emp1@proj.com", "pass", Role.EMPLOYEE);
        Cookie cookie = createAuthCookie(employee);

        Project payload = new Project();
        payload.setName("Unauthorized Project");

        mockMvc.perform(post("/api/projects")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/projects - Returns all projects for authenticated users")
    void testGetProjectsIsolation() throws Exception {
        User managerA = createUser("Manager A", "mgra@proj.com", "pass", Role.MANAGER);
        User managerB = createUser("Manager B", "mgrb@proj.com", "pass", Role.MANAGER);
        User admin = createUser("Admin", "admin@proj.com", "pass", Role.ADMIN);

        createProject("Project A1", "Desc", managerA, ProjectStatus.IN_PROGRESS);
        createProject("Project A2", "Desc", managerA, ProjectStatus.PLANNED);
        createProject("Project B1", "Desc", managerB, ProjectStatus.IN_PROGRESS);

        // Manager A gets all projects
        mockMvc.perform(get("/api/projects").cookie(createAuthCookie(managerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // Admin gets all 3
        mockMvc.perform(get("/api/projects").cookie(createAuthCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("GET /api/projects/{id} - Successfully retrieves project by ID; Non-existent returns 404")
    void testGetProjectById() throws Exception {
        User manager = createUser("Manager", "mgr@proj.com", "pass", Role.MANAGER);
        Project project = createProject("Mobile App", "Desc", manager, ProjectStatus.IN_PROGRESS);

        mockMvc.perform(get("/api/projects/" + project.getId()).cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(project.getId()))
                .andExpect(jsonPath("$.name").value("Mobile App"));

        // Non-existent project
        mockMvc.perform(get("/api/projects/999999").cookie(createAuthCookie(manager)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT /api/projects/{id} - Manager can update own project; Manager A cannot update Manager B's project (403)")
    void testUpdateProjectOwnership() throws Exception {
        User managerA = createUser("Manager A", "mgra@proj.com", "pass", Role.MANAGER);
        User managerB = createUser("Manager B", "mgrb@proj.com", "pass", Role.MANAGER);

        Project projectB = createProject("Project B", "Original", managerB, ProjectStatus.PLANNED);

        Project updatePayload = new Project();
        updatePayload.setName("Hijacked Project B");
        updatePayload.setDescription("Attempted hack");
        updatePayload.setStatus(ProjectStatus.COMPLETED);

        // Manager A tries to update Manager B's project -> 403
        mockMvc.perform(put("/api/projects/" + projectB.getId())
                        .cookie(createAuthCookie(managerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        // Manager B updates own project -> 200
        mockMvc.perform(put("/api/projects/" + projectB.getId())
                        .cookie(createAuthCookie(managerB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hijacked Project B"));
    }

    @Test
    @DisplayName("DELETE /api/projects/{id} - Manager deletes own project; Manager A cannot delete Manager B's project")
    void testDeleteProjectOwnership() throws Exception {
        User managerA = createUser("Manager A", "mgra@proj.com", "pass", Role.MANAGER);
        User managerB = createUser("Manager B", "mgrb@proj.com", "pass", Role.MANAGER);

        Project projectB = createProject("Project B", "Desc", managerB, ProjectStatus.PLANNED);

        // Manager A tries to delete Project B -> 403
        mockMvc.perform(delete("/api/projects/" + projectB.getId())
                        .cookie(createAuthCookie(managerA)))
                .andExpect(status().isForbidden());

        // Manager B deletes own project -> 200
        mockMvc.perform(delete("/api/projects/" + projectB.getId())
                        .cookie(createAuthCookie(managerB)))
                .andExpect(status().isOk())
                .andExpect(content().string("Project deleted successfully"));

        assertFalse(projectRepository.existsById(projectB.getId()));
    }
}
