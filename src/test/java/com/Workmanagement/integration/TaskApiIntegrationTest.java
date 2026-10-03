package com.Workmanagement.integration;

import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class TaskApiIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("POST /api/tasks - Manager creates and assigns task; Sends notification to employee")
    void testCreateTaskSuccess() throws Exception {
        User manager = createUser("Manager M", "mgr@task.com", "pass", Role.MANAGER);
        User employee = createUser("Emp E", "emp@task.com", "pass", Role.EMPLOYEE);
        Project project = createProject("Mobile Redesign", "Desc", manager, ProjectStatus.IN_PROGRESS);

        Task taskPayload = new Task();
        taskPayload.setTitle("Implement Login UI");
        taskPayload.setDescription("Build Flutter login screen");
        taskPayload.setPriority("HIGH");
        taskPayload.setEndDate(LocalDate.now().plusDays(5));

        mockMvc.perform(post("/api/tasks")
                        .param("projectId", project.getId().toString())
                        .param("employeeId", employee.getId().toString())
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Implement Login UI"))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedTo.id").value(employee.getId()))
                .andExpect(jsonPath("$.project.id").value(project.getId()));

        assertEquals(1, taskRepository.count());
        assertEquals(1, notificationRepository.count());
    }

    @Test
    @DisplayName("POST /api/tasks - Manager cannot create task in another manager's project (403 FORBIDDEN)")
    void testCreateTaskInAnotherManagerProjectForbidden() throws Exception {
        User managerA = createUser("Manager A", "mgra@task.com", "pass", Role.MANAGER);
        User managerB = createUser("Manager B", "mgrb@task.com", "pass", Role.MANAGER);
        User employee = createUser("Emp E", "emp@task.com", "pass", Role.EMPLOYEE);

        Project projectB = createProject("Project B", "Desc", managerB, ProjectStatus.PLANNED);

        Task taskPayload = new Task();
        taskPayload.setTitle("Sneaky Task");

        mockMvc.perform(post("/api/tasks")
                        .param("projectId", projectB.getId().toString())
                        .param("employeeId", employee.getId().toString())
                        .cookie(createAuthCookie(managerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(taskPayload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/tasks/{id} - Assigned employee and project manager can view; Unrelated employee gets 403")
    void testGetTaskAccessControl() throws Exception {
        User manager = createUser("Manager", "mgr@task.com", "pass", Role.MANAGER);
        User emp1 = createUser("Emp 1", "emp1@task.com", "pass", Role.EMPLOYEE);
        User emp2 = createUser("Emp 2", "emp2@task.com", "pass", Role.EMPLOYEE);

        Project project = createProject("Web App", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task for Emp 1", "Desc", project, emp1, TaskStatus.ASSIGNED);

        // Assigned Employee -> 200 OK
        mockMvc.perform(get("/api/tasks/" + task.getId()).cookie(createAuthCookie(emp1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task.getId()));

        // Managing Manager -> 200 OK
        mockMvc.perform(get("/api/tasks/" + task.getId()).cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task.getId()));

        // Unrelated Employee -> 403 Forbidden
        mockMvc.perform(get("/api/tasks/" + task.getId()).cookie(createAuthCookie(emp2)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/tasks/me - Authenticated employee gets strictly their assigned tasks")
    void testGetMyTasks() throws Exception {
        User manager = createUser("Manager", "mgr@task.com", "pass", Role.MANAGER);
        User emp1 = createUser("Emp 1", "emp1@task.com", "pass", Role.EMPLOYEE);
        User emp2 = createUser("Emp 2", "emp2@task.com", "pass", Role.EMPLOYEE);

        Project project = createProject("Web App", "Desc", manager, ProjectStatus.IN_PROGRESS);
        createTask("Emp 1 Task 1", "Desc", project, emp1, TaskStatus.ASSIGNED);
        createTask("Emp 1 Task 2", "Desc", project, emp1, TaskStatus.IN_PROGRESS);
        createTask("Emp 2 Task 1", "Desc", project, emp2, TaskStatus.ASSIGNED);

        mockMvc.perform(get("/api/tasks/me").cookie(createAuthCookie(emp1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].assignedTo.id").value(emp1.getId()))
                .andExpect(jsonPath("$[1].assignedTo.id").value(emp1.getId()));
    }

    @Test
    @DisplayName("PATCH /api/tasks/{id}/status - Employee can transition ASSIGNED -> IN_PROGRESS but cannot set COMPLETED or APPROVED")
    void testTaskStatusTransitions() throws Exception {
        User manager = createUser("Manager", "mgr@task.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@task.com", "pass", Role.EMPLOYEE);
        Project project = createProject("Web App", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Build API", "Desc", project, emp, TaskStatus.ASSIGNED);

        Cookie empCookie = createAuthCookie(emp);

        // Employee transitions ASSIGNED -> IN_PROGRESS -> 200
        mockMvc.perform(patch("/api/tasks/" + task.getId() + "/status")
                        .param("status", "IN_PROGRESS")
                        .cookie(empCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Employee attempts to set COMPLETED directly -> 403 Forbidden
        mockMvc.perform(patch("/api/tasks/" + task.getId() + "/status")
                        .param("status", "COMPLETED")
                        .cookie(empCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        // Employee attempts to set APPROVED directly -> 403 Forbidden
        mockMvc.perform(patch("/api/tasks/" + task.getId() + "/status")
                        .param("status", "APPROVED")
                        .cookie(empCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("DELETE /api/tasks/{id} - Project manager can delete task; Unrelated manager receives 403")
    void testDeleteTask() throws Exception {
        User managerA = createUser("Manager A", "mgra@task.com", "pass", Role.MANAGER);
        User managerB = createUser("Manager B", "mgrb@task.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@task.com", "pass", Role.EMPLOYEE);

        Project projectA = createProject("Proj A", "Desc", managerA, ProjectStatus.IN_PROGRESS);
        Task taskA = createTask("Task A", "Desc", projectA, emp, TaskStatus.ASSIGNED);

        // Manager B tries to delete Manager A's task -> 403
        mockMvc.perform(delete("/api/tasks/" + taskA.getId()).cookie(createAuthCookie(managerB)))
                .andExpect(status().isForbidden());

        // Manager A deletes own task -> 204 No Content
        mockMvc.perform(delete("/api/tasks/" + taskA.getId()).cookie(createAuthCookie(managerA)))
                .andExpect(status().isNoContent());

        assertFalse(taskRepository.existsById(taskA.getId()));
    }
}
