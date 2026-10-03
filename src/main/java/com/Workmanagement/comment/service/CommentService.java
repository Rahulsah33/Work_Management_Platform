package com.Workmanagement.comment.service;

import com.Workmanagement.audit.entity.AuditAction;
import com.Workmanagement.audit.service.AuditLogService;
import com.Workmanagement.comment.entity.Comment;
import com.Workmanagement.comment.model.CommentResponse;
import com.Workmanagement.comment.model.CreateCommentRequest;
import com.Workmanagement.comment.repository.CommentRepository;
import com.Workmanagement.common.exception.BadRequestException;
import com.Workmanagement.common.exception.ResourceNotFoundException;
import com.Workmanagement.common.exception.UnauthorizedException;
import com.Workmanagement.notification.entity.NotificationType;
import com.Workmanagement.notification.service.NotificationService;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.repository.TaskRepository;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public CommentService(
            CommentRepository commentRepository,
            TaskRepository taskRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            AuditLogService auditLogService
    ) {
        this.commentRepository = commentRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new UnauthorizedException("User is not authenticated");
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private void checkTaskAccess(Task task, User user) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        if (user.getRole() == Role.EMPLOYEE) {
            if (task.getAssignedTo() != null && task.getAssignedTo().getId().equals(user.getId())) {
                return;
            }
        } else if (user.getRole() == Role.MANAGER) {
            if (task.getProject() != null && task.getProject().getManager() != null && task.getProject().getManager().getId().equals(user.getId())) {
                return;
            }
        }
        throw new AccessDeniedException("You are not authorized to access this task");
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BadRequestException("Comment content cannot be empty");
        }
        if (content.length() > 3000) {
            throw new BadRequestException("Comment content cannot exceed 3000 characters");
        }
    }

    private CommentResponse toResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getTask() != null ? comment.getTask().getId() : null,
                comment.getAuthor() != null ? comment.getAuthor().getId() : null,
                comment.getAuthor() != null ? comment.getAuthor().getName() : null,
                comment.getAuthor() != null && comment.getAuthor().getRole() != null ? comment.getAuthor().getRole().name() : null,
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }

    @Transactional
    public CommentResponse createComment(Long taskId, CreateCommentRequest request) {
        User user = getAuthenticatedUser();
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));

        checkTaskAccess(task, user);

        if (request == null) {
            throw new BadRequestException("Request body cannot be null");
        }
        validateContent(request.content());

        Comment comment = Comment.builder()
                .task(task)
                .author(user)
                .content(request.content().trim())
                .build();

        Comment saved = commentRepository.save(comment);

        // Notify other participant safely
        try {
            if (user.getRole() == Role.EMPLOYEE) {
                if (task.getProject() != null && task.getProject().getManager() != null) {
                    User manager = task.getProject().getManager();
                    if (!manager.getId().equals(user.getId())) {
                        notificationService.createNotification(
                                manager.getId(),
                                "New Task Comment",
                                user.getName() + " added a new comment on task: " + task.getTitle(),
                                NotificationType.GENERAL
                        );
                    }
                }
            } else if (user.getRole() == Role.MANAGER) {
                if (task.getAssignedTo() != null) {
                    User assignedEmployee = task.getAssignedTo();
                    if (!assignedEmployee.getId().equals(user.getId())) {
                        notificationService.createNotification(
                                assignedEmployee.getId(),
                                "New Task Comment",
                                "Your manager added a new comment on task: " + task.getTitle(),
                                NotificationType.GENERAL
                        );
                    }
                }
            }
        } catch (Exception e) {
            // Notification failure should not break comment creation
        }

        auditLogService.createLog(
                user,
                AuditAction.COMMENT_CREATED,
                "COMMENT",
                saved.getId(),
                "Created comment on task ID: " + taskId
        );

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getTaskComments(Long taskId) {
        User user = getAuthenticatedUser();
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));

        checkTaskAccess(task, user);

        return commentRepository.findByTaskIdOrderByCreatedAtAsc(taskId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getMyComments() {
        User user = getAuthenticatedUser();
        return commentRepository.findByAuthorIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CommentResponse updateComment(Long commentId, CreateCommentRequest request) {
        User user = getAuthenticatedUser();
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + commentId));

        if (!comment.getAuthor().getId().equals(user.getId())) {
            throw new AccessDeniedException("You are not authorized to edit this comment");
        }

        if (request == null) {
            throw new BadRequestException("Request body cannot be null");
        }
        validateContent(request.content());

        comment.setContent(request.content().trim());
        Comment updated = commentRepository.save(comment);

        auditLogService.createLog(
                user,
                AuditAction.COMMENT_UPDATED,
                "COMMENT",
                updated.getId(),
                "Updated comment ID: " + commentId
        );

        return toResponse(updated);
    }

    @Transactional
    public void deleteComment(Long commentId) {
        User user = getAuthenticatedUser();
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + commentId));

        boolean isAuthor = comment.getAuthor() != null && comment.getAuthor().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == Role.ADMIN;

        if (!isAuthor && !isAdmin) {
            throw new AccessDeniedException("You are not authorized to delete this comment");
        }

        commentRepository.delete(comment);

        auditLogService.createLog(
                user,
                AuditAction.COMMENT_DELETED,
                "COMMENT",
                commentId,
                "Deleted comment ID: " + commentId
        );
    }
}

