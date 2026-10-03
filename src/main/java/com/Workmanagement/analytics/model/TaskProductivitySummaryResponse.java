package com.Workmanagement.analytics.model;

public record TaskProductivitySummaryResponse(
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
        Double averageAiCompletion,
        Double averageAiQuality,
        Double averageAiConfidence,
        Double averageCompletionTimeHours,
        String startDate,
        String endDate
) {
}
