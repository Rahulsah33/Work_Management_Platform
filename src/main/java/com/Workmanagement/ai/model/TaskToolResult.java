package com.Workmanagement.ai.model;

import com.Workmanagement.task.entity.Task;

import java.time.LocalDate;

public record TaskToolResult(
        Long id,
        String title,
        String description,
        String status,
        String priority,
        LocalDate endDate,
        Long projectId,
        String projectName,
        Long assignedToId,
        String assignedToName
) {

    public static TaskToolResult from(Task task) {
        return new TaskToolResult(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus() == null ? null : task.getStatus().name(),
                task.getPriority(),
                task.getEndDate(),
                task.getProject() == null ? null : task.getProject().getId(),
                task.getProject() == null ? null : task.getProject().getName(),
                task.getAssignedTo() == null ? null : task.getAssignedTo().getId(),
                task.getAssignedTo() == null ? null : task.getAssignedTo().getName()
        );
    }
}
