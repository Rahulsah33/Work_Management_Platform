package com.Workmanagement.dashboard.model;

import java.time.LocalDateTime;

public record EmployeeSubmissionSummary(
        Long submissionId,
        Long taskId,
        String taskTitle,
        String status,
        LocalDateTime submittedAt,
        Double completionPercentage,
        Double qualityScore,
        Double confidenceScore
) {
}
