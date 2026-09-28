package com.Workmanagement.ai.tools;

import com.Workmanagement.ai.model.SubmissionToolResult;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.repository.SubmissionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class SubmissionTools {

    private final SubmissionRepository submissionRepository;

    public SubmissionTools(SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    @Tool(description = "Get a submission by its ID")
    @Transactional(readOnly = true)
    public SubmissionToolResult getSubmission(Long submissionId) {

        return submissionRepository.findById(submissionId)
                .map(SubmissionToolResult::from)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Submission not found: " + submissionId
                        ));
    }

    @Tool(description = "Get all submissions for a task by task ID")
    @Transactional(readOnly = true)
    public List<SubmissionToolResult> getTaskSubmissions(Long taskId) {

        return submissionRepository.findByTaskId(taskId)
                .stream()
                .map(SubmissionToolResult::from)
                .toList();
    }

    @Tool(description = "Get all submissions made by an employee by employee ID")
    @Transactional(readOnly = true)
    public List<SubmissionToolResult> getEmployeeSubmissions(Long employeeId) {

        return submissionRepository.findBySubmittedById(employeeId)
                .stream()
                .map(SubmissionToolResult::from)
                .toList();
    }
}