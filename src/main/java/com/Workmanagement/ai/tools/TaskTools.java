package com.Workmanagement.ai.tools;

import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.task.repository.TaskRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class TaskTools {

    private final TaskRepository taskRepository;

    public TaskTools(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Tool(description = "Get a task by its ID")
    public Task getTask(Long taskId) {

        return taskRepository.findById(taskId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Task not found: " + taskId
                        ));
    }

    @Tool(description = "Get all tasks assigned to an employee")
    public List<Task> getEmployeeTasks(Long employeeId) {

        return taskRepository.findByAssignedToId(employeeId);
    }

    @Tool(description = "Get all overdue tasks that are not completed")
    public List<Task> getOverdueTasks() {

        LocalDate today = LocalDate.now();

        return taskRepository.findAll()
                .stream()
                .filter(task ->
                        task.getEndDate() != null
                                && task.getEndDate().isBefore(today)
                                && task.getStatus() != TaskStatus.COMPLETED
                )
                .toList();
    }

    @Tool(description = "Get all tasks belonging to a project")
    public List<Task> getProjectTasks(Long projectId) {

        return taskRepository.findByProjectId(projectId);
    }
}