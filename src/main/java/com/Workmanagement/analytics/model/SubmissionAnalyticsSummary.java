package com.Workmanagement.analytics.model;

public record SubmissionAnalyticsSummary(
        long totalSubmissions,
        long submittedSubmissions,
        long aiEvaluatingSubmissions,
        long underReviewSubmissions,
        long approvedSubmissions,
        long changesRequestedSubmissions,
        long reviewedSubmissions
) {
}
