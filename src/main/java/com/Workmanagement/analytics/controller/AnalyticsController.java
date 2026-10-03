package com.Workmanagement.analytics.controller;

import com.Workmanagement.analytics.model.AnalyticsOverviewResponse;
import com.Workmanagement.analytics.model.EmployeePerformanceResponse;
import com.Workmanagement.analytics.model.ProjectPerformanceResponse;
import com.Workmanagement.analytics.model.TaskPerformanceResponse;
import com.Workmanagement.analytics.model.TaskProductivitySummaryResponse;
import com.Workmanagement.analytics.service.AnalyticsService;
import com.Workmanagement.task.entity.TaskStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

import com.Workmanagement.analytics.model.ReportFile;
import com.Workmanagement.analytics.model.ReportFormat;
import com.Workmanagement.analytics.service.AnalyticsReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@Tag(name = "Analytics", description = "Endpoints for performance analytics, project statistics, and employee performance aggregation")
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AnalyticsReportService analyticsReportService;

    public AnalyticsController(AnalyticsService analyticsService, AnalyticsReportService analyticsReportService) {
        this.analyticsService = analyticsService;
        this.analyticsReportService = analyticsReportService;
    }

    @Operation(
            summary = "Get analytics overview",
            description = "Retrieves high-level analytics for projects, tasks, deliverables, and AI evaluations. Supports optional startDate and endDate filtering. Restricted to Managers and Admins."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Analytics overview successfully computed"),
            @ApiResponse(responseCode = "400", description = "Bad Request if startDate is after endDate"),
            @ApiResponse(responseCode = "401", description = "Unauthorized if session is unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Forbidden if authenticated user is an Employee")
    })
    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<AnalyticsOverviewResponse> getOverview(
            @Parameter(description = "Optional start date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "Optional end date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        AnalyticsOverviewResponse response = analyticsService.getOverviewAnalytics(startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get employee performance analytics list",
            description = "Retrieves performance analytics for employees within the authenticated manager or admin's project scope. Supports optional date range and projectId filters."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Employee analytics list successfully retrieved"),
            @ApiResponse(responseCode = "400", description = "Bad Request if startDate is after endDate"),
            @ApiResponse(responseCode = "401", description = "Unauthorized if session is unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Forbidden if user is an Employee or lacks manager permissions")
    })
    @GetMapping("/employees")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<List<EmployeePerformanceResponse>> getEmployeesAnalytics(
            @Parameter(description = "Optional start date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "Optional end date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            @Parameter(description = "Optional project ID filter")
            @RequestParam(required = false) Long projectId
    ) {
        List<EmployeePerformanceResponse> response = analyticsService.getEmployeesAnalytics(startDate, endDate, projectId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get performance analytics for a single employee",
            description = "Retrieves detailed performance analytics for a specific employee. Employees may only access their own metrics. Managers may only access employees in their projects. Admins have global access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Employee analytics successfully retrieved"),
            @ApiResponse(responseCode = "400", description = "Bad Request if startDate is after endDate"),
            @ApiResponse(responseCode = "401", description = "Unauthorized if session is unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Forbidden if user lacks authorization for this employee"),
            @ApiResponse(responseCode = "404", description = "Not Found if employee does not exist")
    })
    @GetMapping("/employees/{employeeId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'MANAGER', 'ADMIN')")
    public ResponseEntity<EmployeePerformanceResponse> getEmployeeAnalyticsById(
            @Parameter(description = "Employee ID", required = true)
            @PathVariable Long employeeId,

            @Parameter(description = "Optional start date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "Optional end date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        EmployeePerformanceResponse response = analyticsService.getEmployeeAnalyticsById(employeeId, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get project performance analytics list",
            description = "Retrieves performance analytics for all projects accessible to the authenticated manager or admin. Supports optional startDate and endDate filters."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project analytics list successfully retrieved"),
            @ApiResponse(responseCode = "400", description = "Bad Request if startDate is after endDate"),
            @ApiResponse(responseCode = "401", description = "Unauthorized if session is unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Forbidden if user is an Employee or lacks manager permissions")
    })
    @GetMapping("/projects")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<List<ProjectPerformanceResponse>> getProjectsAnalytics(
            @Parameter(description = "Optional start date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "Optional end date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        List<ProjectPerformanceResponse> response = analyticsService.getProjectsAnalytics(startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get performance analytics for a single project",
            description = "Retrieves detailed performance analytics for a specific project. Managers may only access projects they manage. Employees may only access projects they are assigned to. Admins have global access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project analytics successfully retrieved"),
            @ApiResponse(responseCode = "400", description = "Bad Request if startDate is after endDate"),
            @ApiResponse(responseCode = "401", description = "Unauthorized if session is unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Forbidden if user lacks authorization for this project"),
            @ApiResponse(responseCode = "404", description = "Not Found if project does not exist")
    })
    @GetMapping("/projects/{projectId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ProjectPerformanceResponse> getProjectAnalyticsById(
            @Parameter(description = "Project ID", required = true)
            @PathVariable Long projectId,

            @Parameter(description = "Optional start date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "Optional end date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        ProjectPerformanceResponse response = analyticsService.getProjectAnalyticsById(projectId, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get task productivity and execution summary",
            description = "Retrieves aggregated task productivity and AI metrics across scoped tasks. Supports date range, project, employee, and status filters."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task productivity summary successfully computed"),
            @ApiResponse(responseCode = "400", description = "Bad Request if startDate is after endDate"),
            @ApiResponse(responseCode = "401", description = "Unauthorized if session is unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Forbidden if user lacks authorization for requested scope")
    })
    @GetMapping("/tasks/summary")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'MANAGER', 'ADMIN')")
    public ResponseEntity<TaskProductivitySummaryResponse> getTaskProductivitySummary(
            @Parameter(description = "Optional start date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "Optional end date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            @Parameter(description = "Optional project ID filter")
            @RequestParam(required = false) Long projectId,

            @Parameter(description = "Optional employee ID filter")
            @RequestParam(required = false) Long employeeId,

            @Parameter(description = "Optional task status filter")
            @RequestParam(required = false) TaskStatus status
    ) {
        TaskProductivitySummaryResponse response = analyticsService.getTaskProductivitySummary(startDate, endDate, projectId, employeeId, status);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get task performance analytics list",
            description = "Retrieves performance analytics for individual tasks within the authenticated user's authorized scope. Supports date range, project, employee, and status filters."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task analytics list successfully retrieved"),
            @ApiResponse(responseCode = "400", description = "Bad Request if startDate is after endDate"),
            @ApiResponse(responseCode = "401", description = "Unauthorized if session is unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Forbidden if user lacks authorization for requested scope")
    })
    @GetMapping("/tasks")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'MANAGER', 'ADMIN')")
    public ResponseEntity<List<TaskPerformanceResponse>> getTasksAnalytics(
            @Parameter(description = "Optional start date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "Optional end date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            @Parameter(description = "Optional project ID filter")
            @RequestParam(required = false) Long projectId,

            @Parameter(description = "Optional employee ID filter")
            @RequestParam(required = false) Long employeeId,

            @Parameter(description = "Optional task status filter")
            @RequestParam(required = false) TaskStatus status
    ) {
        List<TaskPerformanceResponse> response = analyticsService.getTasksAnalytics(startDate, endDate, projectId, employeeId, status);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get performance analytics for a single task",
            description = "Retrieves detailed performance analytics for a specific task. Managers may access tasks in projects they manage. Employees may only access their assigned tasks. Admins have global access."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task analytics successfully retrieved"),
            @ApiResponse(responseCode = "400", description = "Bad Request if startDate is after endDate"),
            @ApiResponse(responseCode = "401", description = "Unauthorized if session is unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Forbidden if user lacks authorization for this task"),
            @ApiResponse(responseCode = "404", description = "Not Found if task does not exist")
    })
    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'MANAGER', 'ADMIN')")
    public ResponseEntity<TaskPerformanceResponse> getTaskAnalyticsById(
            @Parameter(description = "Task ID", required = true)
            @PathVariable Long taskId,

            @Parameter(description = "Optional start date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "Optional end date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        TaskPerformanceResponse response = analyticsService.getTaskAnalyticsById(taskId, startDate, endDate);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // REPORT EXPORT ENDPOINTS (Milestone 8 Step 6)
    // =========================================================================

    @Operation(
            summary = "Export Overview Analytics Report",
            description = "Generates and downloads an Overview Analytics Report in CSV, Excel, or PDF format. Restricted to Managers and Admins."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report successfully generated"),
            @ApiResponse(responseCode = "400", description = "Bad Request if startDate is after endDate or format is invalid"),
            @ApiResponse(responseCode = "401", description = "Unauthorized if session is unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Forbidden if user is an Employee")
    })
    @GetMapping("/reports/overview")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<byte[]> exportOverviewReport(
            @Parameter(description = "Report format: CSV, EXCEL, or PDF", example = "CSV")
            @RequestParam(required = false, defaultValue = "CSV") String format,

            @Parameter(description = "Optional start date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "Optional end date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        ReportFormat reportFormat = ReportFormat.fromString(format);
        ReportFile file = analyticsReportService.generateOverviewReport(startDate, endDate, reportFormat);
        return buildFileResponse(file);
    }

    @Operation(
            summary = "Export Employee Performance Analytics Report",
            description = "Generates and downloads an Employee Performance Report in CSV, Excel, or PDF format. Restricted to Managers and Admins."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report successfully generated"),
            @ApiResponse(responseCode = "400", description = "Bad Request if startDate is after endDate or format is invalid"),
            @ApiResponse(responseCode = "401", description = "Unauthorized if session is unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Forbidden if user is an Employee")
    })
    @GetMapping("/reports/employees")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<byte[]> exportEmployeesReport(
            @Parameter(description = "Report format: CSV, EXCEL, or PDF", example = "CSV")
            @RequestParam(required = false, defaultValue = "CSV") String format,

            @Parameter(description = "Optional start date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "Optional end date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            @Parameter(description = "Optional project ID filter")
            @RequestParam(required = false) Long projectId
    ) {
        ReportFormat reportFormat = ReportFormat.fromString(format);
        ReportFile file = analyticsReportService.generateEmployeesReport(startDate, endDate, projectId, reportFormat);
        return buildFileResponse(file);
    }

    @Operation(
            summary = "Export Project Analytics Report",
            description = "Generates and downloads a Project Analytics Report in CSV, Excel, or PDF format. Restricted to Managers and Admins."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report successfully generated"),
            @ApiResponse(responseCode = "400", description = "Bad Request if startDate is after endDate or format is invalid"),
            @ApiResponse(responseCode = "401", description = "Unauthorized if session is unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Forbidden if user is an Employee")
    })
    @GetMapping("/reports/projects")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<byte[]> exportProjectsReport(
            @Parameter(description = "Report format: CSV, EXCEL, or PDF", example = "CSV")
            @RequestParam(required = false, defaultValue = "CSV") String format,

            @Parameter(description = "Optional start date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "Optional end date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        ReportFormat reportFormat = ReportFormat.fromString(format);
        ReportFile file = analyticsReportService.generateProjectsReport(startDate, endDate, reportFormat);
        return buildFileResponse(file);
    }

    @Operation(
            summary = "Export Task Analytics Report",
            description = "Generates and downloads a Task Analytics Report in CSV, Excel, or PDF format for the authenticated user's authorized scope."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report successfully generated"),
            @ApiResponse(responseCode = "400", description = "Bad Request if startDate is after endDate or format is invalid"),
            @ApiResponse(responseCode = "401", description = "Unauthorized if session is unauthenticated"),
            @ApiResponse(responseCode = "403", description = "Forbidden if user lacks authorization for requested scope")
    })
    @GetMapping("/reports/tasks")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'MANAGER', 'ADMIN')")
    public ResponseEntity<byte[]> exportTasksReport(
            @Parameter(description = "Report format: CSV, EXCEL, or PDF", example = "CSV")
            @RequestParam(required = false, defaultValue = "CSV") String format,

            @Parameter(description = "Optional start date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "Optional end date filter (ISO format: YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            @Parameter(description = "Optional project ID filter")
            @RequestParam(required = false) Long projectId,

            @Parameter(description = "Optional employee ID filter")
            @RequestParam(required = false) Long employeeId,

            @Parameter(description = "Optional task status filter")
            @RequestParam(required = false) TaskStatus status
    ) {
        ReportFormat reportFormat = ReportFormat.fromString(format);
        ReportFile file = analyticsReportService.generateTasksReport(startDate, endDate, projectId, employeeId, status, reportFormat);
        return buildFileResponse(file);
    }

    private ResponseEntity<byte[]> buildFileResponse(ReportFile file) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.filename()).build());
        headers.setContentLength(file.content().length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(file.content());
    }
}
