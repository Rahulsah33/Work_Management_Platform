package com.Workmanagement.project.service;

import com.Workmanagement.ai.repository.AiEvaluationRepository;
import com.Workmanagement.audit.entity.AuditAction;
import com.Workmanagement.audit.service.AuditLogService;
import com.Workmanagement.auth.security.AuthorizationService;
import com.Workmanagement.auth.security.CurrentUserService;
import com.Workmanagement.comment.repository.CommentRepository;
import com.Workmanagement.common.exception.ResourceNotFoundException;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.project.repository.ProjectRepository;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.repository.SubmissionRepository;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.repository.TaskRepository;
import com.Workmanagement.task.repository.TaskRequirementRepository;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final CurrentUserService currentUserService;
    private final AuthorizationService authorizationService;
    private final TaskRepository taskRepository;
    private final SubmissionRepository submissionRepository;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final CommentRepository commentRepository;
    private final TaskRequirementRepository taskRequirementRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            CurrentUserService currentUserService,
            AuthorizationService authorizationService,
            TaskRepository taskRepository,
            SubmissionRepository submissionRepository,
            AiEvaluationRepository aiEvaluationRepository,
            CommentRepository commentRepository,
            TaskRequirementRepository taskRequirementRepository
    ) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.currentUserService = currentUserService;
        this.authorizationService = authorizationService;
        this.taskRepository = taskRepository;
        this.submissionRepository = submissionRepository;
        this.aiEvaluationRepository = aiEvaluationRepository;
        this.commentRepository = commentRepository;
        this.taskRequirementRepository = taskRequirementRepository;
    }

    @Transactional
    public Project createProject(Project project, String managerEmail) {
        User currentUser = currentUserService.getCurrentUser();

        if (currentUser.getRole() == Role.EMPLOYEE) {
            throw new AccessDeniedException("Only ADMIN or MANAGER can create a project");
        }

        User manager;
        if (project.getManager() != null && project.getManager().getId() != null) {
            manager = userRepository.findById(project.getManager().getId()).orElse(currentUser);
        } else if (project.getManager() != null && project.getManager().getEmail() != null) {
            manager = userRepository.findByEmail(project.getManager().getEmail()).orElse(currentUser);
        } else if (managerEmail != null && !managerEmail.isBlank()) {
            manager = userRepository.findByEmail(managerEmail).orElse(currentUser);
        } else {
            manager = currentUser;
        }

        project.setManager(manager);

        if (project.getStatus() == null) {
            project.setStatus(ProjectStatus.PLANNED);
        }
        project.setCreatedAt(java.time.LocalDateTime.now());
        Project saved = projectRepository.save(project);

        auditLogService.createLog(
                currentUser,
                AuditAction.PROJECT_CREATED,
                "PROJECT",
                saved.getId(),
                "Created project: " + saved.getName()
        );

        return saved;
    }

    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    public Project getProjectById(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + id));

        currentUserService.getCurrentUserOptional().ifPresent(user ->
                authorizationService.checkAccessProject(project, user));

        return project;
    }

    @Transactional
    public Project updateProject(Long id, Project updatedProject) {
        Project existingProject = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + id));

        User currentUser = currentUserService.getCurrentUser();
        authorizationService.checkManageProject(existingProject, currentUser);

        if (updatedProject.getName() != null) {
            existingProject.setName(updatedProject.getName());
        }
        if (updatedProject.getDescription() != null) {
            existingProject.setDescription(updatedProject.getDescription());
        }
        if (updatedProject.getStatus() != null) {
            existingProject.setStatus(updatedProject.getStatus());
        }
        if (updatedProject.getStartDate() != null) {
            existingProject.setStartDate(updatedProject.getStartDate());
        }
        if (updatedProject.getEndDate() != null) {
            existingProject.setEndDate(updatedProject.getEndDate());
        }
        if (updatedProject.getManager() != null) {
            if (updatedProject.getManager().getId() != null) {
                User newManager = userRepository.findById(updatedProject.getManager().getId()).orElse(existingProject.getManager());
                existingProject.setManager(newManager);
            } else if (updatedProject.getManager().getEmail() != null) {
                User newManager = userRepository.findByEmail(updatedProject.getManager().getEmail()).orElse(existingProject.getManager());
                existingProject.setManager(newManager);
            }
        }

        Project saved = projectRepository.save(existingProject);

        auditLogService.createLog(
                currentUser,
                AuditAction.PROJECT_UPDATED,
                "PROJECT",
                saved.getId(),
                "Updated project: " + saved.getName()
        );

        return saved;
    }

    @Transactional
    public void deleteProjectById(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + id));

        User currentUser = currentUserService.getCurrentUser();
        authorizationService.checkManageProject(project, currentUser);

        String projectName = project.getName();

        // 1. Cascade cleanup all tasks belonging to this project
        List<Task> tasks = taskRepository.findByProjectId(id);
        for (Task task : tasks) {
            // Delete comments for task
            commentRepository.deleteAll(commentRepository.findByTaskIdOrderByCreatedAtAsc(task.getId()));

            // Handle submissions and their evaluations
            List<Submission> submissions = submissionRepository.findByTaskId(task.getId());
            for (Submission sub : submissions) {
                aiEvaluationRepository.findBySubmissionId(sub.getId()).ifPresent(aiEvaluationRepository::delete);
                // Nullify self-referencing previousSubmission FK
                sub.setPreviousSubmission(null);
                submissionRepository.save(sub);
            }
            submissionRepository.flush();
            submissionRepository.deleteAll(submissions);

            // Delete task requirements
            taskRequirementRepository.deleteAll(taskRequirementRepository.findByTaskId(task.getId()));

            // Delete task
            taskRepository.delete(task);
        }
        taskRepository.flush();

        // 2. Delete project itself
        projectRepository.delete(project);

        auditLogService.createLog(
                currentUser,
                AuditAction.PROJECT_DELETED,
                "PROJECT",
                id,
                "Deleted project: " + projectName
        );
    }
}


