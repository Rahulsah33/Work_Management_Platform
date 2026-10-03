package com.Workmanagement.project.controller;

import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Projects", description = "Endpoints for managing projects, lifecycles, and manager assignments")
@AllArgsConstructor
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "Get all projects", description = "Retrieves all projects visible to the authenticated user (scoped to manager ownership for managers, global for admin).")
    @GetMapping
    public ResponseEntity<List<Project>> getAllProjects() {
        return ResponseEntity.ok(projectService.getAllProjects());
    }

    @Operation(summary = "Create a new project", description = "Creates a new project. Managers can create projects assigned to themselves; Admins can assign to any manager.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project successfully created"),
            @ApiResponse(responseCode = "403", description = "Forbidden if user is employee or unauthorized")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Project> createProject(@RequestBody Project project, Authentication authentication) {
        Project savedProject = projectService.createProject(project, authentication.getName());
        return ResponseEntity.ok(savedProject);
    }

    @Operation(summary = "Get project by ID", description = "Retrieves details of a project by its ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project found and returned"),
            @ApiResponse(responseCode = "404", description = "Project not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Project> getProjectById(@Parameter(description = "Project ID", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProjectById(id));
    }

    @Operation(summary = "Update project", description = "Updates an existing project. Managers can only update projects they own; Admins can update any project.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project updated successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden if manager does not own the project"),
            @ApiResponse(responseCode = "404", description = "Project not found")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Project> updateProject(@Parameter(description = "Project ID", required = true) @PathVariable Long id, @RequestBody Project project) {
        return ResponseEntity.ok(projectService.updateProject(id, project));
    }

    @Operation(summary = "Delete project", description = "Deletes a project by ID. Managers can only delete their own projects; Admins can delete any project.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden if manager does not own the project"),
            @ApiResponse(responseCode = "404", description = "Project not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<String> deleteProjectById(@Parameter(description = "Project ID", required = true) @PathVariable Long id) {
        projectService.deleteProjectById(id);
        return ResponseEntity.ok("Project deleted successfully");
    }
}
