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

public class Milestone8Step2EmployeePerformanceAnalyticsIntegrationTest extends BaseIntegrationTest {

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
    @DisplayName("Test 1: Employee with no tasks returns 0 counts and rates without division by zero")
    void testEmployeeWithNoTasks() throws Exception {
        User manager = createTestUser("Manager Zero", "mgr_zero_m8s2@example.com", Role.MANAGER);
        User employee = createTestUser("Employee Zero", "emp_zero_m8s2@example.com", Role.EMPLOYEE);
        createTestProject("Project Zero", manager, LocalDate.of(2026, 9, 1), null);

        // Employee views self
        mockMvc.perform(get("/api/analytics/employees/" + employee.getId())
                        .cookie(createAuthCookie(employee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(employee.getId()))
                .andExpect(jsonPath("$.employeeName").value("Employee Zero"))
                .andExpect(jsonPath("$.totalAssignedTasks").value(0))
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
                .andExpect(jsonPath("$.averageConfidence").value(0.0));
    }

    @Test
    @DisplayName("Test 2, 3, 4, 5: Assigned, completed, pending, in-progress, and active task metric breakdown")
    void testEmployeeTaskStatusMetrics() throws Exception {
        User manager = createTestUser("Manager Tasks", "mgr_tasks_m8s2@example.com", Role.MANAGER);
        User employee = createTestUser("Employee Tasks", "emp_tasks_m8s2@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Project Tasks", manager, LocalDate.of(2026, 9, 1), null);

        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate tomorrow = LocalDate.now().plusDays(2);

        // 2 completed/approved tasks
        createTestTask("Task 1 Completed", project, employee, TaskStatus.COMPLETED, null, LocalDateTime.of(2026, 9, 2, 10, 0));
        createTestTask("Task 2 Approved", project, employee, TaskStatus.APPROVED, null, LocalDateTime.of(2026, 9, 3, 10, 0));

        // 1 pending task
        createTestTask("Task 3 Assigned", project, employee, TaskStatus.ASSIGNED, tomorrow, LocalDateTime.of(2026, 9, 4, 10, 0));

        // 1 in-progress task (overdue)
        createTestTask("Task 4 InProgress Overdue", project, employee, TaskStatus.IN_PROGRESS, yesterday, LocalDateTime.of(2026, 9, 5, 10, 0));

        // Total = 4 tasks. Completed = 2. Pending = 1. InProgress = 1. Active = 2. Overdue = 1. CompletionRate = 50.0%
        mockMvc.perform(get("/api/analytics/employees/" + employee.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssignedTasks").value(4))
                .andExpect(jsonPath("$.completedTasks").value(2))
                .andExpect(jsonPath("$.pendingTasks").value(1))
                .andExpect(jsonPath("$.inProgressTasks").value(1))
                .andExpect(jsonPath("$.activeTasks").value(2))
                .andExpect(jsonPath("$.overdueTasks").value(1))
                .andExpect(jsonPath("$.completionRate").value(50.0));
    }

    @Test
    @DisplayName("Test 6, 7, 8: Submission iterations, resubmissions, approvals, changes requested, and approval rate")
    void testEmployeeSubmissionAndResubmissionMetrics() throws Exception {
        User manager = createTestUser("Manager Subs", "mgr_subs_m8s2@example.com", Role.MANAGER);
        User employee = createTestUser("Employee Subs", "emp_subs_m8s2@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Project Subs", manager, LocalDate.of(2026, 9, 1), null);

        Task t1 = createTestTask("Task 1", project, employee, TaskStatus.APPROVED, null, LocalDateTime.of(2026, 9, 2, 10, 0));
        Task t2 = createTestTask("Task 2", project, employee, TaskStatus.CHANGES_REQUESTED, null, LocalDateTime.of(2026, 9, 3, 10, 0));

        // Task 1: v1 (changes requested), v2 (approved) -> 1 resubmission
        createTestSubmission(t1, employee, SubmissionStatus.CHANGES_REQUESTED, 1, LocalDateTime.of(2026, 9, 4, 10, 0));
        createTestSubmission(t1, employee, SubmissionStatus.APPROVED, 2, LocalDateTime.of(2026, 9, 5, 10, 0));

        // Task 2: v1 (changes requested)
        createTestSubmission(t2, employee, SubmissionStatus.CHANGES_REQUESTED, 1, LocalDateTime.of(2026, 9, 6, 10, 0));

        // Total submissions = 3. Approved = 1. Changes requested = 2. Resubmissions (v > 1) = 1.
        // Reviewed = 1 + 2 = 3. Approval rate = (1 / 3) * 100 = 33.33%
        mockMvc.perform(get("/api/analytics/employees/" + employee.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSubmissions").value(3))
                .andExpect(jsonPath("$.approvedSubmissions").value(1))
                .andExpect(jsonPath("$.changesRequested").value(2))
                .andExpect(jsonPath("$.resubmissions").value(1))
                .andExpect(jsonPath("$.approvalRate").value(33.33));
    }

    @Test
    @DisplayName("Test 9, 10: AI evaluation averages calculation with rounding and ignoring null values")
    void testEmployeeAiEvaluationAverages() throws Exception {
        User manager = createTestUser("Manager AI", "mgr_ai_m8s2@example.com", Role.MANAGER);
        User employee = createTestUser("Employee AI", "emp_ai_m8s2@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Project AI", manager, LocalDate.of(2026, 9, 1), null);

        Task t1 = createTestTask("Task 1", project, employee, TaskStatus.IN_PROGRESS, null, LocalDateTime.of(2026, 9, 2, 10, 0));
        Task t2 = createTestTask("Task 2", project, employee, TaskStatus.IN_PROGRESS, null, LocalDateTime.of(2026, 9, 3, 10, 0));

        Submission s1 = createTestSubmission(t1, employee, SubmissionStatus.SUBMITTED, 1, LocalDateTime.of(2026, 9, 4, 10, 0));
        Submission s2 = createTestSubmission(t2, employee, SubmissionStatus.SUBMITTED, 1, LocalDateTime.of(2026, 9, 5, 10, 0));

        // Evaluation 1: 90.5, 85.0, 95.0
        // Evaluation 2: 80.0, 75.5, 85.0
        // Avg completion: (90.5 + 80.0) / 2 = 85.25
        // Avg quality: (85.0 + 75.5) / 2 = 80.25
        // Avg confidence: (95.0 + 85.0) / 2 = 90.0
        createTestEvaluation(s1, 90.5, 85.0, 95.0, LocalDateTime.of(2026, 9, 4, 11, 0));
        createTestEvaluation(s2, 80.0, 75.5, 85.0, LocalDateTime.of(2026, 9, 5, 11, 0));

        mockMvc.perform(get("/api/analytics/employees/" + employee.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageCompletion").value(85.25))
                .andExpect(jsonPath("$.averageQuality").value(80.25))
                .andExpect(jsonPath("$.averageConfidence").value(90.0));
    }

    // =========================================================================
    // PART 2: DATE FILTERING & PROJECT FILTERING
    // =========================================================================

    @Test
    @DisplayName("Test 12: Date range filtering correctly filters employee tasks and metrics")
    void testEmployeeDateRangeFiltering() throws Exception {
        User manager = createTestUser("Manager Filter", "mgr_filter_m8s2@example.com", Role.MANAGER);
        User employee = createTestUser("Employee Filter", "emp_filter_m8s2@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Project Filter", manager, LocalDate.of(2026, 8, 1), null);

        // August task
        createTestTask("August Task", project, employee, TaskStatus.COMPLETED, null, LocalDateTime.of(2026, 8, 15, 10, 0));
        // September tasks
        createTestTask("Sept Task 1", project, employee, TaskStatus.COMPLETED, null, LocalDateTime.of(2026, 9, 10, 10, 0));
        createTestTask("Sept Task 2", project, employee, TaskStatus.IN_PROGRESS, null, LocalDateTime.of(2026, 9, 20, 10, 0));

        // Filter by September: 2 tasks total, 1 completed, 50% completion rate
        mockMvc.perform(get("/api/analytics/employees/" + employee.getId() + "?startDate=2026-09-01&endDate=2026-09-30")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssignedTasks").value(2))
                .andExpect(jsonPath("$.completedTasks").value(1))
                .andExpect(jsonPath("$.completionRate").value(50.0))
                .andExpect(jsonPath("$.startDate").value("2026-09-01"))
                .andExpect(jsonPath("$.endDate").value("2026-09-30"));
    }

    @Test
    @DisplayName("Test 13: Project filter scopes employee analytics list to specified project")
    void testEmployeeListProjectFiltering() throws Exception {
        User manager = createTestUser("Manager ProjFilter", "mgr_proj_m8s2@example.com", Role.MANAGER);
        User emp1 = createTestUser("Employee P1", "emp_p1_m8s2@example.com", Role.EMPLOYEE);
        User emp2 = createTestUser("Employee P2", "emp_p2_m8s2@example.com", Role.EMPLOYEE);

        Project p1 = createTestProject("Project 1", manager, LocalDate.of(2026, 9, 1), null);
        Project p2 = createTestProject("Project 2", manager, LocalDate.of(2026, 9, 1), null);

        createTestTask("P1 Task", p1, emp1, TaskStatus.COMPLETED, null, LocalDateTime.of(2026, 9, 5, 10, 0));
        createTestTask("P2 Task", p2, emp2, TaskStatus.IN_PROGRESS, null, LocalDateTime.of(2026, 9, 5, 10, 0));

        // Filter by Project 1: only Employee 1 should appear
        mockMvc.perform(get("/api/analytics/employees?projectId=" + p1.getId())
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value(emp1.getId()))
                .andExpect(jsonPath("$[0].totalAssignedTasks").value(1));
    }

    @Test
    @DisplayName("Test 14: Multiple employees produce independent analytics in list view")
    void testMultipleEmployeesIndependentAnalytics() throws Exception {
        User manager = createTestUser("Manager Multi", "mgr_multi_m8s2@example.com", Role.MANAGER);
        User empA = createTestUser("Alice Dev", "alice_m8s2@example.com", Role.EMPLOYEE);
        User empB = createTestUser("Bob Dev", "bob_m8s2@example.com", Role.EMPLOYEE);

        Project project = createTestProject("Multi Dev Project", manager, LocalDate.of(2026, 9, 1), null);

        // Alice: 2 tasks, 2 completed (100%)
        createTestTask("Alice Task 1", project, empA, TaskStatus.COMPLETED, null, LocalDateTime.of(2026, 9, 5, 10, 0));
        createTestTask("Alice Task 2", project, empA, TaskStatus.COMPLETED, null, LocalDateTime.of(2026, 9, 6, 10, 0));

        // Bob: 2 tasks, 0 completed (0%)
        createTestTask("Bob Task 1", project, empB, TaskStatus.IN_PROGRESS, null, LocalDateTime.of(2026, 9, 5, 10, 0));
        createTestTask("Bob Task 2", project, empB, TaskStatus.ASSIGNED, null, LocalDateTime.of(2026, 9, 6, 10, 0));

        mockMvc.perform(get("/api/analytics/employees")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].employeeName").value("Alice Dev"))
                .andExpect(jsonPath("$[0].completionRate").value(100.0))
                .andExpect(jsonPath("$[1].employeeName").value("Bob Dev"))
                .andExpect(jsonPath("$[1].completionRate").value(0.0));
    }

    // =========================================================================
    // PART 3: SECURITY, IDOR & ROLE AUTHORIZATION
    // =========================================================================

    @Test
    @DisplayName("Test 15: ADMIN can access all employee analytics")
    void testAdminCanAccessAnyEmployeeAnalytics() throws Exception {
        User admin = createTestUser("Admin User", "admin_m8s2@example.com", Role.ADMIN);
        User manager = createTestUser("Manager AdminTest", "mgr_admintest_m8s2@example.com", Role.MANAGER);
        User employee = createTestUser("Employee AdminTest", "emp_admintest_m8s2@example.com", Role.EMPLOYEE);
        Project project = createTestProject("Admin Scoped Proj", manager, LocalDate.of(2026, 9, 1), null);

        createTestTask("Admin Task", project, employee, TaskStatus.COMPLETED, null, LocalDateTime.of(2026, 9, 5, 10, 0));

        mockMvc.perform(get("/api/analytics/employees/" + employee.getId())
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(employee.getId()))
                .andExpect(jsonPath("$.totalAssignedTasks").value(1));
    }

    @Test
    @DisplayName("Test 16 & 17: MANAGER can access employee in scope but is forbidden for employee outside scope")
    void testManagerScopeAccessAndDataIsolation() throws Exception {
        User managerA = createTestUser("Manager A", "mgra_scope_m8s2@example.com", Role.MANAGER);
        User managerB = createTestUser("Manager B", "mgrb_scope_m8s2@example.com", Role.MANAGER);
        User employeeA = createTestUser("Employee A", "empa_scope_m8s2@example.com", Role.EMPLOYEE);
        User employeeB = createTestUser("Employee B", "empb_scope_m8s2@example.com", Role.EMPLOYEE);

        Project projectA = createTestProject("Project A", managerA, LocalDate.of(2026, 9, 1), null);
        Project projectB = createTestProject("Project B", managerB, LocalDate.of(2026, 9, 1), null);

        createTestTask("Task A", projectA, employeeA, TaskStatus.IN_PROGRESS, null, LocalDateTime.of(2026, 9, 5, 10, 0));
        createTestTask("Task B", projectB, employeeB, TaskStatus.IN_PROGRESS, null, LocalDateTime.of(2026, 9, 5, 10, 0));

        // Manager A -> Employee A = 200 OK
        mockMvc.perform(get("/api/analytics/employees/" + employeeA.getId())
                        .cookie(createAuthCookie(managerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(employeeA.getId()));

        // Manager A -> Employee B = 403 Forbidden
        mockMvc.perform(get("/api/analytics/employees/" + employeeB.getId())
                        .cookie(createAuthCookie(managerA)))
                .andExpect(status().isForbidden());

        // Manager B -> Employee B = 200 OK
        mockMvc.perform(get("/api/analytics/employees/" + employeeB.getId())
                        .cookie(createAuthCookie(managerB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(employeeB.getId()));

        // Manager B -> Employee A = 403 Forbidden
        mockMvc.perform(get("/api/analytics/employees/" + employeeA.getId())
                        .cookie(createAuthCookie(managerB)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 18, 19, 22: EMPLOYEE can view own analytics but is forbidden from viewing other employees (IDOR protection)")
    void testEmployeeSelfAccessAndIdorProtection() throws Exception {
        User employee1 = createTestUser("Employee Self", "emp_self_m8s2@example.com", Role.EMPLOYEE);
        User employee2 = createTestUser("Employee Other", "emp_other_m8s2@example.com", Role.EMPLOYEE);

        // Employee 1 viewing Employee 1 = 200 OK
        mockMvc.perform(get("/api/analytics/employees/" + employee1.getId())
                        .cookie(createAuthCookie(employee1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(employee1.getId()));

        // Employee 1 attempting to view Employee 2 = 403 Forbidden (IDOR prevented)
        mockMvc.perform(get("/api/analytics/employees/" + employee2.getId())
                        .cookie(createAuthCookie(employee1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 20: Unauthenticated request to analytics returns Forbidden or Unauthorized")
    void testUnauthenticatedRequestReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/analytics/employees"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/analytics/employees/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 21: Employee attempting list endpoint /api/analytics/employees returns 403 Forbidden")
    void testEmployeeListEndpointForbidden() throws Exception {
        User employee = createTestUser("Emp ListTest", "emp_listtest_m8s2@example.com", Role.EMPLOYEE);

        mockMvc.perform(get("/api/analytics/employees")
                        .cookie(createAuthCookie(employee)))
                .andExpect(status().isForbidden());
    }
}
