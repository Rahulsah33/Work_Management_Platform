package com.Workmanagement.ai.tools;

import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.repository.SubmissionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SubmissionTools {

    private final SubmissionRepository submissionRepository;

    public SubmissionTools(SubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    @Tool(description = "Get a submission by its ID")
    public Submission getSubmission(Long submissionId) {

        return submissionRepository.findById(submissionId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Submission not found: " + submissionId
                        ));
    }

    @Tool(description = "Get all submissions for a task")
    public List<Submission> getTaskSubmissions(Long taskId) {

        return submissionRepository.findByTaskId(taskId);
    }

    @Tool(description = "Get all submissions made by an employee")
    public List<Submission> getEmployeeSubmissions(Long employeeId) {

        return submissionRepository.findBySubmittedById(employeeId);
    }
}