package com.Workmanagement.integration;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.entity.AiEvaluationStatus;
import com.Workmanagement.ai.repository.AiEvaluationRepository;
import com.Workmanagement.audit.entity.AuditAction;
import com.Workmanagement.audit.entity.AuditLog;
import com.Workmanagement.audit.repository.AuditLogRepository;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.submission.dto.ReviewSubmissionRequest;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskRequirement;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Milestone 6 Step 6: End-to-End Task Lifecycle Verification & Edge Case Hardening Integration Test.
 * Validates the complete lifecycle across Project Creation, Task Setup, Multi-Iteration Resubmissions,
 * AI Evaluations, Manager Reviews, Final Sign-off, Audit Log Integrity, and Authorization Edge Cases.
 */
public class Milestone6Step6EndToEndHardeningIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AiEvaluationRepository aiEvaluationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("Complete E2E Lifecycle: Project -> Task -> v1 -> AI -> Changes -> v2 -> AI -> Changes -> v3 -> AI -> Approve -> Audit")
    void testCompleteThreeIterationLifecycleWithAudit() throws Exception {
        // =========================================================================
        // Phase 1: User & Project Setup
        // =========================================================================
        User admin = createUser("Admin User", "admin_e2e@portal.com", "pass123", Role.ADMIN);
        User manager = createUser("Lead Manager", "mgr_e2e@portal.com", "pass123", Role.MANAGER);
        User dev = createUser("Senior Dev", "dev_e2e@portal.com", "pass123", Role.EMPLOYEE);

        Project project = createProject("Cloud Infrastructure Gateway", "Microservices gateway project", manager, ProjectStatus.IN_PROGRESS);
        assertNotNull(project.getId());

        // =========================================================================
        // Phase 2: Task Creation with Acceptance Criteria & Weights
        // =========================================================================
        Task task = createTask("Implement Rate Limiting & JWT Auth Middleware", "Build token bucket rate limiter and secure authentication filters.", project, dev, TaskStatus.ASSIGNED);
        assertNotNull(task.getId());

        // Add 2 acceptance criteria requirements
        TaskRequirement req1 = new TaskRequirement();
        req1.setDescription("Token Bucket Rate Limiter supporting 100 req/min per API key");
        req1.setWeight(60);
        req1.setMandatory(true);

        mockMvc.perform(post("/api/tasks/" + task.getId() + "/requirements")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.weight").value(60));

        TaskRequirement req2 = new TaskRequirement();
        req2.setDescription("Unit test suite with at least 90% code coverage on filter chain");
        req2.setWeight(40);
        req2.setMandatory(true);

        mockMvc.perform(post("/api/tasks/" + task.getId() + "/requirements")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weight").value(40));

        // =========================================================================
        // Phase 3 & 4: Employee Submits Initial Work (Version 1)
        // =========================================================================
        Submission v1Body = new Submission();
        v1Body.setReport("Implemented in-memory rate limiter filter.");
        v1Body.setGithubUrl("https://github.com/org/gateway/pull/1");

        String v1Response = mockMvc.perform(post("/api/submissions")
                        .param("taskId", task.getId().toString())
                        .cookie(createAuthCookie(dev))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(v1Body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn().getResponse().getContentAsString();

        Long v1Id = objectMapper.readTree(v1Response).get("id").asLong();
        Submission v1 = submissionRepository.findById(v1Id).orElseThrow();

        // =========================================================================
        // Phase 5: AI Evaluation for Version 1
        // =========================================================================
        AiEvaluation eval1 = new AiEvaluation();
        eval1.setSubmission(v1);
        eval1.setCompletionPercentage(55.0);
        eval1.setQualityScore(50.0);
        eval1.setConfidenceScore(0.80);
        eval1.setFeedback("Rate limiter implemented, but unit tests and Redis distributed support are missing.");
        eval1.setMissingRequirements("Unit test coverage requirement (40 pts)");
        eval1.setStatus(AiEvaluationStatus.COMPLETED);
        eval1.setEvaluatedAt(java.time.LocalDateTime.now());
        aiEvaluationRepository.save(eval1);

        v1.setStatus(SubmissionStatus.UNDER_REVIEW);
        submissionRepository.save(v1);

        // =========================================================================
        // Phase 6: Manager Reviews Version 1 -> REQUEST CHANGES
        // =========================================================================
        ReviewSubmissionRequest reqChanges1 = new ReviewSubmissionRequest();
        reqChanges1.setFeedback("Please write comprehensive JUnit tests for the rate limiter filter.");

        mockMvc.perform(post("/api/submissions/" + v1Id + "/request-changes")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqChanges1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.managerFeedback").value("Please write comprehensive JUnit tests for the rate limiter filter."));

        // =========================================================================
        // Phase 7: Employee Resubmits -> Version 2 Created
        // =========================================================================
        Submission v2Body = new Submission();
        v2Body.setReport("Added unit tests covering token replenishment and 429 Too Many Requests response.");
        v2Body.setGithubUrl("https://github.com/org/gateway/pull/1_v2");
        v2Body.setEvidenceUrl("https://ci.gateway.io/builds/102");

        String v2Response = mockMvc.perform(post("/api/submissions/" + v1Id + "/resubmit")
                        .cookie(createAuthCookie(dev))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(v2Body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.previousSubmission.id").value(v1Id))
                .andReturn().getResponse().getContentAsString();

        Long v2Id = objectMapper.readTree(v2Response).get("id").asLong();
        Submission v2 = submissionRepository.findById(v2Id).orElseThrow();

        // =========================================================================
        // Phase 8: AI Evaluation for Version 2
        // =========================================================================
        AiEvaluation eval2 = new AiEvaluation();
        eval2.setSubmission(v2);
        eval2.setCompletionPercentage(85.0);
        eval2.setQualityScore(80.0);
        eval2.setConfidenceScore(0.90);
        eval2.setFeedback("Unit tests added (82% coverage), but edge case for burst traffic needs verification.");
        eval2.setPartialRequirements("Unit test suite at 82% coverage (target 90%)");
        eval2.setStatus(AiEvaluationStatus.COMPLETED);
        eval2.setEvaluatedAt(java.time.LocalDateTime.now());
        aiEvaluationRepository.save(eval2);

        v2.setStatus(SubmissionStatus.UNDER_REVIEW);
        submissionRepository.save(v2);

        // =========================================================================
        // Phase 9: Manager Reviews Version 2 -> REQUEST CHANGES
        // =========================================================================
        ReviewSubmissionRequest reqChanges2 = new ReviewSubmissionRequest();
        reqChanges2.setFeedback("Coverage is at 82%, please boost it to >90% by testing concurrency lock contention.");

        mockMvc.perform(post("/api/submissions/" + v2Id + "/request-changes")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqChanges2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"));

        // =========================================================================
        // Phase 10: Employee Resubmits -> Version 3 Created
        // =========================================================================
        Submission v3Body = new Submission();
        v3Body.setReport("Added multi-threaded concurrent burst tests with CountDownLatch. Coverage is now 94%.");
        v3Body.setGithubUrl("https://github.com/org/gateway/pull/1_v3");
        v3Body.setEvidenceUrl("https://ci.gateway.io/builds/105");

        String v3Response = mockMvc.perform(post("/api/submissions/" + v2Id + "/resubmit")
                        .cookie(createAuthCookie(dev))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(v3Body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.previousSubmission.id").value(v2Id))
                .andReturn().getResponse().getContentAsString();

        Long v3Id = objectMapper.readTree(v3Response).get("id").asLong();
        Submission v3 = submissionRepository.findById(v3Id).orElseThrow();

        // =========================================================================
        // Phase 11: AI Evaluation for Version 3
        // =========================================================================
        AiEvaluation eval3 = new AiEvaluation();
        eval3.setSubmission(v3);
        eval3.setCompletionPercentage(100.0);
        eval3.setQualityScore(98.0);
        eval3.setConfidenceScore(0.98);
        eval3.setFeedback("All requirements fully verified. 94% test coverage with concurrent stress test verified.");
        eval3.setStatus(AiEvaluationStatus.COMPLETED);
        eval3.setEvaluatedAt(java.time.LocalDateTime.now());
        aiEvaluationRepository.save(eval3);

        v3.setStatus(SubmissionStatus.UNDER_REVIEW);
        submissionRepository.save(v3);

        // =========================================================================
        // Phase 12: Manager Reviews Version 3 -> FINAL APPROVAL
        // =========================================================================
        ReviewSubmissionRequest approveReq = new ReviewSubmissionRequest();
        approveReq.setFeedback("Outstanding engineering quality and test coverage. Approved for production deploy.");

        mockMvc.perform(post("/api/submissions/" + v3Id + "/approve")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.managerFeedback").value("Outstanding engineering quality and test coverage. Approved for production deploy."));

        // Verify task status transitioned to COMPLETED
        Task finalTask = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.COMPLETED, finalTask.getStatus());

        // =========================================================================
        // Phase 13: History Verification (All 3 Iterations Preserved)
        // =========================================================================
        mockMvc.perform(get("/api/submissions/task/" + task.getId() + "/history")
                        .cookie(createAuthCookie(dev)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].version").value(3))
                .andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[0].qualityScore").value(98.0))
                .andExpect(jsonPath("$[1].version").value(2))
                .andExpect(jsonPath("$[1].status").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$[1].qualityScore").value(80.0))
                .andExpect(jsonPath("$[2].version").value(1))
                .andExpect(jsonPath("$[2].status").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$[2].qualityScore").value(50.0));

        // =========================================================================
        // Phase 14: Post-Approval Lock (Cannot resubmit after approval)
        // =========================================================================
        Submission illegalResubmit = new Submission();
        illegalResubmit.setReport("Attempting resubmit after approval");

        mockMvc.perform(post("/api/submissions/" + v3Id + "/resubmit")
                        .cookie(createAuthCookie(dev))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(illegalResubmit)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        // =========================================================================
        // Phase 15: Audit Log Integrity Verification
        // =========================================================================
        List<AuditLog> logs = auditLogRepository.findAll();
        assertFalse(logs.isEmpty());

        // Verify audit records exist for submissions, changes requested, and approvals
        boolean hasSubmissionCreated = logs.stream().anyMatch(l -> l.getAction() == AuditAction.SUBMISSION_CREATED);
        boolean hasResubmitted = logs.stream().anyMatch(l -> l.getAction() == AuditAction.SUBMISSION_RESUBMITTED);
        boolean hasChangesRequested = logs.stream().anyMatch(l -> l.getAction() == AuditAction.SUBMISSION_CHANGES_REQUESTED);
        boolean hasApproved = logs.stream().anyMatch(l -> l.getAction() == AuditAction.SUBMISSION_APPROVED);

        assertTrue(hasSubmissionCreated, "Expected SUBMISSION_CREATED audit log");
        assertTrue(hasResubmitted, "Expected SUBMISSION_RESUBMITTED audit log");
        assertTrue(hasChangesRequested, "Expected SUBMISSION_CHANGES_REQUESTED audit log");
        assertTrue(hasApproved, "Expected SUBMISSION_APPROVED audit log");

        // Admin can view audit logs
        mockMvc.perform(get("/api/audit-logs").cookie(createAuthCookie(admin)))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // Edge Case Suite: Invalid Status Transitions
    // =========================================================================

    @Test
    @DisplayName("Edge Case: Cannot approve a SUBMITTED submission before AI evaluation")
    void testCannotApproveSubmittedBeforeEvaluation() throws Exception {
        User manager = createUser("Mgr Invalid", "mgr_inv1@portal.com", "pass123", Role.MANAGER);
        User dev = createUser("Dev Invalid", "dev_inv1@portal.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("Inv Proj", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Inv Task", "Desc", project, dev, TaskStatus.SUBMITTED);

        Submission sub = createSubmission(task, dev, "Report", "link", SubmissionStatus.SUBMITTED);

        mockMvc.perform(post("/api/submissions/" + sub.getId() + "/approve")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("Edge Case: Cannot request changes on an already APPROVED submission")
    void testCannotRequestChangesOnApprovedSubmission() throws Exception {
        User manager = createUser("Mgr Approved", "mgr_app1@portal.com", "pass123", Role.MANAGER);
        User dev = createUser("Dev Approved", "dev_app1@portal.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("App Proj", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("App Task", "Desc", project, dev, TaskStatus.COMPLETED);

        Submission sub = createSubmission(task, dev, "Report", "link", SubmissionStatus.APPROVED);

        ReviewSubmissionRequest req = new ReviewSubmissionRequest();
        req.setFeedback("Attempting to reject approved work");

        mockMvc.perform(post("/api/submissions/" + sub.getId() + "/request-changes")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("Edge Case: Cannot resubmit an UNDER_REVIEW submission")
    void testCannotResubmitUnderReviewSubmission() throws Exception {
        User manager = createUser("Mgr UnderReview", "mgr_ur1@portal.com", "pass123", Role.MANAGER);
        User dev = createUser("Dev UnderReview", "dev_ur1@portal.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("UR Proj", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("UR Task", "Desc", project, dev, TaskStatus.UNDER_REVIEW);

        Submission sub = createSubmission(task, dev, "Report", "link", SubmissionStatus.UNDER_REVIEW);

        Submission newBody = new Submission();
        newBody.setReport("Attempting premature resubmission");

        mockMvc.perform(post("/api/submissions/" + sub.getId() + "/resubmit")
                        .cookie(createAuthCookie(dev))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    // =========================================================================
    // Edge Case Suite: IDOR & Cross-User Security Hardening
    // =========================================================================

    @Test
    @DisplayName("Edge Case: Employee B cannot access or resubmit Employee A's private submission (403 Forbidden)")
    void testEmployeeIdorProtection() throws Exception {
        User manager = createUser("Mgr IDOR", "mgr_idor@portal.com", "pass123", Role.MANAGER);
        User devA = createUser("Dev A", "dev_a@portal.com", "pass123", Role.EMPLOYEE);
        User devB = createUser("Dev B", "dev_b@portal.com", "pass123", Role.EMPLOYEE);

        Project project = createProject("IDOR Proj", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task taskA = createTask("Task A", "Desc", project, devA, TaskStatus.CHANGES_REQUESTED);

        Submission subA = createSubmission(taskA, devA, "Dev A Report", "link", SubmissionStatus.CHANGES_REQUESTED);

        // Dev B tries to view Dev A's submission
        mockMvc.perform(get("/api/submissions/" + subA.getId())
                        .cookie(createAuthCookie(devB)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        // Dev B tries to resubmit on Dev A's submission
        Submission attackBody = new Submission();
        attackBody.setReport("Dev B attack payload");

        mockMvc.perform(post("/api/submissions/" + subA.getId() + "/resubmit")
                        .cookie(createAuthCookie(devB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attackBody)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Edge Case: Manager A cannot review or approve Manager B's project submission (403 Forbidden)")
    void testManagerIdorProtection() throws Exception {
        User managerA = createUser("Manager A", "mgr_a@portal.com", "pass123", Role.MANAGER);
        User managerB = createUser("Manager B", "mgr_b@portal.com", "pass123", Role.MANAGER);
        User dev = createUser("Dev Scope", "dev_scope@portal.com", "pass123", Role.EMPLOYEE);

        Project projectB = createProject("Project B", "Desc", managerB, ProjectStatus.IN_PROGRESS);
        Task taskB = createTask("Task B", "Desc", projectB, dev, TaskStatus.UNDER_REVIEW);
        Submission subB = createSubmission(taskB, dev, "Report B", "link", SubmissionStatus.UNDER_REVIEW);

        // Manager A attempts approval
        mockMvc.perform(post("/api/submissions/" + subB.getId() + "/approve")
                        .cookie(createAuthCookie(managerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Edge Case: Employee cannot access global audit logs (403 Forbidden)")
    void testEmployeeCannotAccessAuditLogs() throws Exception {
        User dev = createUser("Dev Audit", "dev_audit@portal.com", "pass123", Role.EMPLOYEE);

        mockMvc.perform(get("/api/audit-logs")
                        .cookie(createAuthCookie(dev)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Edge Case: Manager cannot access global audit logs (403 Forbidden)")
    void testManagerCannotAccessAuditLogs() throws Exception {
        User manager = createUser("Mgr Audit", "mgr_audit@portal.com", "pass123", Role.MANAGER);

        mockMvc.perform(get("/api/audit-logs")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isForbidden());
    }
}
