package com.Workmanagement.integration;

import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class DashboardApiIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("GET /api/dashboard/manager - Manager receives only their own project & task metrics")
    void testManagerDashboardIsolation() throws Exception {
        User managerA = createUser("Manager A", "mgra@dash.com", "pass", Role.MANAGER);
        User managerB = createUser("Manager B", "mgrb@dash.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@dash.com", "pass", Role.EMPLOYEE);

        Project projectA = createProject("Project A", "Desc", managerA, ProjectStatus.IN_PROGRESS);
        Task taskA = createTask("Task A", "Desc", projectA, emp, TaskStatus.IN_PROGRESS);
        createSubmission(taskA, emp, "Sub A", "link", SubmissionStatus.SUBMITTED);

        Project projectB = createProject("Project B", "Desc", managerB, ProjectStatus.IN_PROGRESS);
        Task taskB = createTask("Task B", "Desc", projectB, emp, TaskStatus.IN_PROGRESS);
        createSubmission(taskB, emp, "Sub B", "link", SubmissionStatus.SUBMITTED);

        // Manager A dashboard metrics
        mockMvc.perform(get("/api/dashboard/manager").cookie(createAuthCookie(managerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProjects").value(1))
                .andExpect(jsonPath("$.totalTasks").value(1))
                .andExpect(jsonPath("$.totalSubmissions").value(1))
                .andExpect(jsonPath("$.projectSummaries[0].projectName").value("Project A"));
    }

    @Test
    @DisplayName("GET /api/dashboard/manager - Employee is denied access (403 FORBIDDEN)")
    void testManagerDashboardEmployeeForbidden() throws Exception {
        User emp = createUser("Emp", "emp@dash.com", "pass", Role.EMPLOYEE);

        mockMvc.perform(get("/api/dashboard/manager").cookie(createAuthCookie(emp)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/dashboard/employee - Employee receives strictly personal metrics; Cross-employee data isolated")
    void testEmployeeDashboardIsolation() throws Exception {
        User manager = createUser("Manager", "mgr@dash.com", "pass", Role.MANAGER);
        User emp1 = createUser("Emp 1", "emp1@dash.com", "pass", Role.EMPLOYEE);
        User emp2 = createUser("Emp 2", "emp2@dash.com", "pass", Role.EMPLOYEE);

        Project project = createProject("Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task1 = createTask("Task 1", "Desc", project, emp1, TaskStatus.IN_PROGRESS);
        createSubmission(task1, emp1, "Sub 1", "link", SubmissionStatus.SUBMITTED);

        Task task2 = createTask("Task 2", "Desc", project, emp2, TaskStatus.IN_PROGRESS);
        createSubmission(task2, emp2, "Sub 2", "link", SubmissionStatus.SUBMITTED);

        // Employee 1 dashboard
        mockMvc.perform(get("/api/dashboard/employee").cookie(createAuthCookie(emp1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(1))
                .andExpect(jsonPath("$.totalSubmissions").value(1))
                .andExpect(jsonPath("$.taskSummaries[0].taskTitle").value("Task 1"));
    }

    @Test
    @DisplayName("GET /api/dashboard/employee - Manager is denied access (403 FORBIDDEN)")
    void testEmployeeDashboardManagerForbidden() throws Exception {
        User manager = createUser("Manager", "mgr@dash.com", "pass", Role.MANAGER);

        mockMvc.perform(get("/api/dashboard/employee").cookie(createAuthCookie(manager)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }
}
