package com.Workmanagement.task.service;

import com.Workmanagement.audit.entity.AuditAction;
import com.Workmanagement.audit.service.AuditLogService;
import com.Workmanagement.auth.security.AuthorizationService;
import com.Workmanagement.auth.security.CurrentUserService;
import com.Workmanagement.common.exception.ResourceNotFoundException;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskRequirement;
import com.Workmanagement.task.repository.TaskRequirementRepository;
import com.Workmanagement.task.repository.TaskRepository;
import com.Workmanagement.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskRequirementService {

    private final TaskRequirementRepository requirementRepository;
    private final TaskRepository taskRepository;
    private final AuditLogService auditLogService;
    private final CurrentUserService currentUserService;
    private final AuthorizationService authorizationService;

    public TaskRequirementService(
            TaskRequirementRepository requirementRepository,
            TaskRepository taskRepository,
            AuditLogService auditLogService,
            CurrentUserService currentUserService,
            AuthorizationService authorizationService) {

        this.requirementRepository = requirementRepository;
        this.taskRepository = taskRepository;
        this.auditLogService = auditLogService;
        this.currentUserService = currentUserService;
        this.authorizationService = authorizationService;
    }

    // Create requirement for a task
    @Transactional
    public TaskRequirement createRequirement(
            Long taskId,
            TaskRequirement requirement) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Task not found with id: " + taskId));

        User currentUser = currentUserService.getCurrentUser();
        authorizationService.checkManageTask(task, currentUser);

        requirement.setTask(task);

        TaskRequirement saved = requirementRepository.save(requirement);

        auditLogService.createLog(
                currentUser,
                AuditAction.REQUIREMENT_CREATED,
                "TASK_REQUIREMENT",
                saved.getId(),
                "Created requirement for task ID " + taskId + ": " + saved.getDescription()
        );

        return saved;
    }

    // Get all requirements of a task
    public List<TaskRequirement> getRequirementsByTask(
            Long taskId) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + taskId));

        currentUserService.getCurrentUserOptional().ifPresent(user ->
                authorizationService.checkAccessTask(task, user));

        return requirementRepository.findByTaskId(taskId);
    }

    // Get requirement by ID
    public TaskRequirement getRequirementById(Long id) {

        TaskRequirement requirement = requirementRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Requirement not found with id: " + id));

        currentUserService.getCurrentUserOptional().ifPresent(user -> {
            if (requirement.getTask() != null) {
                authorizationService.checkAccessTask(requirement.getTask(), user);
            }
        });

        return requirement;
    }

    // Update requirement
    @Transactional
    public TaskRequirement updateRequirement(
            Long id,
            TaskRequirement updatedRequirement) {

        TaskRequirement existingRequirement =
                getRequirementById(id);

        User currentUser = currentUserService.getCurrentUser();
        if (existingRequirement.getTask() != null) {
            authorizationService.checkManageTask(existingRequirement.getTask(), currentUser);
        }

        existingRequirement.setDescription(
                updatedRequirement.getDescription());

        existingRequirement.setWeight(
                updatedRequirement.getWeight());

        existingRequirement.setMandatory(
                updatedRequirement.getMandatory());

        TaskRequirement saved = requirementRepository.save(existingRequirement);

        auditLogService.createLog(
                currentUser,
                AuditAction.REQUIREMENT_UPDATED,
                "TASK_REQUIREMENT",
                saved.getId(),
                "Updated requirement ID: " + id
        );

        return saved;
    }

    // Delete requirement
    @Transactional
    public void deleteRequirement(Long id) {

        TaskRequirement requirement =
                getRequirementById(id);

        User currentUser = currentUserService.getCurrentUser();
        if (requirement.getTask() != null) {
            authorizationService.checkManageTask(requirement.getTask(), currentUser);
        }

        requirementRepository.delete(requirement);

        auditLogService.createLog(
                currentUser,
                AuditAction.REQUIREMENT_DELETED,
                "TASK_REQUIREMENT",
                id,
                "Deleted requirement ID: " + id
        );
    }
}
