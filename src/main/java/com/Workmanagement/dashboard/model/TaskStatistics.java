package com.Workmanagement.dashboard.model;

public record TaskStatistics(
        long totalTasks,
        long createdTasks,
        long assignedTasks,
        long inProgressTasks,
        long submittedTasks,
        long aiEvaluatingTasks,
        long underReviewTasks,
        long approvedTasks,
        long changesRequestedTasks,
        long completedTasks,
        long overdueTasks
) {
}
