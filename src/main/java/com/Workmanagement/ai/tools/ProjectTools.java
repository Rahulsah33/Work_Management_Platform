package com.Workmanagement.ai.tools;

import com.Workmanagement.common.dto.ProjectToolResult;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.repository.ProjectRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Read-only AI agent tools for projects.
 * Returns flat {@link ProjectToolResult} DTOs (never JPA entities).
 */
@Component
public class ProjectTools {

    private final ProjectRepository projectRepository;
    private final ToolMapper toolMapper;

    public ProjectTools(ProjectRepository projectRepository,
                        ToolMapper toolMapper) {
        this.projectRepository = projectRepository;
        this.toolMapper = toolMapper;
    }

    @Tool(description = "Get a project by its ID. Returns name, description, status, dates and manager.")
    public ProjectToolResult getProject(Long projectId) {

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Project not found: " + projectId
                        ));

        return toolMapper.toProjectToolResult(project);
    }

    @Tool(description = "Get all projects")
    public List<ProjectToolResult> getAllProjects() {

        return projectRepository.findAll()
                .stream()
                .map(toolMapper::toProjectToolResult)
                .toList();
    }
}
