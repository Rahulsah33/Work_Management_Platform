package com.Workmanagement.analytics.model;

public record EmployeePerformanceResponse(
        Long employeeId,
        String employeeName,
        String employeeEmail,
        long totalAssignedTasks,
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
        String startDate,
        String endDate
) {
}
