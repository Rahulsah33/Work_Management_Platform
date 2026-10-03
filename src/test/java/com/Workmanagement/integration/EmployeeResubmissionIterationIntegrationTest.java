package com.Workmanagement.integration;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.entity.AiEvaluationStatus;
import com.Workmanagement.ai.repository.AiEvaluationRepository;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.submission.dto.ReviewSubmissionRequest;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive Integration Test Suite for Milestone 6 Step 5:
 * Employee Resubmission Workflow & Iteration Tracking.
 */
public class EmployeeResubmissionIterationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AiEvaluationRepository aiEvaluationRepository;

    @Test
    @DisplayName("Complete Resubmission & Iteration Lifecycle (Tests 1 through 9)")
    void testCompleteResubmissionIterationLifecycle() throws Exception {
        User manager = createUser("Iteration Manager", "it_mgr@resub.com", "pass123", Role.MANAGER);
        User emp = createUser("Iteration Dev", "it_dev@resub.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("Payment Processing Service", "Core payment gateway", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Stripe Checkout Integration", "Integrate webhook and refunds", project, emp, TaskStatus.IN_PROGRESS);

        // Test 1: Employee submits first version -> Version 1
        Submission v1Payload = new Submission();
        v1Payload.setReport("Implemented Stripe basic checkout flow");
        v1Payload.setGithubUrl("https://github.com/repo/pr/1");

        String v1ResponseStr = mockMvc.perform(post("/api/submissions")
                        .param("taskId", task.getId().toString())
                        .cookie(createAuthCookie(emp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(v1Payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.report").value("Implemented Stripe basic checkout flow"))
                .andReturn().getResponse().getContentAsString();

        Long v1Id = objectMapper.readTree(v1ResponseStr).get("id").asLong();
        Submission v1Saved = submissionRepository.findById(v1Id).orElseThrow();
        assertNotNull(v1Id);

        // Simulate AI Evaluation on Version 1
        AiEvaluation eval1 = new AiEvaluation();
        eval1.setSubmission(v1Saved);
        eval1.setCompletionPercentage(70.0);
        eval1.setQualityScore(65.0);
        eval1.setConfidenceScore(0.85);
        eval1.setFeedback("Checkout flow works, but webhook verification and refund handler are missing.");
        eval1.setMissingRequirements("Webhook signature verification; Refund endpoint");
        eval1.setStatus(AiEvaluationStatus.COMPLETED);
        eval1.setEvaluatedAt(java.time.LocalDateTime.now());
        aiEvaluationRepository.save(eval1);

        v1Saved.setStatus(SubmissionStatus.UNDER_REVIEW);
        submissionRepository.save(v1Saved);

        // Test 2 & 3: Manager requests changes -> Version 1 status = CHANGES_REQUESTED with feedback
        ReviewSubmissionRequest reviewReq = new ReviewSubmissionRequest();
        reviewReq.setFeedback("Please implement webhook signature verification and add unit tests for refund failures.");

        mockMvc.perform(post("/api/submissions/" + v1Id + "/request-changes")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(v1Id))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.managerFeedback").value("Please implement webhook signature verification and add unit tests for refund failures."));

        // Verify task transitioned to CHANGES_REQUESTED
        Task taskAfterReq = taskRepository.findById(task.getId()).orElse(null);
        assertNotNull(taskAfterReq);
        assertEquals(TaskStatus.CHANGES_REQUESTED, taskAfterReq.getStatus());

        // Test 4 & 5: Employee resubmits -> Version 2 created, status = SUBMITTED, Version 1 remains intact
        Submission v2Payload = new Submission();
        v2Payload.setReport("Implemented Stripe webhook HMAC verification and refund retry logic with unit tests.");
        v2Payload.setGithubUrl("https://github.com/repo/pr/1_v2");
        v2Payload.setEvidenceUrl("https://preview.app/stripe-test");

        String v2ResponseStr = mockMvc.perform(post("/api/submissions/" + v1Id + "/resubmit")
                        .cookie(createAuthCookie(emp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(v2Payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.report").value("Implemented Stripe webhook HMAC verification and refund retry logic with unit tests."))
                .andExpect(jsonPath("$.previousSubmission.id").value(v1Id))
                .andReturn().getResponse().getContentAsString();

        Long v2Id = objectMapper.readTree(v2ResponseStr).get("id").asLong();
        Submission v2Saved = submissionRepository.findById(v2Id).orElseThrow();

        // Verify DB state: Version 1 is still present with CHANGES_REQUESTED and its managerFeedback
        Submission v1InDb = submissionRepository.findById(v1Id).orElse(null);
        assertNotNull(v1InDb);
        assertEquals(1, v1InDb.getVersion());
        assertEquals(SubmissionStatus.CHANGES_REQUESTED, v1InDb.getStatus());
        assertEquals("Please implement webhook signature verification and add unit tests for refund failures.", v1InDb.getManagerFeedback());

        // Verify DB state: Version 2 is saved with version = 2 and status = SUBMITTED
        Submission v2InDb = submissionRepository.findById(v2Id).orElse(null);
        assertNotNull(v2InDb);
        assertEquals(2, v2InDb.getVersion());
        assertEquals(SubmissionStatus.SUBMITTED, v2InDb.getStatus());

        // Test 6 & 7: AI evaluates Version 2 -> evaluation created for v2, status becomes UNDER_REVIEW
        AiEvaluation eval2 = new AiEvaluation();
        eval2.setSubmission(v2Saved);
        eval2.setCompletionPercentage(100.0);
        eval2.setQualityScore(96.0);
        eval2.setConfidenceScore(0.95);
        eval2.setFeedback("All webhook security checks and refund test suites verified.");
        eval2.setStatus(AiEvaluationStatus.COMPLETED);
        eval2.setEvaluatedAt(java.time.LocalDateTime.now());
        aiEvaluationRepository.save(eval2);

        v2Saved.setStatus(SubmissionStatus.UNDER_REVIEW);
        submissionRepository.save(v2Saved);

        // Test 8: Manager reviews Version 2 + gets evaluation 2
        mockMvc.perform(get("/api/submissions/" + v2Id)
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(v2Id))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        mockMvc.perform(get("/api/ai/evaluations/submission/" + v2Id)
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qualityScore").value(96.0))
                .andExpect(jsonPath("$.completionPercentage").value(100.0));

        // Test 9: Manager approves Version 2 -> Version 2 = APPROVED, Task = COMPLETED
        ReviewSubmissionRequest approveReq = new ReviewSubmissionRequest();
        approveReq.setFeedback("Looks great! Perfect implementation of refund edge cases.");

        mockMvc.perform(post("/api/submissions/" + v2Id + "/approve")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(v2Id))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.managerFeedback").value("Looks great! Perfect implementation of refund edge cases."));

        Task taskAfterApproval = taskRepository.findById(task.getId()).orElse(null);
        assertNotNull(taskAfterApproval);
        assertEquals(TaskStatus.COMPLETED, taskAfterApproval.getStatus());

        // Test 10: Employee attempts to resubmit Version 2 after approval -> Rejected (400 Bad Request)
        Submission v3IllegalPayload = new Submission();
        v3IllegalPayload.setReport("Trying to resubmit approved task");

        mockMvc.perform(post("/api/submissions/" + v2Id + "/resubmit")
                        .cookie(createAuthCookie(emp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(v3IllegalPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("Test 11: Another employee cannot resubmit someone else's submission (403 Forbidden)")
    void testCrossEmployeeResubmissionForbidden() throws Exception {
        User manager = createUser("Mgr 11", "mgr11@resub.com", "pass123", Role.MANAGER);
        User emp1 = createUser("Dev 11A", "dev11a@resub.com", "pass123", Role.EMPLOYEE);
        User emp2 = createUser("Dev 11B", "dev11b@resub.com", "pass123", Role.EMPLOYEE);

        Project project = createProject("Project 11", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task 11", "Desc", project, emp1, TaskStatus.CHANGES_REQUESTED);

        Submission sub1 = createSubmission(task, emp1, "Emp1 report", "link", SubmissionStatus.CHANGES_REQUESTED);
        sub1.setVersion(1);
        sub1.setManagerFeedback("Needs fixes");
        submissionRepository.save(sub1);

        Submission attackPayload = new Submission();
        attackPayload.setReport("Attacker submitting work");

        mockMvc.perform(post("/api/submissions/" + sub1.getId() + "/resubmit")
                        .cookie(createAuthCookie(emp2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attackPayload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Test 12: Server ignores / overrides client-supplied status and version fields")
    void testClientSuppliedFieldsOverridden() throws Exception {
        User manager = createUser("Mgr 12", "mgr12@resub.com", "pass123", Role.MANAGER);
        User emp = createUser("Dev 12", "dev12@resub.com", "pass123", Role.EMPLOYEE);

        Project project = createProject("Project 12", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task 12", "Desc", project, emp, TaskStatus.IN_PROGRESS);

        Submission spoofPayload = new Submission();
        spoofPayload.setReport("Spoof payload attempting auto-approval");
        spoofPayload.setStatus(SubmissionStatus.APPROVED);
        spoofPayload.setVersion(99);
        spoofPayload.setManagerFeedback("Faked manager approval");

        mockMvc.perform(post("/api/submissions")
                        .param("taskId", task.getId().toString())
                        .cookie(createAuthCookie(emp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(spoofPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.managerFeedback").isEmpty());
    }

    @Test
    @DisplayName("Test 13: History Endpoint retrieves all versions with evaluation summaries and manager notes")
    void testHistoryEndpointSuccess() throws Exception {
        User manager = createUser("Mgr 13", "mgr13@resub.com", "pass123", Role.MANAGER);
        User emp = createUser("Dev 13", "dev13@resub.com", "pass123", Role.EMPLOYEE);

        Project project = createProject("Project 13", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task 13", "Desc", project, emp, TaskStatus.UNDER_REVIEW);

        Submission sub1 = createSubmission(task, emp, "First draft", "link1", SubmissionStatus.CHANGES_REQUESTED);
        sub1.setVersion(1);
        sub1.setManagerFeedback("Fix lint errors");
        submissionRepository.save(sub1);

        Submission sub2 = createSubmission(task, emp, "Second draft with fixes", "link2", SubmissionStatus.UNDER_REVIEW);
        sub2.setVersion(2);
        sub2.setPreviousSubmission(sub1);
        submissionRepository.save(sub2);

        // Fetch task submission history
        mockMvc.perform(get("/api/submissions/task/" + task.getId() + "/history")
                        .cookie(createAuthCookie(emp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[0].status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$[1].version").value(1))
                .andExpect(jsonPath("$[1].status").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$[1].managerFeedback").value("Fix lint errors"));
    }

    @Test
    @DisplayName("Test 14: Manager cannot access unauthorized employee task history (403 Forbidden)")
    void testManagerHistoryAccessIsolation() throws Exception {
        User managerA = createUser("Manager 14A", "mgra14@resub.com", "pass123", Role.MANAGER);
        User managerB = createUser("Manager 14B", "mgrb14@resub.com", "pass123", Role.MANAGER);
        User emp = createUser("Dev 14", "dev14@resub.com", "pass123", Role.EMPLOYEE);

        Project projectB = createProject("Project 14B", "Desc", managerB, ProjectStatus.IN_PROGRESS);
        Task taskB = createTask("Task 14B", "Desc", projectB, emp, TaskStatus.IN_PROGRESS);
        createSubmission(taskB, emp, "Report B", "link", SubmissionStatus.SUBMITTED);

        mockMvc.perform(get("/api/submissions/task/" + taskB.getId() + "/history")
                        .cookie(createAuthCookie(managerA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }
}
