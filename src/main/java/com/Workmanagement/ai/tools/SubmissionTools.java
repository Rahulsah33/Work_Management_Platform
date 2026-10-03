package com.Workmanagement.ai.tools;

import com.Workmanagement.ai.model.SubmissionToolResult;
import com.Workmanagement.auth.security.AuthorizationService;
import com.Workmanagement.auth.security.CurrentUserService;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.repository.SubmissionRepository;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.repository.TaskRepository;
import com.Workmanagement.user.entity.User;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class SubmissionTools {

    private final SubmissionRepository submissionRepository;
    private final TaskRepository taskRepository;
    private final CurrentUserService currentUserService;
    private final AuthorizationService authorizationService;

    public SubmissionTools(
            SubmissionRepository submissionRepository,
            TaskRepository taskRepository,
            CurrentUserService currentUserService,
            AuthorizationService authorizationService
    ) {
        this.submissionRepository = submissionRepository;
        this.taskRepository = taskRepository;
        this.currentUserService = currentUserService;
        this.authorizationService = authorizationService;
    }

    @Tool(description = "Get a submission by its ID")
    @Transactional(readOnly = true)
    public SubmissionToolResult getSubmission(Long submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found: " + submissionId));

        User currentUser = currentUserService.getCurrentUser();
        if (!authorizationService.canAccessSubmission(submission, currentUser)) {
            throw new RuntimeException("Access denied to submission: " + submissionId);
        }

        return SubmissionToolResult.from(submission);
    }

    @Tool(description = "Get all submissions for a task by task ID")
    @Transactional(readOnly = true)
    public List<SubmissionToolResult> getTaskSubmissions(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        User currentUser = currentUserService.getCurrentUser();
        if (!authorizationService.canAccessTask(task, currentUser)) {
            throw new RuntimeException("Access denied to task submissions: " + taskId);
        }

        return submissionRepository.findByTaskId(taskId)
                .stream()
                .map(SubmissionToolResult::from)
                .toList();
    }

    @Tool(description = "Get all submissions made by an employee by employee ID")
    @Transactional(readOnly = true)
    public List<SubmissionToolResult> getEmployeeSubmissions(Long employeeId) {
        User currentUser = currentUserService.getCurrentUser();
        List<Submission> submissions = submissionRepository.findBySubmittedById(employeeId);

        if (currentUserService.isAdmin()) {
            return submissions.stream().map(SubmissionToolResult::from).toList();
        }

        return submissions.stream()
                .filter(submission -> authorizationService.canAccessSubmission(submission, currentUser))
                .map(SubmissionToolResult::from)
                .toList();
    }
}