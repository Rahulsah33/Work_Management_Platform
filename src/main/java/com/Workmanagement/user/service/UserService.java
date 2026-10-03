package com.Workmanagement.user.service;

import com.Workmanagement.ai.repository.AiEvaluationRepository;
import com.Workmanagement.audit.entity.AuditAction;
import com.Workmanagement.audit.entity.AuditLog;
import com.Workmanagement.audit.repository.AuditLogRepository;
import com.Workmanagement.audit.service.AuditLogService;
import com.Workmanagement.comment.entity.Comment;
import com.Workmanagement.comment.repository.CommentRepository;
import com.Workmanagement.common.exception.BadRequestException;
import com.Workmanagement.common.exception.ConflictException;
import com.Workmanagement.common.exception.ResourceNotFoundException;
import com.Workmanagement.notification.repository.NotificationPreferenceRepository;
import com.Workmanagement.notification.repository.NotificationRepository;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.repository.ProjectRepository;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.repository.SubmissionRepository;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.repository.TaskRepository;
import com.Workmanagement.task.repository.TaskRequirementRepository;
import com.Workmanagement.user.dto.CreateUserRequest;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final AuditLogRepository auditLogRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final CommentRepository commentRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskRequirementRepository taskRequirementRepository;
    private final SubmissionRepository submissionRepository;
    private final AiEvaluationRepository aiEvaluationRepository;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService,
            AuditLogRepository auditLogRepository,
            NotificationRepository notificationRepository,
            NotificationPreferenceRepository notificationPreferenceRepository,
            CommentRepository commentRepository,
            ProjectRepository projectRepository,
            TaskRepository taskRepository,
            TaskRequirementRepository taskRequirementRepository,
            SubmissionRepository submissionRepository,
            AiEvaluationRepository aiEvaluationRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.auditLogRepository = auditLogRepository;
        this.notificationRepository = notificationRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.commentRepository = commentRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.taskRequirementRepository = taskRequirementRepository;
        this.submissionRepository = submissionRepository;
        this.aiEvaluationRepository = aiEvaluationRepository;
    }

    @Transactional
    public User createUser(CreateUserRequest request, User creator) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email is already registered: " + request.getEmail());
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole() != null ? request.getRole() : Role.MANAGER);

        User savedUser = userRepository.save(user);

        auditLogService.createLog(
                creator,
                AuditAction.USER_REGISTERED,
                "USER",
                savedUser.getId(),
                "Admin created user: " + savedUser.getEmail() + " with role: " + savedUser.getRole()
        );

        return savedUser;
    }

    @Transactional
    public User createManager(CreateUserRequest request, User creator) {
        request.setRole(Role.MANAGER);
        return createUser(request, creator);
    }

    @Transactional
    public void deleteUser(Long id, User currentUser) {
        if (currentUser.getId().equals(id)) {
            throw new BadRequestException("You cannot delete your own administrative account");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // Ensure at least one admin remains
        if (user.getRole() == Role.ADMIN) {
            long adminCount = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == Role.ADMIN)
                    .count();
            if (adminCount <= 1) {
                throw new BadRequestException("Cannot delete the only remaining administrator");
            }
        }

        String userEmail = user.getEmail();
        String userName = user.getName();
        Role userRole = user.getRole();

        // 1. Delete notifications & notification preferences for user
        notificationRepository.deleteAll(notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(id));
        notificationPreferenceRepository.deleteAll(notificationPreferenceRepository.findByUserId(id));

        // 2. Delete comments authored by this user
        commentRepository.deleteAll(commentRepository.findByAuthorIdOrderByCreatedAtDesc(id));

        // 3. If user is MANAGER, reassign their managed projects to current admin
        List<Project> managedProjects = projectRepository.findAll().stream()
                .filter(p -> p.getManager() != null && p.getManager().getId().equals(id))
                .toList();
        for (Project proj : managedProjects) {
            proj.setManager(currentUser);
            projectRepository.save(proj);
        }
        projectRepository.flush();

        // 4. Submissions submitted by this user
        List<Submission> userSubmissions = submissionRepository.findBySubmittedById(id);
        for (Submission sub : userSubmissions) {
            aiEvaluationRepository.findBySubmissionId(sub.getId()).ifPresent(aiEvaluationRepository::delete);
            sub.setPreviousSubmission(null);
            submissionRepository.save(sub);
        }
        submissionRepository.flush();
        submissionRepository.deleteAll(userSubmissions);

        // 5. Tasks assigned to this user
        List<Task> assignedTasks = taskRepository.findByAssignedToId(id);
        for (Task task : assignedTasks) {
            // Delete task comments
            commentRepository.deleteAll(commentRepository.findByTaskIdOrderByCreatedAtAsc(task.getId()));

            // Delete task submissions
            List<Submission> taskSubs = submissionRepository.findByTaskId(task.getId());
            for (Submission sub : taskSubs) {
                aiEvaluationRepository.findBySubmissionId(sub.getId()).ifPresent(aiEvaluationRepository::delete);
                sub.setPreviousSubmission(null);
                submissionRepository.save(sub);
            }
            submissionRepository.flush();
            submissionRepository.deleteAll(taskSubs);

            // Delete task requirements
            taskRequirementRepository.deleteAll(taskRequirementRepository.findByTaskId(task.getId()));

            // Delete task
            taskRepository.delete(task);
        }
        taskRepository.flush();

        // 6. Nullify performedBy in audit logs so historical logs are retained without violating FK constraints
        List<AuditLog> userLogs = auditLogRepository.findByPerformedByIdOrderByCreatedAtDesc(id);
        for (AuditLog log : userLogs) {
            log.setPerformedBy(null);
            auditLogRepository.save(log);
        }
        auditLogRepository.flush();

        // 7. Delete the user
        userRepository.delete(user);

        // 8. Record audit log
        auditLogService.createLog(
                currentUser,
                AuditAction.GENERAL,
                "USER",
                id,
                "Admin deleted user: " + userEmail + " (" + userName + ", Role: " + userRole + ")"
        );
    }

    @Transactional
    public User updateProfile(User currentUser, String name, String avatarUrl) {
        if (name != null && !name.trim().isEmpty()) {
            currentUser.setName(name.trim());
        }
        if (avatarUrl != null) {
            currentUser.setAvatarUrl(avatarUrl.trim().isEmpty() ? null : avatarUrl.trim());
        }
        User saved = userRepository.save(currentUser);

        auditLogService.createLog(
                saved,
                AuditAction.GENERAL,
                "USER",
                saved.getId(),
                "User updated profile details / photo: " + saved.getEmail()
        );

        return saved;
    }
}
