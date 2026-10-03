package com.Workmanagement.dashboard.model;

import java.time.LocalDate;

public record EmployeeTaskSummary(
        Long taskId,
        String taskTitle,
        String projectName,
        String status,
        String priority,
        LocalDate endDate,
        boolean overdue
) {
}
