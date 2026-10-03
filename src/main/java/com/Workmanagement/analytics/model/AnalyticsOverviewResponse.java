package com.Workmanagement.analytics.model;

public record AnalyticsOverviewResponse(
        ProjectAnalyticsSummary projects,
        TaskAnalyticsSummary tasks,
        SubmissionAnalyticsSummary submissions,
        EvaluationAnalyticsSummary evaluations,
        double completionRate,
        double approvalRate,
        String startDate,
        String endDate
) {
}
