package com.Workmanagement.integration;

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
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test suite for Milestone 6 Step 4: Manager Review & Approval Workflow.
 * Verifies security, authorization, idempotency, status validation, and full review lifecycle.
 */
public class ManagerReviewApprovalIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Test 1 & 2: Manager logs in and retrieves submission authorized to review")
    void testManagerLoginAndGetReviewableSubmission() throws Exception {
        User manager = createUser("Manager One", "mgr1@review.com", "pass123", Role.MANAGER);
        User emp = createUser("Dev User", "dev@review.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("Portal Project", "Portal Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task Alpha", "Implement login", project, emp, TaskStatus.UNDER_REVIEW);

        Submission submission = createSubmission(task, emp, "Implemented OAuth & JWT auth", "https://github.com/repo/pr/1", SubmissionStatus.UNDER_REVIEW);

        // Manager fetches submission details
        mockMvc.perform(get("/api/submissions/" + submission.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submission.getId()))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.report").value("Implemented OAuth & JWT auth"))
                .andExpect(jsonPath("$.task.id").value(task.getId()));
    }

    @Test
    @DisplayName("Test 3: Manager approves reviewable submission -> 200, status APPROVED, task COMPLETED")
    void testManagerApproveSubmissionSuccess() throws Exception {
        User manager = createUser("Manager Two", "mgr2@review.com", "pass123", Role.MANAGER);
        User emp = createUser("Dev Two", "dev2@review.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("Payment Engine", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Payment Gateway", "Desc", project, emp, TaskStatus.UNDER_REVIEW);

        Submission submission = createSubmission(task, emp, "Stripe integration complete", "https://github.com/repo/pr/2", SubmissionStatus.UNDER_REVIEW);

        ReviewSubmissionRequest request = new ReviewSubmissionRequest();
        request.setFeedback("Excellent work, clean code and unit tests are complete.");

        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/approve")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submission.getId()))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.managerFeedback").value("Excellent work, clean code and unit tests are complete."));

        // Verify DB persistence
        Submission persistedSub = submissionRepository.findById(submission.getId()).orElse(null);
        assertNotNull(persistedSub);
        assertEquals(SubmissionStatus.APPROVED, persistedSub.getStatus());
        assertEquals("Excellent work, clean code and unit tests are complete.", persistedSub.getManagerFeedback());

        Task persistedTask = taskRepository.findById(task.getId()).orElse(null);
        assertNotNull(persistedTask);
        assertEquals(TaskStatus.COMPLETED, persistedTask.getStatus());
    }

    @Test
    @DisplayName("Test 4: Manager requests changes -> 200, status CHANGES_REQUESTED, task CHANGES_REQUESTED, feedback persisted")
    void testManagerRequestChangesSuccess() throws Exception {
        User manager = createUser("Manager Three", "mgr3@review.com", "pass123", Role.MANAGER);
        User emp = createUser("Dev Three", "dev3@review.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("Analytics Module", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Data Aggregation", "Desc", project, emp, TaskStatus.UNDER_REVIEW);

        Submission submission = createSubmission(task, emp, "First draft implementation", "https://github.com/repo/pr/3", SubmissionStatus.UNDER_REVIEW);

        ReviewSubmissionRequest request = new ReviewSubmissionRequest();
        request.setFeedback("Please address the missing requirement for CSV exports and add edge case tests.");

        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/request-changes")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(submission.getId()))
                .andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.managerFeedback").value("Please address the missing requirement for CSV exports and add edge case tests."));

        // Verify DB persistence
        Submission persistedSub = submissionRepository.findById(submission.getId()).orElse(null);
        assertNotNull(persistedSub);
        assertEquals(SubmissionStatus.CHANGES_REQUESTED, persistedSub.getStatus());
        assertEquals("Please address the missing requirement for CSV exports and add edge case tests.", persistedSub.getManagerFeedback());

        Task persistedTask = taskRepository.findById(task.getId()).orElse(null);
        assertNotNull(persistedTask);
        assertEquals(TaskStatus.CHANGES_REQUESTED, persistedTask.getStatus());
    }

    @Test
    @DisplayName("Test 5: EMPLOYEE attempts approval endpoint -> 403 Forbidden")
    void testEmployeeCannotApproveSubmission() throws Exception {
        User manager = createUser("Manager Four", "mgr4@review.com", "pass123", Role.MANAGER);
        User emp = createUser("Dev Four", "dev4@review.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("Security Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Audit Task", "Desc", project, emp, TaskStatus.UNDER_REVIEW);

        Submission submission = createSubmission(task, emp, "Draft report", "link", SubmissionStatus.UNDER_REVIEW);

        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/approve")
                        .cookie(createAuthCookie(emp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Test 6: EMPLOYEE attempts request-changes endpoint -> 403 Forbidden")
    void testEmployeeCannotRequestChanges() throws Exception {
        User manager = createUser("Manager Five", "mgr5@review.com", "pass123", Role.MANAGER);
        User emp = createUser("Dev Five", "dev5@review.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("Infra Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Deploy Task", "Desc", project, emp, TaskStatus.UNDER_REVIEW);

        Submission submission = createSubmission(task, emp, "Deploy report", "link", SubmissionStatus.UNDER_REVIEW);

        ReviewSubmissionRequest request = new ReviewSubmissionRequest();
        request.setFeedback("Attempting unauthorized rejection");

        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/request-changes")
                        .cookie(createAuthCookie(emp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Test 7: Manager attempts to review submission outside their assigned project scope -> 403 Forbidden")
    void testManagerScopeIsolation() throws Exception {
        User managerA = createUser("Manager Alpha", "mgra@scope.com", "pass123", Role.MANAGER);
        User managerB = createUser("Manager Beta", "mgrb@scope.com", "pass123", Role.MANAGER);
        User emp = createUser("Dev Scope", "devscope@review.com", "pass123", Role.EMPLOYEE);

        Project projectB = createProject("Beta Project", "Desc", managerB, ProjectStatus.IN_PROGRESS);
        Task taskB = createTask("Beta Task", "Desc", projectB, emp, TaskStatus.UNDER_REVIEW);
        Submission submissionB = createSubmission(taskB, emp, "Beta report", "link", SubmissionStatus.UNDER_REVIEW);

        // Manager A attempts to approve Project B's submission
        mockMvc.perform(post("/api/submissions/" + submissionB.getId() + "/approve")
                        .cookie(createAuthCookie(managerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        // Manager A attempts to request changes on Project B's submission
        ReviewSubmissionRequest request = new ReviewSubmissionRequest();
        request.setFeedback("Unauthorized changes requested");

        mockMvc.perform(post("/api/submissions/" + submissionB.getId() + "/request-changes")
                        .cookie(createAuthCookie(managerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Test 8: Manager attempts to approve an already approved submission -> 400 Bad Request")
    void testDuplicateApprovalFails() throws Exception {
        User manager = createUser("Manager Six", "mgr6@review.com", "pass123", Role.MANAGER);
        User emp = createUser("Dev Six", "dev6@review.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("Idempotent Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Idempotent Task", "Desc", project, emp, TaskStatus.COMPLETED);

        Submission submission = createSubmission(task, emp, "Done", "link", SubmissionStatus.APPROVED);

        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/approve")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("Test 9: Manager attempts to approve a non-reviewable submission (e.g., still in AI_EVALUATING) -> 400 Bad Request")
    void testApproveNonReviewableSubmissionFails() throws Exception {
        User manager = createUser("Manager Seven", "mgr7@review.com", "pass123", Role.MANAGER);
        User emp = createUser("Dev Seven", "dev7@review.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("AI Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("AI Evaluation in flight", "Desc", project, emp, TaskStatus.AI_EVALUATING);

        Submission submission = createSubmission(task, emp, "Evaluating work", "link", SubmissionStatus.AI_EVALUATING);

        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/approve")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("Test 10: Blank or missing Manager feedback for request-changes -> 400 Bad Request")
    void testRequestChangesBlankFeedbackFails() throws Exception {
        User manager = createUser("Manager Eight", "mgr8@review.com", "pass123", Role.MANAGER);
        User emp = createUser("Dev Eight", "dev8@review.com", "pass123", Role.EMPLOYEE);
        Project project = createProject("Feedback Validation Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Feedback Task", "Desc", project, emp, TaskStatus.UNDER_REVIEW);

        Submission submission = createSubmission(task, emp, "Report under review", "link", SubmissionStatus.UNDER_REVIEW);

        // Blank string feedback
        ReviewSubmissionRequest blankRequest = new ReviewSubmissionRequest();
        blankRequest.setFeedback("   ");

        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/request-changes")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        // Null feedback
        ReviewSubmissionRequest nullRequest = new ReviewSubmissionRequest();

        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/request-changes")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }
}
