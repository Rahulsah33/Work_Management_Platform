package com.Workmanagement.ai.model;

import com.Workmanagement.project.entity.Project;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProjectToolResult(
        Long id,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        Long managerId,
        String managerName,
        LocalDateTime createdAt
) {

    public static ProjectToolResult from(Project project) {
        return new ProjectToolResult(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStartDate(),
                project.getEndDate(),
                project.getStatus() == null ? null : project.getStatus().name(),
                project.getManager() == null ? null : project.getManager().getId(),
                project.getManager() == null ? null : project.getManager().getName(),
                project.getCreatedAt()
        );
    }
}
