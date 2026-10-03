package com.Workmanagement.dashboard.model;

public record ProjectSummary(
        Long projectId,
        String projectName,
        String status,
        long totalTasks,
        long completedTasks,
        long inProgressTasks,
        long overdueTasks
) {
}
