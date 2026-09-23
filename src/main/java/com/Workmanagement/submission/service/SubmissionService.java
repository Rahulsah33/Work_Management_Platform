package com.Workmanagement.submission.service;

import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.submission.repository.SubmissionRepository;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.task.repository.TaskRepository;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    // Submit work for a task
    @Transactional
    public Submission createSubmission(Long taskId, Submission submission, String userEmail) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + taskId));

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + userEmail));

        // If the user is an employee, ensure they are assigned to this task
        if (user.getRole() == Role.EMPLOYEE && !task.getAssignedTo().getId().equals(user.getId())) {
            throw new RuntimeException("You can only submit work for tasks assigned to you");
        }

        if (submission.getReport() == null || submission.getReport().trim().isEmpty()) {
            throw new RuntimeException("Submission report cannot be empty");
        }

        submission.setTask(task);
        submission.setSubmittedBy(user);
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setSubmittedAt(LocalDateTime.now());

        // Update task status to SUBMITTED
        task.setStatus(TaskStatus.SUBMITTED);
        taskRepository.save(task);

        return submissionRepository.save(submission);
    }

    // Get submission by ID
    public Submission getSubmissionById(Long id) {
        return submissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Submission not found with id: " + id));
    }

    // Get all submissions for a task
    public List<Submission> getSubmissionsByTaskId(Long taskId) {
        if (!taskRepository.existsById(taskId)) {
            throw new RuntimeException("Task not found with id: " + taskId);
        }
        return submissionRepository.findByTaskId(taskId);
    }

    // Get all submissions submitted by a specific employee
    public List<Submission> getSubmissionsByEmployee(Long employeeId) {
        if (!userRepository.existsById(employeeId)) {
            throw new RuntimeException("Employee not found with id: " + employeeId);
        }
        return submissionRepository.findBySubmittedById(employeeId);
    }

    // Get all submissions for the current authenticated user
    public List<Submission> getMySubmissions(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
        return submissionRepository.findBySubmittedBy(user);
    }

    // Get all submissions
    public List<Submission> getAllSubmissions() {
        return submissionRepository.findAll();
    }

    // Filter submissions by status
    public List<Submission> getSubmissionsByStatus(SubmissionStatus status) {
        return submissionRepository.findByStatus(status);
    }

    // Review submission (APPROVE, CHANGES_REQUESTED, UNDER_REVIEW, AI_EVALUATING)
    @Transactional
    public Submission reviewSubmission(Long id, SubmissionStatus reviewStatus) {
        Submission submission = getSubmissionById(id);
        submission.setStatus(reviewStatus);

        Task task = submission.getTask();
        if (reviewStatus == SubmissionStatus.APPROVED) {
            task.setStatus(TaskStatus.APPROVED);
        } else if (reviewStatus == SubmissionStatus.CHANGES_REQUESTED) {
            task.setStatus(TaskStatus.CHANGES_REQUESTED);
        } else if (reviewStatus == SubmissionStatus.UNDER_REVIEW) {
            task.setStatus(TaskStatus.UNDER_REVIEW);
        } else if (reviewStatus == SubmissionStatus.AI_EVALUATING) {
            task.setStatus(TaskStatus.AI_EVALUATING);
        }
        taskRepository.save(task);

        return submissionRepository.save(submission);
    }

    // Delete submission
    public void deleteSubmission(Long id) {
        Submission submission = getSubmissionById(id);
        submissionRepository.delete(submission);
    }
}

