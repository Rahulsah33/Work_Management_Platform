package com.Workmanagement.dashboard.model;

public record EmployeeTaskStatistics(
        long totalTasks,
        long completedTasks,
        long inProgressTasks,
        long submittedTasks,
        long underReviewTasks,
        long changesRequestedTasks,
        long overdueTasks
) {
}
