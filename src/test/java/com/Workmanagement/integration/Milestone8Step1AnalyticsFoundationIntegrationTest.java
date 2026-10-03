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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class Milestone8Step1AnalyticsFoundationIntegrationTest extends BaseIntegrationTest {

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
        project.setDescription("Analytics project description for " + name);
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
        return taskRepository.save(task);
    }

    private Submission createTestSubmission(Task task, User submitter, SubmissionStatus status, LocalDateTime submittedAt) {
        Submission submission = new Submission();
        submission.setTask(task);
        submission.setSubmittedBy(submitter);
        submission.setReport("Submission completion report");
        submission.setStatus(status);
        submission.setSubmittedAt(submittedAt != null ? submittedAt : LocalDateTime.now());
        return submissionRepository.save(submission);
    }

    private AiEvaluation createTestEvaluation(Submission submission, Double completion, Double quality, Double confidence, LocalDateTime evaluatedAt) {
        AiEvaluation evaluation = new AiEvaluation();
        evaluation.setSubmission(submission);
        evaluation.setCompletionPercentage(completion != null ? completion : 0.0);
        evaluation.setQualityScore(quality != null ? quality : 0.0);
        evaluation.setConfidenceScore(confidence != null ? confidence : 0.0);
        evaluation.setStatus(AiEvaluationStatus.COMPLETED);
        evaluation.setEvaluatedAt(evaluatedAt != null ? evaluatedAt : LocalDateTime.now());
        return aiEvaluationRepository.save(evaluation);
    }

    // =========================================================================
    // AUTHORIZATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Test 1: Authorized manager can access analytics overview")
    void testAuthorizedManagerCanAccessAnalytics() throws Exception {
        User manager = createTestUser("Manager One", "mgr1_analytics@example.com", Role.MANAGER);
        User employee = createTestUser("Employee One", "emp1_analytics@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Project Alpha", manager, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        createTestTask("Task 1", project, employee, TaskStatus.COMPLETED, LocalDate.of(2026, 9, 15), LocalDateTime.of(2026, 9, 2, 10, 0));

        mockMvc.perform(get("/api/analytics/overview")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects.totalProjects").value(1))
                .andExpect(jsonPath("$.tasks.totalTasks").value(1))
                .andExpect(jsonPath("$.tasks.completedTasks").value(1))
                .andExpect(jsonPath("$.completionRate").value(100.0));
    }

    @Test
    @DisplayName("Test 2: Unauthorized employee cannot access manager-wide analytics overview")
    void testUnauthorizedEmployeeCannotAccessAnalytics() throws Exception {
        User employee = createTestUser("Employee Two", "emp2_analytics@example.com", Role.EMPLOYEE);

        mockMvc.perform(get("/api/analytics/overview")
                        .cookie(createAuthCookie(employee)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 3: Admin access can view organization-wide analytics across all managers")
    void testAdminCanViewAllAnalytics() throws Exception {
        User admin = createTestUser("Admin User", "admin_analytics@example.com", Role.ADMIN);
        User manager1 = createTestUser("Manager Alpha", "mgr_alpha_an@example.com", Role.MANAGER);
        User manager2 = createTestUser("Manager Beta", "mgr_beta_an@example.com", Role.MANAGER);
        User employee = createTestUser("Employee Three", "emp3_analytics@example.com", Role.EMPLOYEE);

        Project p1 = createTestProject("Proj 1", manager1, LocalDate.of(2026, 9, 1), null);
        Project p2 = createTestProject("Proj 2", manager2, LocalDate.of(2026, 9, 1), null);

        createTestTask("Task M1", p1, employee, TaskStatus.IN_PROGRESS, null, LocalDateTime.of(2026, 9, 5, 10, 0));
        createTestTask("Task M2", p2, employee, TaskStatus.COMPLETED, null, LocalDateTime.of(2026, 9, 6, 10, 0));

        mockMvc.perform(get("/api/analytics/overview")
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects.totalProjects").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.tasks.totalTasks").value(greaterThanOrEqualTo(2)));
    }

    // =========================================================================
    // DATE FILTERING TESTS
    // =========================================================================

    @Test
    @DisplayName("Test 4: No date filter includes all records")
    void testNoDateFilterIncludesAll() throws Exception {
        User manager = createTestUser("Mgr DateAll", "mgr_date_all@example.com", Role.MANAGER);
        User employee = createTestUser("Emp DateAll", "emp_date_all@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Proj All Dates", manager, LocalDate.of(2026, 8, 1), null);

        createTestTask("August Task", project, employee, TaskStatus.COMPLETED, null, LocalDateTime.of(2026, 8, 10, 10, 0));
        createTestTask("September Task", project, employee, TaskStatus.IN_PROGRESS, null, LocalDateTime.of(2026, 9, 10, 10, 0));
        createTestTask("October Task", project, employee, TaskStatus.ASSIGNED, null, LocalDateTime.of(2026, 10, 10, 10, 0));

        mockMvc.perform(get("/api/analytics/overview")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.totalTasks").value(3))
                .andExpect(jsonPath("$.startDate").doesNotExist())
                .andExpect(jsonPath("$.endDate").doesNotExist());
    }

    @Test
    @DisplayName("Test 5: startDate filter includes records on or after startDate")
    void testStartDateFilter() throws Exception {
        User manager = createTestUser("Mgr StartDate", "mgr_start_date@example.com", Role.MANAGER);
        User employee = createTestUser("Emp StartDate", "emp_start_date@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Proj Start Date", manager, LocalDate.of(2026, 9, 1), null);

        createTestTask("Old Task", project, employee, TaskStatus.COMPLETED, null, LocalDateTime.of(2026, 8, 15, 10, 0));
        createTestTask("New Task 1", project, employee, TaskStatus.IN_PROGRESS, null, LocalDateTime.of(2026, 9, 5, 10, 0));
        createTestTask("New Task 2", project, employee, TaskStatus.COMPLETED, null, LocalDateTime.of(2026, 9, 20, 10, 0));

        mockMvc.perform(get("/api/analytics/overview?startDate=2026-09-01")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.totalTasks").value(2))
                .andExpect(jsonPath("$.startDate").value("2026-09-01"));
    }

    @Test
    @DisplayName("Test 6: endDate filter includes records on or before endDate")
    void testEndDateFilter() throws Exception {
        User manager = createTestUser("Mgr EndDate", "mgr_end_date@example.com", Role.MANAGER);
        User employee = createTestUser("Emp EndDate", "emp_end_date@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Proj End Date", manager, LocalDate.of(2026, 8, 1), null);

        createTestTask("Early Task", project, employee, TaskStatus.COMPLETED, null, LocalDateTime.of(2026, 8, 15, 10, 0));
        createTestTask("Mid Task", project, employee, TaskStatus.IN_PROGRESS, null, LocalDateTime.of(2026, 8, 30, 10, 0));
        createTestTask("Late Task", project, employee, TaskStatus.ASSIGNED, null, LocalDateTime.of(2026, 9, 15, 10, 0));

        mockMvc.perform(get("/api/analytics/overview?endDate=2026-08-31")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.totalTasks").value(2))
                .andExpect(jsonPath("$.endDate").value("2026-08-31"));
    }

    @Test
    @DisplayName("Test 7: Both startDate and endDate filters bound records strictly in range")
    void testDateRangeFilter() throws Exception {
        User manager = createTestUser("Mgr Range", "mgr_range@example.com", Role.MANAGER);
        User employee = createTestUser("Emp Range", "emp_range@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Proj Range", manager, LocalDate.of(2026, 9, 1), null);

        createTestTask("Before Range", project, employee, TaskStatus.COMPLETED, null, LocalDateTime.of(2026, 8, 31, 10, 0));
        createTestTask("Inside Range 1", project, employee, TaskStatus.COMPLETED, null, LocalDateTime.of(2026, 9, 1, 10, 0));
        createTestTask("Inside Range 2", project, employee, TaskStatus.IN_PROGRESS, null, LocalDateTime.of(2026, 9, 15, 10, 0));
        createTestTask("Inside Range 3", project, employee, TaskStatus.ASSIGNED, null, LocalDateTime.of(2026, 9, 30, 10, 0));
        createTestTask("After Range", project, employee, TaskStatus.COMPLETED, null, LocalDateTime.of(2026, 10, 1, 10, 0));

        mockMvc.perform(get("/api/analytics/overview?startDate=2026-09-01&endDate=2026-09-30")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.totalTasks").value(3))
                .andExpect(jsonPath("$.startDate").value("2026-09-01"))
                .andExpect(jsonPath("$.endDate").value("2026-09-30"));
    }

    @Test
    @DisplayName("Test 8: Invalid date range (startDate > endDate) returns 400 Bad Request")
    void testInvalidDateRangeReturnsBadRequest() throws Exception {
        User manager = createTestUser("Mgr BadDate", "mgr_baddate@example.com", Role.MANAGER);

        mockMvc.perform(get("/api/analytics/overview?startDate=2026-10-01&endDate=2026-09-01")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Start date cannot be after end date")));
    }

    // =========================================================================
    // MATHEMATICAL INTEGRITY & EDGE CASES
    // =========================================================================

    @Test
    @DisplayName("Test 9: Zero-task dataset does not divide by zero and returns 0.0 rates")
    void testZeroTaskDatasetDoesNotDivideByZero() throws Exception {
        User manager = createTestUser("Mgr ZeroTask", "mgr_zerotask@example.com", Role.MANAGER);
        createTestProject("Empty Project", manager, LocalDate.of(2026, 9, 1), null);

        mockMvc.perform(get("/api/analytics/overview")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects.totalProjects").value(1))
                .andExpect(jsonPath("$.tasks.totalTasks").value(0))
                .andExpect(jsonPath("$.submissions.totalSubmissions").value(0))
                .andExpect(jsonPath("$.evaluations.totalEvaluations").value(0))
                .andExpect(jsonPath("$.completionRate").value(0.0))
                .andExpect(jsonPath("$.approvalRate").value(0.0))
                .andExpect(jsonPath("$.evaluations.averageCompletionPercentage").value(0.0))
                .andExpect(jsonPath("$.evaluations.averageQualityScore").value(0.0))
                .andExpect(jsonPath("$.evaluations.averageConfidenceScore").value(0.0));
    }

    @Test
    @DisplayName("Test 10: AI evaluation averages and rates are accurately calculated with rounding")
    void testEvaluationAveragesAndRatesCalculation() throws Exception {
        User manager = createTestUser("Mgr Scores", "mgr_scores@example.com", Role.MANAGER);
        User employee = createTestUser("Emp Scores", "emp_scores@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Score Project", manager, LocalDate.of(2026, 9, 1), null);

        Task t1 = createTestTask("Task 1", project, employee, TaskStatus.COMPLETED, null, LocalDateTime.of(2026, 9, 2, 10, 0));
        Task t2 = createTestTask("Task 2", project, employee, TaskStatus.IN_PROGRESS, null, LocalDateTime.of(2026, 9, 3, 10, 0));
        Task t3 = createTestTask("Task 3", project, employee, TaskStatus.APPROVED, null, LocalDateTime.of(2026, 9, 4, 10, 0));

        // Submissions
        Submission s1 = createTestSubmission(t1, employee, SubmissionStatus.APPROVED, LocalDateTime.of(2026, 9, 5, 10, 0));
        Submission s2 = createTestSubmission(t2, employee, SubmissionStatus.CHANGES_REQUESTED, LocalDateTime.of(2026, 9, 6, 10, 0));

        // AI Evaluations
        createTestEvaluation(s1, 95.5, 88.0, 92.0, LocalDateTime.of(2026, 9, 5, 10, 30));
        createTestEvaluation(s2, 75.0, 70.0, 80.0, LocalDateTime.of(2026, 9, 6, 10, 30));

        // Completion rate: 2 completed/approved out of 3 tasks = 66.67%
        // Approval rate: 1 approved out of 2 reviewed = 50.0%
        // Avg completion: (95.5 + 75.0) / 2 = 85.25
        // Avg quality: (88.0 + 70.0) / 2 = 79.0
        // Avg confidence: (92.0 + 80.0) / 2 = 86.0

        mockMvc.perform(get("/api/analytics/overview")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.totalTasks").value(3))
                .andExpect(jsonPath("$.completionRate").value(66.67))
                .andExpect(jsonPath("$.submissions.totalSubmissions").value(2))
                .andExpect(jsonPath("$.submissions.approvedSubmissions").value(1))
                .andExpect(jsonPath("$.submissions.changesRequestedSubmissions").value(1))
                .andExpect(jsonPath("$.approvalRate").value(50.0))
                .andExpect(jsonPath("$.evaluations.totalEvaluations").value(2))
                .andExpect(jsonPath("$.evaluations.averageCompletionPercentage").value(85.25))
                .andExpect(jsonPath("$.evaluations.averageQualityScore").value(79.0))
                .andExpect(jsonPath("$.evaluations.averageConfidenceScore").value(86.0));
    }

    // =========================================================================
    // MANAGER DATA ISOLATION
    // =========================================================================

    @Test
    @DisplayName("Test 11: Analytics do not leak another manager's data")
    void testManagerDataIsolation() throws Exception {
        User managerA = createTestUser("Manager A", "mgra_isolate@example.com", Role.MANAGER);
        User managerB = createTestUser("Manager B", "mgrb_isolate@example.com", Role.MANAGER);
        User employee = createTestUser("Emp Shared", "emp_shared@example.com", Role.EMPLOYEE);

        Project projectA = createTestProject("Project A", managerA, LocalDate.of(2026, 9, 1), null);
        Project projectB = createTestProject("Project B", managerB, LocalDate.of(2026, 9, 1), null);

        createTestTask("Task A1", projectA, employee, TaskStatus.COMPLETED, null, LocalDateTime.of(2026, 9, 2, 10, 0));
        createTestTask("Task A2", projectA, employee, TaskStatus.IN_PROGRESS, null, LocalDateTime.of(2026, 9, 3, 10, 0));

        createTestTask("Task B1", projectB, employee, TaskStatus.ASSIGNED, null, LocalDateTime.of(2026, 9, 4, 10, 0));
        createTestTask("Task B2", projectB, employee, TaskStatus.ASSIGNED, null, LocalDateTime.of(2026, 9, 5, 10, 0));
        createTestTask("Task B3", projectB, employee, TaskStatus.ASSIGNED, null, LocalDateTime.of(2026, 9, 6, 10, 0));

        // Manager A should only see 1 project and 2 tasks
        mockMvc.perform(get("/api/analytics/overview")
                        .cookie(createAuthCookie(managerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects.totalProjects").value(1))
                .andExpect(jsonPath("$.tasks.totalTasks").value(2))
                .andExpect(jsonPath("$.tasks.completedTasks").value(1))
                .andExpect(jsonPath("$.tasks.inProgressTasks").value(1));

        // Manager B should only see 1 project and 3 tasks
        mockMvc.perform(get("/api/analytics/overview")
                        .cookie(createAuthCookie(managerB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects.totalProjects").value(1))
                .andExpect(jsonPath("$.tasks.totalTasks").value(3))
                .andExpect(jsonPath("$.tasks.pendingTasks").value(3));
    }

    @Test
    @DisplayName("Test 12: Overdue task aggregation accurately checks end dates")
    void testOverdueTaskAggregation() throws Exception {
        User manager = createTestUser("Mgr Overdue", "mgr_overdue@example.com", Role.MANAGER);
        User employee = createTestUser("Emp Overdue", "emp_overdue@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Overdue Project", manager, LocalDate.of(2026, 9, 1), null);

        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        // Overdue (ended yesterday and not completed)
        createTestTask("Overdue Task", project, employee, TaskStatus.IN_PROGRESS, yesterday, null);
        // Not overdue (ended yesterday but completed)
        createTestTask("Completed Past Task", project, employee, TaskStatus.COMPLETED, yesterday, null);
        // Not overdue (ends tomorrow)
        createTestTask("Future Task", project, employee, TaskStatus.IN_PROGRESS, tomorrow, null);

        mockMvc.perform(get("/api/analytics/overview")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.totalTasks").value(3))
                .andExpect(jsonPath("$.tasks.overdueTasks").value(1));
    }
}
