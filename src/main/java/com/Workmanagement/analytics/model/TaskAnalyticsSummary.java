package com.Workmanagement.analytics.model;

public record TaskAnalyticsSummary(
        long totalTasks,
        long pendingTasks,
        long inProgressTasks,
        long submittedTasks,
        long underReviewTasks,
        long approvedTasks,
        long completedTasks,
        long changesRequestedTasks,
        long overdueTasks
) {
}
