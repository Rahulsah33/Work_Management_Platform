package com.Workmanagement.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Compact task row used by dashboards (no entity graph).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardTaskItem {

    private Long taskId;
    private String title;
    private String status;
    private String priority;
    private LocalDate deadline;
    private Long projectId;
    private String projectName;
    private LocalDateTime createdAt;
}
