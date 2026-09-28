package com.Workmanagement.ai.model;

import com.Workmanagement.submission.entity.Submission;

import java.time.LocalDateTime;

public record SubmissionToolResult(
        Long id,
        String report,
        String githubUrl,
        String evidenceUrl,
        String status,
        Long taskId,
        String taskTitle,
        Long submittedById,
        String submittedByName,
        LocalDateTime submittedAt,
        Long previousSubmissionId
) {

    public static SubmissionToolResult from(Submission submission) {
        return new SubmissionToolResult(
                submission.getId(),
                submission.getReport(),
                submission.getGithubUrl(),
                submission.getEvidenceUrl(),
                submission.getStatus() == null ? null : submission.getStatus().name(),
                submission.getTask() == null ? null : submission.getTask().getId(),
                submission.getTask() == null ? null : submission.getTask().getTitle(),
                submission.getSubmittedBy() == null ? null : submission.getSubmittedBy().getId(),
                submission.getSubmittedBy() == null ? null : submission.getSubmittedBy().getName(),
                submission.getSubmittedAt(),
                submission.getPreviousSubmission() == null
                        ? null
                        : submission.getPreviousSubmission().getId()
        );
    }
}
