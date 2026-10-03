package com.Workmanagement.integration;

import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskRequirement;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class TaskRequirementApiIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("POST /api/tasks/{taskId}/requirements - Managing manager creates requirement")
    void testCreateRequirementSuccess() throws Exception {
        User manager = createUser("Manager", "mgr@req.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@req.com", "pass", Role.EMPLOYEE);
        Project project = createProject("Security Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Security Audit", "Desc", project, emp, TaskStatus.ASSIGNED);

        TaskRequirement payload = new TaskRequirement();
        payload.setDescription("Must enforce JWT expiration checks");
        payload.setWeight(25);
        payload.setMandatory(true);

        mockMvc.perform(post("/api/tasks/" + task.getId() + "/requirements")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.description").value("Must enforce JWT expiration checks"))
                .andExpect(jsonPath("$.weight").value(25))
                .andExpect(jsonPath("$.mandatory").value(true));

        assertEquals(1, requirementRepository.count());
    }

    @Test
    @DisplayName("POST /api/tasks/{taskId}/requirements - Unrelated manager receives 403 FORBIDDEN")
    void testCreateRequirementUnauthorizedManagerForbidden() throws Exception {
        User managerA = createUser("Manager A", "mgra@req.com", "pass", Role.MANAGER);
        User managerB = createUser("Manager B", "mgrb@req.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@req.com", "pass", Role.EMPLOYEE);

        Project projectB = createProject("Project B", "Desc", managerB, ProjectStatus.IN_PROGRESS);
        Task taskB = createTask("Task B", "Desc", projectB, emp, TaskStatus.ASSIGNED);

        TaskRequirement payload = new TaskRequirement();
        payload.setDescription("Requirement from wrong manager");

        mockMvc.perform(post("/api/tasks/" + taskB.getId() + "/requirements")
                        .cookie(createAuthCookie(managerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/tasks/{taskId}/requirements - Retrieves all requirements for task")
    void testGetRequirementsByTask() throws Exception {
        User manager = createUser("Manager", "mgr@req.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@req.com", "pass", Role.EMPLOYEE);
        Project project = createProject("Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task", "Desc", project, emp, TaskStatus.ASSIGNED);

        createRequirement(task, "Requirement 1", 10, true);
        createRequirement(task, "Requirement 2", 20, false);

        mockMvc.perform(get("/api/tasks/" + task.getId() + "/requirements")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].description").value("Requirement 1"))
                .andExpect(jsonPath("$[1].description").value("Requirement 2"));
    }

    @Test
    @DisplayName("PUT /api/tasks/requirements/{id} - Managing manager updates requirement; Unrelated gets 403")
    void testUpdateRequirement() throws Exception {
        User managerA = createUser("Manager A", "mgra@req.com", "pass", Role.MANAGER);
        User managerB = createUser("Manager B", "mgrb@req.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@req.com", "pass", Role.EMPLOYEE);

        Project projectB = createProject("Project B", "Desc", managerB, ProjectStatus.IN_PROGRESS);
        Task taskB = createTask("Task B", "Desc", projectB, emp, TaskStatus.ASSIGNED);
        TaskRequirement reqB = createRequirement(taskB, "Original Req", 10, true);

        TaskRequirement updatePayload = new TaskRequirement();
        updatePayload.setDescription("Updated Req Content");
        updatePayload.setWeight(30);
        updatePayload.setMandatory(false);

        // Manager A tries to update -> 403
        mockMvc.perform(put("/api/tasks/requirements/" + reqB.getId())
                        .cookie(createAuthCookie(managerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isForbidden());

        // Manager B updates -> 200
        mockMvc.perform(put("/api/tasks/requirements/" + reqB.getId())
                        .cookie(createAuthCookie(managerB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated Req Content"))
                .andExpect(jsonPath("$.weight").value(30))
                .andExpect(jsonPath("$.mandatory").value(false));
    }

    @Test
    @DisplayName("DELETE /api/tasks/requirements/{id} - Managing manager deletes requirement")
    void testDeleteRequirement() throws Exception {
        User manager = createUser("Manager", "mgr@req.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@req.com", "pass", Role.EMPLOYEE);
        Project project = createProject("Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task", "Desc", project, emp, TaskStatus.ASSIGNED);
        TaskRequirement req = createRequirement(task, "To Delete", 10, true);

        mockMvc.perform(delete("/api/tasks/requirements/" + req.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isNoContent());

        assertFalse(requirementRepository.existsById(req.getId()));
    }
}
