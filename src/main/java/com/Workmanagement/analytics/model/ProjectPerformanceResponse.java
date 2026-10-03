package com.Workmanagement.analytics.model;

public record ProjectPerformanceResponse(
        Long projectId,
        String projectName,
        long totalTasks,
        long completedTasks,
        long pendingTasks,
        long inProgressTasks,
        long activeTasks,
        long overdueTasks,
        double completionRate,
        long totalSubmissions,
        long approvedSubmissions,
        long changesRequested,
        long resubmissions,
        double approvalRate,
        double averageCompletion,
        double averageQuality,
        double averageConfidence,
        long employeeCount,
        String startDate,
        String endDate
) {
}
