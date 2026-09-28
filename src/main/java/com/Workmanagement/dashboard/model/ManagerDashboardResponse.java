package com.Workmanagement.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Compact statistics for the manager dashboard.
 * Clean response model - no entity graphs.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManagerDashboardResponse {

    private long totalProjects;
    private long totalTasks;
    private long completedTasks;
    private long pendingTasks;      // CREATED + ASSIGNED + IN_PROGRESS
    private long submittedTasks;    // awaiting decision (SUBMITTED / AI_EVALUATING / UNDER_REVIEW / CHANGES_REQUESTED)
    private long overdueTasks;
    private long underReviewTasks;
    private long awaitingAiEvaluationTasks;
    private long approvedTasks;
    private long totalEmployees;
    private long totalSubmissions;
    private Double averageAiCompletionPercentage;
    private Double averageAiQualityScore;
    private long highRiskTasks;
}
