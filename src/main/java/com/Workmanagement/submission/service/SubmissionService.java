package com.Workmanagement.submission.service;

import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.submission.repository.SubmissionRepository;
import com.Workmanagement.task.entity.Task;
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
}