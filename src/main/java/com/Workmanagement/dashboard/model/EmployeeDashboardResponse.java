package com.Workmanagement.dashboard.model;

import java.util.List;

public record EmployeeDashboardResponse(
        long totalTasks,
        long completedTasks,
        long inProgressTasks,
        long submittedTasks,
        long underReviewTasks,
        long changesRequestedTasks,
        long overdueTasks,

        long totalSubmissions,
        long approvedSubmissions,
        long changesRequestedSubmissions,

        double averageCompletionPercentage,
        double averageQualityScore,
        double averageConfidenceScore,

        EmployeeTaskStatistics taskStatistics,

        List<EmployeeTaskSummary> taskSummaries,

        List<EmployeeSubmissionSummary> recentSubmissions
) {
}
