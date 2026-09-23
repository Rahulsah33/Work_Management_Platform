package com.Workmanagement.task.service;

import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskRequirement;
import com.Workmanagement.task.repository.TaskRequirementRepository;
import com.Workmanagement.task.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskRequirementService {

    private final TaskRequirementRepository requirementRepository;
    private final TaskRepository taskRepository;

    public TaskRequirementService(
            TaskRequirementRepository requirementRepository,
            TaskRepository taskRepository) {

        this.requirementRepository = requirementRepository;
        this.taskRepository = taskRepository;
    }

    // Create requirement for a task
    public TaskRequirement createRequirement(
            Long taskId,
            TaskRequirement requirement) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() ->
                        new RuntimeException("Task not found"));

        requirement.setTask(task);

        return requirementRepository.save(requirement);
    }

    // Get all requirements of a task
    public List<TaskRequirement> getRequirementsByTask(
            Long taskId) {

        if (!taskRepository.existsById(taskId)) {
            throw new RuntimeException("Task not found");
        }

        return requirementRepository.findByTaskId(taskId);
    }

    // Get requirement by ID
    public TaskRequirement getRequirementById(Long id) {

        return requirementRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Requirement not found"));
    }

    // Update requirement
    public TaskRequirement updateRequirement(
            Long id,
            TaskRequirement updatedRequirement) {

        TaskRequirement existingRequirement =
                getRequirementById(id);

        existingRequirement.setDescription(
                updatedRequirement.getDescription());

        existingRequirement.setWeight(
                updatedRequirement.getWeight());

        existingRequirement.setMandatory(
                updatedRequirement.getMandatory());

        return requirementRepository.save(existingRequirement);
    }

    // Delete requirement
    public void deleteRequirement(Long id) {

        TaskRequirement requirement =
                getRequirementById(id);

        requirementRepository.delete(requirement);
    }
}