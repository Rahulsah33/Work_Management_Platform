package com.Workmanagement.task.service;

import com.Workmanagement.audit.entity.AuditAction;
import com.Workmanagement.audit.service.AuditService;
import com.Workmanagement.notification.entity.NotificationType;
import com.Workmanagement.notification.service.NotificationService;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.repository.ProjectRepository;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.task.repository.TaskRepository;
import com.Workmanagement.user.entity.Role;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public TaskService(TaskRepository taskRepository,
                       ProjectRepository projectRepository,
                       UserRepository userRepository,
                       NotificationService notificationService,
                       AuditService auditService) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }


    // Create Task
    public Task createTask(Task task, Long projectId, Long employeeId, String managerEmail) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found with id: " + projectId));

        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + employeeId));

        if (employee.getRole() != Role.EMPLOYEE) {
            throw new RuntimeException("Task can only be assigned to employees");
        }
        task.setProject(project);
        task.setAssignedTo(employee);
        task.setStatus(TaskStatus.ASSIGNED);

        Task saved = taskRepository.save(task);

        // Notify the employee about the new assignment
        notificationService.createNotification(
                employee.getId(),
                "New task assigned",
                "Task \"" + saved.getTitle() + "\" has been assigned to you."
                        + (saved.getEndDate() != null ? " Deadline: " + saved.getEndDate() : ""),
                NotificationType.TASK_ASSIGNED);

        // Audit trail
        auditService.recordByEmail(managerEmail, AuditAction.TASK_CREATED,
                "Task", saved.getId(),
                "Created and assigned task \"" + saved.getTitle() + "\" to employee " + employee.getName());
        auditService.recordByEmail(managerEmail, AuditAction.TASK_ASSIGNED,
                "Task", saved.getId(),
                "Assigned task to employee id=" + employee.getId());

        return saved;
    }

    //Get task By id
    public Task getTaskById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found with id: " + id));
    }

    // Get Task Assigned to employee
    public List<Task> getTasksByEmployee(Long employeeId) {

        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + employeeId));

        return taskRepository.findByAssignedTo(employee);
    }

    /**
     * Employee lists their own tasks - the employee is taken from
     * the authenticated JWT user, never from a client-supplied id.
     */
    public List<Task> getMyTasks(String email) {

        User employee = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        return taskRepository.findByAssignedTo(employee);
    }

    // Get task by projects

    public List<Task> getTasksByProject(Long projectId) {
        return taskRepository.findByProjectId(projectId);
    }

    // update Project
    public Task updateTask(Long id, Task updatedTask, String managerEmail) {

        Task existingTask = getTaskById(id);

        existingTask.setTitle(updatedTask.getTitle());
        existingTask.setDescription(updatedTask.getDescription());
        existingTask.setPriority(updatedTask.getPriority());
        existingTask.setEndDate(updatedTask.getEndDate());

        Task saved = taskRepository.save(existingTask);

        auditService.recordByEmail(managerEmail, AuditAction.TASK_UPDATED,
                "Task", saved.getId(), "Updated task details");

        return saved;
    }

    // Update Task Status
    public Task updateTaskStatus(Long id, TaskStatus status, String actorEmail) {
        Task task = getTaskById(id);
        task.setStatus(status);
        Task saved = taskRepository.save(task);

        AuditAction action = (status == TaskStatus.COMPLETED)
                ? AuditAction.TASK_COMPLETED
                : AuditAction.TASK_STATUS_CHANGED;

        auditService.recordByEmail(actorEmail, action,
                "Task", saved.getId(), "Task status changed to " + status);

        return saved;
    }

    // Delete task
    public void deleteTask(Long id, String managerEmail) {
        Task task = getTaskById(id);
        taskRepository.delete(task);

        auditService.recordByEmail(managerEmail, AuditAction.TASK_DELETED,
                "Task", id, "Deleted task \"" + task.getTitle() + "\"");
    }

}
