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
import com.Workmanagement.task.entity.TaskRequirement;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class EmployeeSubmissionAiEvaluationIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Milestone 6 Step 3: Complete Workflow - Employee submits, Manager triggers AI evaluation, employee views evaluation scorecard")
    void testCompleteSubmissionAndAiEvaluationWorkflow() throws Exception {
        User manager = createUser("Project Manager", "pm@step3.com", "pass123", Role.MANAGER);
        User employee = createUser("Dev Employee", "dev@step3.com", "pass123", Role.EMPLOYEE);

        Project project = createProject("Portal App", "Portal description", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Build JWT Filter", "Implement JWT auth filter", project, employee, TaskStatus.IN_PROGRESS);

        // Add 2 weighted requirements totaling 100
        TaskRequirement req1 = createRequirement(task, "Validate Bearer Token", 60, true);
        TaskRequirement req2 = createRequirement(task, "Handle Expired Tokens", 40, true);

        // Step 1: Employee submits work
        String submissionJson = """
            {
                "report": "Completed JWT token validation with expired token handling and unit tests.",
                "githubUrl": "https://github.com/company/repo/pull/42",
                "evidenceUrl": "https://ci.company.com/build/105"
            }
        """;

        mockMvc.perform(post("/api/submissions")
                        .param("taskId", task.getId().toString())
                        .cookie(createAuthCookie(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submissionJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.report").value("Completed JWT token validation with expired token handling and unit tests."))
                .andReturn();

        Submission submission = submissionRepository.findAll().stream()
                .filter(s -> s.getTask().getId().equals(task.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(SubmissionStatus.SUBMITTED, submission.getStatus());

        // Step 2: Prepare Mock AI Result
        AiEvaluationResult mockResult = new AiEvaluationResult();
        mockResult.setCompletionPercentage(100.0);
        mockResult.setQualityScore(94.0);
        mockResult.setConfidenceScore(96.0);
        mockResult.setFeedback("Superb code quality, full requirement coverage and clear documentation.");

        RequirementEvaluation rEval1 = new RequirementEvaluation();
        rEval1.setRequirementId(req1.getId());
        rEval1.setRequirement(req1.getDescription());
        rEval1.setWeight(60);
        rEval1.setStatus("COMPLETED");
        rEval1.setExplanation("Bearer header correctly extracted and verified.");

        RequirementEvaluation rEval2 = new RequirementEvaluation();
        rEval2.setRequirementId(req2.getId());
        rEval2.setRequirement(req2.getDescription());
        rEval2.setWeight(40);
        rEval2.setStatus("COMPLETED");
        rEval2.setExplanation("ExpiredJwtException cleanly caught and returned 401.");

        mockResult.setRequirements(List.of(rEval1, rEval2));

        when(aiPromptService.evaluationResult(anyString(), anyString(), anyString())).thenReturn(mockResult);

        // Step 3: Trigger AI evaluation
        mockMvc.perform(post("/api/ai/evaluations/evaluate/" + submission.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completionPercentage").value(100.0))
                .andExpect(jsonPath("$.qualityScore").value(94.0))
                .andExpect(jsonPath("$.confidenceScore").value(96.0))
                .andExpect(jsonPath("$.feedback").value("Superb code quality, full requirement coverage and clear documentation."));

        // Verify DB updates
        Submission updatedSubmission = submissionRepository.findById(submission.getId()).orElseThrow();
        assertEquals(SubmissionStatus.UNDER_REVIEW, updatedSubmission.getStatus());

        Task updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.UNDER_REVIEW, updatedTask.getStatus());

        AiEvaluation persistedEvaluation = aiEvaluationRepository.findBySubmissionId(submission.getId()).orElseThrow();
        assertEquals(100.0, persistedEvaluation.getCompletionPercentage());
        assertEquals(94.0, persistedEvaluation.getQualityScore());
        assertEquals(96.0, persistedEvaluation.getConfidenceScore());
        assertEquals(AiEvaluationStatus.COMPLETED, persistedEvaluation.getStatus());

        // Step 4: Employee retrieves evaluation for their submission
        mockMvc.perform(get("/api/ai/evaluations/submission/" + submission.getId())
                        .cookie(createAuthCookie(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(persistedEvaluation.getId()))
                .andExpect(jsonPath("$.completionPercentage").value(100.0))
                .andExpect(jsonPath("$.qualityScore").value(94.0))
                .andExpect(jsonPath("$.feedback").value("Superb code quality, full requirement coverage and clear documentation."));

        // Step 5: Employee retrieves evaluation by ID
        mockMvc.perform(get("/api/ai/evaluations/" + persistedEvaluation.getId())
                        .cookie(createAuthCookie(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(persistedEvaluation.getId()))
                .andExpect(jsonPath("$.completionPercentage").value(100.0));
    }

    @Test
    @DisplayName("Milestone 6 Step 3: Evaluation with Partial & Missing Requirements notes issues")
    void testAiEvaluationWithPartialAndMissingRequirements() throws Exception {
        User manager = createUser("Manager 2", "mgr2@step3.com", "pass123", Role.MANAGER);
        User employee = createUser("Dev 2", "dev2@step3.com", "pass123", Role.EMPLOYEE);

        Project project = createProject("Billing App", "Billing desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Stripe Webhook", "Handle Stripe webhook events", project, employee, TaskStatus.IN_PROGRESS);
        Submission submission = createSubmission(task, employee, "Only payment_intent.succeeded handled.", null, SubmissionStatus.SUBMITTED);

        AiEvaluationResult mockResult = new AiEvaluationResult();
        mockResult.setCompletionPercentage(50.0);
        mockResult.setQualityScore(70.0);
        mockResult.setConfidenceScore(90.0);
        mockResult.setFeedback("Payment success handled, but refund and failure webhooks are missing.");

        RequirementEvaluation r1 = new RequirementEvaluation();
        r1.setRequirementId(1L);
        r1.setRequirement("Payment Succeeded Webhook");
        r1.setWeight(50);
        r1.setStatus("COMPLETED");
        r1.setExplanation("Implemented correctly");

        RequirementEvaluation r2 = new RequirementEvaluation();
        r2.setRequirementId(2L);
        r2.setRequirement("Refund & Dispute Webhooks");
        r2.setWeight(50);
        r2.setStatus("MISSING");
        r2.setExplanation("No handler found in submission report.");

        mockResult.setRequirements(List.of(r1, r2));

        when(aiPromptService.evaluationResult(anyString(), anyString(), anyString())).thenReturn(mockResult);

        mockMvc.perform(post("/api/ai/evaluations/evaluate/" + submission.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completionPercentage").value(50.0))
                .andExpect(jsonPath("$.missingRequirements").value("Refund & Dispute Webhooks"));

        AiEvaluation saved = aiEvaluationRepository.findBySubmissionId(submission.getId()).orElseThrow();
        assertEquals("Refund & Dispute Webhooks", saved.getMissingRequirements());
    }

    @Test
    @DisplayName("Milestone 6 Step 3: Security - Unauthorized employee cannot view or evaluate other employee's submission")
    void testUnauthorizedEmployeeCannotAccessOrEvaluate() throws Exception {
        User manager = createUser("Manager 3", "mgr3@step3.com", "pass123", Role.MANAGER);
        User employeeA = createUser("Employee A", "empa@step3.com", "pass123", Role.EMPLOYEE);
        User employeeB = createUser("Employee B", "empb@step3.com", "pass123", Role.EMPLOYEE);

        Project project = createProject("Core Backend", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task A", "Task desc", project, employeeA, TaskStatus.IN_PROGRESS);
        Submission submission = createSubmission(task, employeeA, "Report by A", null, SubmissionStatus.SUBMITTED);
        AiEvaluation eval = createAiEvaluation(submission, 85.0, 90.0, AiEvaluationStatus.COMPLETED);

        // Employee B attempts to get Employee A's evaluation via submissionId -> 403
        mockMvc.perform(get("/api/ai/evaluations/submission/" + submission.getId())
                        .cookie(createAuthCookie(employeeB)))
                .andExpect(status().isForbidden());

        // Employee B attempts to get Employee A's evaluation via evalId -> 403
        mockMvc.perform(get("/api/ai/evaluations/" + eval.getId())
                        .cookie(createAuthCookie(employeeB)))
                .andExpect(status().isForbidden());

        // Employee B attempts to trigger evaluation on Employee A's submission -> 403
        mockMvc.perform(post("/api/ai/evaluations/evaluate/" + submission.getId())
                        .cookie(createAuthCookie(employeeB)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Milestone 6 Step 3: Failure Handling - AI error does not corrupt or delete submission")
    void testAiEvaluationFailurePreservesSubmission() throws Exception {
        User manager = createUser("Manager 4", "mgr4@step3.com", "pass123", Role.MANAGER);
        User employee = createUser("Dev 4", "dev4@step3.com", "pass123", Role.EMPLOYEE);

        Project project = createProject("Security Audit", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Security Scan", "Scan dependencies", project, employee, TaskStatus.IN_PROGRESS);
        Submission submission = createSubmission(task, employee, "Vulnerability audit report attached.", null, SubmissionStatus.SUBMITTED);

        when(aiPromptService.evaluationResult(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Gemini API connection timeout"));

        mockMvc.perform(post("/api/ai/evaluations/evaluate/" + submission.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("INTERNAL_SERVER_ERROR"));

        // Submission remains intact with SUBMITTED status and was not corrupted
        Submission intactSubmission = submissionRepository.findById(submission.getId()).orElseThrow();
        assertEquals(SubmissionStatus.SUBMITTED, intactSubmission.getStatus());
        assertEquals("Vulnerability audit report attached.", intactSubmission.getReport());
        assertEquals(0, aiEvaluationRepository.count());
    }

    @Test
    @DisplayName("Milestone 6 Step 3: Duplicate evaluation on SUBMITTED submission updates existing record safely")
    void testDuplicateEvaluationUpdatesSafely() throws Exception {
        User manager = createUser("Manager 5", "mgr5@step3.com", "pass123", Role.MANAGER);
        User employee = createUser("Dev 5", "dev5@step3.com", "pass123", Role.EMPLOYEE);

        Project project = createProject("Refactor Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Refactor Service", "Refactor logic", project, employee, TaskStatus.IN_PROGRESS);
        Submission submission = createSubmission(task, employee, "Initial report", null, SubmissionStatus.SUBMITTED);

        // First evaluation
        AiEvaluationResult result1 = new AiEvaluationResult();
        result1.setCompletionPercentage(70.0);
        result1.setQualityScore(75.0);
        result1.setConfidenceScore(80.0);
        result1.setFeedback("Initial feedback");
        RequirementEvaluation r1 = new RequirementEvaluation();
        r1.setRequirementId(1L);
        r1.setRequirement("Refactor Req");
        r1.setWeight(100);
        r1.setStatus("PARTIAL");
        r1.setExplanation("Incomplete");
        result1.setRequirements(List.of(r1));

        when(aiPromptService.evaluationResult(anyString(), anyString(), anyString())).thenReturn(result1);

        mockMvc.perform(post("/api/ai/evaluations/evaluate/" + submission.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionPercentage").value(70.0));

        assertEquals(1, aiEvaluationRepository.count());

        // Submission is resubmitted / reset to SUBMITTED for re-evaluation
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submissionRepository.save(submission);

        // Second evaluation with updated result
        AiEvaluationResult result2 = new AiEvaluationResult();
        result2.setCompletionPercentage(100.0);
        result2.setQualityScore(95.0);
        result2.setConfidenceScore(98.0);
        result2.setFeedback("Updated feedback");
        RequirementEvaluation r2 = new RequirementEvaluation();
        r2.setRequirementId(1L);
        r2.setRequirement("Refactor Req");
        r2.setWeight(100);
        r2.setStatus("COMPLETED");
        r2.setExplanation("Complete");
        result2.setRequirements(List.of(r2));

        when(aiPromptService.evaluationResult(anyString(), anyString(), anyString())).thenReturn(result2);

        mockMvc.perform(post("/api/ai/evaluations/evaluate/" + submission.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionPercentage").value(100.0))
                .andExpect(jsonPath("$.feedback").value("Updated feedback"));

        // Ensure no duplicate entity was created, existing record was updated
        assertEquals(1, aiEvaluationRepository.count());
        AiEvaluation updated = aiEvaluationRepository.findBySubmissionId(submission.getId()).orElseThrow();
        assertEquals(100.0, updated.getCompletionPercentage());
        assertEquals("Updated feedback", updated.getFeedback());
    }
}
