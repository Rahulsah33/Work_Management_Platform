package com.Workmanagement.task.controller;

import com.Workmanagement.task.entity.TaskRequirement;
import com.Workmanagement.task.service.TaskRequirementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskRequirementController {

    private final TaskRequirementService requirementService;

    public TaskRequirementController(
            TaskRequirementService requirementService) {
        this.requirementService = requirementService;
    }

    // Create requirement for a task
    @PostMapping("/{taskId}/requirements")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<TaskRequirement> createRequirement(
            @PathVariable Long taskId,
            @RequestBody TaskRequirement requirement) {

        return ResponseEntity.ok(
                requirementService.createRequirement(
                        taskId, requirement)
        );
    }

    // Get all requirements for a task
    @GetMapping("/{taskId}/requirements")
    public ResponseEntity<List<TaskRequirement>> getRequirements(
            @PathVariable Long taskId) {

        return ResponseEntity.ok(
                requirementService.getRequirementsByTask(taskId)
        );
    }

    // Get requirement by ID
    @GetMapping("/requirements/{id}")
    public ResponseEntity<TaskRequirement> getRequirement(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                requirementService.getRequirementById(id)
        );
    }

    // Update requirement
    @PutMapping("/requirements/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<TaskRequirement> updateRequirement(
            @PathVariable Long id,
            @RequestBody TaskRequirement requirement) {

        return ResponseEntity.ok(
                requirementService.updateRequirement(
                        id, requirement)
        );
    }

    // Delete requirement
    @DeleteMapping("/requirements/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteRequirement(
            @PathVariable Long id) {

        requirementService.deleteRequirement(id);

        return ResponseEntity.noContent().build();
    }
}