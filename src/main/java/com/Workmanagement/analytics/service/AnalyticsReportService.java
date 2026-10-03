package com.Workmanagement.analytics.service;

import com.Workmanagement.analytics.model.AnalyticsOverviewResponse;
import com.Workmanagement.analytics.model.EmployeePerformanceResponse;
import com.Workmanagement.analytics.model.ProjectPerformanceResponse;
import com.Workmanagement.analytics.model.ReportFile;
import com.Workmanagement.analytics.model.ReportFormat;
import com.Workmanagement.analytics.model.TaskPerformanceResponse;
import com.Workmanagement.audit.entity.AuditAction;
import com.Workmanagement.audit.service.AuditLogService;
import com.Workmanagement.task.entity.TaskStatus;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AnalyticsReportService {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AnalyticsService analyticsService;
    private final AuditLogService auditLogService;

    public AnalyticsReportService(AnalyticsService analyticsService, AuditLogService auditLogService) {
        this.analyticsService = analyticsService;
        this.auditLogService = auditLogService;
    }

    // =========================================================================
    // 1. OVERVIEW REPORT
    // =========================================================================

    public ReportFile generateOverviewReport(LocalDate startDate, LocalDate endDate, ReportFormat format) {
        AnalyticsOverviewResponse data = analyticsService.getOverviewAnalytics(startDate, endDate);

        auditLogService.createLog(
                AuditAction.ANALYTICS_REPORT_EXPORTED,
                "REPORT",
                null,
                "Exported Overview Analytics Report in format: " + format.name()
        );

        String dateSuffix = LocalDate.now().toString();
        String filenameBase = "analytics-overview-" + dateSuffix;

        return switch (format) {
            case CSV -> new ReportFile(buildOverviewCsv(data), filenameBase + ".csv", "text/csv; charset=UTF-8");
            case EXCEL -> new ReportFile(buildOverviewExcel(data), filenameBase + ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case PDF -> new ReportFile(buildOverviewPdf(data), filenameBase + ".pdf", "application/pdf");
        };
    }

    // =========================================================================
    // 2. EMPLOYEE PERFORMANCE REPORT
    // =========================================================================

    public ReportFile generateEmployeesReport(LocalDate startDate, LocalDate endDate, Long projectId, ReportFormat format) {
        List<EmployeePerformanceResponse> data = analyticsService.getEmployeesAnalytics(startDate, endDate, projectId);

        auditLogService.createLog(
                AuditAction.ANALYTICS_REPORT_EXPORTED,
                "REPORT",
                null,
                "Exported Employee Performance Analytics Report in format: " + format.name()
        );

        String dateSuffix = LocalDate.now().toString();
        String filenameBase = "employee-performance-" + dateSuffix;

        return switch (format) {
            case CSV -> new ReportFile(buildEmployeesCsv(data), filenameBase + ".csv", "text/csv; charset=UTF-8");
            case EXCEL -> new ReportFile(buildEmployeesExcel(data, startDate, endDate), filenameBase + ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case PDF -> new ReportFile(buildEmployeesPdf(data, startDate, endDate), filenameBase + ".pdf", "application/pdf");
        };
    }

    // =========================================================================
    // 3. PROJECT ANALYTICS REPORT
    // =========================================================================

    public ReportFile generateProjectsReport(LocalDate startDate, LocalDate endDate, ReportFormat format) {
        List<ProjectPerformanceResponse> data = analyticsService.getProjectsAnalytics(startDate, endDate);

        auditLogService.createLog(
                AuditAction.ANALYTICS_REPORT_EXPORTED,
                "REPORT",
                null,
                "Exported Project Analytics Report in format: " + format.name()
        );

        String dateSuffix = LocalDate.now().toString();
        String filenameBase = "project-analytics-" + dateSuffix;

        return switch (format) {
            case CSV -> new ReportFile(buildProjectsCsv(data), filenameBase + ".csv", "text/csv; charset=UTF-8");
            case EXCEL -> new ReportFile(buildProjectsExcel(data, startDate, endDate), filenameBase + ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case PDF -> new ReportFile(buildProjectsPdf(data, startDate, endDate), filenameBase + ".pdf", "application/pdf");
        };
    }

    // =========================================================================
    // 4. TASK ANALYTICS REPORT
    // =========================================================================

    public ReportFile generateTasksReport(
            LocalDate startDate,
            LocalDate endDate,
            Long projectId,
            Long employeeId,
            TaskStatus status,
            ReportFormat format
    ) {
        List<TaskPerformanceResponse> data = analyticsService.getTasksAnalytics(startDate, endDate, projectId, employeeId, status);

        auditLogService.createLog(
                AuditAction.ANALYTICS_REPORT_EXPORTED,
                "REPORT",
                null,
                "Exported Task Analytics Report in format: " + format.name()
        );

        String dateSuffix = LocalDate.now().toString();
        String filenameBase = "task-analytics-" + dateSuffix;

        return switch (format) {
            case CSV -> new ReportFile(buildTasksCsv(data), filenameBase + ".csv", "text/csv; charset=UTF-8");
            case EXCEL -> new ReportFile(buildTasksExcel(data, startDate, endDate), filenameBase + ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case PDF -> new ReportFile(buildTasksPdf(data, startDate, endDate), filenameBase + ".pdf", "application/pdf");
        };
    }

    // =========================================================================
    // CSV BUILDERS
    // =========================================================================

    private byte[] buildOverviewCsv(AnalyticsOverviewResponse data) {
        StringBuilder sb = new StringBuilder();
        sb.append("Category,Metric,Value\n");

        sb.append("Report Info,Report Title,Work Management Analytics Overview\n");
        sb.append("Report Info,Generated At,").append(escapeCsv(LocalDateTime.now().format(TIMESTAMP_FORMATTER))).append("\n");
        sb.append("Report Info,Start Date,").append(escapeCsv(data.startDate() != null ? data.startDate() : "All Time")).append("\n");
        sb.append("Report Info,End Date,").append(escapeCsv(data.endDate() != null ? data.endDate() : "All Time")).append("\n");

        if (data.projects() != null) {
            sb.append("Projects,Total Projects,").append(data.projects().totalProjects()).append("\n");
            sb.append("Projects,Active Projects,").append(data.projects().activeProjects()).append("\n");
            sb.append("Projects,Planned Projects,").append(data.projects().plannedProjects()).append("\n");
            sb.append("Projects,Completed Projects,").append(data.projects().completedProjects()).append("\n");
            sb.append("Projects,Archived Projects,").append(data.projects().archivedProjects()).append("\n");
        }

        if (data.tasks() != null) {
            sb.append("Tasks,Total Tasks,").append(data.tasks().totalTasks()).append("\n");
            sb.append("Tasks,Completed Tasks,").append(data.tasks().completedTasks()).append("\n");
            sb.append("Tasks,In Progress Tasks,").append(data.tasks().inProgressTasks()).append("\n");
            sb.append("Tasks,Pending Tasks,").append(data.tasks().pendingTasks()).append("\n");
            sb.append("Tasks,Submitted Tasks,").append(data.tasks().submittedTasks()).append("\n");
            sb.append("Tasks,Under Review Tasks,").append(data.tasks().underReviewTasks()).append("\n");
            sb.append("Tasks,Changes Requested Tasks,").append(data.tasks().changesRequestedTasks()).append("\n");
            sb.append("Tasks,Overdue Tasks,").append(data.tasks().overdueTasks()).append("\n");
            sb.append("Tasks,Completion Rate,").append(formatPercentage(data.completionRate())).append("\n");
        }

        if (data.submissions() != null) {
            sb.append("Submissions,Total Submissions,").append(data.submissions().totalSubmissions()).append("\n");
            sb.append("Submissions,Approved Submissions,").append(data.submissions().approvedSubmissions()).append("\n");
            sb.append("Submissions,Changes Requested,").append(data.submissions().changesRequestedSubmissions()).append("\n");
            sb.append("Submissions,Reviewed Submissions,").append(data.submissions().reviewedSubmissions()).append("\n");
            sb.append("Submissions,Approval Rate,").append(formatPercentage(data.approvalRate())).append("\n");
        }

        if (data.evaluations() != null) {
            sb.append("AI Evaluations,Total Evaluations,").append(data.evaluations().totalEvaluations()).append("\n");
            sb.append("AI Evaluations,Average AI Completion,").append(formatPercentage(data.evaluations().averageCompletionPercentage())).append("\n");
            sb.append("AI Evaluations,Average AI Quality,").append(formatPercentage(data.evaluations().averageQualityScore())).append("\n");
            sb.append("AI Evaluations,Average AI Confidence,").append(formatPercentage(data.evaluations().averageConfidenceScore())).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] buildEmployeesCsv(List<EmployeePerformanceResponse> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("Employee ID,Employee Name,Employee Email,Assigned Tasks,Completed Tasks,In Progress Tasks,Pending Tasks,Overdue Tasks,Completion Rate,Total Submissions,Approved Submissions,Changes Requested,Resubmissions,Approval Rate,Avg AI Completion,Avg AI Quality,Avg AI Confidence\n");

        for (EmployeePerformanceResponse row : data) {
            sb.append(escapeCsv(row.employeeId())).append(",")
                    .append(escapeCsv(row.employeeName())).append(",")
                    .append(escapeCsv(row.employeeEmail())).append(",")
                    .append(row.totalAssignedTasks()).append(",")
                    .append(row.completedTasks()).append(",")
                    .append(row.inProgressTasks()).append(",")
                    .append(row.pendingTasks()).append(",")
                    .append(row.overdueTasks()).append(",")
                    .append(formatPercentage(row.completionRate())).append(",")
                    .append(row.totalSubmissions()).append(",")
                    .append(row.approvedSubmissions()).append(",")
                    .append(row.changesRequested()).append(",")
                    .append(row.resubmissions()).append(",")
                    .append(formatPercentage(row.approvalRate())).append(",")
                    .append(formatPercentage(row.averageCompletion())).append(",")
                    .append(formatPercentage(row.averageQuality())).append(",")
                    .append(formatPercentage(row.averageConfidence())).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] buildProjectsCsv(List<ProjectPerformanceResponse> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("Project ID,Project Name,Total Tasks,Completed Tasks,In Progress Tasks,Pending Tasks,Overdue Tasks,Completion Rate,Total Submissions,Approved Submissions,Changes Requested,Resubmissions,Approval Rate,Avg AI Completion,Avg AI Quality,Avg AI Confidence,Employee Count\n");

        for (ProjectPerformanceResponse row : data) {
            sb.append(escapeCsv(row.projectId())).append(",")
                    .append(escapeCsv(row.projectName())).append(",")
                    .append(row.totalTasks()).append(",")
                    .append(row.completedTasks()).append(",")
                    .append(row.inProgressTasks()).append(",")
                    .append(row.pendingTasks()).append(",")
                    .append(row.overdueTasks()).append(",")
                    .append(formatPercentage(row.completionRate())).append(",")
                    .append(row.totalSubmissions()).append(",")
                    .append(row.approvedSubmissions()).append(",")
                    .append(row.changesRequested()).append(",")
                    .append(row.resubmissions()).append(",")
                    .append(formatPercentage(row.approvalRate())).append(",")
                    .append(formatPercentage(row.averageCompletion())).append(",")
                    .append(formatPercentage(row.averageQuality())).append(",")
                    .append(formatPercentage(row.averageConfidence())).append(",")
                    .append(row.employeeCount()).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] buildTasksCsv(List<TaskPerformanceResponse> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("Task ID,Task Title,Project Name,Assigned Employee,Task Status,Due Date,Overdue,Total Submissions,Approved Submissions,Changes Requested,Resubmissions,Approval Rate,AI Completion,AI Quality,AI Confidence\n");

        for (TaskPerformanceResponse row : data) {
            sb.append(escapeCsv(row.taskId())).append(",")
                    .append(escapeCsv(row.taskTitle())).append(",")
                    .append(escapeCsv(row.projectName())).append(",")
                    .append(escapeCsv(row.employeeName())).append(",")
                    .append(escapeCsv(row.taskStatus() != null ? row.taskStatus().name() : "—")).append(",")
                    .append(escapeCsv(row.dueDate() != null ? row.dueDate().toString() : "—")).append(",")
                    .append(row.isOverdue() ? "Yes" : "No").append(",")
                    .append(row.totalSubmissions()).append(",")
                    .append(row.approvedSubmissions()).append(",")
                    .append(row.changesRequested()).append(",")
                    .append(row.resubmissions()).append(",")
                    .append(formatPercentage(row.approvalRate())).append(",")
                    .append(formatPercentage(row.aiCompletionPercentage())).append(",")
                    .append(formatPercentage(row.aiQualityScore())).append(",")
                    .append(formatPercentage(row.aiConfidenceScore())).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // =========================================================================
    // EXCEL BUILDERS (Apache POI)
    // =========================================================================

    private byte[] buildOverviewExcel(AnalyticsOverviewResponse data) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Overview Analytics");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle subHeaderStyle = createSubHeaderStyle(workbook);
            CellStyle cellStyle = createDataStyle(workbook);

            int rowIdx = 0;

            // Title
            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Work Management Analytics Overview");
            titleCell.setCellStyle(headerStyle);

            sheet.createRow(rowIdx++); // spacer

            // Metadata
            addExcelRow(sheet, rowIdx++, cellStyle, "Generated At", LocalDateTime.now().format(TIMESTAMP_FORMATTER));
            addExcelRow(sheet, rowIdx++, cellStyle, "Start Date", data.startDate() != null ? data.startDate() : "All Time");
            addExcelRow(sheet, rowIdx++, cellStyle, "End Date", data.endDate() != null ? data.endDate() : "All Time");
            sheet.createRow(rowIdx++); // spacer

            // Projects Section
            if (data.projects() != null) {
                Row pHeader = sheet.createRow(rowIdx++);
                Cell pCell = pHeader.createCell(0);
                pCell.setCellValue("Project Metrics");
                pCell.setCellStyle(subHeaderStyle);

                addExcelRow(sheet, rowIdx++, cellStyle, "Total Projects", data.projects().totalProjects());
                addExcelRow(sheet, rowIdx++, cellStyle, "Active Projects", data.projects().activeProjects());
                addExcelRow(sheet, rowIdx++, cellStyle, "Planned Projects", data.projects().plannedProjects());
                addExcelRow(sheet, rowIdx++, cellStyle, "Completed Projects", data.projects().completedProjects());
                addExcelRow(sheet, rowIdx++, cellStyle, "Archived Projects", data.projects().archivedProjects());
                sheet.createRow(rowIdx++);
            }

            // Tasks Section
            if (data.tasks() != null) {
                Row tHeader = sheet.createRow(rowIdx++);
                Cell tCell = tHeader.createCell(0);
                tCell.setCellValue("Task Metrics");
                tCell.setCellStyle(subHeaderStyle);

                addExcelRow(sheet, rowIdx++, cellStyle, "Total Tasks", data.tasks().totalTasks());
                addExcelRow(sheet, rowIdx++, cellStyle, "Completed Tasks", data.tasks().completedTasks());
                addExcelRow(sheet, rowIdx++, cellStyle, "In Progress Tasks", data.tasks().inProgressTasks());
                addExcelRow(sheet, rowIdx++, cellStyle, "Pending Tasks", data.tasks().pendingTasks());
                addExcelRow(sheet, rowIdx++, cellStyle, "Submitted Tasks", data.tasks().submittedTasks());
                addExcelRow(sheet, rowIdx++, cellStyle, "Under Review Tasks", data.tasks().underReviewTasks());
                addExcelRow(sheet, rowIdx++, cellStyle, "Changes Requested Tasks", data.tasks().changesRequestedTasks());
                addExcelRow(sheet, rowIdx++, cellStyle, "Overdue Tasks", data.tasks().overdueTasks());
                addExcelRow(sheet, rowIdx++, cellStyle, "Task Completion Rate", formatPercentage(data.completionRate()));
                sheet.createRow(rowIdx++);
            }

            // Submissions Section
            if (data.submissions() != null) {
                Row sHeader = sheet.createRow(rowIdx++);
                Cell sCell = sHeader.createCell(0);
                sCell.setCellValue("Submission Metrics");
                sCell.setCellStyle(subHeaderStyle);

                addExcelRow(sheet, rowIdx++, cellStyle, "Total Submissions", data.submissions().totalSubmissions());
                addExcelRow(sheet, rowIdx++, cellStyle, "Approved Submissions", data.submissions().approvedSubmissions());
                addExcelRow(sheet, rowIdx++, cellStyle, "Changes Requested", data.submissions().changesRequestedSubmissions());
                addExcelRow(sheet, rowIdx++, cellStyle, "Reviewed Submissions", data.submissions().reviewedSubmissions());
                addExcelRow(sheet, rowIdx++, cellStyle, "Submission Approval Rate", formatPercentage(data.approvalRate()));
                sheet.createRow(rowIdx++);
            }

            // AI Evaluations Section
            if (data.evaluations() != null) {
                Row eHeader = sheet.createRow(rowIdx++);
                Cell eCell = eHeader.createCell(0);
                eCell.setCellValue("AI Evaluation Metrics");
                eCell.setCellStyle(subHeaderStyle);

                addExcelRow(sheet, rowIdx++, cellStyle, "Total Evaluations", data.evaluations().totalEvaluations());
                addExcelRow(sheet, rowIdx++, cellStyle, "Average AI Completion", formatPercentage(data.evaluations().averageCompletionPercentage()));
                addExcelRow(sheet, rowIdx++, cellStyle, "Average AI Quality", formatPercentage(data.evaluations().averageQualityScore()));
                addExcelRow(sheet, rowIdx++, cellStyle, "Average AI Confidence", formatPercentage(data.evaluations().averageConfidenceScore()));
            }

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel overview report: " + e.getMessage(), e);
        }
    }

    private byte[] buildEmployeesExcel(List<EmployeePerformanceResponse> data, LocalDate startDate, LocalDate endDate) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Employee Performance");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            String[] columns = {
                    "Employee ID", "Employee Name", "Employee Email", "Assigned Tasks", "Completed Tasks",
                    "In Progress", "Pending", "Overdue", "Completion Rate", "Submissions", "Approved",
                    "Changes Requested", "Resubmissions", "Approval Rate", "Avg AI Completion", "Avg AI Quality", "Avg AI Confidence"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (EmployeePerformanceResponse row : data) {
                Row dataRow = sheet.createRow(rowIdx++);
                int c = 0;
                setExcelCell(dataRow, c++, row.employeeId(), dataStyle);
                setExcelCell(dataRow, c++, row.employeeName(), dataStyle);
                setExcelCell(dataRow, c++, row.employeeEmail(), dataStyle);
                setExcelCell(dataRow, c++, row.totalAssignedTasks(), dataStyle);
                setExcelCell(dataRow, c++, row.completedTasks(), dataStyle);
                setExcelCell(dataRow, c++, row.inProgressTasks(), dataStyle);
                setExcelCell(dataRow, c++, row.pendingTasks(), dataStyle);
                setExcelCell(dataRow, c++, row.overdueTasks(), dataStyle);
                setExcelCell(dataRow, c++, formatPercentage(row.completionRate()), dataStyle);
                setExcelCell(dataRow, c++, row.totalSubmissions(), dataStyle);
                setExcelCell(dataRow, c++, row.approvedSubmissions(), dataStyle);
                setExcelCell(dataRow, c++, row.changesRequested(), dataStyle);
                setExcelCell(dataRow, c++, row.resubmissions(), dataStyle);
                setExcelCell(dataRow, c++, formatPercentage(row.approvalRate()), dataStyle);
                setExcelCell(dataRow, c++, formatPercentage(row.averageCompletion()), dataStyle);
                setExcelCell(dataRow, c++, formatPercentage(row.averageQuality()), dataStyle);
                setExcelCell(dataRow, c++, formatPercentage(row.averageConfidence()), dataStyle);
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel employee performance report: " + e.getMessage(), e);
        }
    }

    private byte[] buildProjectsExcel(List<ProjectPerformanceResponse> data, LocalDate startDate, LocalDate endDate) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Project Analytics");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            String[] columns = {
                    "Project ID", "Project Name", "Total Tasks", "Completed Tasks", "In Progress",
                    "Pending", "Overdue", "Completion Rate", "Submissions", "Approved", "Changes Requested",
                    "Resubmissions", "Approval Rate", "Avg AI Completion", "Avg AI Quality", "Avg AI Confidence", "Employee Count"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (ProjectPerformanceResponse row : data) {
                Row dataRow = sheet.createRow(rowIdx++);
                int c = 0;
                setExcelCell(dataRow, c++, row.projectId(), dataStyle);
                setExcelCell(dataRow, c++, row.projectName(), dataStyle);
                setExcelCell(dataRow, c++, row.totalTasks(), dataStyle);
                setExcelCell(dataRow, c++, row.completedTasks(), dataStyle);
                setExcelCell(dataRow, c++, row.inProgressTasks(), dataStyle);
                setExcelCell(dataRow, c++, row.pendingTasks(), dataStyle);
                setExcelCell(dataRow, c++, row.overdueTasks(), dataStyle);
                setExcelCell(dataRow, c++, formatPercentage(row.completionRate()), dataStyle);
                setExcelCell(dataRow, c++, row.totalSubmissions(), dataStyle);
                setExcelCell(dataRow, c++, row.approvedSubmissions(), dataStyle);
                setExcelCell(dataRow, c++, row.changesRequested(), dataStyle);
                setExcelCell(dataRow, c++, row.resubmissions(), dataStyle);
                setExcelCell(dataRow, c++, formatPercentage(row.approvalRate()), dataStyle);
                setExcelCell(dataRow, c++, formatPercentage(row.averageCompletion()), dataStyle);
                setExcelCell(dataRow, c++, formatPercentage(row.averageQuality()), dataStyle);
                setExcelCell(dataRow, c++, formatPercentage(row.averageConfidence()), dataStyle);
                setExcelCell(dataRow, c++, row.employeeCount(), dataStyle);
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel project analytics report: " + e.getMessage(), e);
        }
    }

    private byte[] buildTasksExcel(List<TaskPerformanceResponse> data, LocalDate startDate, LocalDate endDate) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Task Analytics");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            String[] columns = {
                    "Task ID", "Task Title", "Project Name", "Assigned Employee", "Status",
                    "Due Date", "Overdue", "Submissions", "Approved", "Changes Requested",
                    "Resubmissions", "Approval Rate", "AI Completion", "AI Quality", "AI Confidence"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (TaskPerformanceResponse row : data) {
                Row dataRow = sheet.createRow(rowIdx++);
                int c = 0;
                setExcelCell(dataRow, c++, row.taskId(), dataStyle);
                setExcelCell(dataRow, c++, row.taskTitle(), dataStyle);
                setExcelCell(dataRow, c++, row.projectName(), dataStyle);
                setExcelCell(dataRow, c++, row.employeeName(), dataStyle);
                setExcelCell(dataRow, c++, row.taskStatus() != null ? row.taskStatus().name() : "—", dataStyle);
                setExcelCell(dataRow, c++, row.dueDate() != null ? row.dueDate().toString() : "—", dataStyle);
                setExcelCell(dataRow, c++, row.isOverdue() ? "Yes" : "No", dataStyle);
                setExcelCell(dataRow, c++, row.totalSubmissions(), dataStyle);
                setExcelCell(dataRow, c++, row.approvedSubmissions(), dataStyle);
                setExcelCell(dataRow, c++, row.changesRequested(), dataStyle);
                setExcelCell(dataRow, c++, row.resubmissions(), dataStyle);
                setExcelCell(dataRow, c++, formatPercentage(row.approvalRate()), dataStyle);
                setExcelCell(dataRow, c++, formatPercentage(row.aiCompletionPercentage()), dataStyle);
                setExcelCell(dataRow, c++, formatPercentage(row.aiQualityScore()), dataStyle);
                setExcelCell(dataRow, c++, formatPercentage(row.aiConfidenceScore()), dataStyle);
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Excel task analytics report: " + e.getMessage(), e);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.INDIGO.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createSubHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private void addExcelRow(Sheet sheet, int rowIdx, CellStyle style, String metric, Object value) {
        Row row = sheet.createRow(rowIdx);
        Cell cell0 = row.createCell(0);
        cell0.setCellValue(metric);
        cell0.setCellStyle(style);

        Cell cell1 = row.createCell(1);
        if (value instanceof Number n) {
            cell1.setCellValue(n.doubleValue());
        } else {
            cell1.setCellValue(value != null ? value.toString() : "—");
        }
        cell1.setCellStyle(style);
    }

    private void setExcelCell(Row row, int col, Object value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else {
            cell.setCellValue(value != null ? value.toString() : "—");
        }
        cell.setCellStyle(style);
    }

    // =========================================================================
    // PDF BUILDERS (OpenPDF)
    // =========================================================================

    private byte[] buildOverviewPdf(AnalyticsOverviewResponse data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            // Title
            com.lowagie.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(30, 41, 59));
            Paragraph title = new Paragraph("Work Management Analytics Overview", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(12);
            document.add(title);

            // Metadata box
            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setSpacingAfter(15);
            addPdfMetaRow(metaTable, "Generated At", LocalDateTime.now().format(TIMESTAMP_FORMATTER));
            addPdfMetaRow(metaTable, "Date Range Scope", (data.startDate() != null ? data.startDate() : "All") + " to " + (data.endDate() != null ? data.endDate() : "All"));
            document.add(metaTable);

            // Metrics tables
            if (data.projects() != null) {
                addPdfSectionHeader(document, "Project Metrics");
                PdfPTable pTable = new PdfPTable(2);
                pTable.setWidthPercentage(100);
                pTable.setSpacingAfter(12);
                addPdfDataRow(pTable, "Total Projects", String.valueOf(data.projects().totalProjects()));
                addPdfDataRow(pTable, "Active Projects", String.valueOf(data.projects().activeProjects()));
                addPdfDataRow(pTable, "Planned Projects", String.valueOf(data.projects().plannedProjects()));
                addPdfDataRow(pTable, "Completed Projects", String.valueOf(data.projects().completedProjects()));
                addPdfDataRow(pTable, "Archived Projects", String.valueOf(data.projects().archivedProjects()));
                document.add(pTable);
            }

            if (data.tasks() != null) {
                addPdfSectionHeader(document, "Task Metrics");
                PdfPTable tTable = new PdfPTable(2);
                tTable.setWidthPercentage(100);
                tTable.setSpacingAfter(12);
                addPdfDataRow(tTable, "Total Tasks", String.valueOf(data.tasks().totalTasks()));
                addPdfDataRow(tTable, "Completed Tasks", String.valueOf(data.tasks().completedTasks()));
                addPdfDataRow(tTable, "In Progress Tasks", String.valueOf(data.tasks().inProgressTasks()));
                addPdfDataRow(tTable, "Pending Tasks", String.valueOf(data.tasks().pendingTasks()));
                addPdfDataRow(tTable, "Submitted Tasks", String.valueOf(data.tasks().submittedTasks()));
                addPdfDataRow(tTable, "Under Review Tasks", String.valueOf(data.tasks().underReviewTasks()));
                addPdfDataRow(tTable, "Changes Requested Tasks", String.valueOf(data.tasks().changesRequestedTasks()));
                addPdfDataRow(tTable, "Overdue Tasks", String.valueOf(data.tasks().overdueTasks()));
                addPdfDataRow(tTable, "Task Completion Rate", formatPercentage(data.completionRate()));
                document.add(tTable);
            }

            if (data.submissions() != null) {
                addPdfSectionHeader(document, "Submission Metrics");
                PdfPTable sTable = new PdfPTable(2);
                sTable.setWidthPercentage(100);
                sTable.setSpacingAfter(12);
                addPdfDataRow(sTable, "Total Submissions", String.valueOf(data.submissions().totalSubmissions()));
                addPdfDataRow(sTable, "Approved Submissions", String.valueOf(data.submissions().approvedSubmissions()));
                addPdfDataRow(sTable, "Changes Requested", String.valueOf(data.submissions().changesRequestedSubmissions()));
                addPdfDataRow(sTable, "Reviewed Submissions", String.valueOf(data.submissions().reviewedSubmissions()));
                addPdfDataRow(sTable, "Submission Approval Rate", formatPercentage(data.approvalRate()));
                document.add(sTable);
            }

            if (data.evaluations() != null) {
                addPdfSectionHeader(document, "AI Evaluation Metrics");
                PdfPTable eTable = new PdfPTable(2);
                eTable.setWidthPercentage(100);
                eTable.setSpacingAfter(12);
                addPdfDataRow(eTable, "Total Evaluations", String.valueOf(data.evaluations().totalEvaluations()));
                addPdfDataRow(eTable, "Average AI Completion", formatPercentage(data.evaluations().averageCompletionPercentage()));
                addPdfDataRow(eTable, "Average AI Quality", formatPercentage(data.evaluations().averageQualityScore()));
                addPdfDataRow(eTable, "Average AI Confidence", formatPercentage(data.evaluations().averageConfidenceScore()));
                document.add(eTable);
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF overview report: " + e.getMessage(), e);
        }
    }

    private byte[] buildEmployeesPdf(List<EmployeePerformanceResponse> data, LocalDate startDate, LocalDate endDate) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 20, 20, 20, 20);
            PdfWriter.getInstance(document, out);
            document.open();

            com.lowagie.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(30, 41, 59));
            Paragraph title = new Paragraph("Employee Performance Analytics Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(10);
            document.add(title);

            Paragraph meta = new Paragraph("Generated At: " + LocalDateTime.now().format(TIMESTAMP_FORMATTER) +
                    " | Date Scope: " + (startDate != null ? startDate : "All") + " to " + (endDate != null ? endDate : "All"),
                    FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY));
            meta.setAlignment(Element.ALIGN_CENTER);
            meta.setSpacingAfter(12);
            document.add(meta);

            String[] headers = {
                    "ID", "Name", "Assigned", "Completed", "In Prog", "Pending", "Overdue",
                    "Comp %", "Subs", "Appr", "Req", "Resub", "Appr %", "AI Comp %", "AI Qual %", "AI Conf %"
            };

            PdfPTable table = new PdfPTable(headers.length);
            table.setWidthPercentage(100);

            for (String h : headers) {
                table.addCell(createPdfHeaderCell(h));
            }

            boolean alt = false;
            for (EmployeePerformanceResponse row : data) {
                Color bg = alt ? new Color(248, 250, 252) : Color.WHITE;
                table.addCell(createPdfDataCell(String.valueOf(row.employeeId()), bg));
                table.addCell(createPdfDataCell(row.employeeName(), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.totalAssignedTasks()), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.completedTasks()), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.inProgressTasks()), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.pendingTasks()), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.overdueTasks()), bg));
                table.addCell(createPdfDataCell(formatPercentage(row.completionRate()), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.totalSubmissions()), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.approvedSubmissions()), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.changesRequested()), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.resubmissions()), bg));
                table.addCell(createPdfDataCell(formatPercentage(row.approvalRate()), bg));
                table.addCell(createPdfDataCell(formatPercentage(row.averageCompletion()), bg));
                table.addCell(createPdfDataCell(formatPercentage(row.averageQuality()), bg));
                table.addCell(createPdfDataCell(formatPercentage(row.averageConfidence()), bg));
                alt = !alt;
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF employee performance report: " + e.getMessage(), e);
        }
    }

    private byte[] buildProjectsPdf(List<ProjectPerformanceResponse> data, LocalDate startDate, LocalDate endDate) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 20, 20, 20, 20);
            PdfWriter.getInstance(document, out);
            document.open();

            com.lowagie.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(30, 41, 59));
            Paragraph title = new Paragraph("Project Analytics Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(10);
            document.add(title);

            Paragraph meta = new Paragraph("Generated At: " + LocalDateTime.now().format(TIMESTAMP_FORMATTER) +
                    " | Date Scope: " + (startDate != null ? startDate : "All") + " to " + (endDate != null ? endDate : "All"),
                    FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY));
            meta.setAlignment(Element.ALIGN_CENTER);
            meta.setSpacingAfter(12);
            document.add(meta);

            String[] headers = {
                    "ID", "Project Name", "Tasks", "Completed", "In Prog", "Pending", "Overdue",
                    "Comp %", "Subs", "Appr", "Req", "Resub", "Appr %", "AI Comp %", "AI Qual %", "AI Conf %", "Staff"
            };

            PdfPTable table = new PdfPTable(headers.length);
            table.setWidthPercentage(100);

            for (String h : headers) {
                table.addCell(createPdfHeaderCell(h));
            }

            boolean alt = false;
            for (ProjectPerformanceResponse row : data) {
                Color bg = alt ? new Color(248, 250, 252) : Color.WHITE;
                table.addCell(createPdfDataCell(String.valueOf(row.projectId()), bg));
                table.addCell(createPdfDataCell(row.projectName(), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.totalTasks()), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.completedTasks()), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.inProgressTasks()), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.pendingTasks()), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.overdueTasks()), bg));
                table.addCell(createPdfDataCell(formatPercentage(row.completionRate()), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.totalSubmissions()), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.approvedSubmissions()), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.changesRequested()), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.resubmissions()), bg));
                table.addCell(createPdfDataCell(formatPercentage(row.approvalRate()), bg));
                table.addCell(createPdfDataCell(formatPercentage(row.averageCompletion()), bg));
                table.addCell(createPdfDataCell(formatPercentage(row.averageQuality()), bg));
                table.addCell(createPdfDataCell(formatPercentage(row.averageConfidence()), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.employeeCount()), bg));
                alt = !alt;
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF project analytics report: " + e.getMessage(), e);
        }
    }

    private byte[] buildTasksPdf(List<TaskPerformanceResponse> data, LocalDate startDate, LocalDate endDate) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 20, 20, 20, 20);
            PdfWriter.getInstance(document, out);
            document.open();

            com.lowagie.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(30, 41, 59));
            Paragraph title = new Paragraph("Task Analytics & Execution Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(10);
            document.add(title);

            Paragraph meta = new Paragraph("Generated At: " + LocalDateTime.now().format(TIMESTAMP_FORMATTER) +
                    " | Date Scope: " + (startDate != null ? startDate : "All") + " to " + (endDate != null ? endDate : "All"),
                    FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY));
            meta.setAlignment(Element.ALIGN_CENTER);
            meta.setSpacingAfter(12);
            document.add(meta);

            String[] headers = {
                    "ID", "Task Title", "Project", "Employee", "Status", "Due Date", "Overdue",
                    "Subs", "Appr", "Req", "Resub", "Appr %", "AI Comp %", "AI Qual %", "AI Conf %"
            };

            PdfPTable table = new PdfPTable(headers.length);
            table.setWidthPercentage(100);

            for (String h : headers) {
                table.addCell(createPdfHeaderCell(h));
            }

            boolean alt = false;
            for (TaskPerformanceResponse row : data) {
                Color bg = alt ? new Color(248, 250, 252) : Color.WHITE;
                table.addCell(createPdfDataCell(String.valueOf(row.taskId()), bg));
                table.addCell(createPdfDataCell(row.taskTitle(), bg));
                table.addCell(createPdfDataCell(row.projectName(), bg));
                table.addCell(createPdfDataCell(row.employeeName(), bg));
                table.addCell(createPdfDataCell(row.taskStatus() != null ? row.taskStatus().name() : "—", bg));
                table.addCell(createPdfDataCell(row.dueDate() != null ? row.dueDate().toString() : "—", bg));
                table.addCell(createPdfDataCell(row.isOverdue() ? "Yes" : "No", bg));
                table.addCell(createPdfDataCell(String.valueOf(row.totalSubmissions()), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.approvedSubmissions()), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.changesRequested()), bg));
                table.addCell(createPdfDataCell(String.valueOf(row.resubmissions()), bg));
                table.addCell(createPdfDataCell(formatPercentage(row.approvalRate()), bg));
                table.addCell(createPdfDataCell(formatPercentage(row.aiCompletionPercentage()), bg));
                table.addCell(createPdfDataCell(formatPercentage(row.aiQualityScore()), bg));
                table.addCell(createPdfDataCell(formatPercentage(row.aiConfidenceScore()), bg));
                alt = !alt;
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF task analytics report: " + e.getMessage(), e);
        }
    }

    private void addPdfSectionHeader(Document document, String title) throws DocumentException {
        Paragraph p = new Paragraph(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(51, 65, 85)));
        p.setSpacingBefore(6);
        p.setSpacingAfter(4);
        document.add(p);
    }

    private void addPdfMetaRow(PdfPTable table, String key, String value) {
        PdfPCell c1 = new PdfPCell(new Phrase(key, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
        c1.setBackgroundColor(new Color(241, 245, 249));
        c1.setPadding(4);
        table.addCell(c1);

        PdfPCell c2 = new PdfPCell(new Phrase(value, FontFactory.getFont(FontFactory.HELVETICA, 9)));
        c2.setPadding(4);
        table.addCell(c2);
    }

    private void addPdfDataRow(PdfPTable table, String metric, String val) {
        PdfPCell c1 = new PdfPCell(new Phrase(metric, FontFactory.getFont(FontFactory.HELVETICA, 9)));
        c1.setPadding(3);
        table.addCell(c1);

        PdfPCell c2 = new PdfPCell(new Phrase(val, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
        c2.setPadding(3);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(c2);
    }

    private PdfPCell createPdfHeaderCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE)));
        cell.setBackgroundColor(new Color(79, 70, 229));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4);
        return cell;
    }

    private PdfPCell createPdfDataCell(String text, Color bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "—", FontFactory.getFont(FontFactory.HELVETICA, 7.5f)));
        cell.setBackgroundColor(bgColor);
        cell.setPadding(3);
        return cell;
    }

    // =========================================================================
    // HELPER FORMATTERS
    // =========================================================================

    private String formatPercentage(Double val) {
        if (val == null) {
            return "—";
        }
        return String.format("%.2f%%", val);
    }

    private String escapeCsv(Object val) {
        if (val == null) {
            return "—";
        }
        String str = val.toString();
        // Prevent CSV Formula Injection in spreadsheet processors
        if (val instanceof String s && (s.startsWith("=") || s.startsWith("+") || s.startsWith("@") || s.startsWith("\t") || s.startsWith("\r"))) {
            str = "\t" + str;
        }
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
}
