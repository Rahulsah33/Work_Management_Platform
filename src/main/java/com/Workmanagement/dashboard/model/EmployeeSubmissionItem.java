package com.Workmanagement.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Submission row with latest AI evaluation summary for the employee dashboard.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeSubmissionItem {

    private Long submissionId;
    private Long taskId;
    private String taskTitle;
    private String status;
    private LocalDateTime submittedAt;
    private Double aiCompletionPercentage;
    private Double aiQualityScore;
    private String aiFeedback;
}
