package com.Workmanagement.task.controller;

import com.Workmanagement.task.entity.TaskRequirement;
import com.Workmanagement.task.service.TaskRequirementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Task Requirements", description = "Endpoints for managing task requirements, acceptance criteria, and weights")
@RestController
@RequestMapping("/api/tasks")
public class TaskRequirementController {

    private final TaskRequirementService requirementService;

    public TaskRequirementController(
            TaskRequirementService requirementService) {
        this.requirementService = requirementService;
    }

    @Operation(summary = "Create task requirement", description = "Creates a requirement/acceptance criteria for a task with description, weight, and mandatory flag. Requires project manager ownership or admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Requirement successfully created"),
            @ApiResponse(responseCode = "403", description = "Forbidden if manager does not own the project")
    })
    @PostMapping("/{taskId}/requirements")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<TaskRequirement> createRequirement(
            @Parameter(description = "Task ID", required = true) @PathVariable Long taskId,
            @RequestBody TaskRequirement requirement) {

        return ResponseEntity.ok(
                requirementService.createRequirement(
                        taskId, requirement)
        );
    }

    @Operation(summary = "Get all requirements for a task", description = "Retrieves all requirements associated with a task.")
    @GetMapping("/{taskId}/requirements")
    public ResponseEntity<List<TaskRequirement>> getRequirements(
            @Parameter(description = "Task ID", required = true) @PathVariable Long taskId) {

        return ResponseEntity.ok(
                requirementService.getRequirementsByTask(taskId)
        );
    }

    @Operation(summary = "Get requirement by ID", description = "Retrieves a specific task requirement by its ID.")
    @GetMapping("/requirements/{id}")
    public ResponseEntity<TaskRequirement> getRequirement(
            @Parameter(description = "Requirement ID", required = true) @PathVariable Long id) {

        return ResponseEntity.ok(
                requirementService.getRequirementById(id)
        );
    }

    @Operation(summary = "Update requirement", description = "Updates a task requirement. Enforces manager project ownership.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Requirement updated successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden if manager does not own the project")
    })
    @PutMapping("/requirements/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<TaskRequirement> updateRequirement(
            @Parameter(description = "Requirement ID", required = true) @PathVariable Long id,
            @RequestBody TaskRequirement requirement) {

        return ResponseEntity.ok(
                requirementService.updateRequirement(
                        id, requirement)
        );
    }

    @Operation(summary = "Delete requirement", description = "Deletes a task requirement by ID. Enforces manager project ownership.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Requirement deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden if manager does not own the project")
    })
    @DeleteMapping("/requirements/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteRequirement(
            @Parameter(description = "Requirement ID", required = true) @PathVariable Long id) {

        requirementService.deleteRequirement(id);

        return ResponseEntity.noContent().build();
    }
}