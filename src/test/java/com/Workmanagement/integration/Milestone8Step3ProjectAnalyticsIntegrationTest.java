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

public class Milestone8Step3ProjectAnalyticsIntegrationTest extends BaseIntegrationTest {

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
        return taskRepository.save(task);
    }

    private Submission createTestSubmission(Task task, User submitter, SubmissionStatus status, int version, LocalDateTime submittedAt) {
        Submission submission = new Submission();
        submission.setTask(task);
        submission.setSubmittedBy(submitter);
        submission.setReport("Submission completion report");
        submission.setStatus(status);
        submission.setVersion(version);
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
    // PART 1: CORE METRICS & ZERO/EDGE CASES
    // =========================================================================

    @Test
    @DisplayName("Test 1: Empty project with zero tasks returns 0 counts and rates without errors")
    void testEmptyProjectMetrics() throws Exception {
        User manager = createTestUser("Manager Empty", "mgr_empty_m8s3@example.com", Role.MANAGER);
        Project project = createTestProject("Empty Project", manager, LocalDate.of(2026, 9, 1), null);

        mockMvc.perform(get("/api/analytics/projects/" + project.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(project.getId()))
                .andExpect(jsonPath("$.projectName").value("Empty Project"))
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
                .andExpect(jsonPath("$.averageCompletion").value(0.0))
                .andExpect(jsonPath("$.averageQuality").value(0.0))
                .andExpect(jsonPath("$.averageConfidence").value(0.0))
                .andExpect(jsonPath("$.employeeCount").value(0));
    }

    @Test
    @DisplayName("Test 2, 3, 4: Task counts, completion rate, active tasks, and overdue calculation")
    void testProjectTaskCountsAndOverdue() throws Exception {
        User manager = createTestUser("Manager Tasks", "mgr_tasks_m8s3@example.com", Role.MANAGER);
        User emp1 = createTestUser("Employee One", "emp1_m8s3@example.com", Role.EMPLOYEE);
        User emp2 = createTestUser("Employee Two", "emp2_m8s3@example.com", Role.EMPLOYEE);

        Project project = createTestProject("Task Test Project", manager, LocalDate.of(2026, 8, 1), null);

        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        // 1 Completed (overdue date, but completed -> NOT overdue)
        createTestTask("Task 1", project, emp1, TaskStatus.COMPLETED, yesterday, LocalDateTime.now().minusDays(5));
        // 1 Approved (counts as completed)
        createTestTask("Task 2", project, emp2, TaskStatus.APPROVED, tomorrow, LocalDateTime.now().minusDays(4));
        // 1 Pending (overdue -> IS overdue)
        createTestTask("Task 3", project, emp1, TaskStatus.CREATED, yesterday, LocalDateTime.now().minusDays(3));
        // 1 In Progress (future -> NOT overdue)
        createTestTask("Task 4", project, emp2, TaskStatus.IN_PROGRESS, tomorrow, LocalDateTime.now().minusDays(2));

        // total = 4, completed = 2, pending = 1, inProgress = 1, active = 2, overdue = 1, completionRate = 50.0, employeeCount = 2
        mockMvc.perform(get("/api/analytics/projects/" + project.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(4))
                .andExpect(jsonPath("$.completedTasks").value(2))
                .andExpect(jsonPath("$.pendingTasks").value(1))
                .andExpect(jsonPath("$.inProgressTasks").value(1))
                .andExpect(jsonPath("$.activeTasks").value(2))
                .andExpect(jsonPath("$.overdueTasks").value(1))
                .andExpect(jsonPath("$.completionRate").value(50.0))
                .andExpect(jsonPath("$.employeeCount").value(2));
    }

    @Test
    @DisplayName("Test 5, 6, 7: Submission counts, resubmissions (version > 1), and approval rate")
    void testSubmissionMetricsAndResubmissions() throws Exception {
        User manager = createTestUser("Manager Submissions", "mgr_sub_m8s3@example.com", Role.MANAGER);
        User emp = createTestUser("Employee Sub", "emp_sub_m8s3@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Submission Project", manager, LocalDate.of(2026, 8, 1), null);

        Task task1 = createTestTask("Task 1", project, emp, TaskStatus.COMPLETED, null, LocalDateTime.now().minusDays(3));
        Task task2 = createTestTask("Task 2", project, emp, TaskStatus.IN_PROGRESS, null, LocalDateTime.now().minusDays(2));

        // Task 1: 3 submissions (v1 CHANGES_REQUESTED, v2 CHANGES_REQUESTED, v3 APPROVED) -> 2 resubmissions
        createTestSubmission(task1, emp, SubmissionStatus.CHANGES_REQUESTED, 1, LocalDateTime.now().minusDays(3));
        createTestSubmission(task1, emp, SubmissionStatus.CHANGES_REQUESTED, 2, LocalDateTime.now().minusDays(2));
        createTestSubmission(task1, emp, SubmissionStatus.APPROVED, 3, LocalDateTime.now().minusDays(1));

        // Task 2: 1 submission (v1 UNDER_REVIEW - not reviewed yet)
        createTestSubmission(task2, emp, SubmissionStatus.UNDER_REVIEW, 1, LocalDateTime.now());

        // totalSubmissions = 4, approvedSubmissions = 1, changesRequested = 2, resubmissions = 2
        // totalReviewed = 1 + 2 = 3. approvalRate = 1/3 * 100 = 33.33%
        mockMvc.perform(get("/api/analytics/projects/" + project.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSubmissions").value(4))
                .andExpect(jsonPath("$.approvedSubmissions").value(1))
                .andExpect(jsonPath("$.changesRequested").value(2))
                .andExpect(jsonPath("$.resubmissions").value(2))
                .andExpect(jsonPath("$.approvalRate").value(33.33));
    }

    @Test
    @DisplayName("Test 8: AI evaluations average computation and null handling")
    void testAiEvaluationsAverageComputation() throws Exception {
        User manager = createTestUser("Manager AI", "mgr_ai_m8s3@example.com", Role.MANAGER);
        User emp = createTestUser("Employee AI", "emp_ai_m8s3@example.com", Role.EMPLOYEE);
        Project project = createTestProject("AI Project", manager, LocalDate.of(2026, 8, 1), null);

        Task task = createTestTask("AI Task", project, emp, TaskStatus.COMPLETED, null, LocalDateTime.now().minusDays(4));
        Submission sub1 = createTestSubmission(task, emp, SubmissionStatus.APPROVED, 1, LocalDateTime.now().minusDays(3));
        Submission sub2 = createTestSubmission(task, emp, SubmissionStatus.APPROVED, 2, LocalDateTime.now().minusDays(2));

        // Eval 1: 80.0, 90.0, 85.0
        createTestEvaluation(sub1, 80.0, 90.0, 85.0, LocalDateTime.now().minusDays(3));
        // Eval 2: 90.0, 70.0, 95.0
        createTestEvaluation(sub2, 90.0, 70.0, 95.0, LocalDateTime.now().minusDays(2));

        // avgCompletion: 85.0, avgQuality: 80.0, avgConfidence: 90.0
        mockMvc.perform(get("/api/analytics/projects/" + project.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageCompletion").value(85.0))
                .andExpect(jsonPath("$.averageQuality").value(80.0))
                .andExpect(jsonPath("$.averageConfidence").value(90.0));
    }

    @Test
    @DisplayName("Test 9: Employee count counts distinct assignees across project tasks")
    void testDistinctEmployeeCount() throws Exception {
        User manager = createTestUser("Manager Count", "mgr_count_m8s3@example.com", Role.MANAGER);
        User empA = createTestUser("Employee Alpha", "emp_a_m8s3@example.com", Role.EMPLOYEE);
        User empB = createTestUser("Employee Beta", "emp_b_m8s3@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Count Project", manager, LocalDate.of(2026, 8, 1), null);

        createTestTask("Task 1", project, empA, TaskStatus.COMPLETED, null, LocalDateTime.now());
        createTestTask("Task 2", project, empA, TaskStatus.IN_PROGRESS, null, LocalDateTime.now());
        createTestTask("Task 3", project, empB, TaskStatus.CREATED, null, LocalDateTime.now());
        createTestTask("Task 4", project, empA, TaskStatus.ASSIGNED, null, LocalDateTime.now());

        // 4 tasks, but only 2 distinct employees
        mockMvc.perform(get("/api/analytics/projects/" + project.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(4))
                .andExpect(jsonPath("$.employeeCount").value(2));
    }

    // =========================================================================
    // PART 2: MULTI-PROJECT & DATE FILTERING
    // =========================================================================

    @Test
    @DisplayName("Test 10: Multiple projects produce independent metrics without data leakage")
    void testMultipleProjectsDataIsolation() throws Exception {
        User manager = createTestUser("Manager Multi", "mgr_multi_m8s3@example.com", Role.MANAGER);
        User emp = createTestUser("Employee Multi", "emp_multi_m8s3@example.com", Role.EMPLOYEE);

        Project projectA = createTestProject("Project Alpha", manager, LocalDate.of(2026, 8, 1), null);
        Project projectB = createTestProject("Project Beta", manager, LocalDate.of(2026, 8, 1), null);

        createTestTask("Task A1", projectA, emp, TaskStatus.COMPLETED, null, LocalDateTime.now());
        createTestTask("Task A2", projectA, emp, TaskStatus.IN_PROGRESS, null, LocalDateTime.now());
        createTestTask("Task B1", projectB, emp, TaskStatus.COMPLETED, null, LocalDateTime.now());

        // List endpoint should return both projects with separate metrics
        mockMvc.perform(get("/api/analytics/projects")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].projectName").value("Project Alpha"))
                .andExpect(jsonPath("$[0].totalTasks").value(2))
                .andExpect(jsonPath("$[0].completedTasks").value(1))
                .andExpect(jsonPath("$[0].completionRate").value(50.0))
                .andExpect(jsonPath("$[1].projectName").value("Project Beta"))
                .andExpect(jsonPath("$[1].totalTasks").value(1))
                .andExpect(jsonPath("$[1].completedTasks").value(1))
                .andExpect(jsonPath("$[1].completionRate").value(100.0));
    }

    @Test
    @DisplayName("Test 11: Date range filtering on project analytics and bad request validation")
    void testDateRangeFiltering() throws Exception {
        User manager = createTestUser("Manager Date", "mgr_date_m8s3@example.com", Role.MANAGER);
        User emp = createTestUser("Employee Date", "emp_date_m8s3@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Date Project", manager, LocalDate.of(2026, 8, 1), null);

        LocalDateTime augDate = LocalDateTime.of(2026, 8, 15, 10, 0);
        LocalDateTime sepDate = LocalDateTime.of(2026, 9, 15, 10, 0);

        createTestTask("Aug Task", project, emp, TaskStatus.COMPLETED, null, augDate);
        createTestTask("Sep Task", project, emp, TaskStatus.IN_PROGRESS, null, sepDate);

        // 1. September only filter
        mockMvc.perform(get("/api/analytics/projects/" + project.getId())
                        .cookie(createAuthCookie(manager))
                        .param("startDate", "2026-09-01")
                        .param("endDate", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(1))
                .andExpect(jsonPath("$.inProgressTasks").value(1))
                .andExpect(jsonPath("$.completedTasks").value(0))
                .andExpect(jsonPath("$.startDate").value("2026-09-01"))
                .andExpect(jsonPath("$.endDate").value("2026-09-30"));

        // 2. August only filter
        mockMvc.perform(get("/api/analytics/projects/" + project.getId())
                        .cookie(createAuthCookie(manager))
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(1))
                .andExpect(jsonPath("$.completedTasks").value(1));

        // 3. Invalid range (startDate > endDate) returns 400 Bad Request
        mockMvc.perform(get("/api/analytics/projects/" + project.getId())
                        .cookie(createAuthCookie(manager))
                        .param("startDate", "2026-09-30")
                        .param("endDate", "2026-09-01"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // PART 3: SECURITY & AUTHORIZATION TESTS
    // =========================================================================

    @Test
    @DisplayName("Test 12: Admin can access project analytics globally")
    void testAdminGlobalAccess() throws Exception {
        User admin = createTestUser("Admin User", "admin_proj_m8s3@example.com", Role.ADMIN);
        User manager = createTestUser("Manager Admin", "mgr_admin_m8s3@example.com", Role.MANAGER);
        Project project = createTestProject("Admin View Project", manager, LocalDate.of(2026, 8, 1), null);

        mockMvc.perform(get("/api/analytics/projects/" + project.getId())
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(project.getId()));
    }

    @Test
    @DisplayName("Test 13, 15: Manager can access own project but forbidden from another manager's project (IDOR protection)")
    void testManagerAccessAndIsolation() throws Exception {
        User manager1 = createTestUser("Manager One", "mgr1_iso_m8s3@example.com", Role.MANAGER);
        User manager2 = createTestUser("Manager Two", "mgr2_iso_m8s3@example.com", Role.MANAGER);

        Project project1 = createTestProject("Manager 1 Project", manager1, LocalDate.of(2026, 8, 1), null);
        Project project2 = createTestProject("Manager 2 Project", manager2, LocalDate.of(2026, 8, 1), null);

        // Manager 1 accessing Project 1 -> OK
        mockMvc.perform(get("/api/analytics/projects/" + project1.getId())
                        .cookie(createAuthCookie(manager1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(project1.getId()));

        // Manager 1 attempting to access Project 2 (IDOR attempt) -> 403 Forbidden
        mockMvc.perform(get("/api/analytics/projects/" + project2.getId())
                        .cookie(createAuthCookie(manager1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 14: Employee can access assigned project but forbidden from unassigned project and project list endpoint")
    void testEmployeeAccessRules() throws Exception {
        User manager = createTestUser("Manager EmpSec", "mgr_empsec_m8s3@example.com", Role.MANAGER);
        User employeeAssigned = createTestUser("Employee Assigned", "emp_ass_m8s3@example.com", Role.EMPLOYEE);
        User employeeUnassigned = createTestUser("Employee Unassigned", "emp_unass_m8s3@example.com", Role.EMPLOYEE);

        Project project = createTestProject("Emp Sec Project", manager, LocalDate.of(2026, 8, 1), null);
        createTestTask("Assigned Task", project, employeeAssigned, TaskStatus.IN_PROGRESS, null, LocalDateTime.now());

        // 1. Employee forbidden from project list endpoint -> 403 Forbidden
        mockMvc.perform(get("/api/analytics/projects")
                        .cookie(createAuthCookie(employeeAssigned)))
                .andExpect(status().isForbidden());

        // 2. Assigned employee accessing project analytics -> OK
        mockMvc.perform(get("/api/analytics/projects/" + project.getId())
                        .cookie(createAuthCookie(employeeAssigned)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(project.getId()));

        // 3. Unassigned employee accessing project analytics -> 403 Forbidden
        mockMvc.perform(get("/api/analytics/projects/" + project.getId())
                        .cookie(createAuthCookie(employeeUnassigned)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 16: Unauthenticated request to project analytics returns Forbidden (403)")
    void testUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/analytics/projects"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/analytics/projects/1"))
                .andExpect(status().isForbidden());
    }
}
