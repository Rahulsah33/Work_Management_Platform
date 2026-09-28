package com.Workmanagement.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * AI-tool friendly projection of a Project.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectToolResult {

    private Long projectId;
    private String name;
    private String description;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;

    private Long managerId;
    private String managerName;
    private String managerEmail;
}
