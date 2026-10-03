package com.Workmanagement.task.controller;

import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Tasks", description = "Endpoints for creating, assigning, querying, updating, and transitioning task statuses")
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController (TaskService taskService) {
        this.taskService = taskService;
    }

    @Operation(summary = "Get all tasks", description = "Retrieves all tasks across authorized scope (Global for ADMIN, managed projects for MANAGER).")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<Task>> getAllTasks() {
        return ResponseEntity.ok(taskService.getAllTasks());
    }

    @Operation(summary = "Create and assign a task", description = "Creates a new task in a project and assigns it to an employee. Managers can only assign tasks to their managed projects.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task created and assigned successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden if manager does not own the project"),
            @ApiResponse(responseCode = "404", description = "Project or employee not found")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Task> createTask(
            @RequestBody Task task,
            @Parameter(description = "ID of the parent project", required = true) @RequestParam Long projectId,
            @Parameter(description = "ID of the employee to whom the task is assigned", required = true) @RequestParam Long employeeId) {

        return ResponseEntity.ok(
                taskService.createTask(task, projectId, employeeId)
        );
    }

    @Operation(summary = "Get task by ID", description = "Retrieves a task by its ID. Access is checked based on role and assignment/management relationship.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task found and returned"),
            @ApiResponse(responseCode = "403", description = "Forbidden if not authorized to view this task"),
            @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Task> getTaskById(@Parameter(description = "Task ID", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(taskService.getTaskById(id));
    }

    @Operation(summary = "Get current authenticated employee's tasks", description = "Retrieves all tasks assigned to the currently logged-in employee derived from authentication context.")
    @ApiResponse(responseCode = "200", description = "List of current employee tasks")
    @GetMapping({"/me", "/my"})
    public ResponseEntity<List<Task>> getMyTasks() {
        return ResponseEntity.ok(taskService.getMyTasks());
    }

    @Operation(summary = "Get tasks assigned to an employee", description = "Retrieves tasks for a given employee. Employees can only query their own ID; Managers can only query tasks within their projects; Admins can query all.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of tasks assigned to the employee"),
            @ApiResponse(responseCode = "403", description = "Forbidden if attempting unauthorized cross-employee access")
    })
    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<Task>> getTasksByEmployee(@Parameter(description = "Employee ID", required = true) @PathVariable Long employeeId) {
        return ResponseEntity.ok(taskService.getTasksByEmployee(employeeId));
    }

    @Operation(summary = "Get tasks for a project", description = "Retrieves tasks belonging to a specified project.")
    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<Task>> getTasksByProject(@Parameter(description = "Project ID", required = true) @PathVariable Long projectId) {
        return ResponseEntity.ok(taskService.getTasksByProject(projectId));
    }

    @Operation(summary = "Update task", description = "Updates details of a task. Requires ADMIN or MANAGER role with ownership of the project.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Task> updateTask(@Parameter(description = "Task ID", required = true) @PathVariable Long id, @RequestBody Task task) {
        return ResponseEntity.ok(taskService.updateTask(id, task));
    }

    @Operation(summary = "Update task status", description = "Updates the status of a task (e.g. IN_PROGRESS, SUBMITTED). Employees can only update their own assigned tasks and cannot directly set COMPLETED or APPROVED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task status updated successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden if employee attempts invalid transition or unassigned task update")
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<Task> updateTaskStatus(
            @Parameter(description = "Task ID", required = true) @PathVariable Long id,
            @Parameter(description = "New task status (ASSIGNED, IN_PROGRESS, SUBMITTED, UNDER_REVIEW, APPROVED, COMPLETED, CHANGES_REQUESTED)", required = true) @RequestParam TaskStatus status) {
        return ResponseEntity.ok(taskService.updateTaskStatus(id, status));
    }

    @Operation(summary = "Delete task", description = "Deletes a task by ID. Requires ADMIN or project MANAGER ownership.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteTask(@Parameter(description = "Task ID", required = true) @PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }
}
