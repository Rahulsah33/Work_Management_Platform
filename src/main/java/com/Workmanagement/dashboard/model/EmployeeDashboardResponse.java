package com.Workmanagement.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Employee dashboard payload. Only contains data belonging to
 * the authenticated employee themselves.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeDashboardResponse {

    private Long employeeId;
    private String employeeName;

    private long assignedTasks;
    private long inProgressTasks;
    private long completedTasks;
    private long pendingSubmissions;     // submissions not yet approved
    private long changesRequestedTasks;  // tasks needing rework
    private long upcomingDeadlineTasks;  // due within next 7 days (not completed)

    private List<DashboardTaskItem> tasks;
    private List<DashboardTaskItem> upcomingDeadlines;
    private List<EmployeeSubmissionItem> recentSubmissions;
}
