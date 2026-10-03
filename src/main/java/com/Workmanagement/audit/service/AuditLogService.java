package com.Workmanagement.audit.service;

import com.Workmanagement.ai.entity.AiEvaluation;
import com.Workmanagement.ai.repository.AiEvaluationRepository;
import com.Workmanagement.audit.entity.AuditAction;
import com.Workmanagement.audit.entity.AuditLog;
import com.Workmanagement.audit.model.AuditLogResponse;
import com.Workmanagement.audit.repository.AuditLogRepository;
import com.Workmanagement.comment.entity.Comment;
import com.Workmanagement.common.exception.ResourceNotFoundException;
import com.Workmanagement.comment.repository.CommentRepository;
import com.Workmanagement.notification.entity.Notification;
import com.Workmanagement.notification.repository.NotificationRepository;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.repository.ProjectRepository;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.repository.SubmissionRepository;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskRequirement;
import com.Workmanagement.task.repository.TaskRepository;
import com.Workmanagement.task.repository.TaskRequirementRepository;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final SubmissionRepository submissionRepository;
    private final CommentRepository commentRepository;
    private final TaskRequirementRepository taskRequirementRepository;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final NotificationRepository notificationRepository;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            SubmissionRepository submissionRepository,
            CommentRepository commentRepository,
            TaskRequirementRepository taskRequirementRepository,
            AiEvaluationRepository aiEvaluationRepository,
            NotificationRepository notificationRepository
    ) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.submissionRepository = submissionRepository;
        this.commentRepository = commentRepository;
        this.taskRequirementRepository = taskRequirementRepository;
        this.aiEvaluationRepository = aiEvaluationRepository;
        this.notificationRepository = notificationRepository;
    }

    private User getAuthenticatedUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email).orElse(null);
    }

    private User getRequiredAuthenticatedUser() {
        User user = getAuthenticatedUserOrNull();
        if (user == null) {
            throw new AccessDeniedException("User is not authenticated");
        }
        return user;
    }

    private AuditLogResponse toResponse(AuditLog log) {
        User user = log.getPerformedBy();
        return new AuditLogResponse(
                log.getId(),
                user != null ? user.getId() : null,
                user != null ? user.getName() : null,
                user != null ? user.getEmail() : null,
                log.getAction() != null ? log.getAction().name() : null,
                log.getEntityType(),
                log.getEntityId(),
                log.getDescription(),
                log.getCreatedAt()
        );
    }

    @Transactional
    public AuditLogResponse createLog(
            AuditAction action,
            String entityType,
            Long entityId,
            String description
    ) {
        User performedBy = getAuthenticatedUserOrNull();

        AuditLog log = AuditLog.builder()
                .performedBy(performedBy)
                .action(action)
                .entityType(entityType != null ? entityType.toUpperCase().trim() : "GENERAL")
                .entityId(entityId)
                .description(description)
                .createdAt(LocalDateTime.now())
                .build();

        AuditLog saved = auditLogRepository.save(log);
        return toResponse(saved);
    }

    @Transactional
    public AuditLogResponse createLog(
            User performedBy,
            AuditAction action,
            String entityType,
            Long entityId,
            String description
    ) {
        AuditLog log = AuditLog.builder()
                .performedBy(performedBy)
                .action(action)
                .entityType(entityType != null ? entityType.toUpperCase().trim() : "GENERAL")
                .entityId(entityId)
                .description(description)
                .createdAt(LocalDateTime.now())
                .build();

        AuditLog saved = auditLogRepository.save(log);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAllLogs() {
        return auditLogRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getMyLogs() {
        User currentUser = getRequiredAuthenticatedUser();
        return auditLogRepository.findByPerformedByIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getEntityLogs(
            String entityType,
            Long entityId
    ) {
        User currentUser = getRequiredAuthenticatedUser();
        String normalizedType = entityType != null ? entityType.toUpperCase().trim() : "";

        if (currentUser.getRole() != Role.ADMIN) {
            checkEntityAccess(normalizedType, entityId, currentUser);
        }

        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(normalizedType, entityId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void checkEntityAccess(String entityType, Long entityId, User user) {
        switch (entityType) {
            case "PROJECT" -> {
                Project project = projectRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + entityId));
                if (user.getRole() == Role.MANAGER) {
                    if (project.getManager() != null && project.getManager().getId().equals(user.getId())) {
                        return;
                    }
                }
                throw new AccessDeniedException("You are not authorized to view audit logs for this project");
            }
            case "TASK" -> {
                Task task = taskRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + entityId));
                checkTaskAccess(task, user);
            }
            case "SUBMISSION" -> {
                Submission submission = submissionRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + entityId));
                checkSubmissionAccess(submission, user);
            }
            case "COMMENT" -> {
                Comment comment = commentRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + entityId));
                if (comment.getAuthor() != null && comment.getAuthor().getId().equals(user.getId())) {
                    return;
                }
                if (comment.getTask() != null) {
                    checkTaskAccess(comment.getTask(), user);
                    return;
                }
                throw new AccessDeniedException("You are not authorized to view audit logs for this comment");
            }
            case "TASK_REQUIREMENT", "REQUIREMENT" -> {
                TaskRequirement requirement = taskRequirementRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("Task requirement not found with id: " + entityId));
                if (requirement.getTask() != null) {
                    checkTaskAccess(requirement.getTask(), user);
                    return;
                }
                throw new AccessDeniedException("You are not authorized to view audit logs for this requirement");
            }
            case "AI_EVALUATION", "EVALUATION" -> {
                AiEvaluation evaluation = aiEvaluationRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("AI evaluation not found with id: " + entityId));
                if (evaluation.getSubmission() != null) {
                    checkSubmissionAccess(evaluation.getSubmission(), user);
                    return;
                }
                throw new AccessDeniedException("You are not authorized to view audit logs for this evaluation");
            }
            case "NOTIFICATION" -> {
                Notification notification = notificationRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + entityId));
                if (notification.getRecipient() != null && notification.getRecipient().getId().equals(user.getId())) {
                    return;
                }
                throw new AccessDeniedException("You are not authorized to view audit logs for this notification");
            }
            case "USER" -> {
                if (user.getId().equals(entityId)) {
                    return;
                }
                throw new AccessDeniedException("You are not authorized to view audit logs for this user");
            }
            default -> throw new AccessDeniedException("Access denied for entity type: " + entityType);
        }
    }

    private void checkTaskAccess(Task task, User user) {
        if (user.getRole() == Role.EMPLOYEE) {
            if (task.getAssignedTo() != null && task.getAssignedTo().getId().equals(user.getId())) {
                return;
            }
        } else if (user.getRole() == Role.MANAGER) {
            if (task.getProject() != null && task.getProject().getManager() != null && task.getProject().getManager().getId().equals(user.getId())) {
                return;
            }
        }
        throw new AccessDeniedException("You are not authorized to access audit logs for this task");
    }

    private void checkSubmissionAccess(Submission submission, User user) {
        if (user.getRole() == Role.EMPLOYEE) {
            if (submission.getSubmittedBy() != null && submission.getSubmittedBy().getId().equals(user.getId())) {
                return;
            }
            if (submission.getTask() != null && submission.getTask().getAssignedTo() != null && submission.getTask().getAssignedTo().getId().equals(user.getId())) {
                return;
            }
        } else if (user.getRole() == Role.MANAGER) {
            if (submission.getTask() != null && submission.getTask().getProject() != null
                    && submission.getTask().getProject().getManager() != null
                    && submission.getTask().getProject().getManager().getId().equals(user.getId())) {
                return;
            }
        }
        throw new AccessDeniedException("You are not authorized to access audit logs for this submission");
    }
}
