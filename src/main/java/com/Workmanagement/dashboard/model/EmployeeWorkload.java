package com.Workmanagement.dashboard.model;

public record EmployeeWorkload(
        Long employeeId,
        String employeeName,
        String employeeEmail,
        long totalTasks,
        long completedTasks,
        long inProgressTasks,
        long submittedTasks,
        long overdueTasks
) {
}
