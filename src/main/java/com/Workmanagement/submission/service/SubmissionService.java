package com.Workmanagement.submission.service;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.repository.AiEvaluationRepository;
import com.Workmanagement.audit.entity.AuditAction;
import com.Workmanagement.audit.service.AuditLogService;
import com.Workmanagement.auth.security.AuthorizationService;
import com.Workmanagement.auth.security.CurrentUserService;
import com.Workmanagement.common.exception.BadRequestException;
import com.Workmanagement.common.exception.ResourceNotFoundException;
import com.Workmanagement.notification.entity.NotificationType;
import com.Workmanagement.notification.service.NotificationService;
import com.Workmanagement.submission.dto.SubmissionHistoryDto;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.entity.SubmissionStatus;
import com.Workmanagement.submission.repository.SubmissionRepository;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.task.repository.TaskRepository;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final CurrentUserService currentUserService;
    private final AuthorizationService authorizationService;

    public SubmissionService(
            SubmissionRepository submissionRepository,
            TaskRepository taskRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            AuditLogService auditLogService,
            CurrentUserService currentUserService,
            AuthorizationService authorizationService) {

        this(submissionRepository, taskRepository, userRepository, null, notificationService, auditLogService, currentUserService, authorizationService);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SubmissionService(
            SubmissionRepository submissionRepository,
            TaskRepository taskRepository,
            UserRepository userRepository,
            AiEvaluationRepository aiEvaluationRepository,
            NotificationService notificationService,
            AuditLogService auditLogService,
            CurrentUserService currentUserService,
            AuthorizationService authorizationService) {

        this.submissionRepository = submissionRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.aiEvaluationRepository = aiEvaluationRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.currentUserService = currentUserService;
        this.authorizationService = authorizationService;
    }

    // Create submission
    @Transactional
    public Submission createSubmission(
            Long taskId,
            Long employeeId,
            Submission submission) {

        if (taskId == null) {
            throw new BadRequestException("Task ID is required");
        }

        if (submission == null || submission.getReport() == null || submission.getReport().trim().isEmpty()) {
            throw new BadRequestException("Submission report cannot be empty");
        }

        User currentUser = currentUserService.getCurrentUser();

        if (currentUser.getRole() != Role.EMPLOYEE) {
            throw new AccessDeniedException("Only employees can submit work");
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Task not found with id: " + taskId));

        if (task.getAssignedTo() == null || !task.getAssignedTo().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("This task is not assigned to you");
        }

        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.APPROVED) {
            throw new BadRequestException("Task is already completed and approved");
        }

        List<Submission> existingSubmissions = submissionRepository.findByTaskIdOrderByVersionDesc(taskId);
        boolean hasActive = existingSubmissions.stream().anyMatch(s ->
                s.getStatus() == SubmissionStatus.SUBMITTED ||
                s.getStatus() == SubmissionStatus.AI_EVALUATING ||
                s.getStatus() == SubmissionStatus.UNDER_REVIEW);

        if (hasActive) {
            throw new BadRequestException("Task already has an active submission under review. Please wait for review before submitting again.");
        }

        Submission newSubmission = new Submission();
        newSubmission.setTask(task);
        newSubmission.setSubmittedBy(currentUser);
        newSubmission.setReport(submission.getReport().trim());
        newSubmission.setGithubUrl(submission.getGithubUrl());
        newSubmission.setEvidenceUrl(submission.getEvidenceUrl());
        newSubmission.setStatus(SubmissionStatus.SUBMITTED);
        newSubmission.setManagerFeedback(null);
        newSubmission.setSubmittedAt(LocalDateTime.now());

        if (existingSubmissions.isEmpty()) {
            newSubmission.setVersion(1);
        } else {
            Submission latest = existingSubmissions.get(0);
            if (latest.getStatus() == SubmissionStatus.CHANGES_REQUESTED) {
                newSubmission.setPreviousSubmission(latest);
                newSubmission.setVersion((latest.getVersion() != null ? latest.getVersion() : 1) + 1);
            } else {
                throw new BadRequestException("Cannot create new submission while latest submission status is " + latest.getStatus());
            }
        }

        Submission savedSubmission = submissionRepository.save(newSubmission);

        // Transition task status to SUBMITTED
        task.setStatus(TaskStatus.SUBMITTED);
        taskRepository.save(task);

        if (task.getProject() != null && task.getProject().getManager() != null) {
            notificationService.createNotification(
                    task.getProject().getManager().getId(),
                    "New Submission Received",
                    currentUser.getName() + " submitted \"" + task.getTitle() + "\" for review.",
                    NotificationType.SUBMISSION_RECEIVED,
                    "SUBMISSION",
                    savedSubmission.getId()
            );
        }

        auditLogService.createLog(
                currentUser,
                AuditAction.SUBMISSION_CREATED,
                "SUBMISSION",
                savedSubmission.getId(),
                "Created submission (v" + savedSubmission.getVersion() + ") for task: " + task.getTitle()
        );

        return savedSubmission;
    }

    // Get submission by ID
    public Submission getSubmissionById(Long id) {
        Submission submission = submissionRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Submission not found with id: " + id));

        currentUserService.getCurrentUserOptional().ifPresent(user ->
                authorizationService.checkAccessSubmission(submission, user));

        return submission;
    }

    // Get current authenticated user's submissions
    public List<Submission> getMySubmissions() {
        User currentUser = currentUserService.getCurrentUser();
        return submissionRepository.findBySubmittedById(currentUser.getId());
    }

    // Get submissions for a task
    public List<Submission> getSubmissionsByTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));

        currentUserService.getCurrentUserOptional().ifPresent(user ->
                authorizationService.checkAccessTask(task, user));

        return submissionRepository.findByTaskIdOrderByVersionDesc(taskId);
    }

    // Get submissions by employee
    public List<Submission> getSubmissionsByEmployee(Long employeeId) {
        User currentUser = currentUserService.getCurrentUser();

        if (currentUser.getRole() == Role.EMPLOYEE) {
            if (!currentUser.getId().equals(employeeId)) {
                throw new AccessDeniedException("Employees can only view their own submissions");
            }
            return submissionRepository.findBySubmittedById(currentUser.getId());
        }

        if (currentUser.getRole() == Role.MANAGER) {
            return submissionRepository.findBySubmittedById(employeeId)
                    .stream()
                    .filter(sub -> sub.getTask() != null && sub.getTask().getProject() != null
                            && authorizationService.canManageProject(sub.getTask().getProject(), currentUser))
                    .toList();
        }

        return submissionRepository.findBySubmittedById(employeeId);
    }

    // Get all submissions scoped by role
    public List<Submission> getAllSubmissions() {
        User currentUser = currentUserService.getCurrentUser();
        if (currentUser.getRole() == Role.ADMIN) {
            return submissionRepository.findAll();
        }
        if (currentUser.getRole() == Role.MANAGER) {
            return submissionRepository.findAll().stream()
                    .filter(sub -> sub.getTask() != null && sub.getTask().getProject() != null
                            && authorizationService.canManageProject(sub.getTask().getProject(), currentUser))
                    .toList();
        }
        return submissionRepository.findBySubmittedById(currentUser.getId());
    }

    // Get submissions by status
    public List<Submission> getSubmissionsByStatus(SubmissionStatus status) {
        User currentUser = currentUserService.getCurrentUser();

        if (currentUser.getRole() == Role.MANAGER) {
            return submissionRepository.findByStatus(status)
                    .stream()
                    .filter(sub -> sub.getTask() != null && sub.getTask().getProject() != null
                            && authorizationService.canManageProject(sub.getTask().getProject(), currentUser))
                    .toList();
        }

        return submissionRepository.findByStatus(status);
    }

    // Update submission status
    @Transactional
    public Submission updateStatus(
            Long id,
            SubmissionStatus status) {

        Submission submission = getSubmissionById(id);
        User currentUser = currentUserService.getCurrentUser();
        authorizationService.checkManageSubmission(submission, currentUser);

        submission.setStatus(status);

        Submission saved = submissionRepository.save(submission);

        auditLogService.createLog(
                currentUser,
                AuditAction.SUBMISSION_STATUS_CHANGED,
                "SUBMISSION",
                saved.getId(),
                "Submission status changed to " + status
        );

        return saved;
    }

    // Approve submission
    @Transactional
    public Submission approveSubmission(Long submissionId) {
        return approveSubmission(submissionId, null);
    }

    @Transactional
    public Submission approveSubmission(Long submissionId, String feedback) {
        Submission submission = getSubmissionById(submissionId);
        User currentUser = currentUserService.getCurrentUser();
        authorizationService.checkManageSubmission(submission, currentUser);

        if (submission.getStatus() == SubmissionStatus.APPROVED) {
            throw new BadRequestException("Submission is already approved");
        }

        if (submission.getStatus() != SubmissionStatus.UNDER_REVIEW) {
            throw new BadRequestException("Only submissions under review can be approved");
        }

        submission.setStatus(SubmissionStatus.APPROVED);
        if (feedback != null && !feedback.trim().isEmpty()) {
            submission.setManagerFeedback(feedback.trim());
        }

        // Task is now completed
        Task task = submission.getTask();
        if (task != null) {
            task.setStatus(TaskStatus.COMPLETED);
            taskRepository.save(task);
        }

        Submission saved = submissionRepository.save(submission);

        if (saved.getSubmittedBy() != null) {
            notificationService.createNotification(
                    saved.getSubmittedBy().getId(),
                    "Submission Approved",
                    "Your submission for \"" + (task != null ? task.getTitle() : "task") + "\" has been approved.",
                    NotificationType.SUBMISSION_APPROVED,
                    "SUBMISSION",
                    saved.getId()
            );
        }

        auditLogService.createLog(
                currentUser,
                AuditAction.SUBMISSION_APPROVED,
                "SUBMISSION",
                saved.getId(),
                "Approved submission for task: " + (task != null ? task.getTitle() : "task")
        );

        return saved;
    }

    // Request changes for submission
    @Transactional
    public Submission requestChanges(Long submissionId) {
        return requestChanges(submissionId, "Please review requirements and address the feedback.");
    }

    @Transactional
    public Submission requestChanges(Long submissionId, String feedback) {
        Submission submission = getSubmissionById(submissionId);
        User currentUser = currentUserService.getCurrentUser();
        authorizationService.checkManageSubmission(submission, currentUser);

        if (submission.getStatus() == SubmissionStatus.APPROVED) {
            throw new BadRequestException("Cannot request changes on an already approved submission");
        }

        if (submission.getStatus() != SubmissionStatus.UNDER_REVIEW) {
            throw new BadRequestException("Changes can only be requested for submissions under review");
        }

        if (feedback == null || feedback.trim().isEmpty()) {
            throw new BadRequestException("Feedback is required when requesting changes");
        }

        submission.setStatus(SubmissionStatus.CHANGES_REQUESTED);
        submission.setManagerFeedback(feedback.trim());

        // Employee can work on the task again
        Task task = submission.getTask();
        if (task != null) {
            task.setStatus(TaskStatus.CHANGES_REQUESTED);
            taskRepository.save(task);
        }

        Submission saved = submissionRepository.save(submission);

        if (saved.getSubmittedBy() != null) {
            notificationService.createNotification(
                    saved.getSubmittedBy().getId(),
                    "Changes Requested",
                    "Changes have been requested for \"" + (task != null ? task.getTitle() : "task") + "\". Please review the manager feedback.",
                    NotificationType.CHANGES_REQUESTED,
                    "SUBMISSION",
                    saved.getId()
            );
        }

        auditLogService.createLog(
                currentUser,
                AuditAction.SUBMISSION_CHANGES_REQUESTED,
                "SUBMISSION",
                saved.getId(),
                "Changes requested for submission on task: " + (task != null ? task.getTitle() : "task")
        );

        return saved;
    }

    // Resubmit after changes requested
    @Transactional
    public Submission resubmit(
            Long previousSubmissionId,
            Submission newSubmission
    ) {
        if (previousSubmissionId == null) {
            throw new BadRequestException("Previous submission ID is required for resubmission");
        }

        User currentUser = currentUserService.getCurrentUser();
        if (currentUser.getRole() != Role.EMPLOYEE) {
            throw new AccessDeniedException("Only employees can resubmit work");
        }

        Submission previousSubmission = getSubmissionById(previousSubmissionId);

        if (previousSubmission.getSubmittedBy() == null || !previousSubmission.getSubmittedBy().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You can only resubmit your own submissions");
        }

        if (previousSubmission.getStatus() != SubmissionStatus.CHANGES_REQUESTED) {
            throw new BadRequestException("Only submissions with requested changes can be resubmitted");
        }

        Task task = previousSubmission.getTask();
        if (task != null && (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.APPROVED)) {
            throw new BadRequestException("Task is already completed and approved");
        }

        if (task != null) {
            List<Submission> existingSubmissions = submissionRepository.findByTaskIdOrderByVersionDesc(task.getId());
            boolean hasActive = existingSubmissions.stream().anyMatch(s ->
                    s.getStatus() == SubmissionStatus.SUBMITTED ||
                    s.getStatus() == SubmissionStatus.AI_EVALUATING ||
                    s.getStatus() == SubmissionStatus.UNDER_REVIEW);

            if (hasActive) {
                throw new BadRequestException("Task already has an active submission under review. Please wait for review before submitting again.");
            }
        }

        if (newSubmission == null || newSubmission.getReport() == null || newSubmission.getReport().trim().isEmpty()) {
            throw new BadRequestException("Submission report cannot be empty");
        }

        int nextVersion = (previousSubmission.getVersion() != null ? previousSubmission.getVersion() : 1) + 1;

        Submission iteration = new Submission();
        iteration.setTask(previousSubmission.getTask());
        iteration.setSubmittedBy(currentUser);
        iteration.setPreviousSubmission(previousSubmission);
        iteration.setReport(newSubmission.getReport().trim());
        iteration.setGithubUrl(newSubmission.getGithubUrl());
        iteration.setEvidenceUrl(newSubmission.getEvidenceUrl());
        iteration.setVersion(nextVersion);
        iteration.setStatus(SubmissionStatus.SUBMITTED);
        iteration.setManagerFeedback(null);
        iteration.setSubmittedAt(LocalDateTime.now());

        Submission saved = submissionRepository.save(iteration);

        if (task != null) {
            task.setStatus(TaskStatus.SUBMITTED);
            taskRepository.save(task);

            if (task.getProject() != null && task.getProject().getManager() != null) {
                notificationService.createNotification(
                        task.getProject().getManager().getId(),
                        "New Submission Received",
                        currentUser.getName() + " resubmitted \"" + task.getTitle() + "\" (v" + nextVersion + ") for review.",
                        NotificationType.SUBMISSION_RECEIVED,
                        "SUBMISSION",
                        saved.getId()
                );
            }
        }

        auditLogService.createLog(
                currentUser,
                AuditAction.SUBMISSION_RESUBMITTED,
                "SUBMISSION",
                saved.getId(),
                "Resubmitted submission (v" + nextVersion + ") for task: " + (task != null ? task.getTitle() : "task")
        );

        return saved;
    }

    // Get submission version history for a task
    public List<SubmissionHistoryDto> getSubmissionHistory(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));

        currentUserService.getCurrentUserOptional().ifPresent(user ->
                authorizationService.checkAccessTask(task, user));

        List<Submission> list = submissionRepository.findByTaskIdOrderByVersionDesc(taskId);
        return list.stream().map(this::toHistoryDto).toList();
    }

    // Get submission version history starting from a submission ID
    public List<SubmissionHistoryDto> getSubmissionHistoryForSubmission(Long submissionId) {
        Submission submission = getSubmissionById(submissionId);
        if (submission.getTask() == null) {
            throw new ResourceNotFoundException("Task associated with submission not found");
        }
        return getSubmissionHistory(submission.getTask().getId());
    }

    // Get latest submission for a task
    public Submission getLatestSubmissionForTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));

        currentUserService.getCurrentUserOptional().ifPresent(user ->
                authorizationService.checkAccessTask(task, user));

        return submissionRepository.findFirstByTaskIdOrderByVersionDesc(taskId).orElse(null);
    }

    // Convert Submission to SubmissionHistoryDto with AI evaluation summary
    public SubmissionHistoryDto toHistoryDto(Submission sub) {
        if (sub == null) return null;

        SubmissionHistoryDto.SubmissionHistoryDtoBuilder builder = SubmissionHistoryDto.builder()
                .id(sub.getId())
                .version(sub.getVersion() != null ? sub.getVersion() : 1)
                .report(sub.getReport())
                .githubUrl(sub.getGithubUrl())
                .evidenceUrl(sub.getEvidenceUrl())
                .status(sub.getStatus())
                .submittedAt(sub.getSubmittedAt())
                .managerFeedback(sub.getManagerFeedback());

        if (sub.getTask() != null) {
            builder.taskId(sub.getTask().getId())
                   .taskTitle(sub.getTask().getTitle());
        }

        if (sub.getSubmittedBy() != null) {
            builder.submittedById(sub.getSubmittedBy().getId())
                   .submittedByName(sub.getSubmittedBy().getName())
                   .submittedByEmail(sub.getSubmittedBy().getEmail());
        }

        if (sub.getPreviousSubmission() != null) {
            builder.previousSubmissionId(sub.getPreviousSubmission().getId())
                   .previousVersion(sub.getPreviousSubmission().getVersion());
        }

        // Attach AI evaluation details if evaluated
        if (aiEvaluationRepository != null) {
            aiEvaluationRepository.findBySubmissionId(sub.getId()).ifPresent(eval -> {
                builder.evaluationId(eval.getId())
                       .completionPercentage(eval.getCompletionPercentage())
                       .qualityScore(eval.getQualityScore())
                       .confidenceScore(eval.getConfidenceScore())
                       .evaluationFeedback(eval.getFeedback())
                       .missingRequirements(eval.getMissingRequirements())
                       .partialRequirements(eval.getPartialRequirements())
                       .evaluationStatus(eval.getStatus())
                       .evaluatedAt(eval.getEvaluatedAt());
            });
        }

        return builder.build();
    }
}