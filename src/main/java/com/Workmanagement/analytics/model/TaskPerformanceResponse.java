package com.Workmanagement.analytics.model;

import com.Workmanagement.task.entity.TaskStatus;

import java.time.LocalDate;

public record TaskPerformanceResponse(
        Long taskId,
        String taskTitle,
        Long projectId,
        String projectName,
        Long employeeId,
        String employeeName,
        TaskStatus taskStatus,
        boolean isOverdue,
        long totalSubmissions,
        long approvedSubmissions,
        long changesRequested,
        long resubmissions,
        double approvalRate,
        Double aiCompletionPercentage,
        Double aiQualityScore,
        Double aiConfidenceScore,
        Double completionTimeHours,
        String createdAt,
        LocalDate dueDate
) {
}
