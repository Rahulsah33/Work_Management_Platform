package com.Workmanagement.project.service;

import com.Workmanagement.project.entity.Project;
import com.Workmanagement.project.entity.ProjectStatus;
import com.Workmanagement.project.repository.ProjectRepository;
import com.Workmanagement.user.entity.User;
import com.Workmanagement.user.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    private final UserRepository  userRepository;

    //
    public Project createProject(Project project, String managerEmail) {

        User manager = userRepository.findByEmail(managerEmail)
                .orElseThrow(() ->
                        new RuntimeException("Manager not found"));

        if (manager.getRole().name().equals("EMPLOYEE")) {
            throw new RuntimeException(
                    "Only ADMIN or MANAGER can create a project");
        }
        project.setManager(manager);

        if (project.getStatus() == null) {
            project.setStatus(ProjectStatus.PLANNED);
        }
        project.setCreatedAt(java.time.LocalDateTime.now());
        return projectRepository.save(project);

    }

    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    public Project getProjectById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found"));
    }

    public Project updateProject(Long id, Project updatedProject) {
        Project existingProject = getProjectById(id);

        if (updatedProject.getName() != null) {
            existingProject.setName(updatedProject.getName());
        }
        if (updatedProject.getDescription() != null) {
            existingProject.setDescription(updatedProject.getDescription());
        }
        if (updatedProject.getStatus() != null) {
            existingProject.setStatus(updatedProject.getStatus());
        }
        if (updatedProject.getStartDate() != null) {
            existingProject.setStartDate(updatedProject.getStartDate());
        }
        if (updatedProject.getEndDate() != null) {
            existingProject.setEndDate(updatedProject.getEndDate());
        }
        if (updatedProject.getManager() != null) {
            existingProject.setManager(updatedProject.getManager());
        }

        return projectRepository.save(existingProject);
    }

    public void deleteProjectById(Long id) {
        Project project = getProjectById(id);
        projectRepository.delete(project);
    }


}
