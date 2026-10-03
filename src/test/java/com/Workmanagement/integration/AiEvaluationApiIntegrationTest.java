package com.Workmanagement.integration;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.entity.AiEvaluationStatus;
import com.Workmanagement.ai.model.AiEvaluationResult;
import com.Workmanagement.ai.model.RequirementEvaluation;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class AiEvaluationApiIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("POST /api/ai/evaluations/evaluate/{submissionId} - Automated evaluation creates evaluation and transitions submission to UNDER_REVIEW")
    void testAutomatedAiEvaluationSuccess() throws Exception {
        User manager = createUser("Manager", "mgr@eval.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@eval.com", "pass", Role.EMPLOYEE);
        Project project = createProject("AI System", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task", "Task Desc", project, emp, TaskStatus.IN_PROGRESS);
        Submission submission = createSubmission(task, emp, "Employee report", "link", SubmissionStatus.SUBMITTED);

        // Mock external Gemini service boundary
        AiEvaluationResult mockResult = new AiEvaluationResult();
        mockResult.setCompletionPercentage(100.0);
        mockResult.setQualityScore(92.0);
        mockResult.setConfidenceScore(95.0);
        mockResult.setFeedback("Excellent work meeting all requirements.");
        RequirementEvaluation reqEval = new RequirementEvaluation();
        reqEval.setRequirementId(1L);
        reqEval.setRequirement("Auth Requirement");
        reqEval.setWeight(100);
        reqEval.setStatus("COMPLETED");
        reqEval.setExplanation("Fully satisfied");
        mockResult.setRequirements(List.of(reqEval));

        when(aiPromptService.evaluationResult(anyString(), anyString(), anyString())).thenReturn(mockResult);

        mockMvc.perform(post("/api/ai/evaluations/evaluate/" + submission.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.qualityScore").value(92.0))
                .andExpect(jsonPath("$.feedback").value("Excellent work meeting all requirements."));

        // Verify DB submission was transitioned to UNDER_REVIEW
        Submission updatedSubmission = submissionRepository.findById(submission.getId()).orElse(null);
        assertNotNull(updatedSubmission);
        assertEquals(SubmissionStatus.UNDER_REVIEW, updatedSubmission.getStatus());
        assertEquals(1, aiEvaluationRepository.count());
    }

    @Test
    @DisplayName("POST /api/ai/evaluations/evaluate/{submissionId} - Non-SUBMITTED submission returns 400 BAD_REQUEST")
    void testEvaluateNonSubmittedSubmissionFails() throws Exception {
        User manager = createUser("Manager", "mgr@eval.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@eval.com", "pass", Role.EMPLOYEE);
        Project project = createProject("AI System", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task", "Desc", project, emp, TaskStatus.IN_PROGRESS);
        Submission submission = createSubmission(task, emp, "Report", "link", SubmissionStatus.APPROVED);

        mockMvc.perform(post("/api/ai/evaluations/evaluate/" + submission.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Only submitted submissions can be evaluated"));
    }

    @Test
    @DisplayName("POST /api/ai/evaluations - Manual evaluation creation by managing manager")
    void testCreateManualEvaluation() throws Exception {
        User manager = createUser("Manager", "mgr@eval.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@eval.com", "pass", Role.EMPLOYEE);
        Project project = createProject("Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task", "Desc", project, emp, TaskStatus.IN_PROGRESS);
        Submission submission = createSubmission(task, emp, "Report", "link", SubmissionStatus.SUBMITTED);

        AiEvaluation payload = new AiEvaluation();
        payload.setCompletionPercentage(85.0);
        payload.setQualityScore(88.0);
        payload.setConfidenceScore(90.0);
        payload.setFeedback("Manual evaluation feedback");
        payload.setStatus(AiEvaluationStatus.COMPLETED);

        mockMvc.perform(post("/api/ai/evaluations")
                        .param("submissionId", submission.getId().toString())
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.completionPercentage").value(85.0))
                .andExpect(jsonPath("$.feedback").value("Manual evaluation feedback"));

        assertEquals(1, aiEvaluationRepository.count());
    }

    @Test
    @DisplayName("GET /api/ai/evaluations/status/{status} - Manager receives evaluations scoped to their projects")
    void testGetEvaluationsByStatusIsolation() throws Exception {
        User managerA = createUser("Manager A", "mgra@eval.com", "pass", Role.MANAGER);
        User managerB = createUser("Manager B", "mgrb@eval.com", "pass", Role.MANAGER);
        User emp = createUser("Emp", "emp@eval.com", "pass", Role.EMPLOYEE);

        Project projectA = createProject("Project A", "Desc", managerA, ProjectStatus.IN_PROGRESS);
        Task taskA = createTask("Task A", "Desc", projectA, emp, TaskStatus.IN_PROGRESS);
        Submission subA = createSubmission(taskA, emp, "Report A", "link", SubmissionStatus.UNDER_REVIEW);
        createAiEvaluation(subA, 90.0, 90.0, AiEvaluationStatus.COMPLETED);

        Project projectB = createProject("Project B", "Desc", managerB, ProjectStatus.IN_PROGRESS);
        Task taskB = createTask("Task B", "Desc", projectB, emp, TaskStatus.IN_PROGRESS);
        Submission subB = createSubmission(taskB, emp, "Report B", "link", SubmissionStatus.UNDER_REVIEW);
        createAiEvaluation(subB, 80.0, 80.0, AiEvaluationStatus.COMPLETED);

        mockMvc.perform(get("/api/ai/evaluations/status/COMPLETED").cookie(createAuthCookie(managerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].completionPercentage").value(90.0));
    }
}
