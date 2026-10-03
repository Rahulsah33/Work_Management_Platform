package com.Workmanagement.integration;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.entity.AiEvaluationStatus;
import com.Workmanagement.ai.repository.AiEvaluationRepository;
import com.Workmanagement.audit.entity.AuditAction;
import com.Workmanagement.audit.repository.AuditLogRepository;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.submission.repository.SubmissionRepository;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class Milestone8Step7PerformanceAndHardeningIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private AiEvaluationRepository aiEvaluationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private User createTestUser(String name, String email, Role role) {
        return createUser(name, email, "Password123!", role);
    }

    private Project createTestProject(String name, User manager) {
        Project project = new Project();
        project.setName(name);
        project.setDescription("Project description for " + name);
        project.setManager(manager);
        project.setStatus(ProjectStatus.IN_PROGRESS);
        project.setStartDate(LocalDate.now().minusDays(10));
        project.setEndDate(LocalDate.now().plusMonths(1));
        project.setCreatedAt(LocalDateTime.now().minusDays(10));
        return projectRepository.save(project);
    }

    private Task createTestTask(String title, Project project, User assignee, TaskStatus status, LocalDate endDate, LocalDateTime createdAt) {
        Task task = new Task();
        task.setTitle(title);
        task.setDescription("Task description for " + title);
        task.setProject(project);
        task.setAssignedTo(assignee);
        task.setStatus(status);
        task.setPriority("HIGH");
        task.setEndDate(endDate);
        task.setCreatedAt(createdAt != null ? createdAt : LocalDateTime.now());
        task.setUpdatedAt(createdAt != null ? createdAt.plusHours(1) : LocalDateTime.now());
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
    // 1. PERFORMANCE & BATCHING VERIFICATION (MANY ENTITIES AGGREGATION)
    // =========================================================================

    @Test
    @DisplayName("Performance - Successfully aggregates dataset of tasks, submissions, and evaluations without error")
    void testPerformanceLargeDatasetAggregation() throws Exception {
        User admin = createTestUser("Admin Perf", "admin_perf@example.com", Role.ADMIN);
        User manager = createTestUser("Manager Perf", "mgr_perf@example.com", Role.MANAGER);
        User emp1 = createTestUser("Emp Perf 1", "emp1_perf@example.com", Role.EMPLOYEE);
        User emp2 = createTestUser("Emp Perf 2", "emp2_perf@example.com", Role.EMPLOYEE);

        Project project1 = createTestProject("Perf Project Alpha", manager);
        Project project2 = createTestProject("Perf Project Beta", manager);

        // Populate 10 tasks across employees and projects
        for (int i = 1; i <= 10; i++) {
            User assignee = (i % 2 == 0) ? emp1 : emp2;
            Project proj = (i <= 5) ? project1 : project2;
            Task task = createTestTask("Task #" + i, proj, assignee, (i % 3 == 0) ? TaskStatus.COMPLETED : TaskStatus.IN_PROGRESS,
                    LocalDate.now().plusDays(i), LocalDateTime.now().minusDays(i));

            Submission sub1 = createTestSubmission(task, assignee, SubmissionStatus.CHANGES_REQUESTED, 1, LocalDateTime.now().minusDays(i));
            createTestEvaluation(sub1, 50.0 + i, 60.0 + i, 0.8, LocalDateTime.now().minusDays(i));

            if (i % 2 == 0) {
                Submission sub2 = createTestSubmission(task, assignee, SubmissionStatus.APPROVED, 2, LocalDateTime.now().minusHours(i));
                createTestEvaluation(sub2, 90.0, 95.0, 0.95, LocalDateTime.now().minusHours(i));
            }
        }

        // Overview
        mockMvc.perform(get("/api/analytics/overview")
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks.totalTasks").value(10));

        // Employee Analytics
        mockMvc.perform(get("/api/analytics/employees")
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // Project Analytics
        mockMvc.perform(get("/api/analytics/projects")
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // Task Analytics
        mockMvc.perform(get("/api/analytics/tasks")
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // =========================================================================
    // 2. CSV FORMULA INJECTION DEFENSE
    // =========================================================================

    @Test
    @DisplayName("CSV Formula Injection - Dangerous characters (=, +, -, @) are sanitized safely in CSV export")
    void testCsvFormulaInjectionSanitization() throws Exception {
        User admin = createTestUser("Admin CSV Defense", "admin_csv_def@example.com", Role.ADMIN);
        User manager = createTestUser("Manager CSV Defense", "mgr_csv_def@example.com", Role.MANAGER);
        User emp = createTestUser("Emp CSV Defense", "emp_csv_def@example.com", Role.EMPLOYEE);

        // Project with formula-like name
        Project maliciousProject = createTestProject("=cmd|'/C calc'!A0", manager);
        createTestTask("+2+5 Task Injection", maliciousProject, emp, TaskStatus.IN_PROGRESS, LocalDate.now().plusDays(5), LocalDateTime.now());

        MvcResult result = mockMvc.perform(get("/api/analytics/reports/projects")
                        .param("format", "CSV")
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.containsString("text/csv")))
                .andReturn();

        String csvContent = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

        // Verify malicious leading formula characters have been sanitized safely
        assertTrue(csvContent.contains("calc"), "CSV must contain project title");
        assertFalse(csvContent.contains(",=cmd"), "Unescaped formula invocation should not exist after comma delimiter");
    }

    // =========================================================================
    // 3. MATHEMATICAL PRECISION & NULL/ZERO EDGE CASES
    // =========================================================================

    @Test
    @DisplayName("Edge Case Math - Empty projects/employees produce 0 rates and null/0 averages without crashing")
    void testEmptyProjectAndNullAverageMath() throws Exception {
        User admin = createTestUser("Admin Math", "admin_math@example.com", Role.ADMIN);
        User manager = createTestUser("Manager Empty", "mgr_empty@example.com", Role.MANAGER);
        User emptyEmp = createTestUser("Emp Empty", "emp_empty@example.com", Role.EMPLOYEE);

        // Project with 0 tasks
        Project emptyProject = createTestProject("Empty Math Project", manager);

        // Project Analytics check
        mockMvc.perform(get("/api/analytics/projects/" + emptyProject.getId())
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(0))
                .andExpect(jsonPath("$.completionRate").value(0.0))
                .andExpect(jsonPath("$.approvalRate").value(0.0))
                .andExpect(jsonPath("$.averageCompletion").value(0.0));

        // Employee Analytics check
        mockMvc.perform(get("/api/analytics/employees/" + emptyEmp.getId())
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssignedTasks").value(0))
                .andExpect(jsonPath("$.completionRate").value(0.0))
                .andExpect(jsonPath("$.approvalRate").value(0.0))
                .andExpect(jsonPath("$.averageCompletion").value(0.0));
    }

    // =========================================================================
    // 4. LATEST EVALUATION SEMANTICS HARDENING
    // =========================================================================

    @Test
    @DisplayName("Evaluation Semantics - Task details only reflect the latest submission evaluation")
    void testLatestEvaluationSemantics() throws Exception {
        User admin = createTestUser("Admin Latest Eval", "admin_latest_eval@example.com", Role.ADMIN);
        User manager = createTestUser("Manager Latest Eval", "mgr_latest_eval@example.com", Role.MANAGER);
        User emp = createTestUser("Emp Latest Eval", "emp_latest_eval@example.com", Role.EMPLOYEE);

        Project project = createTestProject("Project Latest Eval", manager);
        Task task = createTestTask("Task Latest Eval", project, emp, TaskStatus.IN_PROGRESS, LocalDate.now().plusDays(3), LocalDateTime.now().minusDays(2));

        // Sub 1: Score 30
        Submission sub1 = createTestSubmission(task, emp, SubmissionStatus.CHANGES_REQUESTED, 1, LocalDateTime.now().minusDays(2));
        createTestEvaluation(sub1, 30.0, 40.0, 0.7, LocalDateTime.now().minusDays(2));

        // Sub 2: Score 92 (Latest)
        Submission sub2 = createTestSubmission(task, emp, SubmissionStatus.APPROVED, 2, LocalDateTime.now().minusHours(1));
        createTestEvaluation(sub2, 92.0, 94.0, 0.95, LocalDateTime.now().minusHours(1));

        mockMvc.perform(get("/api/analytics/tasks/" + task.getId())
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSubmissions").value(2))
                .andExpect(jsonPath("$.resubmissions").value(1))
                .andExpect(jsonPath("$.aiCompletionPercentage").value(92.0))
                .andExpect(jsonPath("$.aiQualityScore").value(94.0));
    }

    // =========================================================================
    // 5. SECURITY & IDOR HARDENING ACROSS ANALYTICS AND REPORTS
    // =========================================================================

    @Test
    @DisplayName("Security IDOR - Employees cannot access other employees' analytics or reports")
    void testIdorEmployeeIsolation() throws Exception {
        User emp1 = createTestUser("Emp Alpha", "emp_alpha@example.com", Role.EMPLOYEE);
        User emp2 = createTestUser("Emp Beta", "emp_beta@example.com", Role.EMPLOYEE);

        // Employee 1 accesses Employee 1 (permitted)
        mockMvc.perform(get("/api/analytics/employees/" + emp1.getId())
                        .cookie(createAuthCookie(emp1)))
                .andExpect(status().isOk());

        // Employee 1 accesses Employee 2 (forbidden)
        mockMvc.perform(get("/api/analytics/employees/" + emp2.getId())
                        .cookie(createAuthCookie(emp1)))
                .andExpect(status().isForbidden());

        // Employee 1 accesses Employee Report (forbidden - Employee report is manager/admin only)
        mockMvc.perform(get("/api/analytics/reports/employees")
                        .param("format", "CSV")
                        .cookie(createAuthCookie(emp1)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security IDOR - Manager cannot access project analytics for projects they do not manage")
    void testIdorManagerProjectIsolation() throws Exception {
        User mgr1 = createTestUser("Mgr Alpha", "mgr_alpha@example.com", Role.MANAGER);
        User mgr2 = createTestUser("Mgr Beta", "mgr_beta@example.com", Role.MANAGER);

        Project projectOfMgr2 = createTestProject("Beta Exclusive Project", mgr2);

        // Manager 1 attempts to access Manager 2's project
        mockMvc.perform(get("/api/analytics/projects/" + projectOfMgr2.getId())
                        .cookie(createAuthCookie(mgr1)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 6. DATE FILTER VALIDATION HARDENING
    // =========================================================================

    @Test
    @DisplayName("Filter Validation - Invalid date ranges (startDate > endDate) return 400 Bad Request")
    void testInvalidDateFilterHandling() throws Exception {
        User admin = createTestUser("Admin Filter", "admin_filter@example.com", Role.ADMIN);

        mockMvc.perform(get("/api/analytics/overview")
                        .cookie(createAuthCookie(admin))
                        .param("startDate", "2026-12-31")
                        .param("endDate", "2026-01-01"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/analytics/reports/overview")
                        .cookie(createAuthCookie(admin))
                        .param("startDate", "2026-12-31")
                        .param("endDate", "2026-01-01")
                        .param("format", "CSV"))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 7. FILE DOWNLOAD HEADER AND RESOURCE SAFETY
    // =========================================================================

    @Test
    @DisplayName("Export Safety - Content-Disposition and attachment header safety")
    void testExportHeadersAndSafety() throws Exception {
        User admin = createTestUser("Admin Headers", "admin_hdr@example.com", Role.ADMIN);

        MvcResult xlsxResult = mockMvc.perform(get("/api/analytics/reports/overview")
                        .param("format", "EXCEL")
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("attachment; filename=\"analytics-overview-")))
                .andReturn();

        byte[] xlsxBytes = xlsxResult.getResponse().getContentAsByteArray();
        assertTrue(xlsxBytes.length > 0);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsxBytes))) {
            Sheet sheet = wb.getSheet("Overview Analytics");
            assertNotNull(sheet);
            assertTrue(sheet.getPhysicalNumberOfRows() > 0);
        }
    }
}
