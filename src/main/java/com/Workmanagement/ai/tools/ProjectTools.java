package com.Workmanagement.ai.tools;

import com.Workmanagement.ai.model.ProjectToolResult;
import com.Workmanagement.auth.security.AuthorizationService;
import com.Workmanagement.auth.security.CurrentUserService;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.repository.ProjectRepository;
import com.Workmanagement.user.entity.User;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class ProjectTools {

    private final ProjectRepository projectRepository;
    private final CurrentUserService currentUserService;
    private final AuthorizationService authorizationService;

    public ProjectTools(
            ProjectRepository projectRepository,
            CurrentUserService currentUserService,
            AuthorizationService authorizationService
    ) {
        this.projectRepository = projectRepository;
        this.currentUserService = currentUserService;
        this.authorizationService = authorizationService;
    }

    @Tool(description = "Get a project by its ID")
    @Transactional(readOnly = true)
    public ProjectToolResult getProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        User currentUser = currentUserService.getCurrentUser();
        if (!authorizationService.canAccessProject(project, currentUser)) {
            throw new RuntimeException("Access denied to project: " + projectId);
        }

        return ProjectToolResult.from(project);
    }

    @Tool(description = "Get all projects accessible to the current user")
    @Transactional(readOnly = true)
    public List<ProjectToolResult> getAllProjects() {
        User currentUser = currentUserService.getCurrentUser();
        if (currentUserService.isAdmin()) {
            return projectRepository.findAll()
                    .stream()
                    .map(ProjectToolResult::from)
                    .toList();
        }

        return projectRepository.findByManagerId(currentUser.getId())
                .stream()
                .map(ProjectToolResult::from)
                .toList();
    }
}