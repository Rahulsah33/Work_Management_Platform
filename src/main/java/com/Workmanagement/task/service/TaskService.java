package com.Workmanagement.task.service;

import com.Workmanagement.ai.repository.AiEvaluationRepository;
import com.Workmanagement.audit.entity.AuditAction;
import com.Workmanagement.audit.service.AuditLogService;
import com.Workmanagement.auth.security.AuthorizationService;
import com.Workmanagement.auth.security.CurrentUserService;
import com.Workmanagement.comment.repository.CommentRepository;
import com.Workmanagement.common.exception.BadRequestException;
import com.Workmanagement.common.exception.ResourceNotFoundException;
import com.Workmanagement.notification.entity.NotificationType;
import com.Workmanagement.notification.service.NotificationService;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.repository.ProjectRepository;
import com.Workmanagement.submission.entity.Submission;
import com.Workmanagement.submission.repository.SubmissionRepository;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
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
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final CurrentUserService currentUserService;
    private final AuthorizationService authorizationService;
    private final SubmissionRepository submissionRepository;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final CommentRepository commentRepository;
    private final TaskRequirementRepository taskRequirementRepository;

    public TaskService(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            AuditLogService auditLogService,
            CurrentUserService currentUserService,
            AuthorizationService authorizationService,
            SubmissionRepository submissionRepository,
            AiEvaluationRepository aiEvaluationRepository,
            CommentRepository commentRepository,
            TaskRequirementRepository taskRequirementRepository
    ) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.currentUserService = currentUserService;
        this.authorizationService = authorizationService;
        this.submissionRepository = submissionRepository;
        this.aiEvaluationRepository = aiEvaluationRepository;
        this.commentRepository = commentRepository;
        this.taskRequirementRepository = taskRequirementRepository;
    }

    // Create Task
    @Transactional
    public Task createTask(Task task, Long projectId, Long employeeId) {
        User currentUser = currentUserService.getCurrentUser();

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        authorizationService.checkManageProject(project, currentUser);

        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        if (employee.getRole() != Role.EMPLOYEE) {
            throw new BadRequestException("Task can only be assigned to users with EMPLOYEE role");
        }
        task.setProject(project);
        task.setAssignedTo(employee);
        task.setStatus(TaskStatus.ASSIGNED);

        Task savedTask = taskRepository.save(task);

        notificationService.createNotification(
                employee.getId(),
                "New Task Assigned",
                "You have been assigned the task: " + savedTask.getTitle(),
                NotificationType.TASK_ASSIGNED,
                "TASK",
                savedTask.getId()
        );

        auditLogService.createLog(
                currentUser,
                AuditAction.TASK_CREATED,
                "TASK",
                savedTask.getId(),
                "Created task: " + savedTask.getTitle()
        );

        return savedTask;
    }

    // Get task By id
    public Task getTaskById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));

        currentUserService.getCurrentUserOptional().ifPresent(user ->
                authorizationService.checkAccessTask(task, user));

        return task;
    }

    // Get all tasks across the current user's authorized scope
    public List<Task> getAllTasks() {
        User currentUser = currentUserService.getCurrentUser();
        if (currentUser.getRole() == Role.ADMIN) {
            return taskRepository.findAll();
        }
        if (currentUser.getRole() == Role.MANAGER) {
            return taskRepository.findAll().stream()
                    .filter(task -> task.getProject() != null
                            && authorizationService.canManageProject(task.getProject(), currentUser))
                    .toList();
        }
        return taskRepository.findByAssignedTo(currentUser);
    }

    // Get Tasks for current authenticated employee
    public List<Task> getMyTasks() {
        User currentUser = currentUserService.getCurrentUser();
        return taskRepository.findByAssignedTo(currentUser);
    }

    // Get Task Assigned to employee
    public List<Task> getTasksByEmployee(Long employeeId) {
        User currentUser = currentUserService.getCurrentUser();

        if (currentUser.getRole() == Role.EMPLOYEE) {
            if (!currentUser.getId().equals(employeeId)) {
                throw new AccessDeniedException("Employees can only view their own tasks");
            }
            return taskRepository.findByAssignedTo(currentUser);
        }

        if (currentUser.getRole() == Role.MANAGER) {
            return taskRepository.findByAssignedToId(employeeId)
                    .stream()
                    .filter(task -> task.getProject() != null && authorizationService.canManageProject(task.getProject(), currentUser))
                    .toList();
        }

        return taskRepository.findByAssignedToId(employeeId);
    }

    // Get task by projects
    public List<Task> getTasksByProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        User currentUser = currentUserService.getCurrentUser();

        if (currentUser.getRole() == Role.EMPLOYEE) {
            return taskRepository.findByProjectId(projectId)
                    .stream()
                    .filter(task -> task.getAssignedTo() != null && task.getAssignedTo().getId().equals(currentUser.getId()))
                    .toList();
        }

        authorizationService.checkAccessProject(project, currentUser);
        return taskRepository.findByProjectId(projectId);
    }

    // Update Task
    @Transactional
    public Task updateTask(Long id, Task updatedTask) {
        Task existingTask = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));

        User currentUser = currentUserService.getCurrentUser();
        authorizationService.checkManageTask(existingTask, currentUser);

        if (updatedTask.getTitle() != null) {
            existingTask.setTitle(updatedTask.getTitle());
        }
        if (updatedTask.getDescription() != null) {
            existingTask.setDescription(updatedTask.getDescription());
        }
        if (updatedTask.getPriority() != null) {
            existingTask.setPriority(updatedTask.getPriority());
        }
        if (updatedTask.getEndDate() != null) {
            existingTask.setEndDate(updatedTask.getEndDate());
        }
        if (updatedTask.getStatus() != null) {
            existingTask.setStatus(updatedTask.getStatus());
        }
        if (updatedTask.getAssignedTo() != null && updatedTask.getAssignedTo().getId() != null) {
            User newAssignee = userRepository.findById(updatedTask.getAssignedTo().getId())
                    .orElse(existingTask.getAssignedTo());
            existingTask.setAssignedTo(newAssignee);
        }
        if (updatedTask.getProject() != null && updatedTask.getProject().getId() != null) {
            Project newProject = projectRepository.findById(updatedTask.getProject().getId())
                    .orElse(existingTask.getProject());
            authorizationService.checkManageProject(newProject, currentUser);
            existingTask.setProject(newProject);
        }

        Task saved = taskRepository.save(existingTask);

        auditLogService.createLog(
                currentUser,
                AuditAction.TASK_UPDATED,
                "TASK",
                saved.getId(),
                "Updated task: " + saved.getTitle()
        );

        return saved;
    }

    // Update Task Status
    @Transactional
    public Task updateTaskStatus(Long id, TaskStatus status) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));

        User currentUser = currentUserService.getCurrentUser();

        if (currentUser.getRole() == Role.EMPLOYEE) {
            if (task.getAssignedTo() == null || !task.getAssignedTo().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("You are not authorized to update status for this task");
            }
            if (status == TaskStatus.COMPLETED || status == TaskStatus.APPROVED || status == TaskStatus.UNDER_REVIEW) {
                throw new AccessDeniedException("Employees cannot directly set task status to " + status + ". Please submit work for review.");
            }
        } else if (currentUser.getRole() == Role.MANAGER) {
            authorizationService.checkManageTask(task, currentUser);
        }

        TaskStatus oldStatus = task.getStatus();
        task.setStatus(status);
        Task saved = taskRepository.save(task);

        if (saved.getAssignedTo() != null && !saved.getAssignedTo().getId().equals(currentUser.getId())) {
            notificationService.createNotification(
                    saved.getAssignedTo().getId(),
                    "Task Status Updated",
                    "The status of \"" + saved.getTitle() + "\" has changed to " + status,
                    NotificationType.TASK_STATUS_CHANGED,
                    "TASK",
                    saved.getId()
            );
        }

        auditLogService.createLog(
                currentUser,
                AuditAction.TASK_STATUS_CHANGED,
                "TASK",
                saved.getId(),
                "Task status changed from " + oldStatus + " to " + status
        );

        return saved;
    }

    // Delete task
    @Transactional
    public void deleteTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));

        User currentUser = currentUserService.getCurrentUser();
        authorizationService.checkManageTask(task, currentUser);

        String taskTitle = task.getTitle();

        // 1. Delete comments for task
        commentRepository.deleteAll(commentRepository.findByTaskIdOrderByCreatedAtAsc(task.getId()));

        // 2. Delete submissions and their evaluations
        List<Submission> submissions = submissionRepository.findByTaskId(task.getId());
        for (Submission sub : submissions) {
            aiEvaluationRepository.findBySubmissionId(sub.getId()).ifPresent(aiEvaluationRepository::delete);
            sub.setPreviousSubmission(null);
            submissionRepository.save(sub);
        }
        submissionRepository.flush();
        submissionRepository.deleteAll(submissions);

        // 3. Delete task requirements
        taskRequirementRepository.deleteAll(taskRequirementRepository.findByTaskId(task.getId()));

        // 4. Delete task
        taskRepository.delete(task);

        auditLogService.createLog(
                currentUser,
                AuditAction.TASK_DELETED,
                "TASK",
                id,
                "Deleted task: " + taskTitle
        );
    }
}
