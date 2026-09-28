package com.Workmanagement.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI-tool friendly projection of a Submission.
 * Contains only scalar fields - no JPA proxies, no cycles.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionToolResult {

    private Long submissionId;
    private String report;
    private String githubUrl;
    private String evidenceUrl;
    private String status;
    private LocalDateTime submittedAt;

    private Long taskId;
    private String taskTitle;

    private Long submittedByEmployeeId;
    private String submittedByName;

    private Long previousSubmissionId;

    private Double aiCompletionPercentage;
    private Double aiQualityScore;
    private String aiFeedback;
}
