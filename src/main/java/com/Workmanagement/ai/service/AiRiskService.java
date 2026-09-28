package com.Workmanagement.ai.service;

import com.Workmanagement.ai.model.AiRiskResult;
import com.Workmanagement.task.entity.Task;
import com.Workmanagement.task.repository.TaskRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class AiRiskService {

    private final ChatClient chatClient;
    private final TaskRepository taskRepository;

    public AiRiskService(
            ChatClient.Builder chatClientBuilder,
            TaskRepository taskRepository
    ) {
        this.chatClient = chatClientBuilder.build();
        this.taskRepository = taskRepository;
    }

    public AiRiskResult analyzeRisk(Long taskId) {

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() ->
                        new RuntimeException("Task not found: " + taskId));

        long daysRemaining = ChronoUnit.DAYS.between(
                LocalDate.now(),
                task.getEndDate()
        );

        String prompt = """
                You are an AI work management assistant.

                Analyze the following task and explain its work risk.

                TASK TITLE:
                %s

                TASK DESCRIPTION:
                %s

                STATUS:
                %s

                PRIORITY:
                %s

                DEADLINE:
                %s

                DAYS REMAINING:
                %d

                RULE-BASED RISK:
                %s

                Provide:
                1. riskLevel
                2. explanation
                3. recommendation

                Return ONLY valid JSON matching:
                {
                  "riskLevel": "LOW | MEDIUM | HIGH",
                  "explanation": "...",
                  "recommendation": "..."
                }
                """.formatted(
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getEndDate(),
                daysRemaining,
                calculateRuleBasedRisk(daysRemaining, task)
        );

        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .entity(AiRiskResult.class);
    }

    private String calculateRuleBasedRisk(
            long daysRemaining,
            Task task
    ) {

        if (task.getStatus().name().equals("COMPLETED")) {
            return "LOW";
        }

        if (daysRemaining < 0) {
            return "HIGH";
        }

        if (daysRemaining <= 2) {
            return "MEDIUM";
        }

        return "LOW";
    }
}