package com.Workmanagement.dashboard.model;

import java.util.List;

public record ManagerDashboardResponse(
        long totalProjects,
        long plannedProjects,
        long activeProjects,
        long completedProjects,
        long archivedProjects,
        long totalTasks,
        long completedTasks,
        long inProgressTasks,
        long submittedTasks,
        long underReviewTasks,
        long overdueTasks,
        long totalEmployees,
        long totalSubmissions,
        long submittedSubmissions,
        long aiEvaluatingSubmissions,
        long underReviewSubmissions,
        long approvedSubmissions,
        long changesRequestedSubmissions,
        long pendingReviewSubmissions,
        double averageCompletionPercentage,
        double averageQualityScore,
        double averageConfidenceScore,
        TaskStatistics taskStatistics,
        List<EmployeeWorkload> employeeWorkloads,
        List<ProjectSummary> projectSummaries
) {
}
