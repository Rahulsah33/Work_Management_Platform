package com.Workmanagement.analytics.model;

public record EvaluationAnalyticsSummary(
        long totalEvaluations,
        double averageCompletionPercentage,
        double averageQualityScore,
        double averageConfidenceScore
) {
}
