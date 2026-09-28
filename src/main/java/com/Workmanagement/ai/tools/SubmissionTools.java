package com.Workmanagement.ai.tools;

import com.Workmanagement.common.dto.SubmissionToolResult;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.repository.SubmissionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Read-only AI agent tools for submissions.
 * Returns flat {@link SubmissionToolResult} DTOs (never JPA entities).
 */
@Component
public class SubmissionTools {

    private final SubmissionRepository submissionRepository;
    private final ToolMapper toolMapper;

    public SubmissionTools(SubmissionRepository submissionRepository,
                           ToolMapper toolMapper) {
        this.submissionRepository = submissionRepository;
        this.toolMapper = toolMapper;
    }

    @Tool(description = "Get a submission by its ID. Includes report, links, status, task, employee and AI evaluation scores if available.")
    public SubmissionToolResult getSubmission(Long submissionId) {

        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Submission not found: " + submissionId
                        ));

        return toolMapper.toSubmissionToolResult(submission);
    }

    @Tool(description = "Get all submissions for a task by task ID")
    public List<SubmissionToolResult> getTaskSubmissions(Long taskId) {

        return submissionRepository.findByTaskId(taskId)
                .stream()
                .map(toolMapper::toSubmissionToolResult)
                .toList();
    }

    @Tool(description = "Get all submissions made by an employee by employee ID")
    public List<SubmissionToolResult> getEmployeeSubmissions(Long employeeId) {

        return submissionRepository.findBySubmittedById(employeeId)
                .stream()
                .map(toolMapper::toSubmissionToolResult)
                .toList();
    }
}
