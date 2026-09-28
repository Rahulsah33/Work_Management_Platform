package com.Workmanagement.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * AI-tool friendly projection of a Task.
 * <p>
 * JPA entities must NEVER be returned directly from AI tools:
 * lazy relations (project / assignedTo) serialize as Hibernate
 * proxies ("hibernateLazyInitializer") and circular references
 * break the tool-response JSON, which makes Spring AI fail with
 * "Failed to generate content".
 * <p>
 * This DTO contains only flat, useful fields.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskToolResult {

    private Long taskId;
    private String title;
    private String description;
    private String status;
    private String priority;
    private LocalDate deadline;

    private Long projectId;
    private String projectName;

    private Long assignedEmployeeId;
    private String assignedEmployeeName;
    private String assignedEmployeeEmail;

    private List<RequirementToolResult> requirements;
}
