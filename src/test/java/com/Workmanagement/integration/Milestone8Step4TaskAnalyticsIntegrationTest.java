package com.Workmanagement.integration;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.entity.AiEvaluationStatus;
import com.Workmanagement.ai.repository.AiEvaluationRepository;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.submission.repository.SubmissionRepository;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class Milestone8Step4TaskAnalyticsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private AiEvaluationRepository aiEvaluationRepository;

    private User createTestUser(String name, String email, Role role) {
        return createUser(name, email, "Password123!", role);
    }

    private Project createTestProject(String name, User manager, LocalDate startDate, LocalDate endDate) {
        Project project = new Project();
        project.setName(name);
        project.setDescription("Project description for " + name);
        project.setManager(manager);
        project.setStatus(ProjectStatus.IN_PROGRESS);
        project.setStartDate(startDate != null ? startDate : LocalDate.now());
        project.setEndDate(endDate != null ? endDate : LocalDate.now().plusMonths(1));
        project.setCreatedAt(LocalDateTime.now());
        return projectRepository.save(project);
    }

    private Task createTestTask(String title, Project project, User assignee, TaskStatus status, LocalDate endDate, LocalDateTime createdAt) {
        Task task = new Task();
        task.setTitle(title);
        task.setDescription("Task description for " + title);
        task.setProject(project);
        task.setAssignedTo(assignee);
        task.setStatus(status);
        task.setPriority("MEDIUM");
        task.setEndDate(endDate);
        task.setCreatedAt(createdAt != null ? createdAt : LocalDateTime.now());
        task.setUpdatedAt(createdAt != null ? createdAt.plusHours(2) : LocalDateTime.now());
        return taskRepository.save(task);
    }

    private Submission createTestSubmission(Task task, User submitter, SubmissionStatus status, int version, LocalDateTime submittedAt) {
        Submission submission = new Submission();
        submission.setTask(task);
        submission.setSubmittedBy(submitter);
        submission.setReport("Submission report for " + task.getTitle());
        submission.setStatus(status);
        submission.setVersion(version);
        submission.setSubmittedAt(submittedAt != null ? submittedAt : LocalDateTime.now());
        return submissionRepository.save(submission);
    }

    private AiEvaluation createTestEvaluation(Submission submission, Double completion, Double quality, Double confidence, LocalDateTime evaluatedAt) {
        AiEvaluation evaluation = new AiEvaluation();
        evaluation.setSubmission(submission);
        evaluation.setCompletionPercentage(completion);
        evaluation.setQualityScore(quality);
        evaluation.setConfidenceScore(confidence);
        evaluation.setStatus(AiEvaluationStatus.COMPLETED);
        evaluation.setEvaluatedAt(evaluatedAt != null ? evaluatedAt : LocalDateTime.now());
        return aiEvaluationRepository.save(evaluation);
    }

    // =========================================================================
    // PART 1: SUMMARY & CORE METRICS
    // =========================================================================

    @Test
    @DisplayName("Test 1: Empty dataset returns 0 counts and rates without errors")
    void testEmptyDatasetSummary() throws Exception {
        User manager = createTestUser("Manager Empty", "mgr_empty_m8s4@example.com", Role.MANAGER);
        createTestProject("Empty Project", manager, LocalDate.of(2026, 9, 1), null);

        mockMvc.perform(get("/api/analytics/tasks/summary")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(0))
                .andExpect(jsonPath("$.completedTasks").value(0))
                .andExpect(jsonPath("$.pendingTasks").value(0))
                .andExpect(jsonPath("$.inProgressTasks").value(0))
                .andExpect(jsonPath("$.activeTasks").value(0))
                .andExpect(jsonPath("$.overdueTasks").value(0))
                .andExpect(jsonPath("$.completionRate").value(0.0))
                .andExpect(jsonPath("$.totalSubmissions").value(0))
                .andExpect(jsonPath("$.approvedSubmissions").value(0))
                .andExpect(jsonPath("$.changesRequested").value(0))
                .andExpect(jsonPath("$.resubmissions").value(0))
                .andExpect(jsonPath("$.approvalRate").value(0.0))
                .andExpect(jsonPath("$.averageAiCompletion").doesNotExist());
    }

    @Test
    @DisplayName("Test 2-7: Task status counts, completed overdue handling, and active calculations")
    void testTaskStatusCountsAndOverdue() throws Exception {
        User manager = createTestUser("Manager Status", "mgr_status_m8s4@example.com", Role.MANAGER);
        User emp = createTestUser("Employee Status", "emp_status_m8s4@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Status Project", manager, LocalDate.of(2026, 8, 1), null);

        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        // 1 Completed (overdue date, but status COMPLETED -> NOT overdue)
        createTestTask("Task Completed", project, emp, TaskStatus.COMPLETED, yesterday, LocalDateTime.now().minusDays(4));
        // 1 Approved (counts as completed)
        createTestTask("Task Approved", project, emp, TaskStatus.APPROVED, tomorrow, LocalDateTime.now().minusDays(3));
        // 1 Pending (overdue date -> IS overdue)
        createTestTask("Task Pending", project, emp, TaskStatus.CREATED, yesterday, LocalDateTime.now().minusDays(2));
        // 1 In-Progress (future date -> NOT overdue)
        createTestTask("Task InProgress", project, emp, TaskStatus.IN_PROGRESS, tomorrow, LocalDateTime.now().minusDays(1));

        mockMvc.perform(get("/api/analytics/tasks/summary")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(4))
                .andExpect(jsonPath("$.completedTasks").value(2))
                .andExpect(jsonPath("$.pendingTasks").value(1))
                .andExpect(jsonPath("$.inProgressTasks").value(1))
                .andExpect(jsonPath("$.activeTasks").value(2))
                .andExpect(jsonPath("$.overdueTasks").value(1))
                .andExpect(jsonPath("$.completionRate").value(50.0));
    }

    // =========================================================================
    // PART 2: SUBMISSION, RESUBMISSION, APPROVAL & AI METRICS
    // =========================================================================

    @Test
    @DisplayName("Test 8-12: Task submission counts, resubmissions (version > 1), and approval rate")
    void testTaskSubmissionsAndApprovalRate() throws Exception {
        User manager = createTestUser("Manager Subs", "mgr_subs_m8s4@example.com", Role.MANAGER);
        User emp = createTestUser("Employee Subs", "emp_subs_m8s4@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Subs Project", manager, LocalDate.of(2026, 8, 1), null);

        Task task = createTestTask("Iterative Task", project, emp, TaskStatus.COMPLETED, null, LocalDateTime.now().minusDays(3));

        // 3 submissions: v1 CHANGES_REQUESTED, v2 CHANGES_REQUESTED, v3 APPROVED -> 2 resubmissions
        createTestSubmission(task, emp, SubmissionStatus.CHANGES_REQUESTED, 1, LocalDateTime.now().minusDays(3));
        createTestSubmission(task, emp, SubmissionStatus.CHANGES_REQUESTED, 2, LocalDateTime.now().minusDays(2));
        createTestSubmission(task, emp, SubmissionStatus.APPROVED, 3, LocalDateTime.now().minusDays(1));

        mockMvc.perform(get("/api/analytics/tasks/" + task.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(task.getId()))
                .andExpect(jsonPath("$.totalSubmissions").value(3))
                .andExpect(jsonPath("$.approvedSubmissions").value(1))
                .andExpect(jsonPath("$.changesRequested").value(2))
                .andExpect(jsonPath("$.resubmissions").value(2))
                .andExpect(jsonPath("$.approvalRate").value(33.33));
    }

    @Test
    @DisplayName("Test 13-17: Latest completed AI evaluation score selection and null handling")
    void testAiEvaluationLatestSelection() throws Exception {
        User manager = createTestUser("Manager AI", "mgr_ai_m8s4@example.com", Role.MANAGER);
        User emp = createTestUser("Employee AI", "emp_ai_m8s4@example.com", Role.EMPLOYEE);
        Project project = createTestProject("AI Task Project", manager, LocalDate.of(2026, 8, 1), null);

        Task task = createTestTask("AI Evaluated Task", project, emp, TaskStatus.COMPLETED, null, LocalDateTime.now().minusDays(4));

        Submission sub1 = createTestSubmission(task, emp, SubmissionStatus.CHANGES_REQUESTED, 1, LocalDateTime.now().minusDays(3));
        Submission sub2 = createTestSubmission(task, emp, SubmissionStatus.APPROVED, 2, LocalDateTime.now().minusDays(1));

        // Earlier evaluation: 60%, 50%, 70%
        createTestEvaluation(sub1, 60.0, 50.0, 70.0, LocalDateTime.now().minusDays(3));
        // Latest evaluation: 92%, 88%, 95%
        createTestEvaluation(sub2, 92.0, 88.0, 95.0, LocalDateTime.now().minusDays(1));

        // Task-level AI scores should reflect the latest completed evaluation
        mockMvc.perform(get("/api/analytics/tasks/" + task.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiCompletionPercentage").value(92.0))
                .andExpect(jsonPath("$.aiQualityScore").value(88.0))
                .andExpect(jsonPath("$.aiConfidenceScore").value(95.0));
    }

    @Test
    @DisplayName("Test 19, 25: Productivity completion time in hours")
    void testProductivityCompletionHours() throws Exception {
        User manager = createTestUser("Manager Prod", "mgr_prod_m8s4@example.com", Role.MANAGER);
        User emp = createTestUser("Employee Prod", "emp_prod_m8s4@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Prod Project", manager, LocalDate.of(2026, 8, 1), null);

        LocalDateTime created = LocalDateTime.now().minusHours(5);
        Task task = createTestTask("Timed Task", project, emp, TaskStatus.COMPLETED, null, created);

        mockMvc.perform(get("/api/analytics/tasks/" + task.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completionTimeHours").isNumber())
                .andExpect(jsonPath("$.completionTimeHours").value(greaterThanOrEqualTo(0.0)));
    }

    // =========================================================================
    // PART 3: FILTERS (PROJECT, EMPLOYEE, STATUS, DATE)
    // =========================================================================

    @Test
    @DisplayName("Test 18, 19, 20, 21: Project, employee, status, and date range filters")
    void testTaskAnalyticsFilters() throws Exception {
        User manager = createTestUser("Manager Filter", "mgr_filt_m8s4@example.com", Role.MANAGER);
        User emp1 = createTestUser("Employee 1", "emp1_filt_m8s4@example.com", Role.EMPLOYEE);
        User emp2 = createTestUser("Employee 2", "emp2_filt_m8s4@example.com", Role.EMPLOYEE);

        Project projectA = createTestProject("Project Alpha", manager, LocalDate.of(2026, 8, 1), null);
        Project projectB = createTestProject("Project Beta", manager, LocalDate.of(2026, 8, 1), null);

        LocalDateTime augDate = LocalDateTime.of(2026, 8, 15, 10, 0);
        LocalDateTime sepDate = LocalDateTime.of(2026, 9, 15, 10, 0);

        createTestTask("Task A1", projectA, emp1, TaskStatus.COMPLETED, null, augDate);
        createTestTask("Task A2", projectA, emp2, TaskStatus.IN_PROGRESS, null, sepDate);
        createTestTask("Task B1", projectB, emp1, TaskStatus.CREATED, null, sepDate);

        // 1. Filter by projectId = projectA
        mockMvc.perform(get("/api/analytics/tasks")
                        .cookie(createAuthCookie(manager))
                        .param("projectId", projectA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // 2. Filter by employeeId = emp1
        mockMvc.perform(get("/api/analytics/tasks")
                        .cookie(createAuthCookie(manager))
                        .param("employeeId", emp1.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // 3. Filter by status = COMPLETED
        mockMvc.perform(get("/api/analytics/tasks")
                        .cookie(createAuthCookie(manager))
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].taskTitle").value("Task A1"));

        // 4. Filter by date range (September only)
        mockMvc.perform(get("/api/analytics/tasks")
                        .cookie(createAuthCookie(manager))
                        .param("startDate", "2026-09-01")
                        .param("endDate", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // 5. Invalid date range (startDate > endDate) -> 400 Bad Request
        mockMvc.perform(get("/api/analytics/tasks")
                        .cookie(createAuthCookie(manager))
                        .param("startDate", "2026-09-30")
                        .param("endDate", "2026-09-01"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // PART 4: SECURITY & AUTHORIZATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Test 28, 29, 30: Admin and manager scope authorization and isolation")
    void testAdminAndManagerSecurity() throws Exception {
        User admin = createTestUser("Admin User", "admin_task_m8s4@example.com", Role.ADMIN);
        User manager1 = createTestUser("Manager One", "mgr1_sec_m8s4@example.com", Role.MANAGER);
        User manager2 = createTestUser("Manager Two", "mgr2_sec_m8s4@example.com", Role.MANAGER);
        User emp = createTestUser("Emp Common", "emp_com_m8s4@example.com", Role.EMPLOYEE);

        Project project1 = createTestProject("Project M1", manager1, LocalDate.of(2026, 8, 1), null);
        Project project2 = createTestProject("Project M2", manager2, LocalDate.of(2026, 8, 1), null);

        Task task1 = createTestTask("Task 1", project1, emp, TaskStatus.IN_PROGRESS, null, LocalDateTime.now());
        Task task2 = createTestTask("Task 2", project2, emp, TaskStatus.IN_PROGRESS, null, LocalDateTime.now());

        // Admin can access both tasks
        mockMvc.perform(get("/api/analytics/tasks/" + task1.getId()).cookie(createAuthCookie(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/analytics/tasks/" + task2.getId()).cookie(createAuthCookie(admin)))
                .andExpect(status().isOk());

        // Manager 1 accessing own task -> OK
        mockMvc.perform(get("/api/analytics/tasks/" + task1.getId()).cookie(createAuthCookie(manager1)))
                .andExpect(status().isOk());

        // Manager 1 accessing Manager 2's task -> 403 Forbidden
        mockMvc.perform(get("/api/analytics/tasks/" + task2.getId()).cookie(createAuthCookie(manager1)))
                .andExpect(status().isForbidden());

        // Manager 1 filtering by Manager 2's project -> 403 Forbidden
        mockMvc.perform(get("/api/analytics/tasks").cookie(createAuthCookie(manager1))
                        .param("projectId", project2.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 31, 32, 33: Employee isolation, IDOR protection, and forbidden queries")
    void testEmployeeIsolationAndIdor() throws Exception {
        User manager = createTestUser("Manager EmpIso", "mgr_empiso_m8s4@example.com", Role.MANAGER);
        User empA = createTestUser("Employee A", "empa_iso_m8s4@example.com", Role.EMPLOYEE);
        User empB = createTestUser("Employee B", "empb_iso_m8s4@example.com", Role.EMPLOYEE);

        Project project = createTestProject("Emp Isolation Project", manager, LocalDate.of(2026, 8, 1), null);

        Task taskA = createTestTask("Task A", project, empA, TaskStatus.IN_PROGRESS, null, LocalDateTime.now());
        Task taskB = createTestTask("Task B", project, empB, TaskStatus.IN_PROGRESS, null, LocalDateTime.now());

        // Employee A accesses own task -> OK
        mockMvc.perform(get("/api/analytics/tasks/" + taskA.getId()).cookie(createAuthCookie(empA)))
                .andExpect(status().isOk());

        // Employee A accesses Employee B's task (IDOR) -> 403 Forbidden
        mockMvc.perform(get("/api/analytics/tasks/" + taskB.getId()).cookie(createAuthCookie(empA)))
                .andExpect(status().isForbidden());

        // Employee A tries to filter by Employee B's id -> 403 Forbidden
        mockMvc.perform(get("/api/analytics/tasks").cookie(createAuthCookie(empA))
                        .param("employeeId", empB.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 34: Unauthenticated request to task analytics returns Forbidden (403)")
    void testUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/analytics/tasks"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/analytics/tasks/1"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/analytics/tasks/summary"))
                .andExpect(status().isForbidden());
    }
}
