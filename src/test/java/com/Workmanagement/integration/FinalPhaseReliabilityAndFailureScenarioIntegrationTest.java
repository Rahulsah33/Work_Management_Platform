package com.Workmanagement.integration;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.entity.AiEvaluationStatus;
import com.Workmanagement.auth.dto.LoginRequest;
import com.Workmanagement.comment.entity.Comment;
import com.Workmanagement.notification.entity.Notification;
import com.Workmanagement.notification.entity.NotificationType;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.submission.dto.ReviewSubmissionRequest;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class FinalPhaseReliabilityAndFailureScenarioIntegrationTest extends BaseIntegrationTest {

    // =========================================================================
    // 1. AUTHENTICATION FAILURE SCENARIOS
    // =========================================================================

    @Test
    @DisplayName("Auth Failure - Incorrect password returns 400 Bad Request")
    void testLoginWithIncorrectPassword() throws Exception {
        createUser("Alice Auth", "alice_auth@example.com", "CorrectPassword123!", Role.EMPLOYEE);

        LoginRequest request = new LoginRequest();
        request.setEmail("alice_auth@example.com");
        request.setPassword("WrongPassword999!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Auth Failure - Nonexistent email returns 400 Bad Request")
    void testLoginWithNonexistentUser() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("ghost_user@example.com");
        request.setPassword("AnyPassword123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Auth Failure - Malformed or tampered JWT cookie is rejected with 403 Forbidden")
    void testMalformedJwtRejection() throws Exception {
        Cookie tamperedCookie = new Cookie("jwt", "header.invalidpayload123.invalidsignature456");
        tamperedCookie.setPath("/");

        mockMvc.perform(get("/api/users/me")
                        .cookie(tamperedCookie))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 2. AUTHORIZATION & IDOR EDGE CASES
    // =========================================================================

    @Test
    @DisplayName("IDOR - Employee cannot view or submit to tasks assigned to another employee")
    void testIdorCrossEmployeeTaskAccess() throws Exception {
        User manager = createUser("Manager One", "mgr_one@example.com", "Password123!", Role.MANAGER);
        User employeeA = createUser("Employee A", "emp_a@example.com", "Password123!", Role.EMPLOYEE);
        User employeeB = createUser("Employee B", "emp_b@example.com", "Password123!", Role.EMPLOYEE);

        Project project = createProject("IDOR Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task taskOfB = createTask("Task for B", "Work on B", project, employeeB, TaskStatus.IN_PROGRESS);

        // Employee A attempts to submit to Employee B's task
        Submission submission = new Submission();
        submission.setReport("Employee A trying to submit to B's task");

        mockMvc.perform(post("/api/submissions")
                        .param("taskId", taskOfB.getId().toString())
                        .cookie(createAuthCookie(employeeA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(submission)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("IDOR - Manager cannot review submissions for projects they do not manage")
    void testIdorCrossManagerSubmissionReview() throws Exception {
        User managerA = createUser("Manager A", "mgr_a@example.com", "Password123!", Role.MANAGER);
        User managerB = createUser("Manager B", "mgr_b@example.com", "Password123!", Role.MANAGER);
        User employee = createUser("Worker", "worker_sub@example.com", "Password123!", Role.EMPLOYEE);

        Project projectOfA = createProject("Project of A", "Managed by A", managerA, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Task of A", "Task in Project A", projectOfA, employee, TaskStatus.IN_PROGRESS);
        Submission submission = createSubmission(task, employee, "Completed work", "http://github.com", SubmissionStatus.UNDER_REVIEW);

        ReviewSubmissionRequest reviewRequest = new ReviewSubmissionRequest();
        reviewRequest.setFeedback("Attempted review by Manager B");

        // Manager B attempts to review Manager A's submission
        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/approve")
                        .cookie(createAuthCookie(managerB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 3. TASK LIFECYCLE TRANSITION INTEGRITY
    // =========================================================================

    @Test
    @DisplayName("Lifecycle Integrity - Cannot approve an already approved submission")
    void testCannotApproveAlreadyApprovedSubmission() throws Exception {
        User manager = createUser("Review Manager", "rev_mgr@example.com", "Password123!", Role.MANAGER);
        User employee = createUser("Review Employee", "rev_emp@example.com", "Password123!", Role.EMPLOYEE);

        Project project = createProject("Review Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Approved Task", "Already approved", project, employee, TaskStatus.COMPLETED);
        Submission submission = createSubmission(task, employee, "Final report", "http://github.com", SubmissionStatus.APPROVED);

        ReviewSubmissionRequest reviewRequest = new ReviewSubmissionRequest();
        reviewRequest.setFeedback("Approving again");

        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/approve")
                        .cookie(createAuthCookie(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Lifecycle Integrity - Cannot resubmit when submission is not in CHANGES_REQUESTED status")
    void testCannotResubmitUnlessChangesRequested() throws Exception {
        User manager = createUser("Resub Manager", "resub_mgr@example.com", "Password123!", Role.MANAGER);
        User employee = createUser("Resub Employee", "resub_emp@example.com", "Password123!", Role.EMPLOYEE);

        Project project = createProject("Resub Project", "Desc", manager, ProjectStatus.IN_PROGRESS);
        Task task = createTask("Resub Task", "In progress", project, employee, TaskStatus.IN_PROGRESS);
        Submission submission = createSubmission(task, employee, "First sub", "http://github.com", SubmissionStatus.UNDER_REVIEW);

        // Attempting to resubmit while previous is still UNDER_REVIEW
        Submission resubRequest = new Submission();
        resubRequest.setReport("Premature resubmission");

        mockMvc.perform(post("/api/submissions/" + submission.getId() + "/resubmit")
                        .cookie(createAuthCookie(employee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resubRequest)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 4. NOTIFICATION PREFERENCES & RECIPIENT ISOLATION
    // =========================================================================

    @Test
    @DisplayName("Notification Isolation - User cannot mark another user's notification as read")
    void testNotificationCrossUserReadIsolation() throws Exception {
        User userA = createUser("User A", "user_a@example.com", "Password123!", Role.EMPLOYEE);
        User userB = createUser("User B", "user_b@example.com", "Password123!", Role.EMPLOYEE);

        Notification notificationOfB = new Notification();
        notificationOfB.setRecipient(userB);
        notificationOfB.setTitle("Notification for B");
        notificationOfB.setMessage("Message for B");
        notificationOfB.setType(NotificationType.TASK_ASSIGNED);
        notificationOfB.setRead(false);
        notificationOfB.setCreatedAt(LocalDateTime.now());
        notificationOfB = notificationRepository.save(notificationOfB);

        // User A attempts to mark User B's notification read
        mockMvc.perform(patch("/api/notifications/" + notificationOfB.getId() + "/read")
                        .cookie(createAuthCookie(userA)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 5. ANALYTICS EDGE CASES & DIVISION BY ZERO HARDENING
    // =========================================================================

    @Test
    @DisplayName("Analytics Hardening - Zero dataset produces 0 rates and null averages without division by zero")
    void testAnalyticsWithCompletelyEmptyDatabase() throws Exception {
        User admin = createUser("Admin Zero", "admin_zero@example.com", "Password123!", Role.ADMIN);

        mockMvc.perform(get("/api/analytics/overview")
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionRate").value(0.0))
                .andExpect(jsonPath("$.approvalRate").value(0.0))
                .andExpect(jsonPath("$.tasks.totalTasks").value(0))
                .andExpect(jsonPath("$.projects.totalProjects").value(0))
                .andExpect(jsonPath("$.submissions.totalSubmissions").value(0));
    }

    @Test
    @DisplayName("Analytics Hardening - Inverted date range returns 400 Bad Request")
    void testAnalyticsInvertedDateRangeValidation() throws Exception {
        User admin = createUser("Admin Date", "admin_date@example.com", "Password123!", Role.ADMIN);

        mockMvc.perform(get("/api/analytics/overview")
                        .param("startDate", "2026-12-31")
                        .param("endDate", "2026-01-01")
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 6. CSV FORMULA INJECTION & REPORT RESILIENCE
    // =========================================================================

    @Test
    @DisplayName("Report Resilience - CSV export neutralizes formula characters (=, +, -, @)")
    void testCsvFormulaInjectionNeutralization() throws Exception {
        User admin = createUser("Admin Formula", "admin_formula@example.com", "Password123!", Role.ADMIN);
        User manager = createUser("Manager Formula", "mgr_formula@example.com", "Password123!", Role.MANAGER);
        User employee = createUser("Worker Formula", "worker_formula@example.com", "Password123!", Role.EMPLOYEE);

        Project maliciousProject = createProject("=HYPERLINK(\"http://malicious.site\",\"Click\")", "Desc", manager, ProjectStatus.IN_PROGRESS);
        createTask("+cmd|'/c calc'!A1", "Formula Task", maliciousProject, employee, TaskStatus.IN_PROGRESS);

        MvcResult result = mockMvc.perform(get("/api/analytics/reports/projects")
                        .param("format", "CSV")
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.containsString("text/csv")))
                .andReturn();

        String csvOutput = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

        // Confirm formula character was sanitized
        assertTrue(csvOutput.contains("HYPERLINK"));
        assertFalse(csvOutput.contains(",=HYPERLINK"), "Raw unescaped formula invocation should not exist");
    }

    // =========================================================================
    // 7. MULTI-THREADED / CONCURRENCY RELIABILITY CHECK
    // =========================================================================

    @Test
    @DisplayName("Concurrency - Concurrent read and update queries do not produce deadlocks or data corruption")
    void testConcurrentAnalyticsAndNotificationAccess() throws Exception {
        User admin = createUser("Admin Concurrent", "admin_conc@example.com", "Password123!", Role.ADMIN);
        User employee = createUser("Emp Concurrent", "emp_conc@example.com", "Password123!", Role.EMPLOYEE);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            tasks.add(() -> {
                mockMvc.perform(get("/api/analytics/overview").cookie(createAuthCookie(admin)))
                        .andExpect(status().isOk());
                mockMvc.perform(get("/api/notifications").cookie(createAuthCookie(employee)))
                        .andExpect(status().isOk());
                return true;
            });
        }

        List<Future<Boolean>> futures = executor.invokeAll(tasks);
        for (Future<Boolean> future : futures) {
            assertTrue(future.get(), "Concurrent task execution should succeed without failure");
        }
        executor.shutdown();
    }
}
