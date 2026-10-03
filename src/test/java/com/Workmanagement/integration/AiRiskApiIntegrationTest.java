package com.Workmanagement.integration;

import com.Workmanagement.ai.model.AiRiskResult;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class AiRiskApiIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("GET /api/ai/risk/task/{taskId} - Managing manager retrieves heuristic risk analysis")
    void testHeuristicRiskAnalysisSuccess() throws Exception {
        User manager = createUser("Manager", "mgr@risk.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@risk.com", "pass", Role.EMPLOYEE);
        Project project = createProject("Security Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Data Encryption", "Desc", project, emp, TaskStatus.IN_PROGRESS);

        mockMvc.perform(get("/api/ai/risk/task/" + task.getId()).cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(task.getId()))
                .andExpect(jsonPath("$.taskTitle").value("Data Encryption"))
                .andExpect(jsonPath("$.riskLevel").isNotEmpty())
                .andExpect(jsonPath("$.reason").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/ai/risk/task/{taskId} - Unrelated manager receives 403 FORBIDDEN")
    void testHeuristicRiskUnauthorizedManagerForbidden() throws Exception {
        User managerA = createUser("Manager A", "mgra@risk.com", "pass", Role.MANAGER);
        User managerB = createUser("Manager B", "mgrb@risk.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@risk.com", "pass", Role.EMPLOYEE);

        Project projectB = createProject("Project B", "Desc", managerB, ProjectStatus.IN_PROGRESS);
        Task taskB = createTask("Task B", "Desc", projectB, emp, TaskStatus.IN_PROGRESS);

        mockMvc.perform(get("/api/ai/risk/task/" + taskB.getId()).cookie(createAuthCookie(managerA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/ai/risk/task/{taskId} - Employee is forbidden (403)")
    void testHeuristicRiskEmployeeForbidden() throws Exception {
        User manager = createUser("Manager", "mgr@risk.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@risk.com", "pass", Role.EMPLOYEE);
        Project project = createProject("Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task", "Desc", project, emp, TaskStatus.IN_PROGRESS);

        mockMvc.perform(get("/api/ai/risk/task/" + task.getId()).cookie(createAuthCookie(emp)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/ai/risk/task/{taskId}/ai - Managing manager retrieves AI risk analysis")
    void testAiRiskAnalysisSuccess() throws Exception {
        User manager = createUser("Manager", "mgr@risk.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@risk.com", "pass", Role.EMPLOYEE);
        Project project = createProject("Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task", "Desc", project, emp, TaskStatus.IN_PROGRESS);

        AiRiskResult result = new AiRiskResult();
        result.setRiskLevel("LOW");
        result.setExplanation("On track with ample deadline margin.");
        result.setRecommendation("Continue current development pace.");

        when(aiRiskService.analyzeRisk(anyLong())).thenReturn(result);

        mockMvc.perform(get("/api/ai/risk/task/" + task.getId() + "/ai").cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.explanation").value("On track with ample deadline margin."))
                .andExpect(jsonPath("$.recommendation").value("Continue current development pace."));
    }
}
