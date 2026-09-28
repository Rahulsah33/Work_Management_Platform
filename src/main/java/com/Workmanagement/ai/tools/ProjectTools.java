package com.Workmanagement.ai.tools;

import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.repository.ProjectRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProjectTools {

    private final ProjectRepository projectRepository;

    public ProjectTools(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Tool(description = "Get a project by its ID")
    public Project getProject(Long projectId) {

        return projectRepository.findById(projectId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Project not found: " + projectId
                        ));
    }

    @Tool(description = "Get all projects")
    public List<Project> getAllProjects() {

        return projectRepository.findAll();
    }
}