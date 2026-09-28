package com.Workmanagement.submission.service;

import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.submission.repository.SubmissionRepository;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.task.repository.TaskRepository;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public SubmissionService(
            SubmissionRepository submissionRepository,
            TaskRepository taskRepository,
            UserRepository userRepository) {

        this.submissionRepository = submissionRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    // Create submission
    public Submission createSubmission(
            Long taskId,
            Long employeeId,
            Submission submission) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() ->
                        new RuntimeException("Task not found"));

        User employee = userRepository.findById(employeeId)
                .orElseThrow(() ->
                        new RuntimeException("Employee not found"));

        if (!employee.getRole().name().equals("EMPLOYEE")) {
            throw new RuntimeException(
                    "Only employees can submit work");
        }

        if (!task.getAssignedTo().getId().equals(employeeId)) {
            throw new RuntimeException(
                    "This task is not assigned to this employee");
        }

        submission.setTask(task);
        submission.setSubmittedBy(employee);
        submission.setStatus(SubmissionStatus.SUBMITTED);

        return submissionRepository.save(submission);
    }

    // Get submission by ID
    public Submission getSubmissionById(Long id) {

        return submissionRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Submission not found"));
    }

    // Get submissions for a task
    public List<Submission> getSubmissionsByTask(Long taskId) {

        if (!taskRepository.existsById(taskId)) {
            throw new RuntimeException("Task not found");
        }

        return submissionRepository.findByTaskId(taskId);
    }

    // Get submissions by employee
    public List<Submission> getSubmissionsByEmployee(
            Long employeeId) {

        if (!userRepository.existsById(employeeId)) {
            throw new RuntimeException("Employee not found");
        }

        return submissionRepository.findBySubmittedById(employeeId);
    }

    // Get submissions by status
    public List<Submission> getSubmissionsByStatus(
            SubmissionStatus status) {

        return submissionRepository.findByStatus(status);
    }

    // Update submission status
    public Submission updateStatus(
            Long id,
            SubmissionStatus status) {

        Submission submission = getSubmissionById(id);

        submission.setStatus(status);

        return submissionRepository.save(submission);
    }

    // Approve submission
    public Submission approveSubmission(Long submissionId) {

        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() ->
                        new RuntimeException("Submission not found: " + submissionId));

        if (submission.getStatus() != SubmissionStatus.UNDER_REVIEW) {
            throw new RuntimeException(
                    "Only submissions under review can be approved"
            );
        }

        submission.setStatus(SubmissionStatus.APPROVED);

        // Task is now completed
        Task task = submission.getTask();
        task.setStatus(TaskStatus.COMPLETED);

        return submissionRepository.save(submission);
    }

    // Request changes for submission
    public Submission requestChanges(Long submissionId) {

        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() ->
                        new RuntimeException("Submission not found: " + submissionId));

        if (submission.getStatus() != SubmissionStatus.UNDER_REVIEW) {
            throw new RuntimeException(
                    "Changes can only be requested for submissions under review"
            );
        }

        submission.setStatus(SubmissionStatus.CHANGES_REQUESTED);

        // Employee can work on the task again
        Task task = submission.getTask();
        task.setStatus(TaskStatus.IN_PROGRESS);

        return submissionRepository.save(submission);
    }

    // Resubmit after changes requested
    public Submission resubmit(
            Long previousSubmissionId,
            Submission newSubmission
    ) {

        Submission previousSubmission =
                submissionRepository.findById(previousSubmissionId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Previous submission not found: "
                                                + previousSubmissionId
                                ));

        if (previousSubmission.getStatus()
                != SubmissionStatus.CHANGES_REQUESTED) {

            throw new RuntimeException(
                    "Only submissions with requested changes can be resubmitted"
            );
        }

        newSubmission.setTask(previousSubmission.getTask());
        newSubmission.setSubmittedBy(previousSubmission.getSubmittedBy());
        newSubmission.setPreviousSubmission(previousSubmission);
        newSubmission.setStatus(SubmissionStatus.SUBMITTED);

        return submissionRepository.save(newSubmission);
    }
}