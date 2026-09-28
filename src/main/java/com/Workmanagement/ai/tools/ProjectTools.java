package com.Workmanagement.ai.tools;

import com.Workmanagement.ai.model.ProjectToolResult;
import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.repository.ProjectRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class ProjectTools {

    private final ProjectRepository projectRepository;

    public ProjectTools(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Tool(description = "Get a project by its ID")
    @Transactional(readOnly = true)
    public ProjectToolResult getProject(Long projectId) {

        return projectRepository.findById(projectId)
                .map(ProjectToolResult::from)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Project not found: " + projectId
                        ));
    }

    @Tool(description = "Get all projects")
    @Transactional(readOnly = true)
    public List<ProjectToolResult> getAllProjects() {

        return projectRepository.findAll()
                .stream()
                .map(ProjectToolResult::from)
                .toList();
    }
}