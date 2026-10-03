package com.Workmanagement.submission.dto;

import com.Workmanagement.ai.entity.AiEvaluationStatus;
import com.Workmanagement.submission.entity.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionHistoryDto {

    private Long id;
    private Integer version;
    private String report;
    private String githubUrl;
    private String evidenceUrl;
    private SubmissionStatus status;
    private LocalDateTime submittedAt;
    private String managerFeedback;

    private Long taskId;
    private String taskTitle;

    private Long submittedById;
    private String submittedByName;
    private String submittedByEmail;

    private Long previousSubmissionId;
    private Integer previousVersion;

    // AI Evaluation details for this specific version
    private Long evaluationId;
    private Double completionPercentage;
    private Double qualityScore;
    private Double confidenceScore;
    private String evaluationFeedback;
    private String missingRequirements;
    private String partialRequirements;
    private AiEvaluationStatus evaluationStatus;
    private LocalDateTime evaluatedAt;
}
