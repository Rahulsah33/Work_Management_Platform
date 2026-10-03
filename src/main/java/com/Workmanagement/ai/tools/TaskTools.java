package com.Workmanagement.ai.tools;

import com.Workmanagement.ai.model.TaskToolResult;
import com.Workmanagement.auth.security.AuthorizationService;
import com.Workmanagement.auth.security.CurrentUserService;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.repository.ProjectRepository;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.task.repository.TaskRepository;
import com.Workmanagement.user.entity.User;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
public class TaskTools {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final CurrentUserService currentUserService;
    private final AuthorizationService authorizationService;

    public TaskTools(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            CurrentUserService currentUserService,
            AuthorizationService authorizationService
    ) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.currentUserService = currentUserService;
        this.authorizationService = authorizationService;
    }

    @Tool(description = "Get a task by its ID. Use this for a specific task lookup.")
    @Transactional(readOnly = true)
    public TaskToolResult getTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        User currentUser = currentUserService.getCurrentUser();
        if (!authorizationService.canAccessTask(task, currentUser)) {
            throw new RuntimeException("Access denied to task: " + taskId);
        }

        return TaskToolResult.from(task);
    }

    @Tool(description = "Get all tasks assigned to an employee by employee ID")
    @Transactional(readOnly = true)
    public List<TaskToolResult> getEmployeeTasks(Long employeeId) {
        User currentUser = currentUserService.getCurrentUser();
        List<Task> tasks = taskRepository.findByAssignedToId(employeeId);

        if (currentUserService.isAdmin()) {
            return tasks.stream().map(TaskToolResult::from).toList();
        }

        return tasks.stream()
                .filter(task -> authorizationService.canAccessTask(task, currentUser))
                .map(TaskToolResult::from)
                .toList();
    }

    @Tool(description = "Get all overdue tasks that are not completed")
    @Transactional(readOnly = true)
    public List<TaskToolResult> getOverdueTasks() {
        LocalDate today = LocalDate.now();
        User currentUser = currentUserService.getCurrentUser();

        return taskRepository.findAll()
                .stream()
                .filter(task ->
                        task.getEndDate() != null
                                && task.getEndDate().isBefore(today)
                                && task.getStatus() != TaskStatus.COMPLETED
                                && (currentUserService.isAdmin() || authorizationService.canAccessTask(task, currentUser))
                )
                .map(TaskToolResult::from)
                .toList();
    }

    @Tool(description = "Get all tasks belonging to a project by project ID")
    @Transactional(readOnly = true)
    public List<TaskToolResult> getProjectTasks(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        User currentUser = currentUserService.getCurrentUser();
        if (!authorizationService.canAccessProject(project, currentUser)) {
            throw new RuntimeException("Access denied to project tasks: " + projectId);
        }

        return taskRepository.findByProjectId(projectId)
                .stream()
                .map(TaskToolResult::from)
                .toList();
    }
}