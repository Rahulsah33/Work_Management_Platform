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

public class Milestone8Step6ReportsExportIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private AiEvaluationRepository aiEvaluationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

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
    // PART 1: OVERVIEW REPORT EXPORT
    // =========================================================================

    @Test
    @DisplayName("Test 1: Overview report CSV export returns valid CSV content and headers")
    void testOverviewReportCsvExport() throws Exception {
        User admin = createTestUser("Admin User", "admin_rep_csv@example.com", Role.ADMIN);
        User manager = createTestUser("Manager Rep", "mgr_rep_csv@example.com", Role.MANAGER);
        User employee = createTestUser("Employee Rep", "emp_rep_csv@example.com", Role.EMPLOYEE);

        Project project = createTestProject("Alpha Report Project", manager, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        Task task = createTestTask("Task 1", project, employee, TaskStatus.COMPLETED, LocalDate.of(2026, 9, 20), LocalDateTime.of(2026, 9, 5, 10, 0));
        Submission sub = createTestSubmission(task, employee, SubmissionStatus.APPROVED, 1, LocalDateTime.of(2026, 9, 18, 14, 0));
        createTestEvaluation(sub, 90.0, 85.0, 95.0, LocalDateTime.of(2026, 9, 18, 14, 30));

        MvcResult result = mockMvc.perform(get("/api/analytics/reports/overview")
                        .param("format", "CSV")
                        .param("startDate", "2026-09-01")
                        .param("endDate", "2026-09-30")
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("attachment; filename=\"analytics-overview-")))
                .andReturn();

        String csvContent = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(csvContent.contains("Category,Metric,Value"));
        assertTrue(csvContent.contains("Work Management Analytics Overview"));
        assertTrue(csvContent.contains("Total Tasks"));
        assertTrue(csvContent.contains("Approval Rate"));
    }

    @Test
    @DisplayName("Test 2: Overview report Excel export returns valid readable XLSX workbook")
    void testOverviewReportExcelExport() throws Exception {
        User admin = createTestUser("Admin XLSX", "admin_rep_xlsx@example.com", Role.ADMIN);
        User manager = createTestUser("Manager XLSX", "mgr_rep_xlsx@example.com", Role.MANAGER);
        Project project = createTestProject("Beta Project", manager, LocalDate.of(2026, 9, 1), null);

        MvcResult result = mockMvc.perform(get("/api/analytics/reports/overview")
                        .param("format", "EXCEL")
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 0);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("Overview Analytics");
            assertNotNull(sheet);
            assertEquals("Work Management Analytics Overview", sheet.getRow(0).getCell(0).getStringCellValue());
        }
    }

    @Test
    @DisplayName("Test 3: Overview report PDF export returns valid PDF binary stream")
    void testOverviewReportPdfExport() throws Exception {
        User manager = createTestUser("Manager PDF", "mgr_rep_pdf@example.com", Role.MANAGER);
        createTestProject("Gamma Project", manager, LocalDate.of(2026, 9, 1), null);

        MvcResult result = mockMvc.perform(get("/api/analytics/reports/overview")
                        .param("format", "PDF")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 0);
        // PDF Magic Bytes: %PDF-
        String magic = new String(bytes, 0, 5, StandardCharsets.US_ASCII);
        assertEquals("%PDF-", magic);
    }

    // =========================================================================
    // PART 2: EMPLOYEE PERFORMANCE REPORT EXPORT
    // =========================================================================

    @Test
    @DisplayName("Test 4: Employee performance report CSV export scoped to Manager's projects")
    void testEmployeeReportManagerScope() throws Exception {
        User manager1 = createTestUser("Manager 1", "mgr1_rep@example.com", Role.MANAGER);
        User manager2 = createTestUser("Manager 2", "mgr2_rep@example.com", Role.MANAGER);

        User emp1 = createTestUser("Emp One", "emp1_rep@example.com", Role.EMPLOYEE);
        User emp2 = createTestUser("Emp Two", "emp2_rep@example.com", Role.EMPLOYEE);

        Project proj1 = createTestProject("Project M1", manager1, LocalDate.of(2026, 9, 1), null);
        Project proj2 = createTestProject("Project M2", manager2, LocalDate.of(2026, 9, 1), null);

        createTestTask("Task M1", proj1, emp1, TaskStatus.COMPLETED, LocalDate.of(2026, 9, 25), LocalDateTime.now());
        createTestTask("Task M2", proj2, emp2, TaskStatus.IN_PROGRESS, LocalDate.of(2026, 9, 25), LocalDateTime.now());

        // Manager 1 exports employee report
        MvcResult result = mockMvc.perform(get("/api/analytics/reports/employees")
                        .param("format", "CSV")
                        .cookie(createAuthCookie(manager1)))
                .andExpect(status().isOk())
                .andReturn();

        String csv = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(csv.contains("emp1_rep@example.com"));
        assertFalse(csv.contains("emp2_rep@example.com"), "Manager 1 must NOT see employees from other managers' projects");
    }

    @Test
    @DisplayName("Test 5: Employee role is forbidden from exporting employee reports")
    void testEmployeeForbiddenFromEmployeeReport() throws Exception {
        User emp = createTestUser("Regular Employee", "emp_forbidden_rep@example.com", Role.EMPLOYEE);

        mockMvc.perform(get("/api/analytics/reports/employees")
                        .param("format", "CSV")
                        .cookie(createAuthCookie(emp)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // PART 3: PROJECT ANALYTICS REPORT EXPORT
    // =========================================================================

    @Test
    @DisplayName("Test 6: Project analytics Excel report contains valid project rows")
    void testProjectAnalyticsExcelExport() throws Exception {
        User manager = createTestUser("Project Mgr", "proj_mgr_xlsx@example.com", Role.MANAGER);
        Project proj1 = createTestProject("Mobile App Core", manager, LocalDate.of(2026, 9, 1), null);
        Project proj2 = createTestProject("Web Portal UI", manager, LocalDate.of(2026, 9, 1), null);

        MvcResult result = mockMvc.perform(get("/api/analytics/reports/projects")
                        .param("format", "EXCEL")
                        .cookie(createAuthCookie(manager)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("Project Analytics");
            assertNotNull(sheet);
            assertEquals("Project ID", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Project Name", sheet.getRow(0).getCell(1).getStringCellValue());
            assertTrue(sheet.getLastRowNum() >= 2);
        }
    }

    @Test
    @DisplayName("Test 7: Employee role is forbidden from exporting project list reports")
    void testEmployeeForbiddenFromProjectReport() throws Exception {
        User emp = createTestUser("Emp Proj Forbidden", "emp_proj_forb@example.com", Role.EMPLOYEE);

        mockMvc.perform(get("/api/analytics/reports/projects")
                        .param("format", "PDF")
                        .cookie(createAuthCookie(emp)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // PART 4: TASK ANALYTICS REPORT EXPORT
    // =========================================================================

    @Test
    @DisplayName("Test 8: Task report PDF export is accessible to Employee and scoped only to their assigned tasks")
    void testTaskReportEmployeeScope() throws Exception {
        User manager = createTestUser("Manager TaskRep", "mgr_task_rep@example.com", Role.MANAGER);
        User emp1 = createTestUser("Emp Assigned", "emp_assigned@example.com", Role.EMPLOYEE);
        User emp2 = createTestUser("Emp Other", "emp_other@example.com", Role.EMPLOYEE);

        Project project = createTestProject("Delta Task Project", manager, LocalDate.of(2026, 9, 1), null);
        createTestTask("Task For Emp 1", project, emp1, TaskStatus.IN_PROGRESS, LocalDate.of(2026, 9, 30), LocalDateTime.now());
        createTestTask("Task For Emp 2", project, emp2, TaskStatus.COMPLETED, LocalDate.of(2026, 9, 30), LocalDateTime.now());

        // Employee 1 downloads task report in CSV
        MvcResult result = mockMvc.perform(get("/api/analytics/reports/tasks")
                        .param("format", "CSV")
                        .cookie(createAuthCookie(emp1)))
                .andExpect(status().isOk())
                .andReturn();

        String csv = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(csv.contains("Task For Emp 1"));
        assertFalse(csv.contains("Task For Emp 2"), "Employee 1 must only see tasks assigned to them");
    }

    @Test
    @DisplayName("Test 9: Task report filtered by TaskStatus returns matching records")
    void testTaskReportStatusFilter() throws Exception {
        User admin = createTestUser("Admin Filter", "admin_filt_task@example.com", Role.ADMIN);
        User manager = createTestUser("Manager Filter", "mgr_filt_task@example.com", Role.MANAGER);
        User emp = createTestUser("Emp Filter", "emp_filt_task@example.com", Role.EMPLOYEE);

        Project project = createTestProject("Filter Project", manager, LocalDate.of(2026, 9, 1), null);
        createTestTask("Completed Task", project, emp, TaskStatus.COMPLETED, LocalDate.of(2026, 9, 30), LocalDateTime.now());
        createTestTask("Pending Task", project, emp, TaskStatus.ASSIGNED, LocalDate.of(2026, 9, 30), LocalDateTime.now());

        MvcResult result = mockMvc.perform(get("/api/analytics/reports/tasks")
                        .param("format", "CSV")
                        .param("status", "COMPLETED")
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isOk())
                .andReturn();

        String csv = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(csv.contains("Completed Task"));
        assertFalse(csv.contains("Pending Task"));
    }

    // =========================================================================
    // PART 5: VALIDATION, AUDIT, & SECURITY
    // =========================================================================

    @Test
    @DisplayName("Test 10: Date validation - startDate after endDate returns 400 Bad Request")
    void testDateRangeValidation() throws Exception {
        User admin = createTestUser("Admin DateVal", "admin_date_val@example.com", Role.ADMIN);

        mockMvc.perform(get("/api/analytics/reports/overview")
                        .param("format", "CSV")
                        .param("startDate", "2026-10-10")
                        .param("endDate", "2026-09-10")
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Test 11: Unauthenticated request returns 403 Forbidden")
    void testUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/analytics/reports/overview"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Test 12: Audit log is recorded upon successful report export")
    void testAuditLogRecordedOnExport() throws Exception {
        User admin = createTestUser("Admin Audit", "admin_audit_rep@example.com", Role.ADMIN);

        mockMvc.perform(get("/api/analytics/reports/overview")
                        .param("format", "PDF")
                        .cookie(createAuthCookie(admin)))
                .andExpect(status().isOk());

        boolean auditFound = auditLogRepository.findAll().stream()
                .anyMatch(log -> log.getAction() == AuditAction.ANALYTICS_REPORT_EXPORTED);
        assertTrue(auditFound, "ANALYTICS_REPORT_EXPORTED audit log should be recorded");
    }
}
