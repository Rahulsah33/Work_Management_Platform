package com.Workmanagement.ai.service;

import com.Workmanagement.ai.entity.WorkRisk;
import com.Workmanagement.auth.security.AuthorizationService;
import com.Workmanagement.auth.security.CurrentUserService;
import com.Workmanagement.common.exception.ResourceNotFoundException;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.entity.TaskStatus;
import com.Workmanagement.task.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class WorkRiskService {

    private final TaskRepository taskRepository;
    private final CurrentUserService currentUserService;
    private final AuthorizationService authorizationService;

    public WorkRiskService(
            TaskRepository taskRepository,
            CurrentUserService currentUserService,
            AuthorizationService authorizationService) {
        this.taskRepository = taskRepository;
        this.currentUserService = currentUserService;
        this.authorizationService = authorizationService;
    }

    public WorkRisk analyzeTaskRisk(Long taskId) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Task not found with id: " + taskId));

        currentUserService.getCurrentUserOptional().ifPresent(user ->
                authorizationService.checkAccessTask(task, user));

        LocalDate today = LocalDate.now();

        long daysRemaining = ChronoUnit.DAYS.between(
                today,
                task.getEndDate()
        );

        String riskLevel;
        String reason;

        // Already completed
        if (task.getStatus() == TaskStatus.COMPLETED) {

            riskLevel = "LOW";
            reason = "Task has already been completed.";

        }
        // Deadline has passed
        else if (daysRemaining < 0) {

            riskLevel = "HIGH";
            reason = "Task deadline has already passed.";

        }
        // Deadline is today
        else if (daysRemaining == 0) {

            riskLevel = "HIGH";
            reason = "Task deadline is today and the task is not completed.";

        }
        // Deadline within 2 days
        else if (daysRemaining <= 2) {

            riskLevel = "MEDIUM";
            reason = "Task deadline is approaching within 2 days.";

        }
        // Task is still in early/normal period
        else {

            riskLevel = "LOW";
            reason = "Task has sufficient time remaining.";
        }

        return new WorkRisk(
                task.getId(),
                task.getTitle(),
                riskLevel,
                reason,
                daysRemaining
        );
    }
}