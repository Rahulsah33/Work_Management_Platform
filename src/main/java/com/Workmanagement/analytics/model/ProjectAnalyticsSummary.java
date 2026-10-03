package com.Workmanagement.analytics.model;

public record ProjectAnalyticsSummary(
        long totalProjects,
        long plannedProjects,
        long activeProjects,
        long completedProjects,
        long archivedProjects
) {
}
