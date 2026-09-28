package com.Workmanagement.ai.tools;

import com.Workmanagement.common.dto.TaskToolResult;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.task.repository.TaskRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only AI agent tools for tasks.
 * <p>
 * IMPORTANT: tools return flat {@link TaskToolResult} DTOs instead of
 * JPA entities. Returning entities directly caused serialization
 * failures ("Failed to generate content") because lazy relations
 * (project / assignedTo) become Hibernate proxies that Jackson cannot
 * serialize cleanly inside a Spring AI tool response.
 */
@Component
public class TaskTools {

    private final TaskRepository taskRepository;
    private final ToolMapper toolMapper;

    public TaskTools(TaskRepository taskRepository, ToolMapper toolMapper) {
        this.taskRepository = taskRepository;
        this.toolMapper = toolMapper;
    }

    @Tool(description = "Get a task by its ID. Returns task title, description, status, priority, deadline, project, assigned employee and requirements.")
    public TaskToolResult getTask(Long taskId) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Task not found: " + taskId
                        ));

        return toolMapper.toTaskToolResult(task);
    }

    @Tool(description = "Get all tasks assigned to an employee by employee ID")
    public List<TaskToolResult> getEmployeeTasks(Long employeeId) {

        return taskRepository.findByAssignedToId(employeeId)
                .stream()
                .map(toolMapper::toTaskToolResult)
                .toList();
    }

    @Tool(description = "Get all overdue tasks (deadline passed and not completed)")
    public List<TaskToolResult> getOverdueTasks() {

        LocalDate today = LocalDate.now();

        return taskRepository.findAll()
                .stream()
                .filter(task ->
                        task.getEndDate() != null
                                && task.getEndDate().isBefore(today)
                                && task.getStatus() != TaskStatus.COMPLETED
                )
                .map(toolMapper::toTaskToolResult)
                .toList();
    }

    @Tool(description = "Get all tasks belonging to a project by project ID")
    public List<TaskToolResult> getProjectTasks(Long projectId) {

        return taskRepository.findByProjectId(projectId)
                .stream()
                .map(toolMapper::toTaskToolResult)
                .toList();
    }
}
