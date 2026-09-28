package com.Workmanagement.ai.tools;

import com.Workmanagement.ai.model.TaskToolResult;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.task.repository.TaskRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
public class TaskTools {

    private final TaskRepository taskRepository;

    public TaskTools(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Tool(description = "Get a task by its ID. Use this for a specific task lookup.")
    @Transactional(readOnly = true)
    public TaskToolResult getTask(Long taskId) {

        return taskRepository.findById(taskId)
                .map(TaskToolResult::from)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Task not found: " + taskId
                        ));
    }

    @Tool(description = "Get all tasks assigned to an employee by employee ID")
    @Transactional(readOnly = true)
    public List<TaskToolResult> getEmployeeTasks(Long employeeId) {

        return taskRepository.findByAssignedToId(employeeId)
                .stream()
                .map(TaskToolResult::from)
                .toList();
    }

    @Tool(description = "Get all overdue tasks that are not completed")
    @Transactional(readOnly = true)
    public List<TaskToolResult> getOverdueTasks() {

        LocalDate today = LocalDate.now();

        return taskRepository.findAll()
                .stream()
                .filter(task ->
                        task.getEndDate() != null
                                && task.getEndDate().isBefore(today)
                                && task.getStatus() != TaskStatus.COMPLETED
                )
                .map(TaskToolResult::from)
                .toList();
    }

    @Tool(description = "Get all tasks belonging to a project by project ID")
    @Transactional(readOnly = true)
    public List<TaskToolResult> getProjectTasks(Long projectId) {

        return taskRepository.findByProjectId(projectId)
                .stream()
                .map(TaskToolResult::from)
                .toList();
    }
}